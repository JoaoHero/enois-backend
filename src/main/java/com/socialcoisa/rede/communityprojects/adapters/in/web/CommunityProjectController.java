package com.socialcoisa.rede.communityprojects.adapters.in.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.moderation.application.port.out.ModeratorApplicationRepository;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.communityprojects.application.CommunityProjectPageView;
import com.socialcoisa.rede.communityprojects.application.CommunityProjectService;
import com.socialcoisa.rede.communityprojects.application.CommunityProjectView;
import com.socialcoisa.rede.communityprojects.domain.CommunityProject;
import com.socialcoisa.rede.shared.web.AdminGuard;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Projetos da comunidade: catálogo público, página do criador, envio/edição e revisão.
 * Separado dos jogos de propósito — outra tabela, outra página (/projetos).
 */
@RestController
class CommunityProjectController {

	private final CommunityProjectService projects;
	private final CurrentUserResolver currentUserResolver;
	private final AdminGuard adminGuard;
	private final ModeratorApplicationRepository moderators;
	private final UserProfileRepository profiles;

	CommunityProjectController(CommunityProjectService projects, CurrentUserResolver currentUserResolver, AdminGuard adminGuard,
			ModeratorApplicationRepository moderators, UserProfileRepository profiles) {
		this.projects = projects;
		this.currentUserResolver = currentUserResolver;
		this.adminGuard = adminGuard;
		this.moderators = moderators;
		this.profiles = profiles;
	}

