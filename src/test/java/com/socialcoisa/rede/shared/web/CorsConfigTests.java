package com.socialcoisa.rede.shared.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class CorsConfigTests {

	private static CorsConfig config(String allowed, String trusted) {
		CorsConfig config = new CorsConfig();
		ReflectionTestUtils.setField(config, "allowedOrigins", allowed);
		ReflectionTestUtils.setField(config, "trustedOrigins", trusted);
		return config;
	}

	@ParameterizedTest
	@ValueSource(strings = { "*", "https://enois.ia.br, *", "https://*", "http://*" })
	void rejectsWildcardAllowedOrigins(String allowed) {
		assertThatThrownBy(() -> config(allowed, "https://*.b7cloud.com.br").rejectWildcardOrigins())
			.isInstanceOf(IllegalStateException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "*", "https://*" })
	void rejectsWildcardTrustedOrigins(String trusted) {
		assertThatThrownBy(() -> config("https://enois.ia.br", trusted).rejectWildcardOrigins())
			.isInstanceOf(IllegalStateException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "https://enois.ia.br", "https://enois.ia.br,http://localhost:5173" })
	void acceptsExplicitOriginsAndSubdomainPatterns(String allowed) {
		assertThatCode(() -> config(allowed, "https://*.b7cloud.com.br").rejectWildcardOrigins())
			.doesNotThrowAnyException();
	}
}
