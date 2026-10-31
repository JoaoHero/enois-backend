package com.socialcoisa.rede.messaging.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.messaging.domain.ConversationKey;
import com.socialcoisa.rede.messaging.domain.UserKeypair;

public interface E2eKeyRepository {

	void saveKeypair(UserKeypair keypair);
	Optional<UserKeypair> findActiveKeypair(String userId);
	List<UserKeypair> findKeypairsByUser(String userId);
	void deactivateKeypairs(String userId);

	void saveConversationKey(ConversationKey key);
	Optional<ConversationKey> findConversationKey(String conversationId, String userId);
	List<ConversationKey> findConversationKeys(String conversationId);
}
