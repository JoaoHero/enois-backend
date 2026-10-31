package com.socialcoisa.rede.media.adapters.in.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.media.application.CreateMediaUploadCommand;
import com.socialcoisa.rede.media.application.RequestMediaDeletionCommand;
import com.socialcoisa.rede.media.application.port.in.CreateMediaUploadUseCase;
import com.socialcoisa.rede.media.application.port.in.GetMediaAssetUseCase;
import com.socialcoisa.rede.media.application.port.in.RequestMediaDeletionUseCase;
import com.socialcoisa.rede.idempotency.application.CommandResponse;
import com.socialcoisa.rede.idempotency.application.IdempotencyApplicationService;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

@RestController
@RequestMapping("/media")
class MediaContractController {

	private final CreateMediaUploadUseCase createMediaUploadUseCase;
	private final GetMediaAssetUseCase getMediaAssetUseCase;
	private final RequestMediaDeletionUseCase requestMediaDeletionUseCase;
	private final IdempotencyApplicationService idempotency;
	private final CurrentUserResolver currentUserResolver;

	MediaContractController(CreateMediaUploadUseCase createMediaUploadUseCase, GetMediaAssetUseCase getMediaAssetUseCase,
			RequestMediaDeletionUseCase requestMediaDeletionUseCase, IdempotencyApplicationService idempotency,
			CurrentUserResolver currentUserResolver) {
		this.createMediaUploadUseCase = createMediaUploadUseCase;
		this.getMediaAssetUseCase = getMediaAssetUseCase;
		this.requestMediaDeletionUseCase = requestMediaDeletionUseCase;
		this.idempotency = idempotency;
		this.currentUserResolver = currentUserResolver;
	}

	@PostMapping("/uploads")
	ResponseEntity<String> upload(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody UploadRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		String hash = idempotency.hash(request);
		return idempotency.execute(user.id(), "POST", "/api/media/uploads", idempotencyKey, hash,
			() -> new CommandResponse(201, createMediaUploadUseCase.createUpload(new CreateMediaUploadCommand(user.id(),
				request.fileName(), request.mimeType(), request.size(), request.purpose()))));
	}

	/**
	 * Leitura do asset por id. Publica de proposito: o id so aparece em post/perfil que qualquer
	 * pessoa ja pode ler, e a resposta e o mesmo dado que a publicacao expoe (nome, tipo, tamanho e
	 * a URL do CDN, que e aberta). Exigir sessao aqui deixava sem imagem o post visto por quem nao
	 * entrou — e tambem o proprio autor, quando a sessao dele estava sendo renovada.
	 *
	 * Upload e exclusao continuam exigindo sessao (o dono), logo abaixo.
	 */
	@GetMapping("/{mediaId}")
	Object media(@PathVariable String mediaId) {
		return getMediaAssetUseCase.getMedia(mediaId);
	}

	@DeleteMapping("/{mediaId}")
	ResponseEntity<String> delete(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@PathVariable String mediaId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		String hash = idempotency.hash(Map.of("mediaId", mediaId));
		return idempotency.execute(user.id(), "DELETE", "/api/media/{mediaId}", idempotencyKey, hash,
			() -> new CommandResponse(200, requestMediaDeletionUseCase.requestDeletion(new RequestMediaDeletionCommand(
				user.id(), mediaId))));
	}

	record UploadRequest(String fileName, String mimeType, long size, String purpose) {
	}
}
