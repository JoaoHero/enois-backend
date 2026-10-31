package com.socialcoisa.rede.feed.domain;

/**
 * Estado da nota de rodapé: só a aprovada aparece no post; a negada pesa na credibilidade.
 * DISCARDED é a nota que perdeu a vez quando outra foi aprovada no mesmo post — não pesa.
 */
public enum PostNoteStatus {
	PENDING, APPROVED, DENIED, DISCARDED
}