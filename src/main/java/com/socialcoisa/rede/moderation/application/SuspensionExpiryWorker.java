package com.socialcoisa.rede.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.identity.application.port.out.AuthSessionRepository;
import com.socialcoisa.rede.moderation.adapters.out.persistence.ExpiredSuspensionReader;
import com.socialcoisa.rede.moderation.application.port.out.AppealRepository;
import com.socialcoisa.rede.moderation.application.port.out.SanctionRepository;
import com.socialcoisa.rede.notifications.application.NotificationService;

/**
 * Fecha a suspensao com prazo: conta SUSPENSA que chega ao fim da graca sem recurso
 * aceito vira BAN definitivo e o conteudo entra na purga. Com recurso pendente, a
 * conversao espera — a decisao humana continua valendo.
 */
@Component
public class SuspensionExpiryWorker {

	private static final Logger LOG = LoggerFactory.getLogger(SuspensionExpiryWorker.class);

	private final ExpiredSuspensionReader suspensions;
	private final AppealRepository appeals;
	private final SanctionRepository sanctions;
	private final AuthSessionRepository sessions;
	private final BanCleanupService banCleanup;
	private final NotificationService notifications;
	private final Clock clock;
	private final Duration grace;
	private final boolean enabled;

	public SuspensionExpiryWorker(ExpiredSuspensionReader suspensions, AppealRepository appeals,
			SanctionRepository sanctions, AuthSessionRepository sessions, BanCleanupService banCleanup,
			NotificationService notifications, Clock clock,
			@Value("${rede.moderation.suspension.grace-days:7}") int graceDays,
			@Value("${rede.moderation.suspension.expiry-enabled:true}") boolean enabled) {
		this.suspensions = suspensions;
		this.appeals = appeals;
		this.sanctions = sanctions;
		this.sessions = sessions;
		this.banCleanup = banCleanup;
		this.notifications = notifications;
		this.clock = clock;
		this.grace = Duration.ofDays(Math.max(1, Math.min(graceDays, 365)));
		this.enabled = enabled;
	}

	@Scheduled(initialDelayString = "${rede.moderation.suspension.expiry-initial-delay-ms:120000}",
		fixedDelayString = "${rede.moderation.suspension.expiry-delay-ms:1800000}")
	@Transactional
	public void expireSuspensions() {
		if (!enabled) {
			return;
		}
		Instant cutoff = clock.instant().minus(grace);
		int converted = 0;
		List<com.socialcoisa.rede.moderation.domain.UserSanction> expired =
			suspensions.expiredActive(cutoff, 200);
		for (var sanction : expired) {
			try {
				if (appeals.existsPendingByUser(sanction.userId())) {
					continue;
				}
				var banned = sanctions.save(sanction.escalateToBan());
				sessions.revokeAllByUser(sanction.userId(), clock.instant());
				banCleanup.cleanup(banned.id(), sanction.userId());
				notifications.create(sanction.userId(), "SANCAO", "sistema", "Conta banida",
					"O prazo de recurso terminou sem contestação aceita e a suspensão virou banimento definitivo.",
					"/api/penalidades", "Ver");
				converted++;
			}
			catch (RuntimeException failure) {
				LOG.warn("Nao foi possivel converter a suspensao {}: {}", sanction.id(), failure.getMessage());
			}
		}
		if (converted > 0) {
			LOG.warn("Suspensao vencida: {} conta(s) convertida(s) em banimento.", converted);
		}
	}
}
