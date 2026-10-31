package com.socialcoisa.rede.importlinkedin.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.importlinkedin.domain.LinkedinContactPreference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "linkedin_contact_preferences")
class LinkedinContactPreferenceJpaEntity {

	@Id
	@Column(name = "user_id")
	private String userId;

	@Column(name = "auto_invite", nullable = false)
	private boolean autoInvite;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected LinkedinContactPreferenceJpaEntity() {
	}

	static LinkedinContactPreferenceJpaEntity fromDomain(LinkedinContactPreference preference) {
		LinkedinContactPreferenceJpaEntity entity = new LinkedinContactPreferenceJpaEntity();
		entity.userId = preference.userId();
		entity.autoInvite = preference.autoInvite();
		entity.createdAt = preference.createdAt();
		entity.updatedAt = preference.updatedAt();
		return entity;
	}

	LinkedinContactPreference toDomain() {
		return new LinkedinContactPreference(userId, autoInvite, createdAt, updatedAt);
	}
}