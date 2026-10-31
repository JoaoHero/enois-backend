package com.socialcoisa.rede.contributions.adapters.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.contributions.application.PullRequestReviewService;
import com.socialcoisa.rede.contributions.application.PullRequestReviewService.OpenPullRequestView;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;
import com.socialcoisa.rede.shared.web.ModeratorGuard;
import com.socialcoisa.rede.shared.web.StatusResponse;

/** PRs abertos dos repositórios oficiais: aprovar ou pedir mudanças pelo painel. */
@RestController
class AdminPullRequestController {

	private final PullRequestReviewService service;
	private final ModeratorGuard moderatorGuard;
	private final CurrentUserResolver currentUserResolver;

	AdminPullRequestController(PullRequestReviewService service, ModeratorGuard moderatorGuard,
			CurrentUserResolver currentUserResolver) {
		this.service = service;
		this.moderatorGuard = moderatorGuard;
		this.currentUserResolver = currentUserResolver;
	}

	@GetMapping("/admin/github/pulls")
	List<OpenPullRequestView> open(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam(defaultValue = "100") int limit) {
		moderatorGuard.requireModerator(currentUserResolver.resolve(sessionToken));
		return service.open(limit);
	}

	@PostMapping("/admin/github/pulls/review")
	StatusResponse review(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody ReviewRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		moderatorGuard.requireModerator(user);
		service.review(user.id().value(), request.repository(), request.number(), request.decision(),
			request.comment());
		return new StatusResponse(request.repository() + "#" + request.number(), "REVIEWED");
	}

	record ReviewRequest(String repository, long number, String decision, String comment) {
	}
}