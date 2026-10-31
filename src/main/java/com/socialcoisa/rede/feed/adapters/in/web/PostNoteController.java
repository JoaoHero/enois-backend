package com.socialcoisa.rede.feed.adapters.in.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.feed.application.MyPostNoteView;
import com.socialcoisa.rede.feed.application.PostNoteService;
import com.socialcoisa.rede.feed.application.PostNoteView;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Nota de rodapé da comunidade. A leitura é pública (o visitante vê as notas aprovadas); o envio
 * exige sessão e vai para a moderação.
 */
@RestController
class PostNoteController {

	private final PostNoteService notes;
	private final CurrentUserResolver currentUserResolver;

	PostNoteController(PostNoteService notes, CurrentUserResolver currentUserResolver) {
		this.notes = notes;
		this.currentUserResolver = currentUserResolver;
	}

	@PostMapping("/feed/posts/{postId}/notes")
	ResponseEntity<MyPostNoteView> create(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String postId, @RequestBody CreateNoteRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		var note = notes.create(postId, user.id().value(), request.body());
		return ResponseEntity.status(HttpStatus.CREATED).body(new MyPostNoteView(note.id(), note.postId(),
			note.body(), note.status().name(), note.reviewReason(), note.createdAt()));
	}

	@GetMapping("/feed/posts/{postId}/notes")
	NoteListView list(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String postId) {
		String viewerId = viewerId(sessionToken);
		List<PostNoteView> items = notes.viewsByPost(List.of(postId), java.util.Set.of())
			.getOrDefault(postId, List.of());
		MyPostNoteView mine = viewerId == null ? null
			: notes.mineByPost(viewerId, List.of(postId)).get(postId);
		return new NoteListView(items, mine);
	}

	/** Sessão opcional: sem ela a leitura continua funcionando (notas aprovadas são públicas). */
	private String viewerId(String sessionToken) {
		try {
			return currentUserResolver.resolve(sessionToken).id().value();
		}
		catch (RuntimeException exception) {
			return null;
		}
	}

	record CreateNoteRequest(String body) {
	}

	record NoteListView(List<PostNoteView> items, MyPostNoteView mine) {
	}
}