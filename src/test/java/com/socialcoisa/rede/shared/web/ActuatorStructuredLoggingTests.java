package com.socialcoisa.rede.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Regressao de DOIS incidentes reais de prod distintos que produziam o MESMO sintoma (Actuator
 * respondendo HTTP 200 com corpo vazio):
 *
 * <p><b>Causa 1 (corrigida no PR #180):</b> {@link RequestObservabilityFilter} escrevia
 * "http.method"/"http.path" tanto via MDC quanto via {@code addKeyValue} na MESMA linha de log do
 * access log; o encoder ECS do Spring Boot (structured logging nativo) lancava
 * {@code IllegalStateException: Duplicate nested pairs added under 'http.method'} quando o mesmo
 * campo aninhado chegava duas vezes no mesmo evento, corrompendo a escrita da resposta em
 * andamento. Corrigida removendo os {@code addKeyValue} redundantes.
 *
 * <p><b>Causa 2 (causa raiz real do sintoma, achada depois de validar o PR #180 em prod via SSH
 * direto e ver que o corpo continuava vazio):</b> {@link FallbackSurfaceFilter} so deixa um
 * caminho passar para o resto da aplicacao quando {@link RouteSurface#conhecido(String)} diz que
 * o roteador conhece aquele caminho. {@code RouteSurface} so indexava o bean
 * {@code RequestMappingHandlerMapping} (controllers {@code @RequestMapping} da aplicacao) — o
 * Actuator registra {@code /actuator/**} num bean IRMAO ({@code WebMvcEndpointHandlerMapping},
 * mesmo ancestral {@code RequestMappingInfoHandlerMapping}, bean diferente), que nunca entrava
 * nesse indice. Resultado: {@code /actuator/prometheus} era tratado EXATAMENTE como uma rota
 * inexistente — 200, corpo vazio, sem {@code Content-Type}, sem nenhum header de
 * {@link SecurityHeadersFilter} (que roda depois na cadeia e nunca era alcancado). Confirmado em
 * prod que uma URL aleatoria sem nenhuma rota (ex. {@code /totally-random-nonexistent-path-xyz})
 * respondia byte a byte igual ao Actuator. Corrigido ampliando {@code RouteSurface} para indexar
 * todo {@code RequestMappingInfoHandlerMapping} do contexto, nao so o da aplicacao.
 *
 * <p>Este teste liga a mesma combinacao de prod (perfil prd-like: ECS + endpoints extras) e
 * verifica que o Actuator responde com corpo real, nao so HTTP 200 — e que um caminho
 * genuinamente inexistente continua recebendo a resposta padronizada em branco (a protecao do
 * {@code FallbackSurfaceFilter} nao foi desligada, so corrigida para conhecer o Actuator).
 */
@SpringBootTest(properties = {
	"springdoc.api-docs.enabled=false",
	"springdoc.swagger-ui.enabled=false",
	"management.endpoints.web.exposure.include=health,prometheus,metrics",
	// Mesmo formato ligado em prd/hml/qa: e o encoder que lancava a exception de campo duplicado.
	"logging.structured.format.console=ecs",
})
@AutoConfigureMockMvc
class ActuatorStructuredLoggingTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void prometheusRespondsWithRealMetricsBodyNotEmpty() throws Exception {
		MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
			.andExpect(status().isOk())
			.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).isNotBlank();
		// Formato de exposicao do Prometheus: toda metrica real vem com pelo menos um comentario HELP/TYPE.
		assertThat(body).contains("# HELP");
	}

	@Test
	void metricsIndexRespondsWithRealBodyNotEmpty() throws Exception {
		MvcResult result = mockMvc.perform(get("/actuator/metrics")).andExpect(status().isOk()).andReturn();

		assertThat(result.getResponse().getContentAsString()).isNotBlank();
	}

	@Test
	void healthRespondsWithRealBodyNotEmpty() throws Exception {
		MvcResult result = mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn();

		assertThat(result.getResponse().getContentAsString()).isNotBlank();
	}

	@Test
	void actuatorIndexRespondsWithRealBodyNotEmpty() throws Exception {
		MvcResult result = mockMvc.perform(get("/actuator")).andExpect(status().isOk()).andReturn();

		assertThat(result.getResponse().getContentAsString()).isNotBlank();
	}

	@Test
	void unknownPathStillGetsTheStandardizedBlankResponse() throws Exception {
		// Controle negativo: o fix ampliou RouteSurface para conhecer o Actuator, mas nao pode ter
		// desligado a protecao do FallbackSurfaceFilter para caminhos que de fato nao existem.
		MvcResult result = mockMvc.perform(get("/totally-random-nonexistent-path-xyz")).andExpect(status().isOk())
			.andReturn();

		assertThat(result.getResponse().getContentAsString()).isEmpty();
	}
}
