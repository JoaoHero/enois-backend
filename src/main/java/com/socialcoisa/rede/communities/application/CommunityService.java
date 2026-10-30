package com.socialcoisa.rede.communities.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.communities.application.port.out.CommunityRepository;
import com.socialcoisa.rede.communities.domain.Community;
import com.socialcoisa.rede.communities.domain.CommunityMember;
import com.socialcoisa.rede.communities.domain.MemberRole;
import com.socialcoisa.rede.feed.application.port.out.PostRepository;
import com.socialcoisa.rede.feed.domain.Post;
import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.TextFieldLimit;

@Service
public class CommunityService {

	private final CommunityRepository repository;
	private final PostRepository postRepository;
	private final com.socialcoisa.rede.shared.sanctions.SanctionGateReader sanctionGate;
	private final com.socialcoisa.rede.shared.security.SuspiciousText suspiciousText;
	private final Clock clock;
	private final com.socialcoisa.rede.coin.application.CoinService coins;

	public CommunityService(CommunityRepository repository, PostRepository postRepository,
			com.socialcoisa.rede.shared.sanctions.SanctionGateReader sanctionGate,
			com.socialcoisa.rede.shared.security.SuspiciousText suspiciousText, Clock clock,
			com.socialcoisa.rede.coin.application.CoinService coins) {
		this.coins = coins;
		this.repository = repository;
		this.postRepository = postRepository;
		this.sanctionGate = sanctionGate;
		this.suspiciousText = suspiciousText;
		this.clock = clock;
	}

	@Transactional
	public Community create(String name, String description, String topic, String cover, String avatar, String userId) {
		// Os tetos sao os das COLUNAS (`communities.name varchar(255)`, `description varchar(2000)`):
		// sem a checagem o banco recusava com "Data too long" e a pessoa levava 409 "conflita com o
		// estado atual dos dados".
		TextFieldLimit.check("COMMUNITY_FIELD_TOO_LONG", "nome", name, 255);
		TextFieldLimit.check("COMMUNITY_FIELD_TOO_LONG", "descricao", description, 2000);
		String id = "community_" + UUID.randomUUID().toString().replace("-", "");
		// Descrição é opcional: em branco vira NULL (o banco guarda "sem descrição", não texto vazio).
		String finalDescription = org.springframework.util.StringUtils.hasText(description) ? description.trim()
			: null;
		Community community = Community.create(id, name, finalDescription, topic, cover, avatar, userId,
			clock.instant());
		repository.save(community);
		repository.saveMember(CommunityMember.join(id, userId, MemberRole.ADMIN, clock.instant()));
		return community;
	}

	@Transactional
	public Community update(String communityId, String name, String description, String topic, String cover,
			String avatar, String bannerHtml, String userId) {
		Community community = repository.findById(communityId)
			.orElseThrow(() -> new ApiException(404, "COMMUNITY_NOT_FOUND", "Comunidade nao encontrada."));
		if (!community.createdBy().equals(userId)) {
			throw new ApiException(403, "FORBIDDEN", "So o criador pode atualizar a comunidade.");
		}
		TextFieldLimit.check("COMMUNITY_FIELD_TOO_LONG", "nome", name, 255);
		TextFieldLimit.check("COMMUNITY_FIELD_TOO_LONG", "descricao", description, 2000);
		String finalName = name != null ? name : community.name();
		String finalDesc = description != null ? description : community.description();
		String finalTopic = topic != null ? topic : community.topic();
		String finalCover = cover != null ? cover : community.cover();
		String finalAvatar = avatar != null ? avatar : community.avatar();
		// Capa em HTML: mesmo sanitizador da capa do perfil. Nulo = campo ausente (mantem); vazio =
		// limpa (volta para a foto); com texto = sanitiza e substitui.
		String finalBannerHtml = bannerHtml == null ? null
			: (org.springframework.util.StringUtils.hasText(bannerHtml)
				? com.socialcoisa.rede.shared.html.SafeHtml.requireProfileHtml(bannerHtml)
				: "");
		Community updated = community.update(finalName, finalDesc, finalTopic, finalCover, finalAvatar,
			finalBannerHtml, clock.instant());
		repository.save(updated);
		return updated;
	}

