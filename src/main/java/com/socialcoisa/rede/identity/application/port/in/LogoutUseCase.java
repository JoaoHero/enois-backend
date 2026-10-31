package com.socialcoisa.rede.identity.application.port.in;

public interface LogoutUseCase {

	/** Revokes the session by its access token and/or its refresh token, whichever is present. */
	void logout(String token, String refreshToken);
}
