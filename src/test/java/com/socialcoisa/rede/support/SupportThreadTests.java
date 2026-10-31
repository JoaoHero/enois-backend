package com.socialcoisa.rede.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;
import com.socialcoisa.rede.support.application.SupportWebhookService;
import com.socialcoisa.rede.support.application.port.out.SupportRequestRepository;
import com.socialcoisa.rede.support.domain.SupportCategory;
import com.socialcoisa.rede.support.domain.SupportRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Conversa do ticket: resposta do painel + comentários do GitHub, sem duplicar o eco do webhook. */
@SpringBootTest
@AutoConfigureMockMvc
class SupportThreadTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private SupportRequestRepository requests;

	@Autowired
	private SupportWebhookService webhook;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void respostaDoPainelEComentariosDoGithubFormamAConversaSemDuplicar() throws Exception {
		var solicitante = auth.authenticate("support_thread_user_" + System.nanoTime());
		var equipe = auth.authenticate("group-admin");

		MvcResult aberto = mockMvc.perform(post("/support/requests")
				.header("X-Session-Token", solicitante.token())
				.header("Idempotency-Key", "support-thread-open")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"subject", "Falha no login", "message", "Não consigo entrar na conta.",
					"contactEmail", "pessoa@example.com", "category", "BUG"))))
			.andExpect(status().isCreated())
			.andReturn();
		String ticketId = objectMapper.readTree(aberto.getResponse().getContentAsString()).get("id").asString();

		// A issue é espelhada no repositório privado; no teste ela é vinculada direto.
		SupportRequest ticket = requests.findById(ticketId).orElseThrow();
		int issue = 9000 + (int) (System.nanoTime() % 1000);
		requests.save(ticket.withGithubIssue(issue, "https://github.com/Rede-ENois/suporte/issues/" + issue));

		mockMvc.perform(post("/admin/support/" + ticketId + "/reply")
				.header("X-Session-Token", equipe.token())
				.header("Idempotency-Key", "support-thread-reply")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("message", "Já estamos verificando o acesso."))))
			.andExpect(status().isOk());

		MvcResult primeira = mockMvc.perform(get("/admin/support/" + ticketId + "/messages")
				.header("X-Session-Token", equipe.token()))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode mensagens = objectMapper.readTree(primeira.getResponse().getContentAsString());
		assertThat(mensagens.size()).isEqualTo(1);
		assertThat(mensagens.get(0).path("direction").asString()).isEqualTo("ENOIS");
		assertThat(mensagens.get(0).path("body").asString()).contains("verificando");

		long commentId = 700000 + System.nanoTime() % 1000;
		assertThat(webhook.applyIssueComment(issue, commentId, "octocat", "Consegui reproduzir.")).isTrue();
		// O webhook do próprio comentário publicado pelo Énóis chega com o mesmo id: não duplica.
		assertThat(webhook.applyIssueComment(issue, commentId, "octocat", "Consegui reproduzir.")).isTrue();

		MvcResult segunda = mockMvc.perform(get("/admin/support/" + ticketId + "/messages")
				.header("X-Session-Token", equipe.token()))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode conversa = objectMapper.readTree(segunda.getResponse().getContentAsString());
		assertThat(conversa.size()).isEqualTo(2);
		assertThat(conversa.get(1).path("direction").asString()).isEqualTo("GITHUB");
		assertThat(conversa.get(1).path("authorGithubLogin").asString()).isEqualTo("octocat");

		// Sem sessão de moderador a conversa não é exposta.
		mockMvc.perform(get("/admin/support/" + ticketId + "/messages")
				.header("X-Session-Token", solicitante.token()))
			.andExpect(status().isForbidden());
	}
}