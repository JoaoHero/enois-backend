package com.socialcoisa.rede.shared.web;

import java.io.IOException;
import java.util.Arrays;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Headers de seguranca para as respostas da API.
 *
 * A API so devolve JSON, entao uma CSP restritiva e segura — exceto nas paginas de documentacao
 * (Swagger UI), que ficam fora das diretivas quando habilitadas para uso interno.
 */
@Component
class SecurityHeadersFilter extends OncePerRequestFilter {

	private static final String SESSION_COOKIE = "enois_session";

	private static final String STRICT_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (isDocumentationPath(request.getRequestURI())) {
			response.setHeader("Content-Security-Policy", "frame-ancestors 'none'");
		}
		else {
			response.setHeader("Content-Security-Policy", STRICT_CSP);
		}
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("X-Frame-Options", "DENY");
		response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
		response.setHeader("Cross-Origin-Resource-Policy", "same-site");
		response.setHeader("Referrer-Policy", "no-referrer");
		response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
		// O trafego chega pelo ALB (HTTPS); includeSubDomains protege os subdominios do mesmo host.
		response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
		if (isAuthenticated(request)) {
			// Per-user data must not be kept by the browser or shared caches.
			response.setHeader("Cache-Control", "no-store");
		}
		filterChain.doFilter(request, response);
	}

	private boolean isAuthenticated(HttpServletRequest request) {
		if (request.getHeader("X-Session-Token") != null) {
			return true;
		}
		Cookie[] cookies = request.getCookies();
		return cookies != null && Arrays.stream(cookies).anyMatch(c -> SESSION_COOKIE.equals(c.getName()));
	}

	private boolean isDocumentationPath(String path) {
		return path.startsWith("/swagger-ui")
			|| path.startsWith("/v3/api-docs")
			|| path.endsWith("/openapi.json");
	}
}