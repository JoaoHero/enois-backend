package com.socialcoisa.rede.support.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.support.application.port.out.SupportRequestRepository;
import com.socialcoisa.rede.support.domain.SupportRequest;
import com.socialcoisa.rede.support.domain.SupportStatus;

/**
 * Porta GitHub -> painel: o webhook de issues fecha/reabre o ticket local e avisa quem abriu.
 * Não chama o GitHub de volta — o estado já veio de lá, então não há laço de eventos.
 */
@Service
public class SupportWebhookService {

	private static final Logger LOG = LoggerFactory.getLogger(SupportWebhookService.class);

	private final SupportRequestRepository repository;
	private final SupportThreadService thread;
	private final SupportStatusNotifier notifier;
	private final SupportGithubConfig config;

	public SupportWebhookService(SupportRequestRepository repository, SupportThreadService thread,
			SupportStatusNotifier notifier, SupportGithubConfig config) {
		this.repository = repository;
		this.thread = thread;
		this.notifier = notifier;
		this.config = config;
	}

	/** Evento de outro repositório não pode mexer no suporte (quando o repo está configurado). */
	public boolean acceptsRepository(String fullName) {
		String expected = config.repository();
		return !StringUtils.hasText(expected) || expected.equalsIgnoreCase(fullName);
	}

	/**
	 * Comentário novo na issue vira mensagem no painel. O id do comentário é a chave de
	 * deduplicação: o eco do comentário que o próprio Énóis publicou não entra duas vezes.
	 */
	@Transactional
	public boolean applyIssueComment(int issueNumber, long commentId, String authorLogin, String body) {
		SupportRequest request = repository.findByGithubIssueNumber(issueNumber).orElse(null);
		if (request == null) {
			LOG.warn("Comentário do GitHub na issue #{} sem ticket de suporte correspondente", issueNumber);
			return false;
		}
		thread.addFromGithub(request.id(), commentId, authorLogin, body);
		return true;
	}

	/** Aplica o estado da issue. Devolve false só quando nenhum ticket corresponde ao número. */
	@Transactional
	public boolean applyIssueState(int issueNumber, String state) {
		SupportRequest request = repository.findByGithubIssueNumber(issueNumber).orElse(null);
		if (request == null) {
			LOG.warn("Webhook do GitHub para a issue #{} sem ticket de suporte correspondente", issueNumber);
			return false;
		}
		if ("closed".equalsIgnoreCase(state)) {
			if (request.status() == SupportStatus.CLOSED) {
				return true;
			}
			SupportRequest saved = repository.save(request.withStatus(SupportStatus.CLOSED));
			notifier.closed(saved);
			return true;
		}
		if ("open".equalsIgnoreCase(state)) {
			if (request.status() != SupportStatus.CLOSED) {
				return true;
			}
			SupportRequest saved = repository.save(request.withStatus(SupportStatus.OPEN));
			notifier.reopened(saved);
			return true;
		}
		return true;
	}
}
