package com.socialcoisa.rede.contributors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.contributions.application.ContributorSealService;
import com.socialcoisa.rede.contributions.application.GitHubIdentityService;
import com.socialcoisa.rede.contributions.application.port.out.GitHubContributionRepository;
import com.socialcoisa.rede.contributions.domain.ContributionCategory;
import com.socialcoisa.rede.contributions.domain.ContributionKind;
import com.socialcoisa.rede.contributions.domain.ContributionStatus;
import com.socialcoisa.rede.contributions.domain.GitHubContribution;
import com.socialcoisa.rede.contributors.application.port.out.ContributorApplicationRepository;
import com.socialcoisa.rede.contributors.domain.ContributorApplicationSource;
import com.socialcoisa.rede.contributors.domain.ContributorApplicationStatus;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Contribuinte verificado aparece sozinho no painel; o formulário da landing só confirma os dados e
 * a aprovação é o GitHub. A candidatura pública continua na fila manual.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VerifiedContributorPanelTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private GitHubIdentityService identities;

	@Autowired
	private GitHubContributionRepository contributions;

	@Autowired
	private ContributorSealService seals;

	@Autowired
	private ContributorApplicationRepository applications;

	@Autowired
	private UserProfileRepository profiles;

	@Test
	void verifiedContributorAppearsInPanelAndConfirmsWithoutApproval() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		var user = new TestAuthHelper(mockMvc, objectMapper).authenticate("verified_" + suffix);
		var admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("group-admin");
		String github = "verified-dev-" + suffix;
		verifyContributor(user.userId(), github, suffix);

		mockMvc.perform(get("/contributors/me").header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.verified").value(true))
			.andExpect(jsonPath("$.applicationPending").value(true))
			.andExpect(jsonPath("$.github").value(github));

		JsonNode before = panel(admin.token());
		JsonNode pending = byUser(before, user.userId());
		assertThat(pending.path("id").isNull()).isTrue();
		assertThat(pending.path("source").asText()).isEqualTo("GITHUB_VERIFIED");
		assertThat(pending.path("formPending").asBoolean()).isTrue();
		assertThat(pending.path("status").asText()).isEqualTo("APPROVED");
		assertThat(pending.path("github").asText()).isEqualTo(github);

		String email = "verified+" + suffix + "@example.com";
		mockMvc.perform(post("/contributors/applications/verified")
				.header("X-Session-Token", user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Pessoa Verificada",
					"github", "handle-que-o-cliente-inventou",
					"email", email,
					"area", "ENGENHARIA",
					"experience", "AVANCADO",
					"message", "Contribuo com código e testes.",
					"acceptedTerms", true))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("APPROVED"));

		var saved = applications.findByUserId(user.userId()).orElseThrow();
		assertThat(saved.source()).isEqualTo(ContributorApplicationSource.GITHUB_VERIFIED);
		assertThat(saved.github()).isEqualTo(github);
		String firstId = saved.id();

		// Repetir a confirmação atualiza a mesma candidatura (sem duplicar).
		mockMvc.perform(post("/contributors/applications/verified")
				.header("X-Session-Token", user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Pessoa Verificada",
					"email", email,
					"message", "Mensagem atualizada.",
					"acceptedTerms", true))))
			.andExpect(status().isCreated());
		assertThat(applications.findByUserId(user.userId()).orElseThrow().id()).isEqualTo(firstId);

		mockMvc.perform(get("/contributors/me").header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.applicationPending").value(false))
			.andExpect(jsonPath("$.application.id").value(firstId));

		JsonNode confirmed = byUser(panel(admin.token()), user.userId());
		assertThat(confirmed.path("id").asText()).isEqualTo(firstId);
		assertThat(confirmed.path("formPending").asBoolean()).isFalse();
		assertThat(confirmed.path("email").asText()).isEqualTo(email);
		assertThat(confirmed.path("github").asText()).isEqualTo(github);
		assertThat(confirmed.path("source").asText()).isEqualTo("GITHUB_VERIFIED");
	}

	@Test
	void unverifiedUserCannotConfirm() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		var user = new TestAuthHelper(mockMvc, objectMapper).authenticate("not_verified_" + suffix);

		mockMvc.perform(get("/contributors/me").header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.verified").value(false))
			.andExpect(jsonPath("$.applicationPending").value(false));

		mockMvc.perform(post("/contributors/applications/verified")
				.header("X-Session-Token", user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Sem Contribuição",
					"email", "sem+" + suffix + "@example.com",
					"acceptedTerms", true))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CONTRIBUTOR_NOT_VERIFIED"));
	}

	@Test
	void verifiedContributorCanChooseTheTagColor() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		var user = new TestAuthHelper(mockMvc, objectMapper).authenticate("tag_" + suffix);
		var other = new TestAuthHelper(mockMvc, objectMapper).authenticate("tag_other_" + suffix);
		String github = "tag-dev-" + suffix;
		verifyContributor(user.userId(), github, suffix);
		String username = profiles.findById(user.userId()).orElseThrow().username();

		mockMvc.perform(put("/cosmetics/profile/contributor-tag")
				.header("X-Session-Token", user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("color", "#12ab34"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.color").value("#12ab34"));

		mockMvc.perform(get("/users/" + username))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contributorTagColor").value("#12ab34"));

		mockMvc.perform(put("/cosmetics/profile/contributor-tag")
				.header("X-Session-Token", user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("color", "vermelho"))))
			.andExpect(status().isBadRequest());

		mockMvc.perform(put("/cosmetics/profile/contributor-tag")
				.header("X-Session-Token", other.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("color", "#12ab34"))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CONTRIBUTOR_NOT_VERIFIED"));
	}

	@Test
	void landingApplicationStillGoesThroughTheManualQueue() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		var admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("group-admin");
		String github = "landing-" + suffix;

		mockMvc.perform(post("/contributors/applications")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Candidato da Landing",
					"github", github,
					"email", "landing+" + suffix + "@example.com",
					"acceptedTerms", true))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"));

		JsonNode entry = byGithub(panel(admin.token()), github);
		assertThat(entry.path("id").isNull()).isFalse();
		assertThat(entry.path("source").asText()).isEqualTo("LANDING");
		assertThat(entry.path("formPending").asBoolean()).isFalse();
		assertThat(entry.path("status").asText()).isEqualTo("NEW");
	}

	@Test
	void landingApplicationWithTheSameGithubIsReusedOnConfirmation() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		var user = new TestAuthHelper(mockMvc, objectMapper).authenticate("reuse_" + suffix);
		String github = "reuse-dev-" + suffix;
		verifyContributor(user.userId(), github, suffix);

		mockMvc.perform(post("/contributors/applications")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Nome Antigo",
					"github", github,
					"email", "reuse+" + suffix + "@example.com",
					"acceptedTerms", true))))
			.andExpect(status().isCreated());
		String landingId = applications.findLandingByGithub(github).orElseThrow().id();

		mockMvc.perform(post("/contributors/applications/verified")
				.header("X-Session-Token", user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Nome Confirmado",
					"email", "reuse+" + suffix + "@example.com",
					"acceptedTerms", true))))
			.andExpect(status().isCreated());

		var saved = applications.findByUserId(user.userId()).orElseThrow();
		assertThat(saved.id()).isEqualTo(landingId);
		assertThat(saved.source()).isEqualTo(ContributorApplicationSource.GITHUB_VERIFIED);
		assertThat(saved.status()).isEqualTo(ContributorApplicationStatus.APPROVED);
		assertThat(saved.name()).isEqualTo("Nome Confirmado");
	}

	private void verifyContributor(String userId, String github, String suffix) {
		identities.remember(userId, "gh-" + github, github);
		Instant now = Instant.now();
		contributions.save(GitHubContribution.builder()
			.id("ghc_" + UUID.randomUUID().toString().replace("-", ""))
			.userId(userId).githubUserId("gh-" + github).githubLogin(github)
			.repository("Rede-ENois/verified-test-" + suffix)
			.kind(ContributionKind.PULL_REQUEST).number(1).objectId("node-verified-1")
			.title("Corrige um problema real").url("https://github.com/Rede-ENois/x/pull/1")
			.state("closed").openedAt(now).mergedAt(now).githubUpdatedAt(now).detailsFetched(true)
			.additions(20).deletions(2).changedFiles(2).filesJson("[]")
			.reviewsCount(1).approvalsCount(1).changesRequestedCount(0).hasTests(true).reverted(false)
			.category(ContributionCategory.CODE).status(ContributionStatus.VALIDATED).score(3)
			.signalsJson("{}").createdAt(now).updatedAt(now).build());
		seals.recalculate(userId);
	}

	private JsonNode panel(String adminToken) throws Exception {
		MvcResult result = mockMvc.perform(get("/admin/contributors/panel")
				.header("X-Session-Token", adminToken))
			.andExpect(status().isOk())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private static JsonNode byUser(JsonNode array, String userId) {
		return StreamSupport.stream(array.spliterator(), false)
			.filter(node -> userId.equals(node.path("userId").asText(null)))
			.findFirst().orElseThrow();
	}

	private static JsonNode byGithub(JsonNode array, String github) {
		return StreamSupport.stream(array.spliterator(), false)
			.filter(node -> github.equals(node.path("github").asText(null)))
			.findFirst().orElseThrow();
	}
}
