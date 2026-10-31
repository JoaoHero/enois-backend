package com.socialcoisa.rede.feed.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.feed.domain.AuthorIdentity;
import com.socialcoisa.rede.feed.domain.Post;
import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.feed.domain.PostType;
import com.socialcoisa.rede.feed.domain.UserId;
import com.socialcoisa.rede.feed.application.port.out.PostRepository;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.shared.age.AgeGate;
import com.socialcoisa.rede.shared.security.ActionThrottle;
import com.socialcoisa.rede.shared.text.PlainText;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Republicar: cria uma cópia do post no feed de quem republicou, com o crédito ao autor
 * original (`repostOfAuthor`). A cópia é um post normal (tem os próprios contadores, pode ser
 * apagada pelo autor); `repostOf` guarda a origem para impedir duplicata e para a limpeza
 * quando o original é apagado.
 *
 * Não republicamos conteúdo que não pode circular fora do seu contexto: post de comunidade ou
 * canal privado e publicação corporativa; MOMENT é efêmero e não se repete.
 *
 * Artigo, página HTML e blog só circulam como citação (com opinião): o corpo deles mora fora do
 * post, então uma cópia sairia vazia. Por isso a citação é gravada como INSIGHT, senão herdaria
 * um tipo que promete um corpo que ela não tem.
 */
@Service
public class RepostPostService {

	private static final Set<PostType> NOT_REPOSTABLE = Set.of(PostType.BUSINESS, PostType.MOMENT);
	private static final Set<PostType> OPINION_ONLY = Set.of(PostType.ARTICLE, PostType.BLOG, PostType.PAGE);
	private static final int PER_HOUR = 30;

	private final PostRepository posts;
	private final UserProfileRepository profiles;
	private final ActionThrottle throttle;
	private final AgeGate ageGate;
	private final Clock clock;

	public RepostPostService(PostRepository posts, UserProfileRepository profiles, ActionThrottle throttle,
			AgeGate ageGate, Clock clock) {
		this.posts = posts;
		this.profiles = profiles;
		this.throttle = throttle;
		this.ageGate = ageGate;
		this.clock = clock;
	}

	@Transactional
	public Post repost(String userId, String postId, String opinion) {
		ageGate.requireCanWrite(userId);
		throttle.exigePermitido("repost", userId, PER_HOUR, Duration.ofHours(1));

		Post original = posts.findById(new PostId(postId))
			.filter(Post::isActive)
			.orElseThrow(() -> new ApiException(404, "POST_NOT_FOUND", "Post nao encontrado."));
		Post root = original;
		if (original.repostOf() != null) {
			// Republicar uma republicação aponta para o original (sem correntes de crédito).
			root = posts.findById(new PostId(original.repostOf()))
				.filter(Post::isActive)
				.orElseThrow(() -> new ApiException(404, "POST_NOT_FOUND", "Post original nao encontrado."));
		}
		if (root.communityId() != null || root.channelId() != null) {
			throw new ApiException(400, "PRIVATE_POST",
				"Posts de comunidades e canais privados nao podem ser republicados no feed.");
		}
		if (NOT_REPOSTABLE.contains(root.type())) {
			throw new ApiException(400, "NOT_REPOSTABLE", "Este tipo de publicacao nao pode ser republicado.");
		}
		if (posts.findActiveRepostByAuthorAndSource(new UserId(userId), root.id().value()).isPresent()) {
			throw new ApiException(409, "ALREADY_REPOSTED", "Voce ja republicou este post.");
		}

		// Opinião opcional: vira o texto da cópia e o original aparece CITADO no card. Sem
		// opinião, mantém o repost simples (a cópia carrega o conteúdo original).
		String cleanOpinion = opinion == null ? "" : PlainText.defuse(opinion.trim());
		if (cleanOpinion.length() > 600) {
			throw new ApiException(400, "OPINION_TOO_LONG",
				"A opiniao do repost deve ter no maximo 600 caracteres.");
		}
		boolean withOpinion = !cleanOpinion.isBlank();
		boolean opinionOnly = OPINION_ONLY.contains(root.type());
		if (opinionOnly && !withOpinion) {
			throw new ApiException(400, "OPINION_REQUIRED",
				"Este tipo de publicacao so pode ser republicado com uma opiniao.");
		}
		String username = profiles.findById(root.authorId().value())
			.map(profile -> profile.username())
			.orElse(null);
		Post copy = Post.publish(new PostId("post_" + UUID.randomUUID().toString().replace("-", "")),
			new UserId(userId), opinionOnly ? PostType.INSIGHT : root.type(),
			withOpinion ? cleanOpinion : root.content(), null,
			withOpinion ? List.of() : root.tags(), withOpinion ? List.of() : root.mediaIds(), null,
			withOpinion ? null : root.topic(), clock.instant(), AuthorIdentity.SOCIAL)
			.markRepostOf(root.id().value(), username, withOpinion);
		return posts.save(copy);
	}
}
