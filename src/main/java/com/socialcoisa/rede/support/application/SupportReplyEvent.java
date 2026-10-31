package com.socialcoisa.rede.support.application;

/** Resposta do painel publicada: espelha o texto na issue do GitHub (melhor esforço, pós-commit). */
public record SupportReplyEvent(String requestId, String messageId, String adminId, String body) {
}