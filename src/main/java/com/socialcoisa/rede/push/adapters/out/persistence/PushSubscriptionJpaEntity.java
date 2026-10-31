package com.socialcoisa.rede.push.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import com.socialcoisa.rede.push.domain.PushSubscription;

@Entity
@Table(name = "push_subscriptions", indexes = { @Index(name = "idx_push_subscriptions_user", columnList = "user_id") })
class PushSubscriptionJpaEntity {

	@Id
	private String id;

	@Column(name = "user_id", nullable = false)
	private String userId;

	// 700 e o tamanho real da coluna (`push_subscriptions.endpoint varchar(700)`) — declarar 1000
	// prometia mais do que o banco guarda, e a quebra chegava como 409 sem dizer o campo.
	@Column(nullable = false, length = 700)
	private String endpoint;

	@Column(nullable = false, length = 255)
	private String p256dh;

	@Column(nullable = false, length = 255)
	private String auth;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_seen_at")
	private Instant lastSeenAt;

	protected PushSubscriptionJpaEntity() {
	}

	static PushSubscriptionJpaEntity fromDomain(PushSubscription subscription) {
		PushSubscriptionJpaEntity entity = new PushSubscriptionJpaEntity();
		entity.id = subscription.id();
		entity.userId = subscription.userId();
		entity.endpoint = subscription.endpoint();
		entity.p256dh = subscription.p256dh();
		entity.auth = subscription.auth();
		entity.createdAt = subscription.createdAt();
		entity.lastSeenAt = subscription.lastSeenAt();
		return entity;
	}

	PushSubscription toDomain() {
		return PushSubscription.restore(id, userId, endpoint, p256dh, auth, createdAt, lastSeenAt);
	}
}
