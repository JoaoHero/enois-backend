package com.socialcoisa.rede.games.adapters.in.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.games.application.GameCatalogService;
import com.socialcoisa.rede.games.application.GamePageView;
import com.socialcoisa.rede.games.application.GameSearchCriteria;
import com.socialcoisa.rede.games.application.GameService;
import com.socialcoisa.rede.games.application.GameView;
import com.socialcoisa.rede.games.application.TrendingGameView;
import com.socialcoisa.rede.games.domain.Game;
import com.socialcoisa.rede.shared.web.AdminGuard;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Agregador de jogos: o criador envia o LINK, a moderação revisa e o jogo roda EMBUTIDO
 * (sandbox + tela cheia). O Énóis não hospeda.
 */
@RestController
class GameController {

	private final GameService games;
	private final GameCatalogService catalog;
	private final CurrentUserResolver currentUserResolver;
	private final AdminGuard adminGuard;
	private final com.socialcoisa.rede.profile.application.port.out.UserProfileRepository profiles;
	private final com.socialcoisa.rede.games.application.GameIdentityService identities;
	private final com.socialcoisa.rede.games.application.GameLibraryService library;

	GameController(GameService games, GameCatalogService catalog, CurrentUserResolver currentUserResolver,
			AdminGuard adminGuard,
			com.socialcoisa.rede.profile.application.port.out.UserProfileRepository profiles,
			com.socialcoisa.rede.games.application.GameIdentityService identities,
			com.socialcoisa.rede.games.application.GameLibraryService library) {
		this.games = games;
		this.catalog = catalog;
		this.currentUserResolver = currentUserResolver;
		this.adminGuard = adminGuard;
		this.profiles = profiles;
		this.identities = identities;
		this.library = library;
	}

	// --- leitura pública ---

	/**
	 * Catálogo público com busca e filtros. `order` é lista fechada (`recent`, `plays`, `title`) e o
	 * cursor é o deslocamento da próxima página — a resposta diz quando acabou.
	 */
	@GetMapping("/games")
	GamePageView catalog(@RequestParam(required = false) String query, @RequestParam(required = false) String genre,
			@RequestParam(required = false) String tag, @RequestParam(required = false) String stage,
			@RequestParam(required = false) String distribution, @RequestParam(required = false) String order,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		var criteria = GameSearchCriteria.of(query, genre, tag, GameSearchCriteria.stageOrNull(stage),
			GameSearchCriteria.distributionOrNull(distribution), order);
		var page = catalog.search(criteria, limit, cursor);
		// Com sessão o catálogo marca o que é MEU (`owned`): é o que faz o card na loja oferecer
		// "Editar" para quem publicou — sem isso, o dono só editava se achasse o painel.
		String viewerId = viewerIdOrNull(sessionToken);
		return new GamePageView(page.items().stream().map(game -> view(game, viewerId)).toList(),
			page.nextCursor());
	}

	/**
	 * "Jogados hoje": as últimas 24 horas, do mais jogado para o menos.
	 *
	 * Público e sem sessão — o card abre no feed de quem acabou de chegar. O endereço literal vem
	 * antes de `/games/{username}` na resolução do Spring, igual a `/games/colecoes`.
	 */
	@GetMapping("/games/hoje")
	List<TrendingGameView> trendingToday(@RequestParam(required = false, defaultValue = "6") int limit) {
		var ranked = catalog.trendingToday(limit);
		Map<String, String> names = new java.util.HashMap<>();
		profiles.findAllByIds(ranked.stream().map(item -> item.game().userId()).distinct().toList())
			.forEach(p -> names.put(p.id(), p.username()));
		return ranked.stream()
			.map(item -> TrendingGameView.of(item, names.get(item.game().userId())))
			.toList();
	}

	@GetMapping("/games/{username}")
	List<GameView> byUser(@PathVariable String username,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		return withUsernames(games.publishedByUser(username), viewerIdOrNull(sessionToken));
	}

