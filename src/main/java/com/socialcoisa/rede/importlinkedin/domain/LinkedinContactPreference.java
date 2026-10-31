package com.socialcoisa.rede.importlinkedin.domain;

import java.time.Instant;

/** Preferência do convite automático para os contatos importados do LinkedIn. */
public record LinkedinContactPreference(String userId, boolean autoInvite, Instant createdAt, Instant updatedAt) {

	public LinkedinContactPreference withAutoInvite(boolean enabled, Instant now) {
		return new LinkedinContactPreference(userId, enabled, createdAt, now);
	}
}