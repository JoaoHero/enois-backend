package com.socialcoisa.rede.cosmetics.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;
import com.socialcoisa.rede.shared.web.TestDatabase;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class CommunityCosmeticsFlowTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void creatorCanSaveDraftAndSubmitForModerationButPendingItemIsNotPublic() throws Exception {
		var creator = auth("community_cosmetic_creator");
		String manifest = paletteManifest();

		MvcResult created = mockMvc.perform(post("/cosmetics/drafts")
			.header("X-Session-Token", creator.token()).header("Idempotency-Key", "cosmetic-draft-1")
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of(
				"kind", "PALETTE", "name", "Aurora Laranja", "description", "Paleta quente",
				"manifestJson", manifest, "license", "ORIGINAL", "rightsConfirmed", true))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.kind").value("PALETTE"))
			.andReturn();
		String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(get("/cosmetics/mine").header("X-Session-Token", creator.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(id));

		mockMvc.perform(post("/cosmetics/" + id + "/submit")
			.header("X-Session-Token", creator.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING_MODERATION"));

		mockMvc.perform(get("/cosmetics/catalog"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty());
	}

	@Test
	void manifestIsDeclarativeAndRightsConfirmationIsRequired() throws Exception {
		var creator = auth("community_cosmetic_validation");

		mockMvc.perform(post("/cosmetics/drafts")
			.header("X-Session-Token", creator.token()).header("Idempotency-Key", "cosmetic-rights-1")
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of(
				"kind", "EFFECT", "name", "Efeito Inseguro", "manifestJson", effectManifest("url(evil)"),
				"license", "ORIGINAL", "rightsConfirmed", true))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_COSMETIC_MANIFEST"));

		mockMvc.perform(post("/cosmetics/drafts")
			.header("X-Session-Token", creator.token()).header("Idempotency-Key", "cosmetic-rights-2")
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of(
				"kind", "EFFECT", "name", "Efeito Sem Licença", "manifestJson", effectManifest("GLOW"),
				"license", "ORIGINAL", "rightsConfirmed", false))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RIGHTS_CONFIRMATION_REQUIRED"));
	}

	@Test
	void onlyCreatorCanEditAndAnonymousCanBrowseThePublicCatalog() throws Exception {
		var creator = auth("community_cosmetic_owner");
		var stranger = auth("community_cosmetic_stranger");
		String id = createDraft(creator, "cosmetic-owner-1", "Borda Solar", "PROFILE_BORDER", borderManifest());

		mockMvc.perform(patch("/cosmetics/" + id).header("X-Session-Token", stranger.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of("name", "Sequestrada"))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_AUTHORIZED"));

		mockMvc.perform(get("/cosmetics/catalog?kind=PROFILE_BORDER"))
			.andExpect(status().isOk());

		mockMvc.perform(post("/cosmetics/drafts").contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of("kind", "PALETTE", "name", "Anonima",
				"manifestJson", paletteManifest(), "license", "ORIGINAL", "rightsConfirmed", true))))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void ownerCanPersistAValidatedProfileNameStyleAndItIsReturnedByTheProfileApi() throws Exception {
		var owner = auth("community_name_style_owner");

		mockMvc.perform(put("/cosmetics/profile/name-style")
			.header("X-Session-Token", owner.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of("theme", "NEON", "font", "DISPLAY", "color", "#FF4FA3"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.theme").value("NEON"))
			.andExpect(jsonPath("$.font").value("DISPLAY"))
			.andExpect(jsonPath("$.color").value("#ff4fa3"));

		mockMvc.perform(get("/me").header("X-Session-Token", owner.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nameTheme").value("NEON"))
			.andExpect(jsonPath("$.nameFont").value("DISPLAY"))
			.andExpect(jsonPath("$.nameColor").value("#ff4fa3"));

		mockMvc.perform(put("/cosmetics/profile/name-style")
			.header("X-Session-Token", owner.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of("font", "url(evil)", "color", "red"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_NAME_FONT"));
	}

	@Test
	void newNameThemesAndFontsAreAccepted() throws Exception {
		var owner = auth("community_name_style_new");

		for (String theme : List.of("RETRO", "OCEAN", "SUNSET", "MATRIX", "GOLD", "ICE")) {
			mockMvc.perform(put("/cosmetics/profile/name-style")
				.header("X-Session-Token", owner.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"theme", theme, "font", "CURSIVE", "color", "#0ea5e9"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.theme").value(theme));
		}

		for (String font : List.of("CURSIVE", "CONDENSED", "COMIC", "SLAB")) {
			mockMvc.perform(put("/cosmetics/profile/name-style")
				.header("X-Session-Token", owner.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"theme", "OCEAN", "font", font, "color", "#0ea5e9"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.font").value(font));
		}
	}

	private String createDraft(TestAuthHelper.AuthSession author, String key, String name, String kind,
		String manifest) throws Exception {
		MvcResult result = mockMvc.perform(post("/cosmetics/drafts")
			.header("X-Session-Token", author.token()).header("Idempotency-Key", key)
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of("kind", kind, "name", name, "manifestJson", manifest,
				"license", "ORIGINAL", "rightsConfirmed", true))))
			.andExpect(status().isOk()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private static String paletteManifest() {
		return "{\"version\":1,\"kind\":\"PALETTE\",\"colors\":{\"primary\":\"#ff7a18\",\"accent\":\"#ff4fa3\"}}";
	}

	private static String effectManifest(String type) {
		return "{\"version\":1,\"kind\":\"EFFECT\",\"effect\":{\"type\":\"" + type + "\"}}";
	}

	private static String borderManifest() {
		return "{\"version\":1,\"kind\":\"PROFILE_BORDER\",\"layers\":[{\"type\":\"ring\",\"color\":\"#ff7a18\"}]}";
	}

	private TestAuthHelper.AuthSession auth(String name) throws Exception {
		String email = name + "@test.com";
		mockMvc.perform(post("/auth/signup").header("Idempotency-Key", "cosmetics-signup-" + name)
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of("name", name, "email", email, "password", "Test1234!",
				"acceptedTerms", true, "birthDate", "1990-05-10"))))
			.andExpect(status().isCreated());
		String verificationToken = "cosmetics-verify-" + name;
		TestDatabase.setPendingSignupToken(email, verificationToken);
		MvcResult verified = mockMvc.perform(post("/auth/email/verify")
			.header("Idempotency-Key", "cosmetics-verify-" + name).contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of("token", verificationToken))))
			.andExpect(status().isOk()).andReturn();
		return new TestAuthHelper.AuthSession(TestAuthHelper.sessionToken(verified), TestAuthHelper.refreshToken(verified),
			TestDatabase.userIdByEmail(email).orElseThrow());
	}
}
