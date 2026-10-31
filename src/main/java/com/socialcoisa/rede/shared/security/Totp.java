package com.socialcoisa.rede.shared.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP (RFC 6238) com os parametros que qualquer authenticator entende: HMAC-SHA1, 6 digitos e passo
 * de 30 segundos. Implementado aqui para nao puxar dependencia nova — e coberto pelos vetores do
 * proprio RFC nos testes, porque um erro de fuso/passo aqui tranca todo mundo fora da conta.
 */
public final class Totp {

	private static final String HMAC_ALGORITHM = "HmacSHA1";
	private static final int DIGITS = 6;
	private static final long STEP_SECONDS = 30;
	private static final int MODULO = 1_000_000;

	/** Uma janela para cada lado cobre relogio adiantado/atrasado sem abrir muito a validade. */
	public static final int DEFAULT_WINDOW = 1;

	private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

	private Totp() {
	}

	public static String generateSecret() {
		byte[] bytes = new byte[20];
		new SecureRandom().nextBytes(bytes);
		return base32Encode(bytes);
	}

	/** Valida o codigo aceitando uma janela de passos em cada direcao. */
	public static boolean verify(String secretBase32, String code, Instant now, int window) {
		return matchingStep(secretBase32, code, now, window) >= 0;
	}

	/**
	 * Time step the code belongs to, or -1 when it matches none inside the window. Callers persist
	 * the step so the same code cannot be accepted twice.
	 */
	public static long matchingStep(String secretBase32, String code, Instant now, int window) {
		if (secretBase32 == null || code == null || code.isBlank()) {
			return -1;
		}
		String digits = code.replaceAll("\\D", "");
		if (digits.length() != DIGITS) {
			return -1;
		}
		long step = now.getEpochSecond() / STEP_SECONDS;
		for (int offset = -window; offset <= window; offset++) {
			if (digits.equals(codeAt(secretBase32, step + offset))) {
				return step + offset;
			}
		}
		return -1;
	}

	public static boolean verify(String secretBase32, String code, Instant now) {
		return verify(secretBase32, code, now, DEFAULT_WINDOW);
	}

	public static String codeAt(String secretBase32, long step) {
		try {
			byte[] key = base32Decode(secretBase32);
			byte[] counter = new byte[8];
			long value = step;
			for (int index = 7; index >= 0; index--) {
				counter[index] = (byte) (value & 0xFF);
				value >>= 8;
			}
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
			byte[] hash = mac.doFinal(counter);

			int offset = hash[hash.length - 1] & 0x0F;
			int binary = ((hash[offset] & 0x7F) << 24)
				| ((hash[offset + 1] & 0xFF) << 16)
				| ((hash[offset + 2] & 0xFF) << 8)
				| (hash[offset + 3] & 0xFF);
			return String.format("%0" + DIGITS + "d", binary % MODULO);
		}
		catch (Exception exception) {
			throw new IllegalStateException("Falha ao calcular o TOTP", exception);
		}
	}

	/** URI padrao {@code otpauth://} que os aplicativos leem (Google Authenticator, 1Password, ...). */
	public static String otpauthUri(String issuer, String account, String secretBase32) {
		String label = URLEncoder.encode(issuer + ":" + account, StandardCharsets.UTF_8);
		return "otpauth://totp/" + label
			+ "?secret=" + secretBase32
			+ "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8)
			+ "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
	}

	static String base32Encode(byte[] data) {
		StringBuilder result = new StringBuilder();
		int buffer = 0;
		int bitsLeft = 0;
		for (byte value : data) {
			buffer = (buffer << 8) | (value & 0xFF);
			bitsLeft += 8;
			while (bitsLeft >= 5) {
				result.append(BASE32_ALPHABET[(buffer >> (bitsLeft - 5)) & 0x1F]);
				bitsLeft -= 5;
			}
		}
		if (bitsLeft > 0) {
			result.append(BASE32_ALPHABET[(buffer << (5 - bitsLeft)) & 0x1F]);
		}
		return result.toString();
	}

	static byte[] base32Decode(String encoded) {
		String normalized = encoded.trim().replace("=", "").replace(" ", "").toUpperCase(java.util.Locale.ROOT);
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		int buffer = 0;
		int bitsLeft = 0;
		for (char character : normalized.toCharArray()) {
			int index = indexOf(character);
			if (index < 0) {
				throw new IllegalArgumentException("Caractere invalido no segredo do TOTP");
			}
			buffer = (buffer << 5) | index;
			bitsLeft += 5;
			if (bitsLeft >= 8) {
				out.write((buffer >> (bitsLeft - 8)) & 0xFF);
				bitsLeft -= 8;
			}
		}
		return out.toByteArray();
	}

	private static int indexOf(char character) {
		for (int index = 0; index < BASE32_ALPHABET.length; index++) {
			if (BASE32_ALPHABET[index] == character) {
				return index;
			}
		}
		return -1;
	}
}