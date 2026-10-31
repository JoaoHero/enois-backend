package com.socialcoisa.rede.games.adapters.in.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.games.application.DeveloperPageView;
import com.socialcoisa.rede.games.application.DeveloperProfileService;
import com.socialcoisa.rede.games.application.GameCatalogService;
import com.socialcoisa.rede.games.application.GameLibraryService;
import com.socialcoisa.rede.games.application.GameUpdateView;
import com.socialcoisa.rede.games.application.GameView;
import com.socialcoisa.rede.games.domain.GameAspect;
import com.socialcoisa.rede.games.domain.GameDetails;
import com.socialcoisa.rede.games.domain.GameDistribution;
import com.socialcoisa.rede.games.domain.GameStage;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.shared.web.AdminGuard;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * A plataforma em si: a página do criador, a biblioteca de quem joga, o devlog e as ações do dono.
 *
 * A leitura é pública (jogo e criador são páginas que a pessoa compartilha); escrever exige sessão.
 * O envio de jogo e a moderação continuam no `GameController` — este controller é o que a V75
 * acrescentou.
 */
@RestController
class GamePlatformController {

	private final DeveloperProfileService developers;
	private final GameLibraryService library;
	private final GameCatalogService catalog;
	private final com.socialcoisa.rede.games.application.GameService games;
	private final CurrentUserResolver currentUserResolver;
	private final UserProfileRepository profiles;
	private final AdminGuard adminGuard;

	GamePlatformController(DeveloperProfileService developers, GameLibraryService library, GameCatalogService catalog,
			com.socialcoisa.rede.games.application.GameService games, CurrentUserResolver currentUserResolver,
			UserProfileRepository profiles, AdminGuard adminGuard) {
		this.developers = developers;
		this.library = library;
		this.catalog = catalog;
		this.games = games;
		this.currentUserResolver = currentUserResolver;
		this.profiles = profiles;
		this.adminGuard = adminGuard;
	}

	// --- criador ---

	/** A página pública do criador. `/games/{username}/profile` é literal e vence o `{gameSlug}`. */
	@GetMapping("/games/{username}/profile")
	DeveloperPageView developerPage(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String username) {
		String viewerId = viewerIdOrNull(sessionToken);
		var page = developers.page(username, viewerId);
		List<String> badges = profiles.findById(page.user().id()).map(p -> p.allBadges()).orElse(List.of());
		return DeveloperPageView.from(page, badges);
	}

	/**
	 * A própria página de criador, sem depender do @: o painel preenche o formulário por aqui
	 * (antes pedia `/games/me/profile`, um @ que não existe, e a tela abria vazia).
	 */
	@GetMapping("/games/profile/mine")
	DeveloperPageView myDeveloperPage(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		var page = developers.page(usernameOf(user.id().value()), user.id().value());
		List<String> badges = profiles.findById(user.id().value()).map(p -> p.allBadges()).orElse(List.of());
		return DeveloperPageView.from(page, badges);
	}

	/** O dono edita a própria página de criador (vazio limpa o campo). */
	@PutMapping("/games/profile")
	DeveloperPageView saveProfile(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody DeveloperProfileRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		developers.save(user.id().value(), request.displayName(), request.headline(), request.bio(),
			request.links(), request.avatar(), request.cover());
		var page = developers.page(usernameOf(user.id().value()), user.id().value());
		List<String> badges = profiles.findById(user.id().value()).map(p -> p.allBadges()).orElse(List.of());
		return DeveloperPageView.from(page, badges);
	}

