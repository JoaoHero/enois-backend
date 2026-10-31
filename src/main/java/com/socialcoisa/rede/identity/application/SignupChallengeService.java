package com.socialcoisa.rede.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Captcha próprio para o cadastro quando o Cloudflare Turnstile não está configurado.
 *
 * É um desafio de soma assinado (HMAC) e com prazo curto. The answer only enters the HMAC input, so
 * the token carries nothing that can be checked offline against candidate answers; the client gets
 * only the question. Além da resposta certa, exigimos pelo menos 1,2s desde a emissão (um script que
 * resolve na hora não passa). Single use is tracked in the database, shared by every pod.
 *
 * <p>ponytail: a sum question is readable by any bot; this is only a fallback. Production should
 * configure Turnstile (TURNSTILE_SITE_KEY / TURNSTILE_SECRET_KEY), which takes precedence.</p>
 *
 * O honeypot e o rate limit por IP continuam valendo em paralelo. Quando TURNSTILE_SITE_KEY e
 * TURNSTILE_SECRET_KEY existirem, o Turnstile assume e este desafio não é usado.
 */
@Service
public class SignupChallengeService {

	private static final Logger LOG = LoggerFactory.getLogger(SignupChallengeService.class);
	private static final Duration TTL = Duration.ofMinutes(5);
	private static final long MIN_ELAPSED_MS = 1_200L;

	private final boolean enabled;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();
	private final byte[] secret;
	private final JdbcTemplate jdbc;

	public SignupChallengeService(Clock clock, JdbcTemplate jdbc,
			@Value("${rede.security.signup-captcha-enabled:true}") boolean enabled,
			@Value("${rede.security.encryption-key:}") String encryptionKey) {
		this.enabled = enabled;
		this.clock = clock;
		this.jdbc = jdbc;
		this.secret = resolveSecret(encryptionKey, enabled);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Challenge issue() {
		int left = 2 + random.nextInt(8);
		int right = 2 + random.nextInt(8);
		int answer = left + right;
		Instant now = clock.instant();
		Instant expiresAt = now.plus(TTL);
		String nonce = UUID.randomUUID().toString().replace("-", "");
		String payload = now.toEpochMilli() + "|" + expiresAt.toEpochMilli() + "|" + nonce;
		String token = encode(payload.getBytes(StandardCharsets.UTF_8)) + "." + encode(sign(payload, answer));
		return new Challenge(token, "Quanto é " + left + " + " + right + "?", expiresAt);
	}

	public void require(String challengeId, Integer answer) {
		if (!enabled) {
			return;
		}
		if (!StringUtils.hasText(challengeId) || answer == null) {
			throw new ApiException(400, "SIGNUP_CAPTCHA_REQUIRED",
				"Resolva a verificação de segurança para criar a conta.");
		}
		String[] parts = challengeId.split("\\.");
		if (parts.length != 2) {
			throw invalid();
		}
		byte[] payloadBytes;
		byte[] signature;
		try {
			payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
			signature = Base64.getUrlDecoder().decode(parts[1]);
		}
		catch (IllegalArgumentException exception) {
			throw invalid();
		}
		String payload = new String(payloadBytes, StandardCharsets.UTF_8);
		// A wrong answer and a forged token are indistinguishable: both fail the HMAC.
		if (!MessageDigest.isEqual(signature, sign(payload, answer))) {
			throw invalid();
		}

		String[] fields = payload.split("\\|");
		if (fields.length != 3) {
			throw invalid();
		}
		long issuedAt;
		long expiresAt;
		try {
			issuedAt = Long.parseLong(fields[0]);
			expiresAt = Long.parseLong(fields[1]);
		}
		catch (NumberFormatException exception) {
			throw invalid();
		}
		long now = clock.millis();
		if (expiresAt < now) {
			throw new ApiException(400, "SIGNUP_CAPTCHA_EXPIRED",
				"A verificação de segurança expirou. Recarregue e tente de novo.");
		}
		if (now - issuedAt < MIN_ELAPSED_MS) {
			throw new ApiException(400, "SIGNUP_CAPTCHA_TOO_FAST",
				"Calma aí: resolva a verificação com um tempinho e envie de novo.");
		}
		remember(fields[2], expiresAt, now);
	}

	/** The primary key makes the second use of a nonce fail on any pod. */
	private void remember(String nonce, long expiresAt, long now) {
		jdbc.update("delete from signup_challenge_nonces where expires_at < ?", new Timestamp(now));
		try {
			jdbc.update("insert into signup_challenge_nonces (nonce, expires_at) values (?, ?)", nonce,
				new Timestamp(expiresAt));
		}
		catch (DuplicateKeyException reused) {
			throw new ApiException(400, "SIGNUP_CAPTCHA_REUSED",
				"Esta verificação já foi usada. Recarregue e tente de novo.");
		}
	}

	private ApiException invalid() {
		return new ApiException(400, "SIGNUP_CAPTCHA_INVALID",
			"Verificação de segurança inválida. Recarregue e tente de novo.");
	}

	private byte[] sign(String payload, int answer) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return mac.doFinal((payload + "|" + answer).getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception exception) {
			throw new IllegalStateException("HmacSHA256 indisponível", exception);
		}
	}

	private static String encode(byte[] value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	private static byte[] resolveSecret(String encryptionKey, boolean enabled) {
		if (StringUtils.hasText(encryptionKey)) {
			try {
				byte[] decoded = Base64.getDecoder().decode(encryptionKey.trim());
				if (decoded.length >= 16) {
					return decoded;
				}
			}
			catch (IllegalArgumentException ignored) {
				// Chave fora do formato base64: tratada abaixo.
			}
		}
		if (enabled) {
			// Chave efêmera por pod quebra o desafio entre instâncias e é previsível para o atacante
			// que compartilha o processo? Não: é aleatória, mas o desafio de um pod não vale no
			// outro. Em produção, exija a chave estável (E-36).
			throw new IllegalStateException(
				"REDE_SECURITY_ENCRYPTION_KEY obrigatoria para o desafio de cadastro (32 bytes em base64).");
		}
		byte[] generated = new byte[32];
		new SecureRandom().nextBytes(generated);
		LOG.warn("Desafio de cadastro desligado sem chave de criptografia: usando segredo efêmero.");
		return generated;
	}

	public record Challenge(String challengeId, String question, Instant expiresAt) {
	}
}
