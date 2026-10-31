package com.socialcoisa.rede.importlinkedin.domain;

/**
 * Estado de um contato importado do LinkedIn.
 *
 * <p>QUEUED é quem já tem correspondência no Énóis e espera o envio (o convite automático ligado
 * ou o próximo ciclo do worker). Os demais são estados finais do ponto de vista do convite.</p>
 */
public enum LinkedinInviteStatus {
	QUEUED, INVITED, PENDING, ALREADY_CONNECTED, NO_MATCH, AMBIGUOUS, SKIPPED
}