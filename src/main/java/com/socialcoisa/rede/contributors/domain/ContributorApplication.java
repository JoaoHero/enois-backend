package com.socialcoisa.rede.contributors.domain;

import java.time.Instant;

/**
 * Registro de quem se ofereceu para contribuir com o projeto.
 *
 * <p>A landing grava uma candidatura pública (sem conta, status NEW, origem LANDING). Quem já tem
 * contribuição verificada pelo GitHub usa o mesmo formulário só para confirmar os dados: a
 * candidatura fica ligada à conta, nasce APPROVED e a origem é GITHUB_VERIFIED — o GitHub é a
 * aprovação, não há revisão manual.</p>
 */
public class ContributorApplication {

	private final String id;
	private final String userId;
	private final String name;
	private final String github;
	private final String email;
	private final String area;
	private final ContributorExperience experience;
	private final String message;
	private final Instant acceptedTermsAt;
	private final ContributorApplicationStatus status;
	private final ContributorApplicationSource source;
	private final Instant createdAt;

	private ContributorApplication(String id, String userId, String name, String github, String email,
			String area, ContributorExperience experience, String message, Instant acceptedTermsAt,
			ContributorApplicationStatus status, ContributorApplicationSource source, Instant createdAt) {
		this.id = id;
		this.userId = userId;
		this.name = name;
		this.github = github;
		this.email = email;
		this.area = area;
		this.experience = experience;
		this.message = message;
		this.acceptedTermsAt = acceptedTermsAt;
		this.status = status;
		this.source = source;
		this.createdAt = createdAt;
	}

	public static ContributorApplication createLanding(String id, String name, String github, String email,
			String area, ContributorExperience experience, String message, Instant now) {
		return new ContributorApplication(id, null, name, github, email, area, experience, message, now,
			ContributorApplicationStatus.NEW, ContributorApplicationSource.LANDING, now);
	}

	/** Confirmação de quem já tem contribuição verificada: nasce aprovada pelo próprio GitHub. */
	public static ContributorApplication createVerified(String id, String userId, String name, String github,
			String email, String area, ContributorExperience experience, String message, Instant now) {
		return new ContributorApplication(id, userId, name, github, email, area, experience, message, now,
			ContributorApplicationStatus.APPROVED, ContributorApplicationSource.GITHUB_VERIFIED, now);
	}

	public static ContributorApplication restore(String id, String userId, String name, String github,
			String email, String area, ContributorExperience experience, String message, Instant acceptedTermsAt,
			ContributorApplicationStatus status, ContributorApplicationSource source, Instant createdAt) {
		return new ContributorApplication(id, userId, name, github, email, area, experience, message,
			acceptedTermsAt, status, source, createdAt);
	}

	public ContributorApplication withStatus(ContributorApplicationStatus newStatus) {
		return new ContributorApplication(id, userId, name, github, email, area, experience, message,
			acceptedTermsAt, newStatus, source, createdAt);
	}

	/** Liga uma candidatura já existente à conta verificada e atualiza os dados confirmados. */
	public ContributorApplication withVerifiedData(String newUserId, String newName, String newGithub,
			String newEmail, String newArea, ContributorExperience newExperience, String newMessage, Instant now) {
		return new ContributorApplication(id, newUserId, newName, newGithub, newEmail, newArea, newExperience,
			newMessage, now, ContributorApplicationStatus.APPROVED,
			ContributorApplicationSource.GITHUB_VERIFIED, createdAt);
	}

	public String id() {
		return id;
	}

	public String userId() {
		return userId;
	}

	public String name() {
		return name;
	}

	public String github() {
		return github;
	}

	public String email() {
		return email;
	}

	public String area() {
		return area;
	}

	public ContributorExperience experience() {
		return experience;
	}

	public String message() {
		return message;
	}

	public Instant acceptedTermsAt() {
		return acceptedTermsAt;
	}

	public ContributorApplicationStatus status() {
		return status;
	}

	public ContributorApplicationSource source() {
		return source;
	}

	public Instant createdAt() {
		return createdAt;
	}
}
