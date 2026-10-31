package com.socialcoisa.rede.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

/**
 * Campo de texto que passa do teto da coluna tem de ser recusado com **400 que diz o campo**.
 *
 * Sem a checagem, o INSERT falhava com "Data too long for column" e o tradutor de exceções
 * devolvia **409 CONFLICT** — "Operação conflita com o estado atual dos dados". Para a pessoa que
 * PUBLICA uma empresa ou um evento, essa mensagem não significa nada: que conflito, com qual
 * estado? (No log do servidor o motivo ficava, invisível para ela.)
 *
 * O teto é o mesmo nos três lugares: a coluna do banco, a checagem da API e o contador do front.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TextFieldLimitTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void empresaComNomeLongoRecusaDizendoOCampo() throws Exception {
		var dono = auth("tfl_empresa");
		mockMvc.perform(post("/empresas")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "tfl-empresa-longa")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "E".repeat(200), "description", "Empresa de teste."))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMPANY_FIELD_TOO_LONG"))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("nome")))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("160")));
	}

	@Test
	void eventoComTituloLongoRecusaDizendoOCampo() throws Exception {
		var dono = auth("tfl_evento");
		mockMvc.perform(post("/events")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "tfl-evento-longo")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "T".repeat(300), "date", "2030-01-02", "time", "19:00"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EVENT_FIELD_TOO_LONG"))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("titulo")))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("255")));
	}

	@Test
	void freelaComTituloLongoRecusaDizendoOCampo() throws Exception {
		var dono = auth("tfl_freela");
		mockMvc.perform(post("/freelances")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "tfl-freela-longo")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "T".repeat(300),
					"scope", "Escopo com pelo menos vinte caracteres para passar da validacao minima.",
					"budgetMin", 10, "budgetMax", 20,
					"deadline", "2030-01-02", "paymentMethod", "FAISCAS"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FREELANCE_FIELD_TOO_LONG"))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("titulo")))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("200")));
	}


	@Test
	void ofertaDeServicoComDescricaoLongaRecusaDizendoOCampo() throws Exception {
		var dono = auth("tfl_oferta");
		// A API aceitava 4000 caracteres e a coluna e varchar(2000): o banco recusava com
		// "Data too long" e a pessoa levava 409 "conflita com o estado atual dos dados".
		mockMvc.perform(post("/servicos/ofertas")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "tfl-oferta-longa")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"category", "design", "title", "Capa de profissional",
					"description", "D".repeat(2500),
					"deliverables", "Arte de capa, dois dias.",
					"price", 100, "deliveryDays", 2))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SERVICE_FIELD_TOO_LONG"))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("descricao")))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("2.000")));
	}

	@Test
	void jogoComTituloLongoRecusaDizendoOCampo() throws Exception {
		var dono = auth("tfl_jogo");
		mockMvc.perform(post("/games")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "tfl-jogo-longo")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "J".repeat(300), "url", "https://exemplo.itch.io/jogo"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("GAME_FIELD_TOO_LONG"))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("titulo")))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("200")));
	}

	@Test
	void microprojetoComTituloLongoRecusaDizendoOCampo() throws Exception {
		var dono = auth("tfl_micro");
		var empresa = mockMvc.perform(post("/empresas")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "tfl-micro-empresa")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Estudio de Teste", "description", "Empresa criada para o teste de limite."))))
			.andExpect(status().isOk())
			.andReturn();
		String companyId = objectMapper.readTree(empresa.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(post("/empresas/" + companyId + "/microprojetos")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "tfl-micro-longo")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "M".repeat(300),
					"problem", "Problema real com pelo menos trinta caracteres para passar.",
					"deliverables", "Entregaveis com pelo menos vinte caracteres.",
					"acceptanceCriteria", "Criterios com pelo menos vinte caracteres."))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMPANY_FIELD_TOO_LONG"))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("titulo")))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("200")));
	}

	@Test
	void contestacaoComMotivoLongoRecusaDizendoOCampo() throws Exception {
		var dono = auth("tfl_contesta");
		mockMvc.perform(post("/carteira/transacoes/coin_x/contestar")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "tfl-contesta-longa")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"reason", "R".repeat(500),
					"description", "Descricao com pelo menos vinte caracteres."))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DISPUTE_FIELD_TOO_LONG"))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("motivo")))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("400")));
	}


	@Test
	void comunidadeComNomeLongoRecusaDizendoOCampo() throws Exception {
		var dono = auth("tfl_comunidade");
		mockMvc.perform(post("/communities")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "tfl-comunidade-longa")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "C".repeat(300), "description", "Comunidade de teste."))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMUNITY_FIELD_TOO_LONG"))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("nome")))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("255")));
	}

	@Test
	void colecaoDeJogoComTituloLongoRecusaDizendoOCampo() throws Exception {
		// A colecao curada e da administracao (a porta exige admin antes de chegar no servico).
		var dono = auth("group-admin");
		mockMvc.perform(post("/games/colecoes")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "tfl-colecao-longa")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "K".repeat(300), "gameIds", java.util.List.of()))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("GAME_FIELD_TOO_LONG"))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("titulo")))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("160")));
	}

	private TestAuthHelper.AuthSession auth(String username) throws Exception {
		return new TestAuthHelper(mockMvc, objectMapper).authenticate(username);
	}
}
