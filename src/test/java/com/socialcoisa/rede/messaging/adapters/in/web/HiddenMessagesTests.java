package com.socialcoisa.rede.messaging.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.messaging.application.MessagingService;
import com.socialcoisa.rede.idempotency.application.IdempotencyApplicationService;
import com.socialcoisa.rede.idempotency.application.CommandResponse;

/**
 * "Apagar para mim" por MENSAGEM (o da conversa inteira já existia).
 *
 * O que este teste protege: a mensagem some só de quem apagou — para o outro participante ela
 * continua na conversa (é o que separa "apagar para mim" de "apagar para todos", que aqui não
 * existe), e o filtro acontece no banco, então a paginação não abre buraco.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HiddenMessagesTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MessagingService messagingService;

	@Autowired
	private IdempotencyApplicationService idempotency;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void hidingAMessageOnlyAffectsWhoAsked() throws Exception {
		var a = auth.authenticate("hide_msg_a");
		var b = auth.authenticate("hide_msg_b");
		String conversationId = createConversation(a, b.userId());
		String messageId = send(a, conversationId, "combinado, te mando o orçamento amanhã");

		mockMvc.perform(post("/conversations/" + conversationId + "/messages/hide")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "hide-msg-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("ids", List.of(messageId)))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hidden").value(1));

		// A nao ve mais; B continua vendo a mesma mensagem.
		assertThat(messageIds(a, conversationId)).doesNotContain(messageId);
		assertThat(messageIds(b, conversationId)).contains(messageId);

		// Apagar de novo e idempotente (nao cria linha repetida nem estoura a unique).
		mockMvc.perform(post("/conversations/" + conversationId + "/messages/hide")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "hide-msg-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("ids", List.of(messageId)))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hidden").value(0));
	}

	@Test
	void refusesHidingInAConversationThePersonIsNotIn() throws Exception {
		var a = auth.authenticate("hide_msg_c");
		var b = auth.authenticate("hide_msg_d");
		var outsider = auth.authenticate("hide_msg_e");
		String conversationId = createConversation(a, b.userId());
		String messageId = send(a, conversationId, "oi");

		mockMvc.perform(post("/conversations/" + conversationId + "/messages/hide")
				.header("X-Session-Token", outsider.token())
				.header("Idempotency-Key", "hide-msg-outsider")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("ids", List.of(messageId)))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_PARTICIPANT"));
	}

	@Test
	void emptySelectionIsRejected() throws Exception {
		var a = auth.authenticate("hide_msg_f");
		var b = auth.authenticate("hide_msg_g");
		String conversationId = createConversation(a, b.userId());

		mockMvc.perform(post("/conversations/" + conversationId + "/messages/hide")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "hide-msg-empty")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("ids", List.of()))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EMPTY_MESSAGE_LIST"));
	}


	@Test
	void rejectsMessageFromForeignConversation() throws Exception {
		assertOtherConversationRejected("hide_scope_foreign", false);
	}

	@Test
	void rejectsMessageFromAnotherConversationOfSameUser() throws Exception {
		assertOtherConversationRejected("hide_scope_own", true);
	}

	private void assertOtherConversationRejected(String prefix, boolean alsoParticipant) throws Exception {
		var requester = auth.authenticate(prefix + "_a");
		var peer = auth.authenticate(prefix + "_b");
		var sender = auth.authenticate(prefix + "_c");
		String conversation = createConversation(requester, peer.userId());
		String otherConversation = createConversation(sender,
			alsoParticipant ? requester.userId() : peer.userId());
		String message = send(sender, otherConversation, "synthetic scoped message");

		hide(requester, conversation, List.of(message), prefix)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));

		assertThat(hiddenRows(requester.userId(), message)).isZero();
		assertThat(messageIds(sender, otherConversation)).contains(message);
		assertThat(messageIds(alsoParticipant ? requester : peer, otherConversation)).contains(message);
	}

	@Test
	void rejectsMissingMessageWithoutWriting() throws Exception {
		var requester = auth.authenticate("hide_scope_missing_a");
		var peer = auth.authenticate("hide_scope_missing_b");
		String conversation = createConversation(requester, peer.userId());
		String missing = "synthetic-missing-hide-scope";
		assertThat(jdbc.queryForObject("select count(*) from messages where id = ?",
			Integer.class, missing)).isZero();

		hide(requester, conversation, List.of(missing), "hide-scope-missing")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
		assertThat(hiddenRows(requester.userId(), missing)).isZero();
	}

	@Test
	void rejectsMixedBatchWithValidMessageFirst() throws Exception {
		assertMixedBatchRejected("hide_scope_first", true);
	}

	@Test
	void rejectsMixedBatchWithInvalidMessageFirst() throws Exception {
		assertMixedBatchRejected("hide_scope_last", false);
	}

	private void assertMixedBatchRejected(String prefix, boolean validFirst) throws Exception {
		var requester = auth.authenticate(prefix + "_a");
		var peer = auth.authenticate(prefix + "_b");
		var sender = auth.authenticate(prefix + "_c");
		String conversation = createConversation(requester, peer.userId());
		String otherConversation = createConversation(sender, peer.userId());
		String valid = send(requester, conversation, "synthetic valid message");
		String invalid = send(sender, otherConversation, "synthetic unrelated message");
		List<String> ids = validFirst ? List.of(valid, invalid) : List.of(invalid, valid);

		hide(requester, conversation, ids, prefix)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));

		assertThat(hiddenRows(requester.userId(), valid)).isZero();
		assertThat(hiddenRows(requester.userId(), invalid)).isZero();
		assertThat(messageIds(requester, conversation)).contains(valid);
		assertThat(messageIds(peer, conversation)).contains(valid);
		assertThat(messageIds(sender, otherConversation)).contains(invalid);
		assertThat(messageIds(peer, otherConversation)).contains(invalid);

		hide(requester, conversation, List.of(valid), prefix)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hidden").value(1));
	}

	@Test
	void idempotencyDistinguishesConversationsAndReplaysSameRequest() throws Exception {
		var requester = auth.authenticate("hide_scope_idem_a");
		var peer = auth.authenticate("hide_scope_idem_b");
		var sender = auth.authenticate("hide_scope_idem_c");
		String first = createConversation(requester, peer.userId());
		String second = createConversation(sender, requester.userId());
		String message = send(requester, first, "synthetic idempotent message");

		hide(requester, first, List.of(message), "hide-scope-shared-key")
			.andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(1));
		hide(requester, first, List.of(message), "hide-scope-shared-key")
			.andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(1));
		hide(requester, second, List.of(message), "hide-scope-shared-key")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_PAYLOAD_CONFLICT"));
		assertThat(hiddenRows(requester.userId(), message)).isEqualTo(1);
	}

	@Test
	void legacyIdempotencyHashReturnsExplicitConflict() throws Exception {
		var requester = auth.authenticate("hide_scope_legacy_a");
		var peer = auth.authenticate("hide_scope_legacy_b");
		String conversation = createConversation(requester, peer.userId());
		String message = send(requester, conversation, "synthetic legacy retry");
		List<String> ids = List.of(message);
		String key = "hide-scope-legacy-key";
		String legacyHash = idempotency.hash(Map.of("ids", ids));
		idempotency.execute(requester.userId(), "POST", "/api/conversations/{conversationId}/messages/hide",
			key, legacyHash, () -> new CommandResponse(200, Map.of("hidden",
				messagingService.hideMessages(conversation, requester.userId(), ids))));

		hide(requester, conversation, ids, key)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_PAYLOAD_CONFLICT"));
		assertThat(hiddenRows(requester.userId(), message)).isEqualTo(1);
		assertThat(messageIds(peer, conversation)).contains(message);
	}

	@Test
	void serviceRejectsOutsiderWithoutController() throws Exception {
		var requester = auth.authenticate("hide_scope_service_a");
		var peer = auth.authenticate("hide_scope_service_b");
		var outsider = auth.authenticate("hide_scope_service_c");
		String conversation = createConversation(requester, peer.userId());
		String message = send(requester, conversation, "synthetic service authorization");

		assertThatThrownBy(() -> messagingService.hideMessages(conversation, outsider.userId(), List.of(message)))
			.isInstanceOfSatisfying(ApiException.class, exception -> {
				assertThat(exception.status()).isEqualTo(403);
				assertThat(exception.code()).isEqualTo("NOT_PARTICIPANT");
			});
		assertThat(hiddenRows(outsider.userId(), message)).isZero();
	}

	@Test
	void serviceRejectsMixedBatchWithoutController() throws Exception {
		var requester = auth.authenticate("hide_scope_service_mix_a");
		var peer = auth.authenticate("hide_scope_service_mix_b");
		String conversation = createConversation(requester, peer.userId());
		String message = send(requester, conversation, "synthetic service batch");
		String missing = "synthetic-service-missing-hide-scope";

		assertThatThrownBy(() -> messagingService.hideMessages(conversation, requester.userId(),
			List.of(message, missing)))
			.isInstanceOfSatisfying(ApiException.class, exception -> {
				assertThat(exception.status()).isEqualTo(404);
				assertThat(exception.code()).isEqualTo("MESSAGE_NOT_FOUND");
			});
		assertThat(hiddenRows(requester.userId(), message)).isZero();
		assertThat(hiddenRows(requester.userId(), missing)).isZero();
		assertThat(messageIds(requester, conversation)).contains(message);
	}

	@Test
	void validBatchNormalizesIdsAndKeepsOtherParticipantVisibility() throws Exception {
		var requester = auth.authenticate("hide_scope_normal_a");
		var peer = auth.authenticate("hide_scope_normal_b");
		String conversation = createConversation(requester, peer.userId());
		String first = send(requester, conversation, "synthetic first message");
		String second = send(peer, conversation, "synthetic second message");
		List<String> ids = new ArrayList<>();
		ids.add(" " + first + " ");
		ids.add(first);
		ids.add(null);
		ids.add(" ");
		ids.add(second);

		hide(requester, conversation, ids, "hide-scope-normal")
			.andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(2));
		assertThat(messageIds(requester, conversation)).doesNotContain(first, second);
		assertThat(messageIds(peer, conversation)).contains(first, second);
		hide(requester, conversation, List.of(first, second), "hide-scope-normal-again")
			.andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(0));
	}

	private int hiddenRows(String userId, String messageId) {
		return jdbc.queryForObject("select count(*) from message_hidden where user_id = ? and message_id = ?",
			Integer.class, userId, messageId);
	}

	private ResultActions hide(TestAuthHelper.AuthSession session, String conversationId, List<String> ids,
			String key) throws Exception {
		return mockMvc.perform(post("/conversations/" + conversationId + "/messages/hide")
			.header("X-Session-Token", session.token()).header("Idempotency-Key", key)
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of("ids", ids))));
	}

	private List<String> messageIds(TestAuthHelper.AuthSession session, String conversationId) throws Exception {
		MvcResult result = mockMvc
			.perform(get("/conversations/" + conversationId + "/messages").header("X-Session-Token", session.token()))
			.andExpect(status().isOk()).andReturn();
		JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
		List<String> out = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) out.add(items.get(i).get("id").asText());
		return out;
	}

	private String send(TestAuthHelper.AuthSession session, String conversationId, String text) throws Exception {
		MvcResult result = mockMvc.perform(post("/conversations/" + conversationId + "/messages")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "hide-msg-send-" + session.userId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("text", text))))
			.andExpect(status().isCreated()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private String createConversation(TestAuthHelper.AuthSession a, String otherId) throws Exception {
		MvcResult result = mockMvc.perform(post("/conversations")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "hide-msg-conv-" + a.userId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("kind", "DIRECT", "participantIds", List.of(otherId)))))
			.andExpect(status().isCreated()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}
}
