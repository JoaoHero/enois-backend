package com.socialcoisa.rede.feed.application;

import java.time.Instant;

/** Nota do próprio leitor num post, em qualquer estado (para o card mostrar "em análise"). */
public record MyPostNoteView(String id, String postId, String body, String status, String reviewReason,
		Instant createdAt) {
}