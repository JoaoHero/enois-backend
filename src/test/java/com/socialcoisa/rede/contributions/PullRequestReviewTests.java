package com.socialcoisa.rede.contributions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.contributions.application.port.out.GitHubGateway;
import com.socialcoisa.rede.contributions.application.port.out.GitHubIssue;
import com.socialcoisa.rede.contributions.application.port.out.GitHubPullRequest;
import com.socialcoisa.rede.contributions.application.port.out.GitHubRepositoryRegistry;
import com.socialcoisa.rede.contributions.domain.GitHubRepository;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/** Aba de PRs do painel: lista os abertos e envia APPROVE/REQUEST_CHANGES ao GitHub. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PullRequestReviewTests.FakeGatewayConfig.class)
class PullRequestReviewTests {

	private static final AtomicReference<String> DECISION = new AtomicReference<>();
	private static final AtomicReference<String> COMMENT = new AtomicReference<>();
	private static final AtomicReference<String> REPOSITORY = new AtomicReference<>();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private GitHubRepositoryRegistry registry;

	@TestConfiguration
	static class FakeGatewayConfig {
		@Bean
		@Primary
		GitHubGateway fakeGateway() {
			return new GitHubGateway() {
				@Override
				public List<GitHubPullRequest> listMergedPullRequests(String repository, Instant since,
						int maxPages) {
					return List.of();
				}

				@Override
				public List<GitHubIssue> listIssues(String repository, Instant since, int maxPages) {
					return List.of();
				}

				@Override
				public GitHubPullRequest withDetails(String repository, GitHubPullRequest pullRequest) {
					return pullRequest;
				}

				@Override
				public List<GitHubPullRequest> listOpenPullRequests(String repository, int maxPages) {
					Instant now = Instant.parse("2026-10-27T12:00:00Z");
					return List.of(new GitHubPullRequest(7, "node-7", "Corrige o cálculo", "https://github.com/x/7",
						"", "gh-7", "dev-seven", "open", now, null, now, 1, 1, 1, List.of(), 0, 0, 0));
				}

				@Override
				public boolean submitReview(String repository, long number, String decision, String body,
						String tokenOverride) {
					REPOSITORY.set(repository);
					DECISION.set(decision);
					COMMENT.set(body);
					return true;
				}
			};
		}
	}

	@Test
	void moderadorListaERevisaPullRequests() throws Exception {
		var admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("group-admin");
		String repo = "Rede-ENois/review-test-" + System.nanoTime();
		registry.save(new GitHubRepository(repo, true, Instant.now(), null));

		mockMvc.perform(get("/admin/github/pulls").header("X-Session-Token", admin.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].repository").exists())
			.andExpect(jsonPath("$[0].title").exists());

		mockMvc.perform(post("/admin/github/pulls/review")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"repository", repo, "number", 7, "decision", "APPROVE"))))
			.andExpect(status().isOk());
		assertThat(REPOSITORY.get()).isEqualTo(repo);
		assertThat(DECISION.get()).isEqualTo("APPROVE");

		mockMvc.perform(post("/admin/github/pulls/review")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"repository", repo, "number", 7, "decision", "REQUEST_CHANGES"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("REVIEW_COMMENT_REQUIRED"));

		mockMvc.perform(post("/admin/github/pulls/review")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"repository", "Outro/repo", "number", 7, "decision", "APPROVE"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNKNOWN_REPOSITORY"));

		mockMvc.perform(post("/admin/github/pulls/review")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"repository", repo, "number", 7, "decision", "APPROVE"))))
			.andExpect(status().isUnauthorized());

		var naoModerador = new TestAuthHelper(mockMvc, objectMapper).authenticate("pull_nao_moderador");
		mockMvc.perform(get("/admin/github/pulls").header("X-Session-Token", naoModerador.token()))
			.andExpect(status().isForbidden());
	}
}