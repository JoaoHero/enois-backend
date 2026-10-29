package com.socialcoisa.rede.coin.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.coin.application.port.out.CoinRateRepository;
import com.socialcoisa.rede.coin.domain.CoinRate;

/**
 * A cotação de REFERÊNCIA da faísca (plano C7/Fase 5): um preço publicado em BRL que financia
 * o saque de criador pelo revenue do marketplace. A moderação/administração pode publicar
 * manualmente; o cálculo automático por uso é do {@link CoinEconomyService}. NÃO é câmbio —
 * não compra faísca.
 */
@Service
public class CoinRateService {

	private final CoinRateRepository rates;
	private final Clock clock;

	public CoinRateService(CoinRateRepository rates, Clock clock) {
		this.rates = rates;
		this.clock = clock;
	}

	/** A cotação vigente em centavos (leitura arredondada, compatível com a API antiga). */
	@Transactional
	public long currentBrlCents() {
		return latest().brlCents();
	}

	/** A cotação vigente em micro-reais — a precisão usada no cálculo e na conversão. */
	@Transactional
	public long currentBrlMicros() {
		return latest().brlMicros();
	}

	/** A cotação vigente: a última publicada; na primeira vez, semeia a inicial (R$ 0,01/faísca). */
	@Transactional
	public CoinRate latest() {
		return rates.findLatest().orElseGet(this::seedInitial);
	}

	@Transactional(readOnly = true)
	public List<CoinRate> history(int limit) {
		return rates.history(Math.min(Math.max(limit, 2), 365));
	}

	/** Publica uma nova cotação de referência em MICRO-REAIS (a linha nova vira a vigente). */
	@Transactional
	public CoinRate publish(long brlMicros, String note) {
		CoinRate rate = CoinRate.create(brlMicros, note, Instant.now(clock));
		return rates.save(rate);
	}

	/** Publicação manual legada: o painel sempre falou em centavos. */
	@Transactional
	public CoinRate publishCents(long brlCents, String note) {
		return publish(brlCents * CoinRate.MICROS_PER_CENT, note);
	}

	private CoinRate seedInitial() {
		return rates.save(CoinRate.create(CoinRate.INITIAL_BRL_MICROS, "Cotação inicial de referência",
			Instant.now(clock)));
	}
}