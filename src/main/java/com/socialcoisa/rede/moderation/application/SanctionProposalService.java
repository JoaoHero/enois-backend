package com.socialcoisa.rede.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.identity.application.port.out.AuthSessionRepository;
import com.socialcoisa.rede.moderation.application.port.out.ModeratorApplicationRepository;
import com.socialcoisa.rede.moderation.application.port.out.SanctionProposalRepository;
import com.socialcoisa.rede.moderation.domain.ModeratorApplicationStatus;
import com.socialcoisa.rede.moderation.domain.SanctionProposal;
import com.socialcoisa.rede.moderation.domain.SanctionProposalVote;
import com.socialcoisa.rede.moderation.domain.SanctionProposalStatus;
import com.socialcoisa.rede.moderation.domain.SanctionType;
import com.socialcoisa.rede.moderation.domain.SanctionVoteType;
import com.socialcoisa.rede.moderation.domain.UserSanction;
import com.socialcoisa.rede.notifications.application.NotificationService;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.profile.domain.UserProfile;
import com.socialcoisa.rede.shared.sanctions.SanctionGateReader;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Punição de usuários por votação de moderadores.
 *
 * A proposta só existe com {@link ModeratorService#MIN_MODERATORS_FOR_SANCTION} moderadores
 * ativos. O limiar é "mais da metade": {@code total / 2 + 1}, recalculado a cada voto. Quando o
 * limiar de aprovação é atingido, a sanção é aplicada pelo mesmo caminho do painel admin; quando
 * mais da metade rejeita, a proposta é encerrada sem punir. Empates continuam abertos.
 *
 * Antes dos 10 moderadores, e para os administradores, a punição direta continua disponível em
 * {@code POST /admin/users/{username}/sanction}.
 */
@Service
public class SanctionProposalService {

	private final SanctionProposalRepository proposals;
	private final ModeratorApplicationRepository applications;
	private final UserProfileRepository profiles;
	private final SanctionService sanctions;
	private final AuthSessionRepository sessions;
	private final NotificationService notifications;
	private final SanctionGateReader sanctionGate;
	private final Clock clock;

	public SanctionProposalService(SanctionProposalRepository proposals,
			ModeratorApplicationRepository applications, UserProfileRepository profiles,
			SanctionService sanctions, AuthSessionRepository sessions, NotificationService notifications,
			SanctionGateReader sanctionGate, Clock clock) {
		this.proposals = proposals;
		this.applications = applications;
		this.profiles = profiles;
		this.sanctions = sanctions;
		this.sessions = sessions;
		this.notifications = notifications;
		this.sanctionGate = sanctionGate;
		this.clock = clock;
	}

	@Transactional
	public ProposalView propose(String proposerId, String username, SanctionType type, String reason) {
		long moderatorCount = moderatorCount();
		if (moderatorCount < ModeratorService.MIN_MODERATORS_FOR_SANCTION) {
			throw new ApiException(409, "NOT_ENOUGH_MODERATORS",
				"Punição por votação só existe a partir de " + ModeratorService.MIN_MODERATORS_FOR_SANCTION
					+ " moderadores. Antes disso, somente administradores podem punir.");
		}
		requireModerator(proposerId);
		if (type == null) {
			throw new ApiException(400, "INVALID_SANCTION_TYPE", "Escolha restrição ou banimento.");
		}
		String cleanReason = trim(reason);
		if (cleanReason.length() < 10) {
			throw new ApiException(400, "REASON_REQUIRED", "Explique o motivo da punição (pelo menos 10 caracteres).");
		}
		UserProfile target = profiles.findByUsername(trim(username))
			.orElseThrow(() -> new ApiException(404, "USER_NOT_FOUND", "Usuário não encontrado."));
		// Conta já banida não precisa (nem pode) entrar em nova votação.
		if (sanctionGate.banned(target.id())) {
			throw new ApiException(409, "USER_ALREADY_BANNED", "Este usuário já está banido.");
		}
		if (target.id().equals(proposerId)) {
			throw new ApiException(400, "CANNOT_SANCTION_SELF", "Você não pode propor punição contra si.");
		}
		proposals.findPendingByTarget(target.id()).ifPresent(existing -> {
			throw new ApiException(409, "PROPOSAL_ALREADY_PENDING",
				"Já existe uma votação de punição aberta contra este usuário.");
		});
		Instant now = Instant.now(clock);
		SanctionProposal proposal = SanctionProposal.create("sproposal_" + randomId(), target.id(),
			target.username(), type, cleanReason, proposerId, requiredVotes(moderatorCount), now);
		SanctionProposal saved = proposals.save(proposal);
		// Quem propõe já concorda; o voto fica registrado para a contagem ser auditável.
		proposals.saveVote(SanctionProposalVote.create("svote_" + randomId(), saved.id(), proposerId,
			SanctionVoteType.APPROVE, "Autor da proposta", now));
		return view(saved, proposerId);
	}

	@Transactional(readOnly = true)
	public List<ProposalView> pending(String viewerId, int limit) {
		long moderatorCount = moderatorCount();
		int required = requiredVotes(moderatorCount);
		return proposals.findByStatus(SanctionProposalStatus.PENDING, limit).stream()
			// Alvo banido por outro caminho (admin, lote, automação): a votação perdeu o objeto.
			// some da fila em vez de ficar pedindo votos para uma decisão já tomada.
			.filter(proposal -> !sanctionGate.banned(proposal.targetUserId()))
			.map(proposal -> view(proposal.withRequiredVotes(required), viewerId, moderatorCount))
			.toList();
	}

	@Transactional
	public DecisionResult vote(String proposalId, String moderatorId, SanctionVoteType type, String note) {
		requireModerator(moderatorId);
		if (type == null) {
			throw new ApiException(400, "INVALID_VOTE", "Escolha concordar ou rejeitar.");
		}
		SanctionProposal proposal = proposals.findById(proposalId)
			.orElseThrow(() -> new ApiException(404, "PROPOSAL_NOT_FOUND", "Votação não encontrada."));
		if (sanctionGate.banned(proposal.targetUserId())) {
			throw new ApiException(409, "TARGET_ALREADY_BANNED", "O usuário desta votação já está banido.");
		}
		if (proposal.status() != SanctionProposalStatus.PENDING) {
			throw new ApiException(409, "ALREADY_DECIDED", "Esta votação já foi encerrada.");
		}
		if (proposal.targetUserId().equals(moderatorId)) {
			throw new ApiException(403, "TARGET_CANNOT_VOTE", "O alvo da punição não participa da votação.");
		}
		long moderatorCount = moderatorCount();
		if (moderatorCount < ModeratorService.MIN_MODERATORS_FOR_SANCTION) {
			throw new ApiException(409, "NOT_ENOUGH_MODERATORS",
				"A votação exige pelo menos " + ModeratorService.MIN_MODERATORS_FOR_SANCTION
					+ " moderadores ativos.");
		}
		int required = requiredVotes(moderatorCount);
		if (proposal.requiredVotes() != required) {
			proposal = proposals.save(proposal.withRequiredVotes(required));
		}
		Instant now = Instant.now(clock);
		String cleanNote = trimOrNull(note);
		SanctionProposalVote existing = proposals.findVote(proposalId, moderatorId).orElse(null);
		SanctionProposalVote savedVote = existing == null
			? proposals.saveVote(SanctionProposalVote.create("svote_" + randomId(), proposalId, moderatorId,
				type, cleanNote, now))
			: proposals.saveVote(existing.changeTo(type, cleanNote));

		List<SanctionProposalVote> all = proposals.findVotes(proposalId);
		int approve = (int) all.stream().filter(v -> v.vote() == SanctionVoteType.APPROVE).count();
		int reject = (int) all.stream().filter(v -> v.vote() == SanctionVoteType.REJECT).count();

		SanctionProposal decided = proposal;
		String status = SanctionProposalStatus.PENDING.name();
		String decision = "PENDING";
		String message = "Voto registrado. A votação segue aberta.";
		if (approve >= required) {
			decided = proposals.save(proposal.apply(now));
			applySanction(proposal, moderatorId, now);
			status = SanctionProposalStatus.APPLIED.name();
			decision = "APPLIED";
			message = "Mais da metade dos moderadores concordou: a punição foi aplicada.";
			notifications.create(proposal.proposerId(), "MODERACAO", moderatorId, "Punição aprovada",
				"A votação contra @" + proposal.targetUsername() + " atingiu o quórum e a punição foi aplicada.",
				"/moderador", "Abrir painel");
		}
		else if (reject >= required) {
			decided = proposals.save(proposal.reject(now));
			status = SanctionProposalStatus.REJECTED.name();
			decision = "REJECTED";
			message = "Mais da metade dos moderadores rejeitou: ninguém foi punido.";
			notifications.create(proposal.proposerId(), "MODERACAO", moderatorId, "Punição rejeitada",
				"A votação contra @" + proposal.targetUsername() + " foi encerrada sem punição.",
				"/moderador", "Abrir painel");
		}
		return new DecisionResult(approve, reject, required, savedVote.vote().name(), status, decision,
			message);
	}

	/** Administrador encerra uma votação que não deve seguir. */
	@Transactional
	public ProposalView cancel(String proposalId, String adminId) {
		SanctionProposal proposal = proposals.findById(proposalId)
			.orElseThrow(() -> new ApiException(404, "PROPOSAL_NOT_FOUND", "Votação não encontrada."));
		if (sanctionGate.banned(proposal.targetUserId())) {
			throw new ApiException(409, "TARGET_ALREADY_BANNED", "O usuário desta votação já está banido.");
		}
		if (proposal.status() != SanctionProposalStatus.PENDING) {
			throw new ApiException(409, "ALREADY_DECIDED", "Esta votação já foi encerrada.");
		}
		SanctionProposal saved = proposals.save(proposal.cancel(Instant.now(clock)));
		notifications.create(proposal.proposerId(), "MODERACAO", adminId, "Votação encerrada",
			"Um administrador encerrou a votação de punição contra @" + proposal.targetUsername() + ".",
			"/moderador", "Abrir painel");
		return view(saved, adminId);
	}

	private void applySanction(SanctionProposal proposal, String reviewerId, Instant now) {
		String reference = "proposal:" + proposal.id();
		if (proposal.type() == SanctionType.BAN) {
			UserSanction sanction = UserSanction.suspended("sanc_" + randomId(), proposal.targetUserId(), reference,
				proposal.reason(), now);
			sanctions.banAuthor(sanction, proposal.targetUserId(), reviewerId, sessions);
			notifications.create(proposal.targetUserId(), "SANCAO", "sistema", "Conta suspensa",
				"Sua conta foi suspensa por decisão de mais da metade dos moderadores: " + proposal.reason() + ".",
				"/api/penalidades", "Ver");
		}
		else {
			sanctions.restrictUser(proposal.targetUserId(), reference, proposal.reason());
		}
	}

	private ProposalView view(SanctionProposal proposal, String viewerId) {
		return view(proposal, viewerId, moderatorCount());
	}

	private ProposalView view(SanctionProposal proposal, String viewerId, long moderatorCount) {
		UserProfile proposer = profiles.findById(proposal.proposerId()).orElse(null);
		UserProfile target = profiles.findById(proposal.targetUserId()).orElse(null);
		List<SanctionProposalVote> all = proposals.findVotes(proposal.id());
		int approve = (int) all.stream().filter(v -> v.vote() == SanctionVoteType.APPROVE).count();
		int reject = (int) all.stream().filter(v -> v.vote() == SanctionVoteType.REJECT).count();
		String myVote = all.stream().filter(v -> v.moderatorId().equals(viewerId))
			.map(v -> v.vote().name()).findFirst().orElse(null);
		List<VoteView> votes = all.stream().map(vote -> {
			UserProfile moderator = profiles.findById(vote.moderatorId()).orElse(null);
			return new VoteView(vote.moderatorId(), moderator == null ? null : moderator.username(),
				moderator == null ? null : moderator.displayName(), vote.vote().name(), vote.note(),
				vote.createdAt().toString());
		}).toList();
		return new ProposalView(proposal.id(), proposal.targetUserId(),
			target == null ? proposal.targetUsername() : target.username(),
			target == null ? null : target.displayName(), proposal.type().name(), proposal.reason(),
			proposal.proposerId(), proposer == null ? null : proposer.username(),
			proposer == null ? null : proposer.displayName(), proposal.status().name(),
			proposal.requiredVotes(), moderatorCount, approve, reject, myVote,
			proposal.createdAt().toString(),
			proposal.decidedAt() == null ? null : proposal.decidedAt().toString(), votes);
	}

	private void requireModerator(String userId) {
		if (applications.findApprovedByUser(userId).isEmpty()) {
			throw new ApiException(403, "NOT_MODERATOR", "Apenas moderadores participam da votação.");
		}
	}

	private long moderatorCount() {
		return applications.countByStatus(ModeratorApplicationStatus.APPROVED);
	}

	private static int requiredVotes(long moderatorCount) {
		return (int) (moderatorCount / 2 + 1);
	}

	private static String randomId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private static String trimOrNull(String value) {
		String trimmed = trim(value);
		return StringUtils.hasText(trimmed) ? trimmed : null;
	}

	public record VoteView(String moderatorId, String username, String name, String vote, String note,
			String createdAt) {
	}

	public record ProposalView(String id, String targetUserId, String targetUsername, String targetName,
			String type, String reason, String proposerId, String proposer, String proposerName, String status,
			int requiredVotes, long moderatorCount, int approveVotes, int rejectVotes, String myVote,
			String createdAt, String decidedAt, List<VoteView> votes) {
	}

	public record DecisionResult(int approveVotes, int rejectVotes, int requiredVotes, String myVote,
			String status, String decision, String message) {
	}
}
