package com.socialcoisa.rede.importlinkedin.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.importlinkedin.application.port.out.LinkedinContactPreferenceRepository;

/**
 * Continua os convites automáticos que ficaram na fila (arquivo grande, reimportação, botão
 * ligado depois). Cada ciclo manda no máximo um lote por pessoa — convidar não pode virar rajada.
 */
@Component
class LinkedinContactInviteWorker {

	private static final Logger LOG = LoggerFactory.getLogger(LinkedinContactInviteWorker.class);

	private final LinkedinContactInviteService service;
	private final LinkedinContactPreferenceRepository preferences;
	private final boolean enabled;
	private final int batch;

	LinkedinContactInviteWorker(LinkedinContactInviteService service,
			LinkedinContactPreferenceRepository preferences,
			@Value("${rede.linkedin.contact-invites.worker-enabled:true}") boolean enabled,
			@Value("${rede.linkedin.contact-invites.max-per-run:200}") int batch) {
		this.service = service;
		this.preferences = preferences;
		this.enabled = enabled;
		this.batch = Math.max(1, Math.min(batch, 1000));
	}

	@Scheduled(fixedDelayString = "${rede.linkedin.contact-invites.worker-delay-ms:900000}",
		initialDelayString = "${rede.linkedin.contact-invites.worker-initial-delay-ms:120000}")
	public void run() {
		if (!enabled) {
			return;
		}
		for (String userId : preferences.findUserIdsWithAutoInvite()) {
			try {
				int sent = service.processQueued(userId, batch);
				if (sent > 0) {
					LOG.info("linkedin_contact_invites user={} sent={}", userId, sent);
				}
			}
			catch (RuntimeException failure) {
				LOG.warn("linkedin_contact_invites_failed user={} error={}", userId, failure.getMessage());
			}
		}
	}
}