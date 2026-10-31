package com.socialcoisa.rede.support.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.support.domain.SupportMessage;
import com.socialcoisa.rede.support.domain.SupportMessageDirection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "support_messages")
class SupportMessageJpaEntity {

	@Id
	private String id;

	@Column(name = "request_id", nullable = false)
	private String requestId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SupportMessageDirection direction;

	@Column(name = "author_name", length = 200)
	private String authorName;

	@Column(name = "author_github_login", length = 100)
	private String authorGithubLogin;

	@Column(nullable = false, length = 4000)
	private String body;

	@Column(name = "github_comment_id")
	private Long githubCommentId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected SupportMessageJpaEntity() {
	}

	static SupportMessageJpaEntity fromDomain(SupportMessage message) {
		SupportMessageJpaEntity entity = new SupportMessageJpaEntity();
		entity.id = message.id();
		entity.requestId = message.requestId();
		entity.direction = message.direction();
		entity.authorName = message.authorName();
		entity.authorGithubLogin = message.authorGithubLogin();
		entity.body = message.body();
		entity.githubCommentId = message.githubCommentId();
		entity.createdAt = message.createdAt();
		return entity;
	}

	SupportMessage toDomain() {
		return new SupportMessage(id, requestId, direction, authorName, authorGithubLogin, body, githubCommentId,
			createdAt);
	}
}