	/** Acompanhar (mão única) é alternar: acompanhar de novo deixa de acompanhar. */
	@PostMapping("/games/{username}/follow")
	Map<String, Object> follow(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String username) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		String developerId = developers.page(username, null).user().id();
		boolean following = developers.toggleFollow(user.id().value(), developerId);
		return Map.of("following", following);
	}

	@DeleteMapping("/games/{username}/follow")
	Map<String, Object> unfollow(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String username) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		String developerId = developers.page(username, null).user().id();
		if (developers.toggleFollow(user.id().value(), developerId)) {
			// Estava seguindo: o toggle seguiu em vez de desfazer — desfaz.
			developers.toggleFollow(user.id().value(), developerId);
		}
		return Map.of("following", false);
	}

	// --- biblioteca ---

	/** A biblioteca de quem está logado (favoritos que continuam publicados). */
	@GetMapping("/games/library")
	List<GameView> library(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return library.library(user.id().value()).stream().map(game -> view(game, user.id().value())).toList();
	}

	@PostMapping("/games/{gameId}/favorite")
	Map<String, Object> favorite(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		boolean favorited = library.toggle(user.id().value(), gameId);
		return Map.of("favorited", favorited, "favorites", catalog.stats(gameId).get("favorites"));
	}

	@DeleteMapping("/games/{gameId}/favorite")
	Map<String, Object> unfavorite(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		if (library.has(user.id().value(), gameId)) {
			library.toggle(user.id().value(), gameId);
		}
		return Map.of("favorited", false, "favorites", catalog.stats(gameId).get("favorites"));
	}

	// --- jogo: jogar, números e diário ---

	/** Alguém começou a jogar: o servidor conta a sessão (é daqui que sai a "popularidade"). */
	@PostMapping("/games/{gameId}/play")
	Map<String, Object> play(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId) {
		String viewerId = viewerIdOrNull(sessionToken);
		int plays = catalog.startPlay(gameId, viewerId);
		return Map.of("plays", plays);
	}

	@GetMapping("/games/{gameId}/stats")
	Map<String, Object> stats(@PathVariable String gameId) {
		return catalog.stats(gameId);
	}

	/**
	 * O GRÁFICO DE USO (só o criador): totais + série diária de visualizações, partidas, jogadores
	 * distintos e compartilhamentos. `days` é a janela (1 a 90; padrão 30).
	 */
	@GetMapping("/games/{gameId}/analytics")
	GameCatalogService.GameAnalytics analytics(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId, @RequestParam(defaultValue = "30") int days) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return catalog.analytics(gameId, user.id().value(), days);
	}

	@GetMapping("/games/{gameId}/updates")
	List<GameUpdateView> updates(@PathVariable String gameId, @RequestParam(defaultValue = "20") int limit) {
		return games.updatesOf(gameId, Math.clamp(limit, 1, 50)).stream().map(GameUpdateView::from).toList();
	}

	/** O dono publica uma novidade; quem acompanha o criador é avisado. */
	@PostMapping("/games/{gameId}/updates")
	GameUpdateView publishUpdate(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId, @RequestBody GameUpdateRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return GameUpdateView.from(games.publishUpdate(gameId, user.id().value(), request.title(), request.body(),
			request.version()));
	}

	/**
	 * Edição pelo dono: a ficha inteira, o endereço público (slug) e a identidade visual (ícone e
	 * banner por foto ou HTML). O link passa pelo mesmo guarda do envio.
	 */
	@PatchMapping("/games/{gameId}")
	Map<String, Object> edit(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId, @RequestBody EditGameRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		var edited = games.edit(gameId, user.id().value(), request.title(), request.description(), request.cover(),
			request.url(), request.tags(), detailsOf(request), request.slug());
		return Map.of("id", edited.id(), "slug", edited.slug(), "status", edited.status().name());
	}

	/** O jogo sai da loja (só o dono): registro, novidades, favoritos e histórico vão junto. */
	@DeleteMapping("/games/{gameId}")
	Map<String, Object> remove(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		games.remove(gameId, user.id().value());
		return Map.of("id", gameId, "status", "DELETED");
	}

	// --- moderação ---

	@PostMapping("/admin/games/{gameId}/suspend")
	Map<String, Object> suspend(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId, @RequestBody(required = false) ReviewRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		adminGuard.requireAdmin(user);
		var suspended = games.suspend(gameId, user.id().value(), request == null ? null : request.note());
		return Map.of("id", suspended.id(), "status", suspended.status().name());
	}

	@PostMapping("/admin/games/{gameId}/unsuspend")
	Map<String, Object> unsuspend(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		adminGuard.requireAdmin(user);
		var restored = games.unsuspend(gameId, user.id().value());
		return Map.of("id", restored.id(), "status", restored.status().name());
	}

	// --- apoio ---

	private GameView view(com.socialcoisa.rede.games.domain.Game game, String viewerId) {
		String username = profiles.findById(game.userId()).map(p -> p.username()).orElse(null);
		int favorites = (int) ((Number) catalog.stats(game.id()).getOrDefault("favorites", 0)).longValue();
		boolean favorited = viewerId != null && library.has(viewerId, game.id());
		return GameView.of(game, username, favorites, favorited, viewerId);
	}

	private String usernameOf(String userId) {
		return profiles.findById(userId).map(p -> p.username())
			.orElseThrow(() -> new com.socialcoisa.rede.shared.web.ApiException(404, "PROFILE_NOT_FOUND",
				"Perfil não encontrado."));
	}

	/** Sessão opcional: sem sessão a leitura pública continua. */
	private String viewerIdOrNull(String sessionToken) {
		try {
			return currentUserResolver.resolve(sessionToken).id().value();
		}
		catch (Exception ignored) {
			return null;
		}
	}

	private GameDetails detailsOf(EditGameRequest request) {
		return GameDetails.of(request.shortDescription(), request.genres(), request.languages(), request.controls(),
			request.requirements(), request.contentWarnings(), parseStage(request.stage()),
			parseDistribution(request.distribution()), request.priceCoins() == null ? 0 : request.priceCoins(),
			parseAspect(request.aspect()), request.version(), com.socialcoisa.rede.shared.web.SafeUrl.requireHttpUrl(request.websiteUrl(), "site"),
			com.socialcoisa.rede.shared.web.SafeUrl.requireHttpUrl(request.supportUrl(), "suporte"),
			request.mediaIds() == null ? null : String.join(",", request.mediaIds()))
			.withBranding(request.icon(), request.banner(), request.bannerHtml());
	}

	private GameStage parseStage(String value) {
		try {
			return value == null ? null : GameStage.valueOf(value.toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			throw new com.socialcoisa.rede.shared.web.ApiException(400, "INVALID_STAGE", "Estágio inválido.");
		}
	}

	private GameDistribution parseDistribution(String value) {
		try {
			return value == null ? null : GameDistribution.valueOf(value.toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			throw new com.socialcoisa.rede.shared.web.ApiException(400, "INVALID_DISTRIBUTION",
				"Modelo de distribuição inválido.");
		}
	}

	private GameAspect parseAspect(String value) {
		try {
			return value == null ? null : GameAspect.valueOf(value.toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			throw new com.socialcoisa.rede.shared.web.ApiException(400, "INVALID_ASPECT", "Proporção inválida.");
		}
	}

	record DeveloperProfileRequest(String displayName, String headline, String bio, String links, String avatar,
			String cover) {
	}

	record GameUpdateRequest(String title, String body, String version) {
	}

	record ReviewRequest(String note) {
	}

	record EditGameRequest(String title, String description, String cover, String url, String tags,
			String shortDescription, String genres, String languages, String controls, String requirements,
			String contentWarnings, String stage, String distribution, Integer priceCoins, String aspect,
			String version, String websiteUrl, String supportUrl, List<String> mediaIds,
			/** Endereço público novo do jogo (vazio mantém o atual). */
			String slug,
			/** Identidade visual: foto de perfil do jogo e banner (foto ou HTML). */
			String icon, String banner, String bannerHtml) {
	}
}
