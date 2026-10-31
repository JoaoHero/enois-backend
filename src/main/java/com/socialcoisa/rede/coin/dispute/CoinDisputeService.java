package com.socialcoisa.rede.coin.dispute;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.coin.application.CoinService;
import com.socialcoisa.rede.coin.application.port.out.CoinLedgerRepository;
import com.socialcoisa.rede.coin.application.port.out.CoinDisputeRepository;
import com.socialcoisa.rede.coin.domain.CoinDispute;
import com.socialcoisa.rede.coin.domain.CoinEntry;
import com.socialcoisa.rede.moderation.application.ModerationService;
import com.socialcoisa.rede.moderation.application.SanctionService;
import com.socialcoisa.rede.moderation.domain.ReportReason;
import com.socialcoisa.rede.moderation.domain.TargetType;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.TextFieldLimit;

/**
 * Contestação de transferência (golpe): a vítima contesta dentro de 7 dias e a disputa
 * entra PELO MESMO FLUXO de denúncia (vira denúncia SCAM contra o receptor na fila de
 * moderação). Decisão:
 * <ul>
 *   <li><b>verdadeira</b> → a transferência é REVERTIDA no ledger (espelho), o golpista
 *   é restrito pela sanção da denúncia e a vítima recebe o valor de volta;</li>
 *   <li><b>falsa</b> → a sanção vai para QUEM CONTOU (contester de má-fé).</li>
 * </ul>
 */
@Service
public class CoinDisputeService {

	/** Janela para contestar: 7 dias (depois disso, o Pix-é-Pix). */
	public static final Duration CONTEST_WINDOW = Duration.ofDays(7);

	private final CoinDisputeRepository disputes;
	private final CoinLedgerRepository ledger;
	private final CoinService coins;
	private final ModerationService moderation;
	private final SanctionService sanctions;
	private final com.socialcoisa.rede.profile.application.port.out.UserProfileRepository profiles;
	private final com.socialcoisa.rede.notifications.application.NotificationService notifications;
	private final com.socialcoisa.rede.moderation.application.port.out.ReportRepository reportRepository;
	private final Clock clock;

	public CoinDisputeService(CoinDisputeRepository disputes, CoinLedgerRepository ledger, CoinService coins,
			ModerationService moderation, SanctionService sanctions,
			com.socialcoisa.rede.profile.application.port.out.UserProfileRepository profiles,
			com.socialcoisa.rede.notifications.application.NotificationService notifications,
			com.socialcoisa.rede.moderation.application.port.out.ReportRepository reportRepository, Clock clock) {
		this.disputes = disputes;
		this.ledger = ledger;
		this.coins = coins;
		this.moderation = moderation;
		this.sanctions = sanctions;
		this.profiles = profiles;
		this.notifications = notifications;
		this.reportRepository = reportRepository;
		this.clock = clock;
	}

	/** A vítima (remetente) contesta a transferência. */
	@Transactional
	public CoinDispute contest(String senderId, String entryRef, String reason, String description) {
		// Os tetos sao os das COLUNAS (`coin_disputes.reason varchar(400)`, `description varchar(1000)`).
		TextFieldLimit.check("DISPUTE_FIELD_TOO_LONG", "motivo", reason, 400);
		TextFieldLimit.check("DISPUTE_FIELD_TOO_LONG", "descricao", description, 1000);
		if (!StringUtils.hasText(reason) || !StringUtils.hasText(description) || description.trim().length() < 20) {
			throw new ApiException(400, "CONTESTATION_DETAILS_REQUIRED",
				"Explica o que aconteceu em pelo menos 20 caracteres.");
		}
		CoinEntry transfer = ledger.findByAccountIdAndRefId(senderId, entryRef)
			.filter(e -> e.kind() == CoinEntry.Kind.TRANSFER_OUT)
			.orElseThrow(() -> new ApiException(404, "TRANSFER_NOT_FOUND",
				"Transferência não encontrada nessa conta."));
		if (Duration.between(transfer.createdAt(), Instant.now(clock)).compareTo(CONTEST_WINDOW) > 0) {
			throw new ApiException(403, "CONTEST_WINDOW_CLOSED",
				"O prazo para contestar (7 dias) já fechou.");
		}
		if (disputes.existsByEntryRef(entryRef)) {
			throw new ApiException(409, "ALREADY_CONTESTED", "Esta transferência já foi contestada.");
		}
		long valorReal = Math.abs(transfer.amount());
		String receiverId = resolveReceiver(entryRef, senderId, valorReal);
		String reportDescription = "Contestação de transação (golpe): " + description.trim();
		var report = moderation.report(senderId, TargetType.USER, receiverId, ReportReason.SCAM, reportDescription);
		CoinDispute dispute = disputes.save(CoinDispute.open("cdisp_" + UUID.randomUUID().toString().replace("-", ""),
			entryRef, senderId, receiverId, valorReal, reason.trim(), description.trim(), report.id(),
			Instant.now(clock)));
		notifications.create(receiverId, "MODERACAO", senderId, "Transferência contestada",
			"Uma transferência que você recebeu foi contestada como golpe. Uma pessoa do time vai analisar os dois lados.",
			"/api/penalidades", "Ver");
		return dispute;
	}

