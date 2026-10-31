package com.socialcoisa.rede.games.adapters.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.games.application.GameSocialService;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/** Avaliações e comentários de um jogo: leitura pública, escrita com sessão. */
@RestController
@RequestMapping("/games/{gameId}")
class GameSocialController {

	private final GameSocialService social;
	private final CurrentUserResolver currentUserResolver;

	GameSocialController(GameSocialService social, CurrentUserResolver currentUserResolver) {
		this.social = social;
		this.currentUserResolver = currentUserResolver;
	}

	@GetMapping("/reviews")
	GameSocialService.ReviewsView reviews(@PathVariable String gameId,
			@RequestParam(defaultValue = "30") int limit) {
		return social.reviews(gameId, limit);
	}

	@PostMapping("/reviews")
	GameSocialService.ReviewView review(@PathVariable String gameId,
			@RequestHeader("X-Session-Token") String sessionToken,
			@RequestBody ReviewRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return social.review(gameId, user.id().value(), request.rating(), request.body());
	}

	@DeleteMapping("/reviews/mine")
	ResponseEntity<Void> deleteReview(@PathVariable String gameId,
			@RequestHeader("X-Session-Token") String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		social.deleteReview(gameId, user.id().value());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/comments")
	GameSocialService.CommentsView comments(@PathVariable String gameId,
			@RequestParam(defaultValue = "50") int limit) {
		return social.comments(gameId, limit);
	}

	@PostMapping("/comments")
	GameSocialService.CommentView comment(@PathVariable String gameId,
			@RequestHeader("X-Session-Token") String sessionToken,
			@RequestBody CommentRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return social.comment(gameId, user.id().value(), request.body());
	}

	@DeleteMapping("/comments/{commentId}")
	ResponseEntity<Void> deleteComment(@PathVariable String gameId, @PathVariable String commentId,
			@RequestHeader("X-Session-Token") String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		social.deleteComment(gameId, commentId, user.id().value());
		return ResponseEntity.noContent().build();
	}

	record ReviewRequest(int rating, String body) {
	}

	record CommentRequest(String body) {
	}
}
