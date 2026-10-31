package com.socialcoisa.rede.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.socialcoisa.rede.shared.security.ClientIpResolver;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class RequestObservabilityFilterTests {

	private final RequestObservabilityFilter filter = new RequestObservabilityFilter(new ClientIpResolver(),
		org.mockito.Mockito.mock(com.socialcoisa.rede.ops.monitoring.MonitoringService.class));
	private final ListAppender<ILoggingEvent> audit = new ListAppender<>();

	@BeforeEach
	void attachAuditAppender() {
		audit.start();
		((Logger) LoggerFactory.getLogger("audit")).addAppender(audit);
	}

	@AfterEach
	void detachAuditAppender() {
		((Logger) LoggerFactory.getLogger("audit")).detachAppender(audit);
	}

	@Test
	void exposesRequestContextInMdcDuringTheRequestAndClearsItAfter() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/feed");
		// The left-most entry is whatever the client sent; the hop our (trusted, loopback) proxy appended
		// is the real client address, and that is what the logs must carry.
		request.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.7");
		MockHttpServletResponse response = new MockHttpServletResponse();
		Map<String, String> seen = new HashMap<>();

		filter.doFilter(request, response, (req, res) -> {
			seen.putAll(MDC.getCopyOfContextMap());
			MDC.put(RequestObservabilityFilter.MDC_USER_ID, "user_1");
		});

		assertThat(seen).containsEntry(RequestObservabilityFilter.MDC_CLIENT_IP, "203.0.113.7").containsKey("requestId");
		assertThat(response.getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER)).isEqualTo(seen.get("requestId"));
		assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
		assertThat(audit.list).isEmpty();
	}

	@Test
	void auditsAdminMutationsWithTheActor() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/reports/r1/ban");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response,
			(req, res) -> MDC.put(RequestObservabilityFilter.MDC_USER_ID, "admin_1"));

		assertThat(audit.list).singleElement()
			.satisfies(event -> assertThat(event.getFormattedMessage()).startsWith("admin_action actor=admin_1"));
	}

	@Test
	void doesNotAuditAdminReads() throws Exception {
		filter.doFilter(new MockHttpServletRequest("GET", "/admin/reports"), new MockHttpServletResponse(),
			(req, res) -> {
			});

		assertThat(audit.list).isEmpty();
	}
}
