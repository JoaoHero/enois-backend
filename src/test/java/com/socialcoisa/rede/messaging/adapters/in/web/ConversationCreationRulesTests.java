package com.socialcoisa.rede.messaging.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.network.application.port.out.ConnectionRepository;
import com.socialcoisa.rede.network.domain.ConnectionStatus;
import com.socialcoisa.rede.network.domain.UserConnection;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/** The generic `POST /conversations` applies block and minor-protection rules to every kind. */
@SpringBootTest
@AutoConfigureMockMvc
class ConversationCreationRulesTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ConnectionRepository connections;

	@Autowired
	private JdbcTemplate jdbc;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	private ResultActions create(TestAuthHelper.AuthSession session, String idem, Map<String, Object> body)
			throws Exception {
		return mockMvc.perform(post("/conversations")
			.header("X-Session-Token", session.token())
			.header("Idempotency-Key", idem)
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(body)));
	}

	private void makeMinor(String userId) {
		jdbc.update("UPDATE users SET birth_date = ? WHERE id = ?",
			LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusYears(15).toString(), userId);
	}

	private void connect(String a, String b) {
		connections.save(UserConnection.restore(null, a, b, ConnectionStatus.ACCEPTED, Instant.now(), Instant.now(),
			null));
	}

	@Test
	void groupWithSomeoneWhoBlockedTheCreatorIsRefused() throws Exception {
		var creator = auth.authenticate("ccr_creator");
		var blocker = auth.authenticate("ccr_blocker");
		var other = auth.authenticate("ccr_other");
		mockMvc.perform(post("/network/blocks/" + creator.userId())
				.header("X-Session-Token", blocker.token())
				.header("Idempotency-Key", "ccr-block-1"))
			.andExpect(status().isCreated());

		create(creator, "ccr-group-1", Map.of("kind", "GROUP", "title", "grupo",
				"participantIds", List.of(other.userId(), blocker.userId())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("USER_BLOCKED"));
	}

	@Test
	void groupWithAnUnconnectedMinorIsRefused() throws Exception {
		var creator = auth.authenticate("ccr_minor_creator");
		var minor = auth.authenticate("ccr_minor_target");
		makeMinor(minor.userId());

		create(creator, "ccr-minor-1", Map.of("kind", "GROUP", "title", "grupo",
				"participantIds", List.of(minor.userId())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MINOR_DM_RESTRICTED"));
	}

	@Test
	void otherGroupMembersCannotMessageAMinorTheyAreNotConnectedTo() throws Exception {
		var creator = auth.authenticate("ccr_g_creator");
		var stranger = auth.authenticate("ccr_g_stranger");
		var minor = auth.authenticate("ccr_g_minor");
		makeMinor(minor.userId());
		connect(minor.userId(), creator.userId());

		var created = create(creator, "ccr-g-1", Map.of("kind", "GROUP", "title", "grupo",
				"participantIds", List.of(minor.userId(), stranger.userId())))
			.andExpect(status().isCreated())
			.andReturn();
		String conversationId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(post("/conversations/" + conversationId + "/messages")
				.header("X-Session-Token", stranger.token())
				.header("Idempotency-Key", "ccr-g-msg-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("text", "oi"))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MINOR_DM_RESTRICTED"));
	}

	@Test
	void aPendingRequestDoesNotUnlockAMinorUntilItIsAccepted() throws Exception {
		var adult = auth.authenticate("ccr_pending_adult");
		var minor = auth.authenticate("ccr_pending_minor");
		makeMinor(minor.userId());
		connections.save(UserConnection.request(adult.userId(), minor.userId(), Instant.now()));

		create(adult, "ccr-pending-dm", Map.of("kind", "DIRECT", "participantIds", List.of(minor.userId())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MINOR_DM_RESTRICTED"));
		create(adult, "ccr-pending-group", Map.of("kind", "GROUP", "title", "grupo",
				"participantIds", List.of(minor.userId())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MINOR_DM_RESTRICTED"));

		jdbc.update("UPDATE user_connections SET status = 'ACCEPTED' WHERE requester_user_id = ? AND receiver_user_id = ?",
			adult.userId(), minor.userId());
		create(adult, "ccr-accepted-dm", Map.of("kind", "DIRECT", "participantIds", List.of(minor.userId())))
			.andExpect(status().is2xxSuccessful());
	}

	@Test
	void missingParticipantsIsABadRequest() throws Exception {
		var creator = auth.authenticate("ccr_empty");
		create(creator, "ccr-empty-1", Map.of("kind", "GROUP"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARTICIPANTS"));
	}

	@Test
	void systemKindsCannotBeCreatedThroughTheGenericEndpoint() throws Exception {
		var creator = auth.authenticate("ccr_kind");
		var other = auth.authenticate("ccr_kind_other");
		create(creator, "ccr-kind-1", Map.of("kind", "JOB", "participantIds", List.of(other.userId())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CONVERSATION_KIND"));
	}
}
