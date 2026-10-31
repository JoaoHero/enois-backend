package com.socialcoisa.rede.shared.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import com.socialcoisa.rede.shared.security.ClientIpResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * One access log line per request plus the MDC every other log line inherits (requestId, clientIp,
 * and userId once {@link CurrentUserResolver} authenticates). Runs right after Spring's observation
 * filter (HIGHEST_PRECEDENCE + 1, which opens the trace) and before the rate limiters (@Order 1/2),
 * so 429s are logged and carry traceId.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestObservabilityFilter extends OncePerRequestFilter {

	static final String REQUEST_ID_HEADER = "X-Request-Id";
	public static final String MDC_CLIENT_IP = "clientIp";
	static final String MDC_USER_ID = "userId";
	static final String MDC_DEVICE_ID = "deviceId";
	/** ID aleatório gerado pelo app (localStorage), NÃO o MAC — ver docs/LOGGING.md. */
	private static final String DEVICE_HEADER = "X-Device-Id";

	private static final int MAX_USER_AGENT = 200;
	/** Acima disso a requisição vira um WARN próprio (o access log normal é INFO). */
	private static final long SLOW_REQUEST_MS = 2000;

	private static final Logger LOGGER = LoggerFactory.getLogger(RequestObservabilityFilter.class);
	// Separate logger name so admin actions can be queried/retained on their own: {logger="audit"}.
	private static final Logger AUDIT = LoggerFactory.getLogger("audit");

	private final ClientIpResolver clientIpResolver;
	private final com.socialcoisa.rede.ops.monitoring.MonitoringService monitoring;

	RequestObservabilityFilter(ClientIpResolver clientIpResolver,
			com.socialcoisa.rede.ops.monitoring.MonitoringService monitoring) {
		this.clientIpResolver = clientIpResolver;
		this.monitoring = monitoring;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = requestId(request);
		String clientIp = clientIpResolver.resolve(request);
		long startedAt = System.nanoTime();
		MDC.put("requestId", requestId);
		MDC.put(MDC_CLIENT_IP, clientIp);
		// Caminho e método no MDC: qualquer log emitido durante a requisição (inclusive erro do
		// handler global) chega ao CloudWatch já correlacionado, sem repetir esses campos à mão.
		MDC.put("http.method", request.getMethod());
		MDC.put("http.path", request.getRequestURI());
		String deviceId = deviceId(request);
		if (deviceId != null) {
			MDC.put(MDC_DEVICE_ID, deviceId);
		}
		response.setHeader(REQUEST_ID_HEADER, requestId);
		boolean tracked = !isProbe(request.getRequestURI())
			&& !request.getRequestURI().startsWith("/admin/monitoring");
		if (tracked) {
			try {
				monitoring.requestStarted();
			}
			catch (RuntimeException ignored) {
				// Métrica não pode impedir a requisição.
			}
		}
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
			String method = request.getMethod();
			String route = route(request);
			int status = response.getStatus();
			String userId = MDC.get(MDC_USER_ID);
			// Health probes do Kubernetes batem a cada 10s: em INFO eles inundariam (e encareceriam)
			// o CloudWatch sem dizer nada sobre o produto. Continuam disponíveis em DEBUG.
			boolean probe = isProbe(request.getRequestURI());
			boolean clientDisconnected = Boolean.TRUE
				.equals(request.getAttribute(GlobalExceptionHandler.CLIENT_DISCONNECTED_ATTRIBUTE));
			var event = clientDisconnected || probe ? LOGGER.atDebug()
				: status >= 500 ? LOGGER.atError() : LOGGER.atInfo();
			// http.method/http.path NAO entram via addKeyValue aqui: eles ja estao no MDC (acima) e o
			// encoder estruturado (ECS) os inclui automaticamente nesta mesma linha. Repeti-los colidia
			// com o MDC sob o mesmo campo aninhado e o encoder lancava "IllegalStateException: Duplicate
			// nested pairs added under 'http.method'" em todo request — em prod isso corrompia a escrita
			// da resposta no meio do processamento (HTTP 200 com corpo vazio), mais visivel no Actuator
			// (/actuator/prometheus, /actuator/health, /actuator/metrics, /actuator) por serem os
			// endpoints com corpo maior e commit mais tardio do que os endpoints normais da API.
			safeLog(() -> event.addKeyValue("http.route", route)
				.addKeyValue("http.status", status)
				.addKeyValue("durationMs", durationMs)
				.addKeyValue("userAgent", userAgent(request))
				.addKeyValue("deviceId", deviceId)
				.log("http_request requestId={} method={} path={} status={} durationMs={} clientIp={} userId={} deviceId={}",
					requestId, method, request.getRequestURI(), status, durationMs, clientIp, userId, deviceId));
			if (!clientDisconnected && !probe && durationMs >= SLOW_REQUEST_MS) {
				safeLog(() -> LOGGER.atWarn()
					.addKeyValue("http.route", route)
					.addKeyValue("http.status", status)
					.addKeyValue("durationMs", durationMs)
					.log("slow_request requestId={} method={} route={} status={} durationMs={}", requestId, method,
						route, status, durationMs));
			}
			// Monitorabilidade: métricas de toda requisição (exceto a própria tela de monitoração,
			// que inflaria o gráfico com o polling do painel) e evento em 4xx/5xx.
			if (tracked) {
				try {
					if (clientDisconnected) {
						monitoring.recordClientDisconnect();
					}
					else {
						monitoring.recordRequest(method, request.getRequestURI(), route, status, durationMs, requestId,
							userId, clientIp, deviceId);
					}
				}
				catch (RuntimeException ignored) {
					// Nunca derruba a requisição real por causa da monitoração.
				}
			}
			if (isAdminMutation(method, request.getRequestURI())) {
				AUDIT.atInfo()
					.addKeyValue("audit.action", method + " " + route)
					.addKeyValue("audit.path", request.getRequestURI())
					.addKeyValue("audit.status", status)
					.log("admin_action actor={} action={} {} path={} status={}", userId, method, route,
						request.getRequestURI(), status);
			}
			MDC.remove("requestId");
			MDC.remove(MDC_CLIENT_IP);
			MDC.remove(MDC_USER_ID);
			MDC.remove("http.method");
			MDC.remove("http.path");
			MDC.remove(MDC_DEVICE_ID);
		}
	}

	/**
	 * Escrever o access log nunca pode comprometer a resposta real. Isto existe por causa de um
	 * incidente real: um encoder estruturado (ECS) lancando {@code IllegalStateException} no meio do
	 * {@code finally} desta classe corrompia a escrita do corpo da resposta ja em andamento (ver
	 * comentario acima, no ponto de uso). Qualquer falha de logging — essa ou futura — deve ficar
	 * contida aqui, nunca propagar pelo filtro.
	 */
	private static void safeLog(Runnable logStatement) {
		try {
			logStatement.run();
		}
		catch (RuntimeException ex) {
			LOGGER.warn("access_log_write_failed {}", ex.toString());
		}
	}

	/** Route template (/admin/reports/{reportId}): bounded cardinality for grouping, unlike the raw path. */
	private static String route(HttpServletRequest request) {
		Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		return pattern != null ? pattern.toString() : "UNMATCHED";
	}

	// ponytail: path-based audit covers every /admin/** mutation in one place; per-action audit with
	// target/before/after needs explicit calls in the admin services.
	private static boolean isAdminMutation(String method, String uri) {
		return uri.contains("/admin/") && !"GET".equals(method) && !"HEAD".equals(method)
			&& !"OPTIONS".equals(method);
	}

	/** Header opcional do app; valor inválido é ignorado (não derruba a requisição). */
	private static String deviceId(HttpServletRequest request) {
		String value = request.getHeader(DEVICE_HEADER);
		return value != null && value.matches("^[A-Za-z0-9._-]{16,64}$") ? value : null;
	}

	/** Health probes e actuator: não entram no access log em INFO (ver {@link #SLOW_REQUEST_MS}). */
	private static boolean isProbe(String uri) {
		return uri.equals("/livez") || uri.equals("/readyz")
			|| uri.startsWith("/actuator/health");
	}

	/** Client-controlled: truncated and stripped of control chars so it cannot forge log lines. */
	private static String userAgent(HttpServletRequest request) {
		String value = request.getHeader("User-Agent");
		if (value == null) {
			return null;
		}
		String clean = value.replaceAll("\\p{Cntrl}", "");
		return clean.length() > MAX_USER_AGENT ? clean.substring(0, MAX_USER_AGENT) : clean;
	}

	private String requestId(HttpServletRequest request) {
		String header = request.getHeader(REQUEST_ID_HEADER);
		// O valor vem do cliente e vai para header de resposta e para o log: aceita apenas um
		// identificador seguro para nao permitir injecao de cabecalho/log.
		if (header != null && header.matches("^[A-Za-z0-9._-]{8,64}$")) {
			return header;
		}
		return UUID.randomUUID().toString().replace("-", "");
	}
}
