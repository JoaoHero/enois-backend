package com.socialcoisa.rede.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

class SecurityHeadersFilterTests {

	private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		new SecurityHeadersFilter().doFilter(request, response, new MockFilterChain());
		return response;
	}

	@Test
	void setsIsolationHeadersOnEveryResponse() throws Exception {
		MockHttpServletResponse response = run(new MockHttpServletRequest("GET", "/posts"));
		assertThat(response.getHeader("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
		assertThat(response.getHeader("Cross-Origin-Resource-Policy")).isEqualTo("same-site");
		assertThat(response.getHeader("Cache-Control")).isNull();
	}

	@Test
	void authenticatedByHeaderIsNotCached() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
		request.addHeader("X-Session-Token", "token");
		assertThat(run(request).getHeader("Cache-Control")).isEqualTo("no-store");
	}

	@Test
	void authenticatedByCookieIsNotCached() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
		request.setCookies(new Cookie("enois_session", "token"));
		assertThat(run(request).getHeader("Cache-Control")).isEqualTo("no-store");
	}
}
