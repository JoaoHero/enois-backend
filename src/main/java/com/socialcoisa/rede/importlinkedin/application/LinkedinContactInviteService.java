package com.socialcoisa.rede.importlinkedin.application;

import java.text.Normalizer;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.importlinkedin.LinkedinImportService.ParsedContact;
import com.socialcoisa.rede.importlinkedin.application.port.out.LinkedinContactInviteRepository;
import com.socialcoisa.rede.importlinkedin.application.port.out.LinkedinContactPreferenceRepository;
import com.socialcoisa.rede.importlinkedin.domain.LinkedinContactInvite;
import com.socialcoisa.rede.importlinkedin.domain.LinkedinContactPreference;
import com.socialcoisa.rede.importlinkedin.domain.LinkedinInviteStatus;
import com.socialcoisa.rede.network.application.NetworkService;
import com.socialcoisa.rede.network.domain.ConnectionStatus;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.profile.domain.UserProfile;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Contatos do LinkedIn importados pelo arquivo de dados e o convite automático no Énóis.
 *
 * <p>O LinkedIn não entrega conexões por API para apps comuns (escopo `r_1st_connections` é
 * restrito a parceiros aprovados), então o caminho é o Connections.csv que o próprio membro
 * exporta. A correspondência com uma conta do Énóis é CONSERVADORA: e-mail exato quando o
 * arquivo traz, ou nome completo idêntico e sem ambiguidade — nome que casa com mais de uma
 * pessoa fica AMBIGUOUS e não recebe convite (convite errado é pior que convite faltando).</p>
 *
 * <p>Guardamos o mínimo: nome exibido, chave normalizada e o usuário correspondente. Empresa,
 * cargo, URL e e-mail do contato não são persistidos.</p>
 */
@Service
public class LinkedinContactInviteService {

	private static final Logger LOG = LoggerFactory.getLogger(LinkedinContactInviteService.class);

	private final LinkedinContactInviteRepository invites;
	private final LinkedinContactPreferenceRepository preferences;
	private final UserProfileRepository profiles;
	private final NetworkService network;
	private final Clock clock;
	private final int maxPerRun;

	public LinkedinContactInviteService(LinkedinContactInviteRepository invites,
			LinkedinContactPreferenceRepository preferences, UserProfileRepository profiles, NetworkService network,
			Clock clock, @Value("${rede.linkedin.contact-invites.max-per-run:200}") int maxPerRun) {
		this.invites = invites;
		this.preferences = preferences;
		this.profiles = profiles;
		this.network = network;
		this.clock = clock;
		this.maxPerRun = Math.max(1, Math.min(maxPerRun, 1000));
	}

	public record ImportResult(int contacts, int invited, int pending, int alreadyConnected, int noMatch,
			int ambiguous, int skipped, int queued, boolean autoInvite) {
	}

	public record Summary(boolean autoInvite, int contacts, int invited, int pending, int alreadyConnected,
			int noMatch, int ambiguous, int skipped, int queued) {
	}

	public record InviteView(String name, String status, String matchedUsername) {
	}

	@Transactional
	public ImportResult importContacts(String userId, List<ParsedContact> contacts) {
		var now = clock.instant();
		for (ParsedContact contact : contacts) {
			String key = nameKey(contact.name());
			if (key.isBlank()) {
				continue;
			}
			var existing = invites.findByUserAndKey(userId, key);
			// Convite já resolvido não é refeito (nem duplicado) numa reimportação.
			if (existing.isPresent() && settled(existing.get().status())) {
				continue;
			}
			LinkedinContactInvite invite = existing.orElseGet(() -> new LinkedinContactInvite(
				"li_invite_" + UUID.randomUUID().toString().replace("-", ""), userId, contact.name(), key, null,
				LinkedinInviteStatus.QUEUED, now, now));
			invites.save(resolve(userId, invite, contact.email(), now));
		}
		boolean autoInvite = autoInvite(userId);
		if (autoInvite) {
			processQueued(userId, maxPerRun);
		}
		return result(userId, contacts.size(), autoInvite);
	}

	@Transactional
	public Summary setAutoInvite(String userId, boolean enabled) {
		var now = clock.instant();
		var existing = preferences.findByUserId(userId);
		preferences.save(existing.map(preference -> preference.withAutoInvite(enabled, now))
			.orElseGet(() -> new LinkedinContactPreference(userId, enabled, now, now)));
		if (enabled) {
			processQueued(userId, maxPerRun);
		}
		return summary(userId);
	}

	public Summary summary(String userId) {
		return new Summary(autoInvite(userId), invites.countByUser(userId),
			invites.countByUserAndStatus(userId, LinkedinInviteStatus.INVITED),
			invites.countByUserAndStatus(userId, LinkedinInviteStatus.PENDING),
			invites.countByUserAndStatus(userId, LinkedinInviteStatus.ALREADY_CONNECTED),
			invites.countByUserAndStatus(userId, LinkedinInviteStatus.NO_MATCH),
			invites.countByUserAndStatus(userId, LinkedinInviteStatus.AMBIGUOUS),
			invites.countByUserAndStatus(userId, LinkedinInviteStatus.SKIPPED),
			invites.countByUserAndStatus(userId, LinkedinInviteStatus.QUEUED));
	}

