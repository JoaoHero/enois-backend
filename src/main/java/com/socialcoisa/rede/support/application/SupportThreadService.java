package com.socialcoisa.rede.support.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.support.application.port.out.SupportMessageRepository;
import com.socialcoisa.rede.support.domain.SupportMessage;
import com.socialcoisa.rede.support.domain.SupportMessageDirection;

/**
 * Conversa do ticket espelhada no GitHub. Cada resposta do painel vira uma mensagem e vira
 * comentário na issue; cada comentário novo na issue vira uma mensagem no painel. O id do
 * comentário é a chave de deduplicação — o webhook do próprio comentário publicado pelo Énóis é
 * ignorado.
 */
@Service
public class SupportThreadService {

	private static final int MAX_BODY = 4000;

	private final SupportMessageRepository messages;
	private final Clock clock;

	public SupportThreadService(SupportMessageRepository messages, Clock clock) {
		this.messages = messages;
		this.clock = clock;
	}

	public List<SupportMessage> thread(String requestId) {
		return messages.findByRequestId(requestId);
	}

	@Transactional
	public SupportMessage addFromPanel(String requestId, String body) {
		String clean = body == null ? "" : body.trim();
		if (clean.length() > MAX_BODY) {
			clean = clean.substring(0, MAX_BODY);
		}
		SupportMessage message = new SupportMessage("support_msg_" + UUID.randomUUID().toString().replace("-", ""),
			requestId, SupportMessageDirection.ENOIS, null, null, clean, null, clock.instant());
		return messages.save(message);
	}

	/** Comentário vindo do GitHub; devolve vazio quando já foi registrado (eco do próprio Énóis). */
	@Transactional
	public Optional<SupportMessage> addFromGithub(String requestId, long commentId, String authorLogin, String body) {
		if (messages.findByGithubCommentId(commentId).isPresent()) {
			return Optional.empty();
		}
		String clean = body == null ? "" : body.trim();
		if (clean.length() > MAX_BODY) {
			clean = clean.substring(0, MAX_BODY);
		}
		String login = StringUtils.hasText(authorLogin) ? authorLogin.trim() : null;
		SupportMessage message = new SupportMessage("support_msg_" + UUID.randomUUID().toString().replace("-", ""),
			requestId, SupportMessageDirection.GITHUB, login, login, clean, commentId, clock.instant());
		return Optional.of(messages.save(message));
	}

	/** Registra o comentário publicado pelo painel para o webhook não duplicar a mensagem. */
	@Transactional
	public void markGithubComment(String messageId, long commentId, String authorName, String authorLogin) {
		// Se o webhook chegou primeiro, o comentário já virou uma mensagem GITHUB; não viola a
		// chave única nem duplica.
		if (messages.findByGithubCommentId(commentId)
			.filter(existing -> !existing.id().equals(messageId)).isPresent()) {
			return;
		}
		messages.findById(messageId).ifPresent(message -> messages.save(
			message.withGithubComment(commentId, authorName, authorLogin)));
	}
}