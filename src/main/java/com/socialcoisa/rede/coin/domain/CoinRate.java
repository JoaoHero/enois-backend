package com.socialcoisa.rede.coin.domain;

import java.time.Instant;
import java.util.UUID;

import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * A cotação de REFERÊNCIA da faísca (plano C7/Fase 5): quanto vale 1 faísca em BRL — o preço
 * publicado que financia o saque de criador pelo revenue do marketplace. NÃO é câmbio nem
 * bolsa de ativos: não compra faísca com dinheiro.
 *
 * <p>O valor é guardado em MICRO-REAIS por faísca (1 centavo = 10.000 µ) porque a cotação
 * precisa andar em passos pequenos (1% de 1 centavo é 100 µ); inteiro em centavos só permitiria
 * saltos de 100%. `brlCents` continua sendo a leitura arredondada para quem já consome a API.</p>
 */
public record CoinRate(String id, long brlCents, long brlMicros, String note, Instant effectiveAt) {

	/** Micro-reais por centavo: 1 centavo = 0,01 BRL = 10.000 µBRL. */
	public static final long MICROS_PER_CENT = 10_000L;

	/** Cotação inicial de referência: 100 faíscas = R$ 1,00 (1 centavo por faísca). */
	public static final long INITIAL_BRL_CENTS = 1L;
	public static final long INITIAL_BRL_MICROS = INITIAL_BRL_CENTS * MICROS_PER_CENT;

	public static CoinRate create(long brlMicros, String note, Instant now) {
		if (brlMicros <= 0) {
			throw new ApiException(400, "INVALID_RATE", "A cotação precisa ser positiva.");
		}
		return new CoinRate("rate_" + UUID.randomUUID().toString().replace("-", ""), toCents(brlMicros),
			brlMicros, StringUtils.hasText(note) ? note.trim() : null, now);
	}

	public static CoinRate restore(String id, long brlCents, long brlMicros, String note, Instant effectiveAt) {
		long micros = brlMicros > 0 ? brlMicros : brlCents * MICROS_PER_CENT;
		return new CoinRate(id, toCents(micros), micros, note, effectiveAt);
	}

	/** Centavos com arredondamento comercial; nunca 0 (a cotação mínima é 1 centavo). */
	private static long toCents(long brlMicros) {
		return Math.max(1L, (brlMicros + MICROS_PER_CENT / 2) / MICROS_PER_CENT);
	}
}