	@Transactional
	public CommunityMember join(String communityId, String userId) {
		// Without this an unknown id left a membership row pointing at nothing.
		repository.findById(communityId)
			.orElseThrow(() -> new ApiException(404, "COMMUNITY_NOT_FOUND", "Comunidade nao encontrada."));
		if (repository.isMember(communityId, userId)) {
			throw new ApiException(409, "ALREADY_MEMBER", "Voce ja e membro desta comunidade.");
		}
		CommunityMember member = CommunityMember.join(communityId, userId, MemberRole.MEMBER, clock.instant());
		repository.saveMember(member);
		coins.recordPresence(userId);
		return member;
	}

	/**
	 * Sair da comunidade. O dono NAO pode simplesmente sair enquanto houver outros membros: a
	 * propriedade e o que autoriza editar/excluir, entao ele precisa transferir antes (409
	 * OWNERSHIP_TRANSFER_REQUIRED). Antes, a saida do dono promovia alguem aleatorio a ADMIN, mas o
	 * novo admin nao conseguia editar (a checagem usa o dono) e quem saiu continuava no controle —
	 * estado inconsistente e inseguro. Ser o ultimo membro apaga a comunidade.
	 */
	@Transactional
	public void leave(String communityId, String userId) {
		Community community = repository.findById(communityId)
			.orElseThrow(() -> new ApiException(404, "COMMUNITY_NOT_FOUND", "Comunidade nao encontrada."));
		if (!repository.isMember(communityId, userId)) {
			throw new ApiException(404, "NOT_MEMBER", "Voce nao e membro desta comunidade.");
		}
		if (community.createdBy().equals(userId) && repository.countMembers(communityId) > 1) {
			throw new ApiException(409, "OWNERSHIP_TRANSFER_REQUIRED",
				"Transfira a administracao para outro membro antes de sair.");
		}
		repository.removeMember(communityId, userId);
		if (repository.countMembers(communityId) == 0) {
			repository.removeAllMembers(communityId);
			repository.delete(communityId);
		}
	}

	/**
	 * Transfere a propriedade para outro membro. So o dono atual transfere; o novo dono vira ADMIN e
	 * o antigo passa a membro comum (se continuar na comunidade).
	 */
	@Transactional
	public Community transferOwnership(String communityId, String currentUserId, String newOwnerId) {
		Community community = repository.findById(communityId)
			.orElseThrow(() -> new ApiException(404, "COMMUNITY_NOT_FOUND", "Comunidade nao encontrada."));
		if (!community.createdBy().equals(currentUserId)) {
			throw new ApiException(403, "FORBIDDEN", "So o dono pode transferir a administracao.");
		}
		if (newOwnerId == null || newOwnerId.isBlank()) {
			throw new ApiException(400, "INVALID_NEW_OWNER", "Escolha quem vai administrar a comunidade.");
		}
		if (newOwnerId.equals(currentUserId)) {
			throw new ApiException(400, "INVALID_NEW_OWNER", "Voce ja e o administrador desta comunidade.");
		}
		if (!repository.isMember(communityId, newOwnerId)) {
			throw new ApiException(400, "NOT_MEMBER", "O novo administrador precisa ser membro da comunidade.");
		}
		Community transferred = community.transferOwnership(newOwnerId, clock.instant());
		repository.save(transferred);
		repository.updateMemberRole(communityId, newOwnerId, MemberRole.ADMIN);
		if (repository.isMember(communityId, currentUserId)) {
			repository.updateMemberRole(communityId, currentUserId, MemberRole.MEMBER);
		}
		return transferred;
	}

