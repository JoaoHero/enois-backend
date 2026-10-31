package com.socialcoisa.rede.search.adapters.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.search.application.SearchService;
import com.socialcoisa.rede.search.application.SearchService.SearchResult;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;
import com.socialcoisa.rede.shared.web.PageResponse;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

@RestController
@RequestMapping("/search")
class SearchContractController {

	private final SearchService searchService;
	private final CurrentUserResolver currentUserResolver;

	SearchContractController(SearchService searchService, CurrentUserResolver currentUserResolver) {
		this.searchService = searchService;
		this.currentUserResolver = currentUserResolver;
	}

	@GetMapping
	@RateLimiter(name = "search")
	PageResponse<SearchResultView> search(@RequestParam(required = false) String q,
			@RequestParam(required = false) List<String> types, @RequestParam(defaultValue = "10") int limit,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		List<SearchResult> results = searchService.search(q, types, limit, viewerId(sessionToken));
		var items = results.stream()
			.map(r -> new SearchResultView(r.id(), r.type(), r.title(), r.subtitle(), r.image(), r.href()))
			.toList();
		return new PageResponse<>(items, null);
	}

	/** Sessao opcional: a busca e publica, mas quem esta logado nao ve usuario bloqueado. */
	private String viewerId(String sessionToken) {
		try {
			return currentUserResolver.resolve(sessionToken).id().value();
		}
		catch (RuntimeException exception) {
			return null;
		}
	}

	record SearchResultView(String id, String type, String title, String subtitle, String image, String href) {
	}

}