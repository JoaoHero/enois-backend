package com.socialcoisa.rede.importlinkedin.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.importlinkedin.domain.LinkedinContactInvite;
import com.socialcoisa.rede.importlinkedin.domain.LinkedinInviteStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "linkedin_contact_invites")
class LinkedinContactInviteJpaEntity {

	@Id
	private String id;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "contact_name", nullable = false, length = 200)
	private String contactName;

	@Column(name = "name_key", nullable = false, length = 200)
	private String nameKey;

	@Column(name = "matched_user_id")
	private String matchedUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LinkedinInviteStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected LinkedinContactInviteJpaEntity() {
	}

	static LinkedinContactInviteJpaEntity fromDomain(LinkedinContactInvite invite) {
		LinkedinContactInviteJpaEntity entity = new LinkedinContactInviteJpaEntity();
		entity.id = invite.id();
		entity.userId = invite.userId();
		entity.contactName = invite.contactName();
		entity.nameKey = invite.nameKey();
		entity.matchedUserId = invite.matchedUserId();
		entity.status = invite.status();
		entity.createdAt = invite.createdAt();
		entity.updatedAt = invite.updatedAt();
		return entity;
	}

	LinkedinContactInvite toDomain() {
		return new LinkedinContactInvite(id, userId, contactName, nameKey, matchedUserId, status, createdAt,
			updatedAt);
	}
}