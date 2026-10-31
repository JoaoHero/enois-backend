package com.socialcoisa.rede.support.domain;

import java.time.Instant;

/** Uma mensagem da conversa do ticket: resposta do painel (ENOIS) ou comentário do GitHub. */
public record SupportMessage(String id, String requestId, SupportMessageDirection direction,
		String authorName, String authorGithubLogin, String body, Long githubCommentId, Instant createdAt) {

	public SupportMessage withGithubComment(long commentId, String authorName, String authorGithubLogin) {
		return new SupportMessage(id, requestId, direction, authorName, authorGithubLogin, body, commentId,
			createdAt);
	}
}