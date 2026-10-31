package com.socialcoisa.rede.support.adapters.in.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;
import com.socialcoisa.rede.support.application.port.out.GithubIssuesGateway;
import com.socialcoisa.rede.support.application.port.out.SupportRequestRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Integração do formulário de suporte com as issues do GitHub: o pedido nasce como issue, fechar
 * no painel fecha a issue e fechar a issue (webhook) conclui o pedido e avisa quem abriu.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SupportGithubIssuesTests {

	private static final String SECRET = "segredo-de-teste";
	private static final String REPO = "Rede-ENois/enois-suporte";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private SupportRequestRepository repository;

	@MockitoBean
	private GithubIssuesGateway gateway;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("rede.admin.emails", () -> "sg_admin@test.com");
		registry.add("rede.support.github.repo", () -> REPO);
		registry.add("rede.support.github.webhook-secret", () -> SECRET);
	}

	@Test
	void webhookSemAssinaturaValidaNaoMexeEmNada() throws Exception {
		String body = issueEvent(9999, "closed");
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

		mockMvc.perform(post("/webhooks/github").header("X-GitHub-Event", "issues")
			.contentType(MediaType.APPLICATION_JSON).content(bytes))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/webhooks/github").header("X-GitHub-Event", "issues")
			.header("X-Hub-Signature-256", "sha256=00")
			.contentType(MediaType.APPLICATION_JSON).content(bytes))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void fecharAIssueConcluiOPedidoEPersisteOEspelho() throws Exception {
		var user = new TestAuthHelper(mockMvc, objectMapper).authenticate("sg_user");
		var admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("sg_admin");
		String ticketId = openTicket(user.token());
		attachIssue(ticketId, 4321);

		String body = issueEvent(4321, "closed");
		mockMvc.perform(post("/webhooks/github").header("X-GitHub-Event", "issues")
			.header("X-Hub-Signature-256", signature(body.getBytes(StandardCharsets.UTF_8)))
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk());

		// O painel mostra o ticket concluído e o número da issue para abrir no GitHub.
		mockMvc.perform(get("/admin/support").header("X-Session-Token", admin.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + ticketId + "')].status").value("CLOSED"))
			.andExpect(jsonPath("$[?(@.id == '" + ticketId + "')].githubIssueNumber").value(4321));

		// Quem abriu vê o encerramento no próprio histórico.
		mockMvc.perform(get("/support/requests/mine").header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + ticketId + "')].status").value("CLOSED"));
	}

	@Test
	void signedWebhookIsNotSubjectToTheUserContentLinkCheck() throws Exception {
		// Issue text written on GitHub is not user content of this app and is never rendered here.
		webhook("""
			{"action":"closed","issue":{"number":9998,"state":"closed","body":"[x](javascript:alert(1))"},
			"repository":{"full_name":"%s"}}
			""".formatted(REPO));
	}

	@Test
	void reabrirAIssueVoltaOPedidoParaAberto() throws Exception {
		var user = new TestAuthHelper(mockMvc, objectMapper).authenticate("sg_user2");
		String ticketId = openTicket(user.token());
		attachIssue(ticketId, 8765);

		String closed = issueEvent(8765, "closed");
		webhook(closed);

		String reopened = issueEvent(8765, "open");
		webhook(reopened);

		mockMvc.perform(get("/support/requests/mine").header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + ticketId + "')].status").value("OPEN"));
	}

	@Test
	void abrirPedidoCriaAIssueEEspelhaONumeroNoPainel() throws Exception {
		var user = new TestAuthHelper(mockMvc, objectMapper).authenticate("sg_user3");
		var admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("sg_admin");
		when(gateway.enabled()).thenReturn(true);
		when(gateway.createIssue(anyString(), anyString()))
			.thenReturn(Optional.of(new GithubIssuesGateway.IssueRef(2468, "https://github.com/" + REPO + "/issues/2468")));

		String ticketId = openTicket(user.token());

		verify(gateway).createIssue(anyString(), anyString());
		mockMvc.perform(get("/admin/support").header("X-Session-Token", admin.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + ticketId + "')].githubIssueNumber").value(2468))
			.andExpect(jsonPath("$[?(@.id == '" + ticketId + "')].githubIssueUrl")
				.value("https://github.com/" + REPO + "/issues/2468"));
	}

	private String openTicket(String token) throws Exception {
		MvcResult created = mockMvc.perform(post("/support/requests").header("X-Session-Token", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of(
				"subject", "Falha ao anexar imagem",
				"message", "O anexo não sobe quando a imagem tem mais de 5 MB no iPhone.",
				"contactEmail", "contato" + System.nanoTime() + "@test.com",
				"category", "BUG"))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asString();
	}

	private void attachIssue(String ticketId, int number) {
		var request = repository.findById(ticketId).orElseThrow();
		repository.save(request.withGithubIssue(number, "https://github.com/" + REPO + "/issues/" + number));
	}

	private void webhook(String body) throws Exception {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		mockMvc.perform(post("/webhooks/github").header("X-GitHub-Event", "issues")
			.header("X-Hub-Signature-256", signature(bytes))
			.contentType(MediaType.APPLICATION_JSON).content(bytes))
			.andExpect(status().isOk());
	}

	private String issueEvent(int number, String state) {
		return """
			{"action":"%s","issue":{"number":%d,"state":"%s"},"repository":{"full_name":"%s"}}
			""".formatted("open".equals(state) ? "reopened" : "closed", number, state, REPO);
	}

	private String signature(byte[] body) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
	}
}
