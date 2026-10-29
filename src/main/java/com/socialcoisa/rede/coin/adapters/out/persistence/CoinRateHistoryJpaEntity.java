package com.socialcoisa.rede.coin.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.coin.domain.CoinRate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Uma cotação de REFERÊNCIA da faísca em BRL (plano C7/Fase 5): o valor que a plataforma
 * usa para remunerar criadores e exibir no monitor. NÃO é câmbio — não compra faísca
 * com dinheiro nem troca P2P (isso é Fase 6/7, só com parecer jurídico).
 *
 * `brl_cents` é a leitura arredondada (compatibilidade da API); `brl_micros` é a precisão
 * real usada no cálculo automático (1 centavo = 10.000 µ).
 */
@Entity
@Table(name = "coin_rate_history")
class CoinRateHistoryJpaEntity {

	@Id
	private String id;

	@Column(name = "brl_cents", nullable = false)
	private long brlCents;

	@Column(name = "brl_micros")
	private Long brlMicros;

	@Column(length = 200)
	private String note;

	@Column(name = "effective_at", nullable = false)
	private Instant effectiveAt;

	static CoinRateHistoryJpaEntity from(CoinRate rate) {
		CoinRateHistoryJpaEntity e = new CoinRateHistoryJpaEntity();
		e.id = rate.id();
		e.brlCents = rate.brlCents();
		e.brlMicros = rate.brlMicros();
		e.note = rate.note();
		e.effectiveAt = rate.effectiveAt();
		return e;
	}

	CoinRate toDomain() {
		return CoinRate.restore(id, brlCents, brlMicros == null ? 0L : brlMicros, note, effectiveAt);
	}
}