	/** Exclui a comunidade. So o dono; remove os vinculos de membros para nao deixar referencia orfa. */
	@Transactional
	public void deleteCommunity(String communityId, String userId) {
		deleteCommunity(communityId, userId, false);
	}

	/** Admin pode excluir comunidade de terceiros (moderacao de ataque/spam, ECA Digital). */
	public void deleteCommunity(String communityId, String userId, boolean adminOverride) {
		Community community = repository.findById(communityId)
			.orElseThrow(() -> new ApiException(404, "COMMUNITY_NOT_FOUND", "Comunidade nao encontrada."));
		if (!adminOverride && !community.createdBy().equals(userId)) {
			throw new ApiException(403, "FORBIDDEN", "So o dono pode excluir a comunidade.");
		}
		repository.removeAllMembers(communityId);
		repository.delete(communityId);
	}

	/** Comunidades das quais o usuario participa (menu lateral). */
	public List<Community> listMine(String userId, int limit) {
		return repository.findByMember(userId, limit);
	}

	/** Favorita (salva) uma comunidade para acesso rapido. Nao exige participar dela. */
	@Transactional
	public void favorite(String communityId, String userId) {
		getById(communityId);
		repository.addFavorite(communityId, userId, clock.instant());
	}

	@Transactional
	public void unfavorite(String communityId, String userId) {
		repository.removeFavorite(communityId, userId);
	}

	public List<Community> listFavorites(String userId, int limit) {
		return repository.findFavorites(userId, limit);
	}

	public List<Community> list(String topic, String query, java.time.Instant before, int limit) {
		// Comunidade criada por conta banida sai da vitrine (o conteúdo dela já é limpo).
		return repository.findAll(topic, query, before, limit).stream()
			.filter(community -> !sanctionGate.banned(community.createdBy()))
			.filter(community -> !suspiciousText.matches(community.name(), community.description()))
			.toList();
	}

	public Community getById(String id) {
		return repository.findById(id)
			.orElseThrow(() -> new ApiException(404, "COMMUNITY_NOT_FOUND", "Comunidade nao encontrada."));
	}

	public boolean isMember(String communityId, String userId) {
		return repository.isMember(communityId, userId);
	}

	/**
	 * Fixa UM post no topo da comunidade. Só ADMIN (dono/administração) fixa; o post precisa ser
	 * daquela comunidade e estar ativo. Fixar outro substitui o anterior (a PK é a comunidade).
	 */
	@Transactional
	public String pinPost(String communityId, String postId, String userId) {
		getById(communityId);
		requireAdmin(communityId, userId);
		Post post = postRepository.findById(new PostId(postId))
			.filter(Post::isActive)
			.orElseThrow(() -> new ApiException(404, "POST_NOT_FOUND", "Post nao encontrado."));
		if (!communityId.equals(post.communityId())) {
			throw new ApiException(404, "POST_NOT_FOUND", "Post nao pertence a esta comunidade.");
		}
		repository.savePinnedPost(communityId, postId, userId, clock.instant());
		return postId;
	}

	@Transactional
	public void unpinPost(String communityId, String userId) {
		getById(communityId);
		requireAdmin(communityId, userId);
		repository.removePinnedPost(communityId);
	}

	public Optional<String> pinnedPostId(String communityId) {
		return repository.findPinnedPostId(communityId);
	}

	private void requireAdmin(String communityId, String userId) {
		repository.findMember(communityId, userId)
			.filter(member -> member.role() == MemberRole.ADMIN)
			.orElseThrow(() -> new ApiException(403, "FORBIDDEN",
				"So quem administra a comunidade pode fixar publicacoes."));
	}

	public int countMembers(String communityId) {
		return repository.countMembers(communityId);
	}

	public List<CommunityMember> findMembers(String communityId) {
		return repository.findMembersByCommunity(communityId);
	}

}
