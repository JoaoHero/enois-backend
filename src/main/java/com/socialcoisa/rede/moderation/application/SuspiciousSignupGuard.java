package com.socialcoisa.rede.moderation.application;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.security.SignupAttackGuard;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Cadastro com o padrão de assinatura do ataque não nasce, and repeated hits from one IP close
 * signups from it for a short, expiring period.
 *
 * Depois de 20/10 e 22/10 (contas "probe-x3mm", "probe-p914", sempre do mesmo provedor de
 * hospedagem), esperar a triagem automática é esperar o atacante criar o próximo lote. Aqui a
 * contenção é imediata: o nome/username derivado bate no padrão configurado
 * ({@code rede.security.suspicious.name-patterns}, o mesmo da triagem) → 403 genérico, sem
 * ensinar a regra.
 *
 * <p>A name is a weak signal and the IP can be shared (NAT, carrier), so this never writes
 * {@code blocked_ips} and never touches login: after {@value #STRIKES_TO_BLOCK} hits in the window,
 * only signups from that IP are refused for {@code SIGNUP_COOLDOWN_MS}. A permanent block stays a
 * moderator decision in the panel.</p>
 *
 * <p>ponytail: per-pod memory; a shared store if the cooldown must hold across replicas.</p>
 */
@Service
public class SuspiciousSignupGuard implements SignupAttackGuard {

	private static final Logger LOG = LoggerFactory.getLogger(SuspiciousSignupGuard.class);

	private final boolean enabled;
	private final List<String> patterns;
	/** Um único nome suspeito não pode derrubar o IP de uma rede inteira (N-08). */
	private final java.util.Map<String, java.util.Deque<Long>> recentByIp = new java.util.concurrent.ConcurrentHashMap<>();
	private static final int STRIKES_TO_BLOCK = 3;
	private static final long STRIKE_WINDOW_MS = 10 * 60 * 1000;
	private static final long SIGNUP_COOLDOWN_MS = 60 * 60 * 1000;
	private static final int MAX_KEYS = 20_000;
	private final java.util.Map<String, Long> cooldownUntil = new java.util.concurrent.ConcurrentHashMap<>();
	/** Ceiling for both maps, so a flood of distinct IPs cannot grow memory without bound. */
	private final int maxKeys;

	@org.springframework.beans.factory.annotation.Autowired
	public SuspiciousSignupGuard(
			@Value("${rede.security.signup-guard-enabled:true}") boolean enabled,
			@Value("${rede.security.suspicious.name-patterns:probe}") String patterns) {
		this(enabled, patterns, MAX_KEYS);
	}

	SuspiciousSignupGuard(boolean enabled, String patterns, int maxKeys) {
		this.maxKeys = maxKeys;
		this.enabled = enabled;
		this.patterns = Arrays.stream(patterns.split(","))
			.map(String::trim)
			.filter(pattern -> !pattern.isEmpty())
			.map(pattern -> pattern.toLowerCase(Locale.ROOT))
			.toList();
	}

	@Override
	public void checkSignup(String name, String email, String ip) {
		if (!enabled || patterns.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		Long until = cooldownUntil.get(ip == null ? "" : ip);
		if (until != null && until > now) {
			throw refused();
		}
		if (!StringUtils.hasText(name)) {
			return;
		}
		String typed = name.trim().toLowerCase(Locale.ROOT);
		if (patterns.stream().noneMatch(typed::contains)) {
			return;
		}
		if (registerStrike(ip)) {
			startCooldown(ip, now);
		}
		LOG.warn("Cadastro recusado por padrão de ataque (ip={}, email={})", ip, mask(email));
		throw refused();
	}

	/** Mensagem genérica: o atacante não descobre qual palavra disparou o bloqueio. */
	private static ApiException refused() {
		return new ApiException(403, "SIGNUP_REFUSED",
			"Não foi possível criar a conta agora. Tente novamente mais tarde.");
	}

	private boolean registerStrike(String ip) {
		if (!StringUtils.hasText(ip) || "desconhecido".equals(ip)) {
			return false;
		}
		long now = System.currentTimeMillis();
		if (recentByIp.size() >= maxKeys) {
			recentByIp.clear();
		}
		java.util.Deque<Long> strikes = recentByIp.computeIfAbsent(ip, key -> new java.util.ArrayDeque<>());
		synchronized (strikes) {
			while (!strikes.isEmpty() && now - strikes.peekFirst() > STRIKE_WINDOW_MS) {
				strikes.pollFirst();
			}
			strikes.addLast(now);
			if (strikes.size() < STRIKES_TO_BLOCK) {
				return false;
			}
		}
		recentByIp.remove(ip);
		return true;
	}

	private void startCooldown(String ip, long now) {
		if (cooldownUntil.size() >= maxKeys) {
			cooldownUntil.values().removeIf(until -> until <= now);
			if (cooldownUntil.size() >= maxKeys) {
				// Same bound as AuthThrottle: live cooldowns are dropped rather than growing without end.
				cooldownUntil.clear();
			}
		}
		cooldownUntil.put(ip, now + SIGNUP_COOLDOWN_MS);
		LOG.warn("Cadastros pausados para um IP após indícios repetidos (ip={})", ip);
	}

	int trackedKeys() {
		return recentByIp.size() + cooldownUntil.size();
	}

	/** O e-mail não vai inteiro para o log: só o bastante para o time reconhecer a conta. */
	private String mask(String email) {
		if (!StringUtils.hasText(email) || !email.contains("@")) {
			return "?";
		}
		int at = email.indexOf('@');
		return email.charAt(0) + "***" + email.substring(at);
	}
}