	@GetMapping("/games/{username}/{gameSlug}")
	GameView bySlug(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String username, @PathVariable String gameSlug) {
		// Com sessão, o dono abre o próprio jogo em qualquer estado (rascunho, recusado, suspenso).
		String viewerId = viewerIdOrNull(sessionToken);
		return viewWithFavorites(games.byUserAndSlug(username, gameSlug, viewerId), viewerId);
	}

	/** A view de um jogo, com o @ do criador resolvido (o endereço público usa ele). */
	private GameView view(Game game, String viewerId) {
		String username = profiles.findById(game.userId()).map(p -> p.username()).orElse(null);
		return GameView.of(game, username, 0, false, viewerId);
	}

	/**
	 * A view do DETALHE: é a única tela que mostra favoritos, então só aqui vale a consulta extra.
	 * Sem isto o coração da página abria sempre vazio, mesmo com o jogo já favoritado.
	 */
	private GameView viewWithFavorites(Game game, String viewerId) {
		String username = profiles.findById(game.userId()).map(p -> p.username()).orElse(null);
		int favorites = (int) ((Number) catalog.stats(game.id()).getOrDefault("favorites", 0)).longValue();
		boolean favorited = viewerId != null && library.has(viewerId, game.id());
		return GameView.of(game, username, favorites, favorited, viewerId);
	}

	private String viewerIdOrNull(String sessionToken) {
		try {
			return currentUserResolver.resolve(sessionToken).id().value();
		}
		catch (Exception ignored) {
			return null;
		}
	}

	/** O endereço público do jogo é /games/{username}/{slug} — o username sai do perfil. */
	private List<GameView> withUsernames(List<Game> list, String viewerId) {
		Map<String, String> names = new java.util.HashMap<>();
		profiles.findAllByIds(list.stream().map(Game::userId).distinct().toList())
			.forEach(p -> names.put(p.id(), p.username()));
		return list.stream()
			.map(g -> GameView.of(g, names.get(g.userId()), 0, false, viewerId))
			.toList();
	}

	// --- criador ---

	@GetMapping("/me/games")
	List<GameView> myGames(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		// `owned = true` em todos: é a lista dos MEUS jogos (o front usa isso para o "Editar").
		return withUsernames(games.ownedBy(user.id().value()), user.id().value());
	}

	@PostMapping("/games")
	Map<String, Object> submit(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody SubmitGameRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		Game created = games.submit(user.id().value(), request.slug(), request.title(), request.description(),
			request.cover(), request.url(), request.tags());
		return Map.of("id", created.id(), "slug", created.slug(), "status", created.status().name());
	}

	/**
	 * IDENTIDADE DO JOGO: emite o token opaco (15 min, uso único) + o pseudônimo estável.
	 * A sessão do Énóis fica AQUI — o jogo recebe só o token. Sessão obrigatória.
	 */
	@PostMapping("/games/{gameId}/identity")
	Map<String, Object> identity(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		var issued = identities.mint(user.id().value(), gameId);
		return Map.of("token", issued.token(), "expiresAt", issued.expiresAt().toString(),
			"playerRef", identities.playerRef(user.id().value(), gameId));
	}

	/** O SERVIDOR DO JOGO troca o token pela identidade pseudônima (uso único). Público. */
	@PostMapping("/games/tokens/validate")
	Map<String, Object> validateToken(@RequestBody ValidateTokenRequest request) {
		var identity = identities.validate(request.token());
		return Map.of("gameId", identity.gameId(), "playerRef", identity.playerRef());
	}

	/**
	 * GASTO DE FAÍSCA DENTRO DO JOGO: o jogo PEDE (postMessage), a pessoa CONFIRMA na
	 * plataforma e ESTE endpoint (sessão do jogador) debita. O jogo nunca autentica.
	 */
	@PostMapping("/games/{gameId}/despesas")
	Map<String, Object> spend(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId, @RequestBody SpendRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		games.spendInGame(user.id().value(), gameId, request.amount(), request.motivo(), request.requestId());
		return Map.of("status", "DEBITED", "requestId", request.requestId());
	}

