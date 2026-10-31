package com.socialcoisa.rede.shared.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * O arquivo de properties de cada perfil não pode repetir chave.
 *
 * Em `.properties` a ÚLTIMA ocorrência vence, e a repetição é silenciosa: o arquivo parece
 * limpo e o valor que vale é o do fim. Na QA isso aconteceu com o bloco de OAuth (o genérico com
 * `${REDE_FRONTEND_URL}` e o do sandbox com os hosts fixos) — quem editasse o bloco genérico não
 * veria efeito nenhum, e o callback do Google é justamente a configuração que já quebrou por
 * divergência de host.
 */
class ProfilePropertiesTests {

	private static final List<String> PERFIS = List.of(
		"application.properties",
		"application-dev.properties",
		"application-hml.properties",
		"application-prd.properties",
		"application-qa.properties");

	@Test
	void nenhumPerfilRepeteChave() throws IOException {
		for (String perfil : PERFIS) {
			Map<String, Integer> ocorrencias = new LinkedHashMap<>();
			for (String linha : linhasDo(perfil)) {
				String texto = linha.trim();
				if (texto.isEmpty() || texto.startsWith("#") || texto.startsWith("!")) {
					continue;
				}
				int igual = texto.indexOf('=');
				if (igual <= 0) {
					continue;
				}
				ocorrencias.merge(texto.substring(0, igual).trim(), 1, Integer::sum);
			}

			List<String> repetidas = new ArrayList<>();
			ocorrencias.forEach((chave, vezes) -> {
				if (vezes > 1) {
					repetidas.add(chave + " (" + vezes + "x)");
				}
			});

			assertEquals(List.of(), repetidas, "chave repetida no " + perfil
				+ ": em .properties a última ocorrência vence — quem editar a primeira não vê efeito");
		}
	}

	/**
	 * O callback do OAuth de cada ambiente tem de apontar para o host DELE: foi a divergência de
	 * host (o `redirect_uri_mismatch` do Google) que impediu o login em produção.
	 */
	@Test
	void oCallbackDoOAuthApontaParaODoHostDoAmbiente() throws IOException {
		Properties qa = propriedades("application-qa.properties");
		assertTrue(qa.getProperty("rede.oauth.frontend-redirect-url", "")
			.startsWith("https://enois-qa.b7cloud.tech"),
			"o callback do front da QA tem de ser o host da QA: " + qa.getProperty("rede.oauth.frontend-redirect-url"));
		assertTrue(qa.getProperty("rede.oauth.google.redirect-uri", "")
			.startsWith("https://enois-api-qa.b7cloud.tech"),
			"o callback do Google na QA tem de ser o host da API da QA: " + qa.getProperty("rede.oauth.google.redirect-uri"));

		Properties prd = propriedades("application-prd.properties");
		assertTrue(prd.getProperty("rede.oauth.frontend-redirect-url", "").startsWith("${REDE_FRONTEND_URL}"),
			"a producao usa a URL do ambiente (deploy exporta REDE_FRONTEND_URL)");
		assertTrue(prd.getProperty("rede.oauth.google.redirect-uri", "").startsWith("${REDE_BACKEND_URL}"),
			"a producao usa a URL do ambiente (deploy exporta REDE_BACKEND_URL)");
	}

	private List<String> linhasDo(String recurso) throws IOException {
		try (InputStream entrada = recurso(recurso)) {
			return List.of(new String(entrada.readAllBytes(), StandardCharsets.UTF_8).split("\\R"));
		}
	}

	private Properties propriedades(String recurso) throws IOException {
		Properties propriedades = new Properties();
		try (InputStream entrada = recurso(recurso)) {
			propriedades.load(entrada);
		}
		return propriedades;
	}

	private InputStream recurso(String nome) {
		InputStream entrada = getClass().getClassLoader().getResourceAsStream(nome);
		assertNotNull(entrada, "perfil " + nome + " nao esta no classpath");
		return entrada;
	}
}
