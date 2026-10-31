package com.socialcoisa.rede.feed.adapters.in.web;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.feed.application.SealPeopleView;
import com.socialcoisa.rede.feed.application.SealService;
import com.socialcoisa.rede.feed.application.SealsView;
import com.socialcoisa.rede.feed.domain.PostSealType;
import com.socialcoisa.rede.shared.blocks.BlockedUsersReader;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Selos de post: a marcacao no topo do post ("importante", "dica", "post duplicado"...).
 *
 * A LEITURA e publica (o selo e informacao do post, como a contagem de reacoes; a sessao, quando
 * existe, so acrescenta quais selos sao de quem esta lendo). A ESCRITA exige sessao, e cada selo e
 * independente: clicar no mesmo remove, clicar em outro acrescenta — a pessoa pode ter varios.
 */
@RestController
@RequestMapping("/feed/posts")
class PostSealContractController {

	/** Teto de ids por consulta: a tela pede uma pagina; acima disso e abuso, nao uso. */
	private static final int MAX_IDS = 100;

	private final SealService sealService;
	private final CurrentUserResolver currentUserResolver;
	private final BlockedUsersReader blockedUsersReader;

	PostSealContractController(SealService sealService, CurrentUserResolver currentUserResolver,
			BlockedUsersReader blockedUsersReader) {
		this.sealService = sealService;
		this.currentUserResolver = currentUserResolver;
		this.blockedUsersReader = blockedUsersReader;
	}

	/** Selos de varios posts de uma vez: `?ids=post_1,post_2` (a pagina inteira numa consulta). */
	@GetMapping("/seals")
	Map<String, SealsView> seals(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam(value = "ids", required = false) String ids) {
		CurrentUser user = resolveSafely(sessionToken);
		List<String> postIds = ids == null ? List.of()
			: Arrays.stream(ids.split(",")).map(String::trim).filter(value -> !value.isEmpty()).distinct()
				.limit(MAX_IDS).toList();
		Map<String, SealsView> found = sealService.sealsFor(postIds, user == null ? null : user.id().value());
		// Garante a chave de todo id pedido (o front nao precisa tratar ausencia).
		Map<String, SealsView> complete = new LinkedHashMap<>();
		for (String postId : postIds) {
			complete.put(postId, found.getOrDefault(postId, SealsView.empty()));
		}
		return complete;
	}

	/**
	 * Quem marcou o post e com o que — a tela abre isto ao clicar num selo, como faz com as reacoes.
	 * Leitura publica; o visitante (ou quem tem sessao) so deixa de ver quem bloqueou.
	 */
	@GetMapping("/{postId}/seals/people")
	SealPeopleView people(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String postId) {
		CurrentUser user = resolveSafely(sessionToken);
		Set<String> hidden = blockedUsersReader.hiddenIdsFor(user == null ? null : user.id().value());
		return sealService.people(postId, hidden);
	}

	@PostMapping("/{postId}/seals")
	SealsView add(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String postId, @RequestBody SealRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return sealService.toggle(user.id().value(), postId, request.seal());
	}

	/** Remover: tira TODOS os selos que a pessoa tiver no post (o seletor tem "tirar meus selos"). */
	@DeleteMapping("/{postId}/seals")
	SealsView remove(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String postId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return sealService.remove(user.id().value(), postId);
	}

	/** Sessao opcional: sem sessao (ou com sessao invalida) a leitura publica continua. */
	private CurrentUser resolveSafely(String sessionToken) {
		try {
			return currentUserResolver.resolve(sessionToken);
		}
		catch (Exception ignored) {
			return null;
		}
	}

	record SealRequest(PostSealType seal) {
	}
}
