package com.socialcoisa.rede.gifs.adapters.in.web;

import java.util.Locale;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.gifs.application.GifSearchResult;
import com.socialcoisa.rede.gifs.application.GifSearchService;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Busca de GIF do seletor de comentario.
 *
 * Exige sessao de proposito: e um proxy para provedor externo com cota, e comentar ja exige conta —
 * sem isso qualquer anonimo gastaria a cota do provedor. O termo vai normalizado (trim + minusculas)
 * porque a chave do cache e o proprio termo.
 */
@RestController
@RequestMapping("/gifs")
class GifController {

	private static final int MAX_LIMIT = 50;

	private final GifSearchService gifSearchService;
	private final CurrentUserResolver currentUserResolver;

	GifController(GifSearchService gifSearchService, CurrentUserResolver currentUserResolver) {
		this.gifSearchService = gifSearchService;
		this.currentUserResolver = currentUserResolver;
	}

	@GetMapping("/search")
	GifSearchResult search(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam(value = "q", required = false) String query,
			@RequestParam(value = "limit", defaultValue = "24") int limit) {
		currentUserResolver.resolve(sessionToken);
		String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		return gifSearchService.search(normalized, Math.clamp(limit, 1, MAX_LIMIT));
	}
}
