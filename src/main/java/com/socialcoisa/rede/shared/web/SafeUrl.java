package com.socialcoisa.rede.shared.web;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

/**
 * Valida links públicos informados pelo usuário (portfólio, links externos, site de empresa).
 *
 * O front renderiza esses valores em {@code href}; aceitar {@code javascript:}, {@code data:} ou
 * {@code vbscript:} transformava o perfil em vetor de XSS armazenado (E-07/E-23). Só http(s) com
 * host; nada de esquema relativo, credenciais embutidas ou controle no meio.
 */
public final class SafeUrl {

	/** Schemes a rendered link may carry. A target without scheme (relative, `#anchor`) is fine. */
	private static final Set<String> LINK_SCHEMES = Set.of("http", "https", "mailto");

	private static final Pattern SCHEME = Pattern.compile("^([a-z][a-z0-9+.\\-]*):");

	/**
	 * Markdown link or image destination, as the front's rich-text parser reads it: from `](` up to
	 * the first `)`.
	 */
	private static final Pattern MARKDOWN_DESTINATION = Pattern.compile("\\]\\(([^)]*)\\)");

	/** Reference-style markdown link definition: `[ref]: url` on its own line. */
	private static final Pattern MARKDOWN_REFERENCE = Pattern.compile("(?m)^\\s{0,3}\\[([^\\]]+)\\]:\\s+(.+)$");

	/** HTML href/src attribute value (covers `<a href="...">`, `<img src="...">`, etc). */
	private static final Pattern HTML_ATTR_URL = Pattern.compile(
		"(?i)(?:href|src|action|formaction)\\s*=\\s*[\"']([^\"']*)[\"']");

	private SafeUrl() {
	}

	/**
	 * Whether a link target is safe to render as {@code href}: no scheme, or http/https/mailto.
	 *
	 * Browsers drop whitespace and control characters inside a scheme and the front drops backslash
	 * escapes, so all of them are removed before reading the scheme.
	 */
	public static boolean isSafeLinkTarget(String target) {
		if (target == null) {
			return true;
		}
		StringBuilder normalized = new StringBuilder(target.length());
		target.codePoints()
			.filter(c -> c > ' ' && c != '\\' && !Character.isWhitespace(c) && !Character.isISOControl(c)
				&& Character.getType(c) != Character.FORMAT)
			.forEach(normalized::appendCodePoint);
		Matcher scheme = SCHEME.matcher(normalized.toString().toLowerCase(Locale.ROOT));
		return !scheme.find() || LINK_SCHEMES.contains(scheme.group(1));
	}

	/**
	 * Rejects text with a Markdown link or image whose destination has a scheme outside
	 * http/https/mailto. Applied to every string of every JSON request body (see
	 * {@link SafeLinkRequestBodyAdvice}); URL fields are checked by {@link #requireHttpUrl}.
	 */
	public static void requireSafeMarkdownLinks(String text, String field) {
		if (text == null) {
			return;
		}
		if (text.indexOf("](") >= 0) {
			Matcher destination = MARKDOWN_DESTINATION.matcher(text);
			while (destination.find()) {
				rejectUnsafe(destination.group(1), field);
			}
		}
		Matcher reference = MARKDOWN_REFERENCE.matcher(text);
		while (reference.find()) {
			rejectUnsafe(reference.group(2).trim(), field);
		}
		Matcher htmlAttr = HTML_ATTR_URL.matcher(text);
		while (htmlAttr.find()) {
			rejectUnsafe(htmlAttr.group(1), field);
		}
	}

	private static void rejectUnsafe(String target, String field) {
		if (!isSafeLinkTarget(target)) {
			throw new ApiException(400, "UNSAFE_LINK",
				"O campo " + field + " tem um link com endereco nao permitido (use http, https ou mailto).");
		}
	}

	public static String requireHttpUrl(String raw, String field) {
		if (!StringUtils.hasText(raw)) {
			return raw;
		}
		String clean = raw.trim();
		if (clean.length() > 500 || clean.chars().anyMatch(Character::isISOControl)) {
			throw invalid(field);
		}
		URI uri;
		try {
			uri = URI.create(clean);
		}
		catch (RuntimeException malformed) {
			throw invalid(field);
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!"http".equals(scheme) && !"https".equals(scheme)) {
			throw invalid(field);
		}
		if (!StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null) {
			throw invalid(field);
		}
		return clean;
	}

	public static List<String> requireHttpUrls(List<String> raw, String field) {
		if (raw == null) {
			return null;
		}
		return raw.stream().map(link -> requireHttpUrl(link, field)).toList();
	}

	/** Links kept as one text (split by {@code separator}): each part must be a valid http(s) link. */
	public static String requireHttpUrls(String joined, String separator, String field) {
		if (StringUtils.hasText(joined)) {
			for (String link : joined.split(separator)) {
				requireHttpUrl(link, field);
			}
		}
		return joined;
	}

	private static ApiException invalid(String field) {
		return new ApiException(400, "INVALID_URL",
			"O campo " + field + " precisa ser um link http(s) valido.");
	}
}
