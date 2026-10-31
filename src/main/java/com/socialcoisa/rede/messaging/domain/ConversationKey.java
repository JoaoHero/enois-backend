package com.socialcoisa.rede.messaging.domain;

import java.time.Instant;

public record ConversationKey(String conversationId, String userId, String encryptedKey,
		int keyVersion, Instant createdAt) {
}
