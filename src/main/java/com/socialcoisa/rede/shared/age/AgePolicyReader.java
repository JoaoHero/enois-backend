package com.socialcoisa.rede.shared.age;

/**
 * Política de idade (ECA Digital, Lei 15.211/2025) — leitura do estado para enforcement.
 *
 * Fica em {@code shared} pelo mesmo motivo do {@code SanctionGateReader}: vários módulos precisam
 * da mesma resposta (publicar, comentar, mensagens) e o perfil é quem detém a escrita.
 *
 * A data de nascimento é DECLARADA — não é verificação oficial. As proteções se aplicam mesmo
 * assim: é melhor proteger com base na declaração do que não proteger nada.
 */
public interface AgePolicyReader {

	/** O usuário já declarou a data de nascimento? Sem isso, escritas ficam bloqueadas. */
	boolean birthDateDeclared(String userId);

	/** Usuário menor de 18 anos segundo a data declarada (calculada no backend). */
	boolean isMinor(String userId);

	/** The ids among {@code userIds} that belong to minors, read in one go. */
	java.util.Set<String> minorIds(java.util.Collection<String> userIds);

	/** Menor com autorização ativa de um responsável (supervisão parental em vigor). */
	boolean parentalConsentActive(String userId);
}
