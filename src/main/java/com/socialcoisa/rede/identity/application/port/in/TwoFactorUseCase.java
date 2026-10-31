package com.socialcoisa.rede.identity.application.port.in;

import java.util.List;

/**
 * Gestao do segundo fator a partir de uma sessao autenticada (ativar, desativar, consultar estado).
 * O caminho publico do login fica em {@link TwoFactorLoginUseCase}.
 */
public interface TwoFactorUseCase {

	// ------------------------------------------------------------------ gestao (sessao)

	TwoFactorStatus status(String userId);

	/** Gera o segredo pendente e o que o aplicativo authenticator precisa (segredo + URI otpauth). */
	TotpSetup startTotpSetup(String userId, String password);

	/** Confirma o codigo do app, ativa o TOTP e devolve os codigos de recuperacao (mostrados uma vez). */
	/** The 2FA changes below revoke every other session of the user; {@code sessionToken} stays. */
	List<String> enableTotp(String userId, String code, String password, String sessionToken);

	void disableTotp(String userId, String password, String sessionToken);

	/** Envia por e-mail o codigo que confirma a ativacao do segundo fator por e-mail. */
	void requestEmailOtpSetup(String userId);

	void confirmEmailOtpSetup(String userId, String code, String sessionToken);

	void disableEmailOtp(String userId, String password, String sessionToken);

	/**
	 * @param totpAvailable false quando a instalacao nao tem a chave de cifra configurada — a tela usa
	 *                      isso para nao oferecer um caminho que terminaria em erro
	 */
	record TwoFactorStatus(boolean emailVerified, boolean totpEnabled, boolean emailOtpEnabled,
			boolean providerSecondFactor, boolean twoFactorEnabled, boolean identityVerified, int backupCodesLeft,
			boolean totpAvailable) {
	}

	record TotpSetup(String secret, String otpauthUri) {
	}
}