package com.socialcoisa.rede.messaging.adapters.in.web;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.messaging.application.MessagingService;
import com.socialcoisa.rede.messaging.application.port.out.E2eKeyRepository;
import com.socialcoisa.rede.messaging.domain.ConversationKey;
import com.socialcoisa.rede.messaging.domain.UserKeypair;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

@RestController
class E2eKeyController {

	private static final int MAX_KEY_SIZE = 8192;

	private final E2eKeyRepository keys;
	private final MessagingService messaging;
	private final CurrentUserResolver currentUserResolver;
	private final Clock clock;

	E2eKeyController(E2eKeyRepository keys, MessagingService messaging,
			CurrentUserResolver currentUserResolver, Clock clock) {
		this.keys = keys;
		this.messaging = messaging;
		this.currentUserResolver = currentUserResolver;
		this.clock = clock;
	}

	@PostMapping("/e2e/keypair")
	Map<String, String> uploadKeypair(@RequestHeader("X-Session-Token") String sessionToken,
			@RequestBody KeypairRequest request) {
		String userId = currentUserResolver.resolve(sessionToken).id().value();
		if (request.publicKey() == null || request.encryptedPrivateKey() == null) {
			throw new ApiException(400, "INVALID_KEYPAIR", "Chave publica e privada cifrada sao obrigatorias.");
		}
		if (request.publicKey().length() > MAX_KEY_SIZE || request.encryptedPrivateKey().length() > MAX_KEY_SIZE) {
			throw new ApiException(400, "KEY_TOO_LARGE", "Chave excede o tamanho maximo permitido.");
		}
		keys.deactivateKeypairs(userId);
		String id = UUID.randomUUID().toString();
		keys.saveKeypair(new UserKeypair(id, userId, request.publicKey(), request.encryptedPrivateKey(),
			request.algorithm() != null ? request.algorithm() : "RSA-OAEP", true, clock.instant()));
		return Map.of("id", id, "status", "ACTIVE");
	}

	@GetMapping("/e2e/keypair")
	KeypairView myKeypair(@RequestHeader("X-Session-Token") String sessionToken) {
		String userId = currentUserResolver.resolve(sessionToken).id().value();
		UserKeypair kp = keys.findActiveKeypair(userId).orElse(null);
		if (kp == null) {
			throw new ApiException(404, "NO_KEYPAIR", "Nenhum keypair configurado.");
		}
		return new KeypairView(kp.publicKey(), kp.encryptedPrivateKey(), kp.algorithm());
	}

	@GetMapping("/e2e/keypair/{userId}")
	PublicKeyView userPublicKey(@RequestHeader("X-Session-Token") String sessionToken,
			@PathVariable String userId) {
		currentUserResolver.resolve(sessionToken);
		return keys.findActiveKeypair(userId)
			.map(kp -> new PublicKeyView(kp.publicKey(), kp.algorithm()))
			.orElse(new PublicKeyView(null, null));
	}

	@PostMapping("/e2e/conversations/{conversationId}/keys")
	Map<String, String> distributeKey(@RequestHeader("X-Session-Token") String sessionToken,
			@PathVariable String conversationId, @RequestBody List<ConversationKeyRequest> participants) {
		String callerId = currentUserResolver.resolve(sessionToken).id().value();
		if (!messaging.isParticipant(conversationId, callerId)) {
			throw new ApiException(403, "NOT_PARTICIPANT", "Voce nao participa desta conversa.");
		}
		Set<String> realParticipants = messaging.getParticipants(conversationId).stream()
			.map(p -> p.userId())
			.collect(Collectors.toSet());
		for (ConversationKeyRequest p : participants) {
			if (!realParticipants.contains(p.userId())) {
				throw new ApiException(400, "INVALID_PARTICIPANT",
					"Usuario " + p.userId() + " nao participa desta conversa.");
			}
			if (p.encryptedKey() == null || p.encryptedKey().length() > MAX_KEY_SIZE) {
				throw new ApiException(400, "INVALID_KEY", "Chave invalida ou excede o tamanho maximo.");
			}
			keys.saveConversationKey(new ConversationKey(conversationId, p.userId(),
				p.encryptedKey(), 1, clock.instant()));
		}
		return Map.of("status", "DISTRIBUTED", "conversationId", conversationId);
	}

	@GetMapping("/e2e/conversations/{conversationId}/key")
	ConversationKeyView myConversationKey(@RequestHeader("X-Session-Token") String sessionToken,
			@PathVariable String conversationId) {
		String userId = currentUserResolver.resolve(sessionToken).id().value();
		if (!messaging.isParticipant(conversationId, userId)) {
			throw new ApiException(403, "NOT_PARTICIPANT", "Voce nao participa desta conversa.");
		}
		ConversationKey ck = keys.findConversationKey(conversationId, userId).orElse(null);
		if (ck == null) {
			return new ConversationKeyView(null, 0);
		}
		return new ConversationKeyView(ck.encryptedKey(), ck.keyVersion());
	}

	record KeypairRequest(String publicKey, String encryptedPrivateKey, String algorithm) {}
	record KeypairView(String publicKey, String encryptedPrivateKey, String algorithm) {}
	record PublicKeyView(String publicKey, String algorithm) {}
	record ConversationKeyRequest(String userId, String encryptedKey) {}
	record ConversationKeyView(String encryptedKey, int keyVersion) {}
}
