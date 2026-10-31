package com.socialcoisa.rede.profile.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * O papel de admin é a coluna imutável: um admin promove e revoga outros admins pela API, com
 * trava contra revogar a própria conta e sem caminho para quem não é admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminPromotionTests {

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("rede.admin.emails", () -> "admprom_admin@test.com");
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void adminPromoteERevogaOutroAdmin() throws Exception {
		var admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("admprom_admin");
		var promoted = new TestAuthHelper(mockMvc, objectMapper).authenticate("admprom_promoted");

		// Antes: não é admin.
		mockMvc.perform(get("/admin/moderators").header("X-Session-Token", promoted.token()))
			.andExpect(status().isForbidden());

		// Promove pelo username.
		mockMvc.perform(post("/admin/admins")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("username", "admprom-promoted"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("admprom-promoted"));

		// O novo admin já tem acesso com a sessão que já tinha (o papel é consultado por request).
		mockMvc.perform(get("/admin/moderators").header("X-Session-Token", promoted.token()))
			.andExpect(status().isOk());

		// Aparece na lista.
		mockMvc.perform(get("/admin/admins").header("X-Session-Token", admin.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.username == 'admprom-promoted')]").exists());

		// Promover de novo é conflito, não erro silencioso.
		mockMvc.perform(post("/admin/admins")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("username", "admprom-promoted"))))
			.andExpect(status().isConflict());

		// Revogar a própria conta pela API é bloqueado (revogação de si é operação manual).
		mockMvc.perform(post("/admin/admins/admprom-admin/revoke").header("X-Session-Token", admin.token()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CANNOT_REVOKE_SELF"));

		// Revoga o promovido: o acesso cai.
		mockMvc.perform(post("/admin/admins/admprom-promoted/revoke").header("X-Session-Token", admin.token()))
			.andExpect(status().isOk());

		mockMvc.perform(get("/admin/moderators").header("X-Session-Token", promoted.token()))
			.andExpect(status().isForbidden());
	}

	@Test
	void quemNaoEAdminNaoMexa() throws Exception {
		var outsider = new TestAuthHelper(mockMvc, objectMapper).authenticate("admprom_outsider");

		mockMvc.perform(get("/admin/admins").header("X-Session-Token", outsider.token()))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/admins")
				.header("X-Session-Token", outsider.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("username", "admprom_outsider"))))
			.andExpect(status().isForbidden());
	}
}