	@GetMapping("/community-projects")
	CommunityProjectPageView catalog(@RequestParam(required = false) String query,
			@RequestParam(required = false) String order,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) String cursor,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		int size = limit == null ? 24 : Math.min(48, Math.max(1, limit));
		int offset = parseCursor(cursor);
		var page = projects.search(query, order, size, offset);
		String viewerId = viewerIdOrNull(sessionToken);
		List<CommunityProjectView> items = views(page.items(), viewerId);
		String next = page.hasMore() ? Integer.toString(offset + size) : null;
		return new CommunityProjectPageView(items, next);
	}

	@GetMapping("/community-projects/{username}")
	List<CommunityProjectView> byUser(@PathVariable String username,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		return views(projects.publishedByUser(username), viewerIdOrNull(sessionToken));
	}

	@GetMapping("/community-projects/{username}/{projectSlug}")
	CommunityProjectView bySlug(@PathVariable String username, @PathVariable String projectSlug,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		String viewerId = viewerIdOrNull(sessionToken);
		CommunityProject project = projects.byUserAndSlug(username, projectSlug, viewerId);
		return CommunityProjectView.of(project, projects.usernameOf(project.userId()), viewerId);
	}

	@GetMapping("/me/community-projects")
	List<CommunityProjectView> mine(@RequestHeader("X-Session-Token") String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return views(projects.ownedBy(user.id().value()), user.id().value());
	}

	@PostMapping("/community-projects")
	CommunityProjectView submit(@RequestHeader("X-Session-Token") String sessionToken,
			@RequestBody SubmitProjectRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		CommunityProject project = projects.submit(user.id().value(), request.slug(), request.title(),
			request.description(), request.shortDescription(), request.cover(), request.url(), request.repoUrl(),
			request.tags(), request.category());
		return CommunityProjectView.of(project, projects.usernameOf(user.id().value()), user.id().value());
	}

	@PatchMapping("/community-projects/{projectId}")
	CommunityProjectView edit(@RequestHeader("X-Session-Token") String sessionToken,
			@PathVariable String projectId, @RequestBody SubmitProjectRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		CommunityProject project = projects.edit(projectId, user.id().value(), request.title(), request.description(),
			request.shortDescription(), request.cover(), request.url(), request.repoUrl(), request.tags(),
			request.category(), request.slug());
		return CommunityProjectView.of(project, projects.usernameOf(user.id().value()), user.id().value());
	}

	@DeleteMapping("/community-projects/{projectId}")
	Map<String, Object> remove(@RequestHeader("X-Session-Token") String sessionToken, @PathVariable String projectId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		projects.remove(projectId, user.id().value());
		return Map.of("id", projectId, "status", "DELETED");
	}

	@PostMapping("/community-projects/{projectId}/resubmit")
	Map<String, Object> resubmit(@RequestHeader("X-Session-Token") String sessionToken, @PathVariable String projectId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		CommunityProject project = projects.resubmit(projectId, user.id().value());
		return Map.of("id", project.id(), "status", project.status().name());
	}

	// --- moderação ---

	@GetMapping("/moderation/community-projects")
	List<CommunityProjectView> pending(@RequestHeader("X-Session-Token") String sessionToken,
			@RequestParam(defaultValue = "30") int limit) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		requireModerator(user);
		return views(projects.pending(limit), null);
	}

	@PostMapping("/moderation/community-projects/{projectId}/approve")
	Map<String, Object> approve(@RequestHeader("X-Session-Token") String sessionToken, @PathVariable String projectId,
			@RequestBody(required = false) ReviewRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		requireModerator(user);
		CommunityProject project = projects.approve(projectId, user.id().value(), request == null ? null : request.note());
		return Map.of("id", project.id(), "status", project.status().name());
	}

	@PostMapping("/moderation/community-projects/{projectId}/reject")
	Map<String, Object> reject(@RequestHeader("X-Session-Token") String sessionToken, @PathVariable String projectId,
			@RequestBody ReviewRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		requireModerator(user);
		CommunityProject project = projects.reject(projectId, user.id().value(), request == null ? null : request.note());
		return Map.of("id", project.id(), "status", project.status().name());
	}

	@PostMapping("/admin/community-projects/{projectId}/suspend")
	Map<String, Object> suspend(@RequestHeader("X-Session-Token") String sessionToken, @PathVariable String projectId,
			@RequestBody(required = false) ReviewRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		adminGuard.requireAdmin(user);
		CommunityProject project = projects.suspend(projectId, user.id().value(), request == null ? null : request.note());
		return Map.of("id", project.id(), "status", project.status().name());
	}

	@PostMapping("/admin/community-projects/{projectId}/unsuspend")
	Map<String, Object> unsuspend(@RequestHeader("X-Session-Token") String sessionToken, @PathVariable String projectId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		adminGuard.requireAdmin(user);
		CommunityProject project = projects.unsuspend(projectId, user.id().value());
		return Map.of("id", project.id(), "status", project.status().name());
	}

	private void requireModerator(CurrentUser user) {
		if (adminGuard.isAdmin(user) || moderators.findApprovedByUser(user.id().value()).isPresent()) {
			return;
		}
		throw new ApiException(403, "FORBIDDEN", "Acesso restrito à moderação.");
	}

	private String viewerIdOrNull(String sessionToken) {
		try {
			return currentUserResolver.resolve(sessionToken).id().value();
		}
		catch (Exception ignored) {
			return null;
		}
	}

	private List<CommunityProjectView> views(List<CommunityProject> list, String viewerId) {
		Map<String, String> names = new HashMap<>();
		profiles.findAllByIds(list.stream().map(CommunityProject::userId).distinct().toList())
			.forEach(profile -> names.put(profile.id(), profile.username()));
		return list.stream()
			.map(project -> CommunityProjectView.of(project, names.get(project.userId()), viewerId))
			.toList();
	}

	private static int parseCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return 0;
		}
		try {
			return Math.max(0, Integer.parseInt(cursor.trim()));
		}
		catch (NumberFormatException invalid) {
			return 0;
		}
	}

	record SubmitProjectRequest(String slug, String title, String description, String shortDescription,
			String cover, String url, String repoUrl, String tags, String category) {
	}

	record ReviewRequest(String note) {
	}
}
