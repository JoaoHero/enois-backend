package com.socialcoisa.rede.contributors.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Formulário público de contribuição + fila interna de documentação. */
@SpringBootTest
@AutoConfigureMockMvc
class ContributorApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void publicApplicationIsStoredAndAdminCanSeeAndTriageIt() throws Exception {
		var admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("group-admin");
		var regular = new TestAuthHelper(mockMvc, objectMapper).authenticate("contrib_regular");

		Map<String, Object> payload = new HashMap<>();
		payload.put("name", "Maria Contribuidora");
		payload.put("github", "https://github.com/Maria-Contrib/");
		payload.put("email", "maria@example.com");
		payload.put("area", "ENGENHARIA");
		payload.put("experience", "AVANCADO");
		payload.put("message", "Quero ajudar com testes e revisão de código.");
		payload.put("acceptedTerms", true);

		MvcResult created = mockMvc.perform(post("/contributors/applications")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(payload)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"))
			.andReturn();
		String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
		assertThat(id).startsWith("contrib_");

		// Não-admin não enxerga a fila.
		mockMvc.perform(get("/admin/contributors/applications")
				.header("X-Session-Token", regular.token()))
			.andExpect(status().isForbidden());

		MvcResult listed = mockMvc.perform(get("/admin/contributors/applications")
				.header("X-Session-Token", admin.token()))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode rows = objectMapper.readTree(listed.getResponse().getContentAsString());
		JsonNode own = null;
		for (JsonNode row : rows) {
			if (id.equals(row.get("id").asText())) {
				own = row;
			}
		}
		assertThat(own).isNotNull();
		assertThat(own.get("github").asText()).isEqualTo("maria-contrib");
		assertThat(own.get("email").asText()).isEqualTo("maria@example.com");
		assertThat(own.get("acceptedTermsAt").asText()).isNotBlank();

		mockMvc.perform(patch("/admin/contributors/applications/" + id)
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("status", "REVIEWING"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REVIEWING"));
	}

	@Test
	void termsAndGithubAreRequired() throws Exception {
		Map<String, Object> withoutTerms = new HashMap<>();
		withoutTerms.put("name", "Sem Aceite");
		withoutTerms.put("github", "sem-aceite");
		withoutTerms.put("email", "sem-aceite@example.com");
		withoutTerms.put("acceptedTerms", false);

		mockMvc.perform(post("/contributors/applications")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(withoutTerms)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));

		Map<String, Object> invalidGithub = new HashMap<>(withoutTerms);
		invalidGithub.put("acceptedTerms", true);
		invalidGithub.put("github", "nome com espaço");

		mockMvc.perform(post("/contributors/applications")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(invalidGithub)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_GITHUB"));
	}
}