	/** Lista para a pessoa conferir quem foi convidado e quem não casou com ninguém. */
	public List<InviteView> list(String userId, int limit) {
		var rows = invites.findByUser(userId, Math.max(1, Math.min(limit, 500)));
		var ids = rows.stream().map(LinkedinContactInvite::matchedUserId).filter(StringUtils::hasText).distinct()
			.toList();
		var usernames = profiles.findAllByIds(ids).stream()
			.collect(java.util.stream.Collectors.toMap(UserProfile::id, UserProfile::username));
		return rows.stream()
			.map(row -> new InviteView(row.contactName(), row.status().name(),
				row.matchedUserId() == null ? null : usernames.get(row.matchedUserId())))
			.toList();
	}

	/**
	 * Envia os convites que estão na fila (com correspondência, ainda não enviados). O worker e o
	 * botão chamam daqui; cada execução tem teto para um arquivo enorme não virar uma rajada.
	 */
	@Transactional
	public int processQueued(String userId, int limit) {
		int sent = 0;
		var now = clock.instant();
		for (LinkedinContactInvite invite : invites.findQueuedByUser(userId, limit)) {
			if (!StringUtils.hasText(invite.matchedUserId())) {
				continue;
			}
			LinkedinInviteStatus status;
			try {
				var connection = network.requestConnection(userId, invite.matchedUserId());
				// PENDING pode ser a solicitação que EU acabei de mandar (INVITED) ou uma que a
				// outra pessoa já tinha mandado (PENDING: o próximo passo é aceitar, não convidar).
				status = switch (connection.status()) {
					case ACCEPTED -> LinkedinInviteStatus.ALREADY_CONNECTED;
					case PENDING -> userId.equals(connection.requesterUserId())
						? LinkedinInviteStatus.INVITED
						: LinkedinInviteStatus.PENDING;
					default -> LinkedinInviteStatus.INVITED;
				};
			}
			catch (ApiException refused) {
				// Bloqueio/auto-conexão: não insiste e registra o motivo no estado.
				status = LinkedinInviteStatus.SKIPPED;
			}
			catch (RuntimeException failure) {
				LOG.warn("Falha ao convidar contato do LinkedIn (user={}): {}", userId,
					failure.getClass().getSimpleName());
				status = LinkedinInviteStatus.SKIPPED;
			}
			invites.save(invite.withStatus(status, now));
			if (status == LinkedinInviteStatus.INVITED) {
				sent++;
			}
		}
		return sent;
	}

	/** Reencontra o contato no Énóis; devolve o convite com correspondência (ou o motivo de não ter). */
	private LinkedinContactInvite resolve(String userId, LinkedinContactInvite invite, String email,
			java.time.Instant now) {
		if (StringUtils.hasText(email)) {
			var byEmail = profiles.findByEmail(email).orElse(null);
			if (byEmail != null) {
				return byEmail.id().equals(userId)
					? invite.withMatch(null, LinkedinInviteStatus.SKIPPED, now)
					: invite.withMatch(byEmail.id(), LinkedinInviteStatus.QUEUED, now);
			}
		}
		List<String> candidates = profiles.searchByQuery(invite.contactName()).stream()
			.filter(profile -> !profile.id().equals(userId))
			.filter(profile -> matchesKey(profile, invite.nameKey()))
			.map(UserProfile::id)
			.distinct()
			.toList();
		if (candidates.size() > 1) {
			return invite.withMatch(null, LinkedinInviteStatus.AMBIGUOUS, now);
		}
		if (candidates.isEmpty()) {
			return invite.withMatch(null, LinkedinInviteStatus.NO_MATCH, now);
		}
		return invite.withMatch(candidates.get(0), LinkedinInviteStatus.QUEUED, now);
	}

	private static boolean matchesKey(UserProfile profile, String key) {
		return key.equals(nameKey(profile.name())) || key.equals(nameKey(profile.socialName()));
	}

	private ImportResult result(String userId, int contacts, boolean autoInvite) {
		Summary summary = summary(userId);
		return new ImportResult(contacts, summary.invited(), summary.pending(), summary.alreadyConnected(),
			summary.noMatch(), summary.ambiguous(), summary.skipped(), summary.queued(), autoInvite);
	}

	private boolean autoInvite(String userId) {
		return preferences.findByUserId(userId).map(LinkedinContactPreference::autoInvite).orElse(false);
	}

	private static boolean settled(LinkedinInviteStatus status) {
		return status == LinkedinInviteStatus.INVITED || status == LinkedinInviteStatus.PENDING
			|| status == LinkedinInviteStatus.ALREADY_CONNECTED;
	}

	/** Chave de comparação de nomes: sem acento, minúscula, espaços colapsados. */
	static String nameKey(String raw) {
		if (raw == null) {
			return "";
		}
		String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return normalized.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
	}

}