	record ValidateTokenRequest(String token) {
	}

	record SpendRequest(Long amount, String motivo, String requestId) {
	}

	/** As coleções curadas (G3) com os jogos resolvidos. */
	@GetMapping("/games/colecoes")
	List<com.socialcoisa.rede.games.application.GameService.ColecaoView> colecoes(
			@RequestParam(defaultValue = "20") int limit) {
		return games.collections(limit);
	}

	/** Cria uma coleção curada (v1: administração). */
	@PostMapping("/games/colecoes")
	Map<String, Object> createCollection(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody CreateCollectionRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		adminGuard.requireAdmin(user);
		var created = games.createCollection(user.id().value(), request.title(), request.description(),
			request.gameIds());
		return Map.of("id", created.id(), "status", created.status().name());
	}

	@PostMapping("/games/colecoes/{collectionId}/esconder")
	Map<String, Object> hideCollection(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String collectionId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		adminGuard.requireAdmin(user);
		var hidden = games.hideCollection(collectionId, user.id().value());
		return Map.of("id", hidden.id(), "status", hidden.status().name());
	}

	record CreateCollectionRequest(String title, String description, List<String> gameIds) {
	}

	/**
	 * G-share: publica a conquista no feed com a tag do jogo. O texto/mídia vem do JOGO
	 * (postMessage) e o APP confirma com o jogador antes de chamar — sessão nunca vaza
	 * para o iframe. Teto de 12/dia por pessoa em cada jogo.
	 */
	@PostMapping("/games/{gameId}/compartilhar")
	Map<String, Object> compartilhar(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId, @RequestBody ShareRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		var post = games.compartilhar(user.id().value(), gameId, request.texto(), request.mediaIds());
		return Map.of("postId", post.id(), "status", "SHARED");
	}

	record ShareRequest(String texto, List<String> mediaIds) {
	}

	/** Destaque pago (G2): o criador impulsiona o próprio jogo por 7 dias (5 faíscas). */
	@PostMapping("/games/{gameId}/boost")
	Map<String, Object> boost(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		var boosted = games.boost(gameId, user.id().value());
		return Map.of("id", boosted.id(), "boosted", boosted.boosted(java.time.Instant.now()));
	}

	@PostMapping("/games/{gameId}/resubmit")
	Map<String, Object> resubmit(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		Game game = games.resubmit(gameId, user.id().value());
		return Map.of("id", game.id(), "status", game.status().name());
	}

	// --- moderação ---

	@GetMapping("/admin/games")
	List<GameView> pending(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam(defaultValue = "30") int limit) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		adminGuard.requireAdmin(user);
		// Fila de revisão: quem decide não é o dono, então `owned` fica de fora de propósito.
		return withUsernames(games.pending(limit), null);
	}

	@PostMapping("/admin/games/{gameId}/approve")
	Map<String, Object> approve(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId, @RequestBody(required = false) ReviewRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		adminGuard.requireAdmin(user);
		Game approved = games.approve(gameId, user.id().value(), request == null ? null : request.note());
		return Map.of("id", approved.id(), "status", approved.status().name());
	}

	@PostMapping("/admin/games/{gameId}/reject")
	Map<String, Object> reject(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String gameId, @RequestBody(required = false) ReviewRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		adminGuard.requireAdmin(user);
		Game rejected = games.reject(gameId, user.id().value(), request == null ? null : request.note());
		return Map.of("id", rejected.id(), "status", rejected.status().name());
	}

	record SubmitGameRequest(String slug, String title, String description, String cover, String url, String tags) {
	}

	record ReviewRequest(String note) {
	}

}
