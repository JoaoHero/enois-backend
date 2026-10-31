package com.socialcoisa.rede.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Cifra de segredos em repouso (hoje o segredo do TOTP) com AES-GCM.
 *
 * A chave vem de {@code REDE_SECURITY_ENCRYPTION_KEY} (32 bytes em base64). Sem ela o recurso fica
 * INDISPONIVEL, com erro explicito — guardar segredo de segundo fator em texto puro seria pior do que
 * nao oferecer o recurso, e degradar em silencio esconderia o problema de quem opera.
 */
@Component
public class SecretCipher {

	private static final String ALGORITHM = "AES/GCM/NoPadding";
	private static final int GCM_TAG_BITS = 128;
	private static final int IV_BYTES = 12;

	private final SecretKeySpec key;

	public SecretCipher(String base64Key) {
		this(base64Key, false);
	}

	/**
	 * Outside the dev profile the key is required at boot: a missing key used to silently disable
	 * TOTP and leave the signup challenge without a stable signing secret across pods.
	 */
	@org.springframework.beans.factory.annotation.Autowired
	public SecretCipher(@Value("${rede.security.encryption-key:}") String base64Key,
			@Value("${rede.security.encryption-key-required:true}") boolean required) {
		if (!StringUtils.hasText(base64Key)) {
			if (required) {
				throw new IllegalStateException(
					"REDE_SECURITY_ENCRYPTION_KEY is required (32 bytes, base64). Only the dev profile may omit it.");
			}
			this.key = null;
			return;
		}
		byte[] decoded;
		try {
			decoded = Base64.getDecoder().decode(base64Key.trim());
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalStateException("REDE_SECURITY_ENCRYPTION_KEY nao e base64 valido.", exception);
		}
		if (decoded.length != 32) {
			throw new IllegalStateException("REDE_SECURITY_ENCRYPTION_KEY precisa ter 32 bytes (chave AES-256).");
		}
		this.key = new SecretKeySpec(decoded, "AES");
	}

	public boolean isEnabled() {
		return key != null;
	}

	public void requireEnabled() {
		if (!isEnabled()) {
			throw new ApiException(503, "SECURITY_KEY_NOT_CONFIGURED",
				"O segundo fator ainda nao esta disponivel nesta instalacao. Avise o time de suporte.");
		}
	}

	/** Saida: base64(iv || ciphertext+tag). */
	public String encrypt(String plaintext) {
		requireEnabled();
		try {
			byte[] iv = new byte[IV_BYTES];
			new SecureRandom().nextBytes(iv);
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

			byte[] payload = new byte[iv.length + encrypted.length];
			System.arraycopy(iv, 0, payload, 0, iv.length);
			System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
			return Base64.getEncoder().encodeToString(payload);
		}
		catch (Exception exception) {
			throw new IllegalStateException("Falha ao cifrar o segredo", exception);
		}
	}

	public String decrypt(String encoded) {
		requireEnabled();
		try {
			byte[] payload = Base64.getDecoder().decode(encoded);
			byte[] iv = java.util.Arrays.copyOfRange(payload, 0, IV_BYTES);
			byte[] encrypted = java.util.Arrays.copyOfRange(payload, IV_BYTES, payload.length);

			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		}
		catch (Exception exception) {
			// Chave trocada/sem correspondencia: nao da para recuperar o segredo, entao o erro e claro.
			throw new IllegalStateException("Falha ao decifrar o segredo guardado", exception);
		}
	}

	/** Util para gerar uma chave nova na documentacao. */
	public static String generateKeyBase64() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}