package com.socialcoisa.rede.identity.application.port.in;

import java.util.Optional;

import com.socialcoisa.rede.identity.application.AuthResponse;

public interface ResetPasswordUseCase {

	/** Redefine a senha. Retorna sessao quando o reset veio de senha expirada (auto-login). */
	Optional<AuthResponse> reset(String token, String newPassword);
}