	/** Decisão VERDADEIRA: reverte a transferência e restringe o golpista (pela denúncia). */
	@Transactional
	public CoinDispute uphold(String disputeId, String reviewerId, String note) {
		CoinDispute dispute = pending(disputeId);
		Instant now = Instant.now(clock);
		coins.reverseTransfer(dispute.receiverId(), dispute.senderId(), dispute.amount(), dispute.entryRef(),
			"Reversão por contestação aceita (" + dispute.id() + ")");
		CoinDispute saved = disputes.save(dispute.uphold(reviewerId, note, now));
		reportRepository.findById(dispute.reportId()).ifPresent(report ->
			sanctions.applyForReport(report, "Golpe confirmado por contestação de transação (" + dispute.id() + ")"));
		notifications.create(dispute.senderId(), "MODERACAO", reviewerId, "Contestação aceita",
			"A transferência contestada foi REVERTIDA e o valor voltou para a sua carteira.",
			"/api/carteira", "Ver carteira");
		return saved;
	}

	/** Decisão FALSA: a sanção vai para quem contestou de má-fé. */
	@Transactional
	public CoinDispute reject(String disputeId, String reviewerId, String note) {
		CoinDispute dispute = pending(disputeId);
		Instant now = Instant.now(clock);
		CoinDispute saved = disputes.save(dispute.reject(reviewerId, note, now));
		sanctions.restrictUser(dispute.senderId(), saved.id(),
			"Contestação de transação julgada FALSA pela moderação (" + dispute.id() + ")");
		notifications.create(dispute.senderId(), "SANCAO", reviewerId, "Contestação não procedeu",
			"A contestação foi analisada e julgada infundada. Sua conta foi restrita; se foi um erro, você pode recorrer.",
			"/api/penalidades", "Recorrer");
		return saved;
	}

	public List<CoinDispute> pending(int limit) {
		return disputes.findPending(limit);
	}

	public List<CoinDispute> bySender(String senderId) {
		return disputes.findBySender(senderId);
	}

	/** A view da disputa (para o app e a fila de moderação). */
	public static Map<String, Object> disputeView(CoinDispute d) {
		Map<String, Object> view = new java.util.LinkedHashMap<>();
		view.put("id", d.id());
		view.put("entryRef", d.entryRef());
		view.put("amount", d.amount());
		view.put("reason", d.reason());
		view.put("state", d.state().name());
		view.put("createdAt", d.createdAt().toString());
		if (d.reviewNote() != null) {
			view.put("reviewNote", d.reviewNote());
		}
		return view;
	}

	private CoinDispute pending(String id) {
		return disputes.findById(id)
			.orElseThrow(() -> new ApiException(404, "DISPUTE_NOT_FOUND", "Contestação não encontrada."));
	}

	/** O receptor da transferência: quem recebeu o TRANSFER_IN com o MESMO ref. */
	private String resolveReceiver(String entryRef, String senderId, long amount) {
		var inEntry = ledger.findByRefIdAndKind(entryRef, CoinEntry.Kind.TRANSFER_IN);
		return inEntry
			.filter(e -> e.amount() == amount && !e.accountId().equals(senderId))
			.map(CoinEntry::accountId)
			.orElseGet(() -> ledger.findByRefIdAndKind(entryRef, CoinEntry.Kind.ESCROW_IN)
				.filter(e -> !e.accountId().equals(senderId))
				.map(CoinEntry::accountId)
				.orElseThrow(() -> new ApiException(409, "RECEIVER_NOT_FOUND",
					"Não foi possível identificar quem recebeu esta transferência.")));
	}
}
