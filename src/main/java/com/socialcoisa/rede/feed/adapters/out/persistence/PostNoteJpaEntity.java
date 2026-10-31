package com.socialcoisa.rede.feed.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.feed.domain.PostNote;
import com.socialcoisa.rede.feed.domain.PostNoteStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "post_notes")
class PostNoteJpaEntity {

	@Id
	private String id;

	@Column(name = "post_id", nullable = false)
	private String postId;

	@Column(name = "author_id", nullable = false)
	private String authorId;

	@Column(nullable = false, length = 500)
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PostNoteStatus status;

	@Column(name = "reviewed_by")
	private String reviewedBy;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "review_reason", length = 500)
	private String reviewReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PostNoteJpaEntity() {
	}

	static PostNoteJpaEntity fromDomain(PostNote note) {
		PostNoteJpaEntity entity = new PostNoteJpaEntity();
		entity.id = note.id();
		entity.postId = note.postId();
		entity.authorId = note.authorId();
		entity.body = note.body();
		entity.status = note.status();
		entity.reviewedBy = note.reviewedBy();
		entity.reviewedAt = note.reviewedAt();
		entity.reviewReason = note.reviewReason();
		entity.createdAt = note.createdAt();
		return entity;
	}

	PostNote toDomain() {
		return new PostNote(id, postId, authorId, body, status, reviewedBy, reviewedAt, reviewReason, createdAt);
	}
}