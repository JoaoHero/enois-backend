package com.socialcoisa.rede.games.application;

import java.util.Locale;

import com.socialcoisa.rede.games.domain.GameDistribution;
import com.socialcoisa.rede.games.domain.GameStage;

/**
 * O que a pessoa pediu na busca do catálogo. Campos vazios não filtram nada.
 *
 * `order` é uma lista FECHADA (`recent`, `plays`, `title`) — ordenar por coluna vinda da URL seria
 * injeção de SQL de graça, e "mais votado por robô" não existe aqui: só entra o que o servidor conta.
 */
public record GameSearchCriteria(String query, String genre, String tag, GameStage stage,
		GameDistribution distribution, String order) {

	private static final int MAX_TERM = 80;

	public static GameSearchCriteria of(String query, String genre, String tag, GameStage stage,
			GameDistribution distribution, String order) {
		return new GameSearchCriteria(clean(query, MAX_TERM), clean(genre, MAX_TERM), clean(tag, MAX_TERM), stage,
			distribution, normalizeOrder(order));
	}

	/** O termo vira padrão de `LIKE` em minúsculas, com `%` e `_` escapados (o usuário não é curinga). */
	public String queryPattern() {
		return pattern(query);
	}

	public String genrePattern() {
		return pattern(genre);
	}

	public String tagPattern() {
		return pattern(tag);
	}

	private static String pattern(String term) {
		if (term == null) {
			return null;
		}
		return "%" + com.socialcoisa.rede.shared.web.LikePattern.escape(term.toLowerCase(Locale.ROOT)) + "%";
	}

	private static String clean(String value, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
	}

	/** Estágio vindo da URL: vazio vira nulo (não filtra) e valor desconhecido é 400. */
	public static GameStage stageOrNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return GameStage.valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			throw new com.socialcoisa.rede.shared.web.ApiException(400, "INVALID_STAGE", "Estágio inválido.");
		}
	}

	public static GameDistribution distributionOrNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return GameDistribution.valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			throw new com.socialcoisa.rede.shared.web.ApiException(400, "INVALID_DISTRIBUTION",
				"Modelo de distribuição inválido.");
		}
	}

	private static String normalizeOrder(String order) {
		if (order == null) {
			return "recent";
		}
		String value = order.trim().toLowerCase(Locale.ROOT);
		return switch (value) {
			case "plays", "title" -> value;
			default -> "recent";
		};
	}
}
