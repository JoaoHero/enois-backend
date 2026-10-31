package com.socialcoisa.rede.identity.domain;

import java.time.Instant;

/**
 * Desafio do segundo fator pedido no login.
 *
 * O id e opaco e nao vale como sessao: sozinho ele nao da acesso a nada — serve para o servidor
 * lembrar qual usuario esta no meio do login (e, no metodo por e-mail, qual codigo foi enviado).
 * Tentativas erradas sao contadas e o desafio morre no limite.
 */
public class TwoFactorChallenge {

	public static final int MAX_ATTEMPTS = 5;

	private final String id;
	private final String userId;
	private final TwoFactorMethod method;
	private final String codeHash;
	private final int attempts;
	private final Instant expiresAt;
	private final Instant consumedAt;
	private final Instant createdAt;

	private TwoFactorChallenge(String id, String userId, TwoFactorMethod method, String codeHash, int attempts,
			Instant expiresAt, Instant consumedAt, Instant createdAt) {
		this.id = id;
		this.userId = userId;
		this.method = method;
		this.codeHash = codeHash;
		this.attempts = attempts;
		this.expiresAt = expiresAt;
		this.consumedAt = consumedAt;
		this.createdAt = createdAt;
	}

	public static TwoFactorChallenge create(String id, String userId, TwoFactorMethod method, String codeHash,
			Instant expiresAt, Instant now) {
		return new TwoFactorChallenge(id, userId, method, codeHash, 0, expiresAt, null, now);
	}

	public static TwoFactorChallenge restore(String id, String userId, TwoFactorMethod method, String codeHash,
			int attempts, Instant expiresAt, Instant consumedAt, Instant createdAt) {
		return new TwoFactorChallenge(id, userId, method, codeHash, attempts, expiresAt, consumedAt, createdAt);
	}

	public boolean isExpired(Instant now) {
		return now.isAfter(expiresAt);
	}

	public boolean isConsumed() {
		return consumedAt != null;
	}

	public String id() {
		return id;
	}

	public String userId() {
		return userId;
	}

	public TwoFactorMethod method() {
		return method;
	}

	public String codeHash() {
		return codeHash;
	}

	public int attempts() {
		return attempts;
	}

	public Instant expiresAt() {
		return expiresAt;
	}

	public Instant consumedAt() {
		return consumedAt;
	}

	public Instant createdAt() {
		return createdAt;
	}
}