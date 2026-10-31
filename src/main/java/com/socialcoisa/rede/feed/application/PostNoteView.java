package com.socialcoisa.rede.feed.application;

import java.time.Instant;
import java.util.List;

import com.socialcoisa.rede.profile.application.ContributorTagView;
import com.socialcoisa.rede.profile.application.NameStyleView;

/** Nota aprovada exibida no rodapé do post como verificação da comunidade. */
public record PostNoteView(String id, String body, Instant createdAt, String authorUsername, String authorName,
		String authorAvatar, List<String> authorBadges, NameStyleView authorStyle,
		ContributorTagView authorContributor) {
}