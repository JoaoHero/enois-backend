package com.socialcoisa.rede.apiv1;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.servlet.http.HttpServletRequest;

/**
 * O índice da versão: <code>GET /api/v1</code> diz o que existe na v1.
 *
 * <p>A lista de endpoints é montada a partir do roteador de verdade ({@code RequestMappingHandlerMapping}),
 * não de uma lista escrita à mão: quando uma rota entra no backend, ela aparece aqui sozinha, e
 * quando sai, some daqui. A página de desenvolvedores do app promete "documenta só o que existe" —
 * este índice é o mesmo compromisso, do lado do servidor.
 */
@RestController
@RequestMapping("/v1")
class V1ApiController {

	private final RequestMappingHandlerMapping mappings;

	V1ApiController(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings) {
		this.mappings = mappings;
	}

	@GetMapping
	V1Index index(HttpServletRequest request) {
		String context = request.getContextPath() == null ? "" : request.getContextPath();
		return new V1Index(V1AliasFilter.VERSION, context + V1AliasFilter.PREFIX,
			"Alias versionado: o caminho e a resposta sao os mesmos do caminho sem versao. "
				+ "O formato do erro e {code, message, timestamp}. "
				+ "Este indice e o contrato legivel por maquina: a API nao publica documento OpenAPI "
				+ "(nao existe /openapi.json); a documentacao legivel esta na pagina /developers do app.",
			endpoints());
	}

	/** Todos os caminhos registrados no roteador, com o metodo HTTP de cada um. */
	private List<V1Endpoint> endpoints() {
		List<V1Endpoint> endpoints = new ArrayList<>();
		for (var entry : mappings.getHandlerMethods().entrySet()) {
			var info = entry.getKey();
			var condicao = info.getMethodsCondition().getMethods();
			List<String> metodos = condicao.isEmpty()
				? List.of("ANY")
				: condicao.stream().map(Object::toString).sorted().toList();
			for (String pattern : info.getPatternValues()) {
				if (pattern.equals(V1AliasFilter.PREFIX) || pattern.startsWith("/actuator")
					|| pattern.startsWith("/v3/api-docs") || pattern.startsWith("/swagger-ui")) {
					continue;
				}
				for (String method : metodos) {
					endpoints.add(new V1Endpoint(method, pattern));
				}
			}
		}
		endpoints.sort(Comparator.comparing(V1Endpoint::path).thenComparing(V1Endpoint::method));
		return endpoints;
	}

	/** Um endpoint do índice: método e caminho (a forma que o app já usa na página de desenvolvedores). */
	record V1Endpoint(String method, String path) {
	}

	/** O índice da versão. */
	record V1Index(String version, String base, String note, List<V1Endpoint> endpoints) {
	}
}
