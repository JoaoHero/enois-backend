package com.socialcoisa.rede.shared.web;

import java.util.Locale;

/**
 * The single place that turns user text into a LIKE pattern without wildcard injection.
 *
 * A search for {@code %} used to become {@code %%} and return every row. We escape {@code !},
 * {@code %} and {@code _}; every LIKE fed by user input must say {@code escape '!'}.
 */
public final class LikePattern {

	/** Escape character every LIKE query must declare ({@code escape '!'}). */
	public static final char ESCAPE = '!';

	private static final int MAX_TERM = 80;

	private LikePattern() {
	}

	/** Escapes the LIKE metacharacters so the text matches literally. Does not add wildcards. */
	public static String escape(String term) {
		// `!` instead of `\`: the SQL literal of a backslash differs between H2 and MariaDB and made
		// `escape '\'` fail silently on H2.
		return term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
	}

	/** "Contains" pattern, lower-cased and trimmed; null when the term is blank (no filter). */
	public static String contains(String raw) {
		String term = normalize(raw);
		return term == null ? null : "%" + escape(term) + "%";
	}

	/** "Starts with" pattern, lower-cased and trimmed; null when the term is blank. */
	public static String startsWith(String raw) {
		String term = normalize(raw);
		return term == null ? null : escape(term) + "%";
	}

	private static String normalize(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String term = raw.trim().toLowerCase(Locale.ROOT);
		return term.length() > MAX_TERM ? term.substring(0, MAX_TERM) : term;
	}
}
