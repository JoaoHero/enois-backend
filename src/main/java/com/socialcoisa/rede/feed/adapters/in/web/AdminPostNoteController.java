package com.socialcoisa.rede.feed.adapters.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.feed.application.PostNoteService;
import com.socialcoisa.rede.feed.application.PostNoteService.AdminNoteView;
import com.socialcoisa.rede.feed.domain.PostNoteStatus;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;
import com.socialcoisa.rede.shared.web.ModeratorGuard;
import com.socialcoisa.rede.shared.web.StatusResponse;

/** Fila de notas de rodapé: aprovar (aparece no post) ou negar (pesa na credibilidade). */
@RestController
class AdminPostNoteController {

	private final PostNoteService notes;
	private final ModeratorGuard moderatorGuard;
	private final CurrentUserResolver currentUserResolver;

	AdminPostNoteController(PostNoteService notes, ModeratorGuard moderatorGuard,
			CurrentUserResolver currentUserResolver) {
		this.notes = notes;
		this.moderatorGuard = moderatorGuard;
		this.currentUserResolver = currentUserResolver;
	}

	@GetMapping("/admin/notes")
	List<AdminNoteView> pending(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam(defaultValue = "100") int limit) {
		moderatorGuard.requireModerator(currentUserResolver.resolve(sessionToken));
		return notes.adminViews(notes.pending(limit));
	}

	@PatchMapping("/admin/notes/{id}")
	StatusResponse review(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String id, @RequestBody ReviewNoteRequest request) {
		CurrentUser reviewer = currentUserResolver.resolve(sessionToken);
		moderatorGuard.requireModerator(reviewer);
		PostNoteStatus status;
		try {
			status = PostNoteStatus.valueOf(request.status() == null ? "" : request.status().trim().toUpperCase());
		}
		catch (IllegalArgumentException invalid) {
			throw new ApiException(400, "INVALID_NOTE_STATUS", "Use APPROVED ou DENIED.");
		}
		var saved = notes.review(id, reviewer.id().value(), status, request.reason());
		return new StatusResponse(saved.id(), saved.status().name());
	}

	record ReviewNoteRequest(String status, String reason) {
	}
}