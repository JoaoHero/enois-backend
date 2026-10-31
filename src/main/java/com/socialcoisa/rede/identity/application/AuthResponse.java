package com.socialcoisa.rede.identity.application;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** The tokens travel only in HttpOnly cookies (AuthCookieAdvice); the JSON body never carries them. */
public record AuthResponse(@JsonIgnore String token, @JsonIgnore String refreshToken, Instant expiresAt,
		Instant refreshExpiresAt, AuthUserView user) {
}
