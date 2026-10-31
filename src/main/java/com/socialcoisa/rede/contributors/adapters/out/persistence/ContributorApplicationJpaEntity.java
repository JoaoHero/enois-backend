package com.socialcoisa.rede.contributors.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.contributors.domain.ContributorApplication;
import com.socialcoisa.rede.contributors.domain.ContributorApplicationSource;
import com.socialcoisa.rede.contributors.domain.ContributorApplicationStatus;
import com.socialcoisa.rede.contributors.domain.ContributorExperience;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "contributor_applications", indexes = {
		@Index(name = "idx_contributor_applications_created", columnList = "created_at"),
		@Index(name = "idx_contributor_applications_status", columnList = "status") })
class ContributorApplicationJpaEntity {

	@Id
	private String id;

	@Column(name = "user_id")
	private String userId;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 120)
	private String github;

	@Column(nullable = false, length = 180)
	private String email;

	@Column(length = 80)
	private String area;

	@Enumerated(EnumType.STRING)
	@Column(length = 40)
	private ContributorExperience experience;

	@Column(length = 2000)
	private String message;

	@Column(name = "accepted_terms_at", nullable = false)
	private Instant acceptedTermsAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private ContributorApplicationStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private ContributorApplicationSource source;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ContributorApplicationJpaEntity() {
	}

	static ContributorApplicationJpaEntity fromDomain(ContributorApplication application) {
		ContributorApplicationJpaEntity entity = new ContributorApplicationJpaEntity();
		entity.id = application.id();
		entity.userId = application.userId();
		entity.name = application.name();
		entity.github = application.github();
		entity.email = application.email();
		entity.area = application.area();
		entity.experience = application.experience();
		entity.message = application.message();
		entity.acceptedTermsAt = application.acceptedTermsAt();
		entity.status = application.status();
		entity.source = application.source();
		entity.createdAt = application.createdAt();
		return entity;
	}

	ContributorApplication toDomain() {
		return ContributorApplication.restore(id, userId, name, github, email, area, experience, message,
			acceptedTermsAt, status, source, createdAt);
	}
}
