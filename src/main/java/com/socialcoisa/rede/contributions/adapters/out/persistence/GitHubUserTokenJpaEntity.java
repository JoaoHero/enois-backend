package com.socialcoisa.rede.contributions.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.contributions.domain.GitHubUserToken;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "github_user_tokens")
class GitHubUserTokenJpaEntity {

	@Id
	@Column(name = "user_id")
	private String userId;

	@Column(name = "token_cipher", nullable = false, columnDefinition = "text")
	private String tokenCipher;

	@Column(length = 255)
	private String scopes;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected GitHubUserTokenJpaEntity() {
	}

	static GitHubUserTokenJpaEntity fromDomain(GitHubUserToken token) {
		GitHubUserTokenJpaEntity entity = new GitHubUserTokenJpaEntity();
		entity.userId = token.userId();
		entity.tokenCipher = token.tokenCipher();
		entity.scopes = token.scopes();
		entity.createdAt = token.createdAt();
		entity.updatedAt = token.updatedAt();
		return entity;
	}

	GitHubUserToken toDomain() {
		return new GitHubUserToken(userId, tokenCipher, scopes, createdAt, updatedAt);
	}
}