package com.socialcoisa.rede.feed.domain;

import java.time.Instant;

/**
 * Correção/contexto enviado pela comunidade a um post. Nasce PENDING e só aparece no post depois
 * de aprovada; a negada fica registrada para a moderação e para o desconto de credibilidade.
 */
public record PostNote(String id, String postId, String authorId, String body, PostNoteStatus status,
		String reviewedBy, Instant reviewedAt, String reviewReason, Instant createdAt) {

	public PostNote review(PostNoteStatus newStatus, String reviewerId, String reason, Instant at) {
		return new PostNote(id, postId, authorId, body, newStatus, reviewerId, at, reason, createdAt);
	}
}