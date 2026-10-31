package com.socialcoisa.rede.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.coin.application.CoinService;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Marketplace além de tema: qualquer conteúdo (template, banner...) é publicado, comprado e o
 * comprador abre o material para editar/copiar; ninguém mais abre.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MarketplaceGenericItemTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private CoinService coins;

	private String publishTemplate(TestAuthHelper.AuthSession seller, String title, String content)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/marketplace/items")
				.header("X-Session-Token", seller.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"kind", "TEMPLATE",
					"title", title,
					"description", "Template de teste",
					"content", content,
					"price", CoinService.FAIS(50)))))
			.andExpect(status().isOk())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	@Test
	void buyerReceivesTheContentAndNobodyElseCanOpenIt() throws Exception {
		var auth = new TestAuthHelper(mockMvc, objectMapper);
		var seller = auth.authenticate("mkt_template_seller");
		var buyer = auth.authenticate("mkt_template_buyer");
		var stranger = auth.authenticate("mkt_template_stranger");

		String html = "<section><h1>Olá, {{nome}}</h1></section>";
		String listingId = publishTemplate(seller, "Template de apresentação", html);

		// O comprador precisa de faíscas para o escrow.
		coins.reward(buyer.userId(), CoinService.FAIS(60), "test-mkt-buyer-" + buyer.userId(),
			"Saldo de teste");

		// Busca pelo título devolve a listagem.
		String catalog = mockMvc.perform(get("/marketplace").param("q", "apresentação"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(catalog).contains(listingId);

		MvcResult buyResult = mockMvc.perform(post("/marketplace/" + listingId + "/buy")
				.header("Idempotency-Key", java.util.UUID.randomUUID().toString())
				.header("X-Session-Token", buyer.token()))
			.andReturn();
		assertThat(buyResult.getResponse().getStatus())
			.as("compra: " + buyResult.getResponse().getContentAsString())
			.isEqualTo(200);
		String purchaseId = objectMapper.readTree(buyResult.getResponse().getContentAsString())
			.get("purchaseId").asText();

		mockMvc.perform(get("/marketplace/purchases/" + purchaseId + "/content")
				.header("X-Session-Token", buyer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("TEMPLATE"))
			.andExpect(jsonPath("$.content").value(html));

		// Quem não comprou não abre.
		mockMvc.perform(get("/marketplace/purchases/" + purchaseId + "/content")
				.header("X-Session-Token", stranger.token()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_THE_BUYER"));

		// As compras trazem a categoria e o título (agrupamento de "Minhas compras").
		String purchases = mockMvc.perform(get("/marketplace/compras")
				.header("X-Session-Token", buyer.token()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(purchases).contains("TEMPLATE").contains("Template de apresentação");

		mockMvc.perform(post("/marketplace/purchases/" + purchaseId + "/refund")
				.header("Idempotency-Key", java.util.UUID.randomUUID().toString())
				.header("X-Session-Token", buyer.token()))
			.andExpect(status().isOk());
		mockMvc.perform(get("/marketplace/purchases/" + purchaseId + "/content")
				.header("X-Session-Token", buyer.token()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PURCHASE_REFUNDED"));
	}
}
