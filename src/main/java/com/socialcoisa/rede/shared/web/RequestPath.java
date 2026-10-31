package com.socialcoisa.rede.shared.web;

import java.nio.charset.StandardCharsets;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.UriUtils;

import jakarta.servlet.http.HttpServletRequest;

/** Leitura consistente do caminho pelos filtros e pela protecao administrativa. */
public final class RequestPath {

	private RequestPath() {
	}

	/** Mantem a normalizacao de separadores usada pelos guards, sem reescrever o request. */
	public static String applicationPath(HttpServletRequest request) {
		String path = routingPath(request);
		while (path.contains("//")) {
			path = path.replace("//", "/");
		}
		return path;
	}

	/** Mesma representacao por segmentos utilizada no matching do Spring MVC. */
	public static PathContainer segments(HttpServletRequest request) {
		return PathContainer.parsePath(rawPath(request), PathContainer.Options.HTTP_PATH);
	}

	private static String rawPath(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (path == null) {
			return "/";
		}
		String context = request.getContextPath();
		return context != null && !context.isEmpty() && path.startsWith(context)
			? path.substring(context.length()) : path;
	}

	/** Decodifica uma vez, preservando separadores para nao inventar uma rota registrada. */
	public static String routingPath(HttpServletRequest request) {
		return UriUtils.decode(rawPath(request), StandardCharsets.UTF_8);
	}
}
