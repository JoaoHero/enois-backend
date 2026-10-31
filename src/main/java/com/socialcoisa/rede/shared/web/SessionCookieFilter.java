package com.socialcoisa.rede.shared.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Resolves the browser session from the HttpOnly cookie and exposes it as {@code X-Session-Token},
 * so every controller, interceptor and rate limiter keeps reading the same header. An explicit
 * header (non-browser clients) always wins and is untouched.
 *
 * <p>Cookies travel on their own, so a write authenticated by cookie must also carry
 * {@code X-CSRF-Protection: 1}: a custom header forces a CORS preflight, which only the app's
 * allowlisted origins pass. Runs after the origin gate and before the rate limiters.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 6)
class SessionCookieFilter extends OncePerRequestFilter {

	static final String SESSION_HEADER = "X-Session-Token";
	static final String CSRF_HEADER = "X-CSRF-Protection";
	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (StringUtils.hasText(request.getHeader(SESSION_HEADER))) {
			chain.doFilter(request, response);
			return;
		}
		String session = cookie(request, SessionCookies.SESSION);
		boolean cookieAuth = session != null || cookie(request, SessionCookies.REFRESH) != null;
		if (cookieAuth && !SAFE_METHODS.contains(request.getMethod().toUpperCase(java.util.Locale.ROOT))
				&& !"1".equals(request.getHeader(CSRF_HEADER))) {
			response.setStatus(403);
			response.setHeader("Cache-Control", "no-store");
			TrustedOriginCors.apply(request, response);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write("{\"code\":\"CSRF_REQUIRED\",\"message\":\"Requisicao recusada. "
				+ "Recarregue a pagina e tente de novo.\"}");
			return;
		}
		chain.doFilter(session == null ? request : new SessionHeaderRequest(request, session), response);
	}

	private static String cookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	/** Adds the session header taken from the cookie. */
	private static final class SessionHeaderRequest extends HttpServletRequestWrapper {

		private final String token;

		SessionHeaderRequest(HttpServletRequest request, String token) {
			super(request);
			this.token = token;
		}

		@Override
		public String getHeader(String name) {
			return SESSION_HEADER.equalsIgnoreCase(name) ? token : super.getHeader(name);
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			return SESSION_HEADER.equalsIgnoreCase(name) ? Collections.enumeration(List.of(token))
				: super.getHeaders(name);
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
			names.add(SESSION_HEADER);
			return Collections.enumeration(names);
		}
	}
}
