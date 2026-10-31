package com.socialcoisa.rede.importlinkedin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Contatos do LinkedIn (Connections.csv) e convite automático: casa por nome completo exato,
 * convida quem ainda não é conexão, não convida em caso de ambiguidade e respeita o botão.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LinkedinContactInviteTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserProfileRepository profiles;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	/** Só o socialName importa para a correspondência; o nome de cadastro do teste é o username. */
	private void nomeSocial(String userId, String nome) {
		var profile = profiles.findById(userId).orElseThrow();
		profile.updateIdentityDisplay(null, nome, null, Instant.now());
		profiles.save(profile);
	}

	private MockMultipartFile connectionsCsv(String... names) {
		StringBuilder csv = new StringBuilder();
		csv.append("Notes:\n");
		csv.append("\"Quando você exporta seus dados, os contatos vêm neste arquivo.\"\n\n");
		csv.append("First Name,Last Name,URL,Email Address,Company,Position,Connected On\n");
		for (String name : names) {
			int space = name.lastIndexOf(' ');
			csv.append(name, 0, space).append(',').append(name.substring(space + 1))
				.append(",https://www.linkedin.com/in/x,,Empresa,Cargo,01 Jan 2024\n");
		}
		return new MockMultipartFile("file", "Connections.csv", "text/csv",
			csv.toString().getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void conviteAutomaticoEnviaParaQuemCasaENaoEnviaEmAmbiguidade() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		var eu = auth.authenticate("li_convite_" + suffix);
		var casado = auth.authenticate("li_casado_" + suffix);
		var ambiguo1 = auth.authenticate("li_amb1_" + suffix);
		var ambiguo2 = auth.authenticate("li_amb2_" + suffix);
		nomeSocial(casado.userId(), "Zulmira Qa" + suffix);
		nomeSocial(ambiguo1.userId(), "Ana Ambigua " + suffix);
		nomeSocial(ambiguo2.userId(), "Ana Ambigua " + suffix);

		// Botão ligado antes do arquivo: o convite sai na importação.
		mockMvc.perform(put("/me/import/linkedin/contatos/auto-convite")
				.header("X-Session-Token", eu.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("enabled", true))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.autoInvite").value(true));

		mockMvc.perform(multipart("/me/import/linkedin/contatos")
				.file(connectionsCsv("Zulmira Qa" + suffix, "Ana Ambigua " + suffix, "Zé Ninguem " + suffix))
				.header("X-Session-Token", eu.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contacts").value(3))
			.andExpect(jsonPath("$.invited").value(1))
			.andExpect(jsonPath("$.ambiguous").value(1))
			.andExpect(jsonPath("$.noMatch").value(1))
			.andExpect(jsonPath("$.autoInvite").value(true));

		// A solicitação chegou para quem casou, e só para ele.
		mockMvc.perform(get("/network/summary").header("X-Session-Token", casado.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pendingRequests").value(1));
		mockMvc.perform(get("/network/summary").header("X-Session-Token", ambiguo1.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pendingRequests").value(0));

		// A lista mostra o motivo de cada contato.
		mockMvc.perform(get("/me/import/linkedin/contatos").header("X-Session-Token", eu.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.status == 'INVITED')]").exists())
			.andExpect(jsonPath("$[?(@.status == 'AMBIGUOUS')]").exists())
			.andExpect(jsonPath("$[?(@.status == 'NO_MATCH')]").exists());
	}

	@Test
	void semOBotaoOsContatosFicamNaFilaELigarProcessa() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		var eu = auth.authenticate("li_fila_" + suffix);
		var contato = auth.authenticate("li_fila_contato_" + suffix);
		nomeSocial(contato.userId(), "Wesley Qa" + suffix);

		mockMvc.perform(multipart("/me/import/linkedin/contatos")
				.file(connectionsCsv("Wesley Qa" + suffix))
				.header("X-Session-Token", eu.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.invited").value(0))
			.andExpect(jsonPath("$.queued").value(1))
			.andExpect(jsonPath("$.autoInvite").value(false));

		mockMvc.perform(get("/network/summary").header("X-Session-Token", contato.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pendingRequests").value(0));

		mockMvc.perform(put("/me/import/linkedin/contatos/auto-convite")
				.header("X-Session-Token", eu.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("enabled", true))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.invited").value(1))
			.andExpect(jsonPath("$.queued").value(0));

		mockMvc.perform(get("/network/summary").header("X-Session-Token", contato.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pendingRequests").value(1));
	}

	@Test
	void reimportacaoNaoDuplicaConviteJaFeito() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		var eu = auth.authenticate("li_dup_" + suffix);
		var contato = auth.authenticate("li_dup_contato_" + suffix);
		nomeSocial(contato.userId(), "Dora Qa" + suffix);

		mockMvc.perform(put("/me/import/linkedin/contatos/auto-convite")
				.header("X-Session-Token", eu.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("enabled", true))))
			.andExpect(status().isOk());

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(multipart("/me/import/linkedin/contatos")
					.file(connectionsCsv("Dora Qa" + suffix))
					.header("X-Session-Token", eu.token()))
				.andExpect(status().isOk());
		}

		mockMvc.perform(get("/network/summary").header("X-Session-Token", contato.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pendingRequests").value(1));
		mockMvc.perform(get("/me/import/linkedin/contatos/resumo").header("X-Session-Token", eu.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contacts").value(1))
			.andExpect(jsonPath("$.invited").value(1))
			.andExpect(jsonPath("$.queued").value(0));
	}
}