package com.socialcoisa.rede.importlinkedin.domain;

import java.time.Instant;

/** Um contato do LinkedIn importado e o estado do convite no Énóis. */
public record LinkedinContactInvite(String id, String userId, String contactName, String nameKey,
		String matchedUserId, LinkedinInviteStatus status, Instant createdAt, Instant updatedAt) {

	public LinkedinContactInvite withMatch(String matchedUserId, LinkedinInviteStatus newStatus, Instant now) {
		return new LinkedinContactInvite(id, userId, contactName, nameKey, matchedUserId, newStatus, createdAt, now);
	}

	public LinkedinContactInvite withStatus(LinkedinInviteStatus newStatus, Instant now) {
		return new LinkedinContactInvite(id, userId, contactName, nameKey, matchedUserId, newStatus, createdAt, now);
	}
}