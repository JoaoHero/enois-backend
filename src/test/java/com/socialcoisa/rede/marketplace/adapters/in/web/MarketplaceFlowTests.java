package com.socialcoisa.rede.marketplace.adapters.in.web;

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

import tools.jackson.databind.ObjectMapper;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * O marketplace com escrow (Fase 4): o criador lista um tema, o comprador paga e as
 * faíscas ficam RETIDAS até a decisão — confirmar libera 90% para o criador (+10% de
 * taxa na tesouraria), reembolso devolve 100%. Ninguém gasta nem saca no meio.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MarketplaceFlowTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void listBuyConfirmAndRefund() throws Exception {
		var author = auth("mkt_author");
		var buyer = auth("mkt_buyer");

		// Carteiras (genesis: 100 em cada).
		openWallet(author.token());
		openWallet(buyer.token());

		// O autor publica um tema na galeria.
		MvcResult created = mockMvc.perform(post("/temas/galeria")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "mkt-theme-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Tema à Venda", "description", "tema do marketplace", "modeDefault", "light",
					"packageJson", pkg("#fdf6e3", "#586e75")))))
			.andExpect(status().isOk())
			.andReturn();
		String themeId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		// Lista o tema por 50 faíscas (o piso do marketplace).
		MvcResult listed = mockMvc.perform(post("/marketplace")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "mkt-pub-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"itemId", themeId, "title", "Tema à Venda", "price", 50_000))))
			.andExpect(status().isOk())
			.andReturn();
		String listingId = objectMapper.readTree(listed.getResponse().getContentAsString()).get("id").asText();

		// O autor não compra o próprio item.
		mockMvc.perform(post("/marketplace/" + listingId + "/buy")
				.header("Idempotency-Key", java.util.UUID.randomUUID().toString())
				.header("X-Session-Token", author.token()))
			.andExpect(status().isBadRequest());

		// O comprador paga: escrow retém, o saldo cai 105.000 -> 55.000 (mf).
		MvcResult bought = mockMvc.perform(post("/marketplace/" + listingId + "/buy")
				.header("Idempotency-Key", java.util.UUID.randomUUID().toString())
				.header("X-Session-Token", buyer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("HELD"))
			.andReturn();
		String purchaseId = objectMapper.readTree(bought.getResponse().getContentAsString())
			.get("purchaseId").asText();
		expectBalance(buyer.token(), 55_000);

		// Confirmado: o criador recebe 90% (45) e a taxa fica na tesouraria.
		mockMvc.perform(post("/marketplace/purchases/" + purchaseId + "/confirm")
				.header("Idempotency-Key", java.util.UUID.randomUUID().toString())
				.header("X-Session-Token", buyer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("RELEASED"));
		expectBalance(author.token(), 150_000);

		// Um segundo ciclo para o reembolso: nova listagem, compra e devolução integral.
		MvcResult created2 = mockMvc.perform(post("/temas/galeria")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "mkt-theme-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Tema Reembolsável", "description", "outro tema", "modeDefault", "light",
					"packageJson", pkg("#eee8d5", "#073642")))))
			.andExpect(status().isOk())
			.andReturn();
		String theme2 = objectMapper.readTree(created2.getResponse().getContentAsString()).get("id").asText();
		MvcResult listed2 = mockMvc.perform(post("/marketplace")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "mkt-pub-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"itemId", theme2, "title", "Tema Reembolsável", "price", 50_000))))
			.andExpect(status().isOk())
			.andReturn();
		String listing2 = objectMapper.readTree(listed2.getResponse().getContentAsString()).get("id").asText();
		MvcResult bought2 = mockMvc.perform(post("/marketplace/" + listing2 + "/buy")
				.header("Idempotency-Key", java.util.UUID.randomUUID().toString())
				.header("X-Session-Token", buyer.token()))
			.andExpect(status().isOk())
			.andReturn();
		String purchase2 = objectMapper.readTree(bought2.getResponse().getContentAsString())
			.get("purchaseId").asText();

		// Reembolso: o comprador volta a ter o valor (55.000 -> 55.000: comprou 50, devolveu 50).
		mockMvc.perform(post("/marketplace/purchases/" + purchase2 + "/refund")
				.header("Idempotency-Key", java.util.UUID.randomUUID().toString())
				.header("X-Session-Token", buyer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("REFUNDED"));
		expectBalance(buyer.token(), 55_000);
	}

	private void openWallet(String token) throws Exception {
		mockMvc.perform(get("/carteira").header("X-Session-Token", token)).andExpect(status().isOk());
	}

	private void expectBalance(String token, long expected) throws Exception {
		MvcResult wallet = mockMvc.perform(get("/carteira").header("X-Session-Token", token)).andExpect(status().isOk())
			.andReturn();
		long balance = objectMapper.readTree(wallet.getResponse().getContentAsString()).get("balance").asLong();
		org.assertj.core.api.Assertions.assertThat(balance)
			.as("saldo: " + wallet.getResponse().getContentAsString()).isEqualTo(expected);
	}

	private TestAuthHelper.AuthSession auth(String name) throws Exception {
		return new TestAuthHelper(mockMvc, objectMapper).authenticate(name);
	}

	private String pkg(String bg, String strong) {
		return "{\"palette\":{\"background\":\"" + bg + "\",\"surface\":\"" + bg
			+ "\",\"muted\":\"" + bg + "\",\"border\":\"" + bg
			+ "\",\"primary\":\"#7c3aed\",\"onPrimary\":\"#ffffff\",\"soft\":\"#ede8f5\",\"text\":{\"strong\":\""
			+ strong + "\",\"subtle\":\"#555555\",\"date\":\"#777777\"},\"radius\":\"8px\"}}";
	}
}
