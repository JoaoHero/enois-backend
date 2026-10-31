package com.socialcoisa.rede.marketplace.domain;

import java.time.Instant;
import java.util.UUID;

import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Item à venda no marketplace (Fase 4): v1 vende TEMA da galeria. O preço é em faíscas
 * e a compra passa por escrow — nada de dinheiro real aqui (isso é Fase 6).
 */
public record MarketplaceListing(String id, String sellerId, ItemKind itemKind, String itemId, String title,
		String description, long price, Status status, Instant createdAt, Instant updatedAt) {

	public enum ItemKind {
		/** Tema da galeria (aponta para theme_gallery). */
		THEME,
		/** Modelo de página/post/banner com textos fáceis de trocar. */
		TEMPLATE,
		BANNER,
		PAGE,
		POST,
		BLOG,
		/** Serviço combinado fora da plataforma (o conteúdo descreve o que está incluído). */
		SERVICE
	}

	public enum Status {
		ACTIVE, SOLD, REMOVED
	}

	public static MarketplaceListing create(String sellerId, ItemKind kind, String itemId, String title,
			String description, long price, Instant now) {
		if (!StringUtils.hasText(itemId)) {
			throw new ApiException(400, "ITEM_REQUIRED", "O item à venda é obrigatório.");
		}
		if (!StringUtils.hasText(title) || title.trim().length() < 3) {
			throw new ApiException(400, "TITLE_REQUIRED", "O título precisa de pelo menos 3 caracteres.");
		}
		if (price <= 0) {
			throw new ApiException(400, "INVALID_PRICE", "O preço precisa ser positivo.");
		}
		return new MarketplaceListing("mkt_" + UUID.randomUUID().toString().replace("-", ""), sellerId, kind, itemId,
			title.trim(), description == null ? null : description.trim(), price, Status.ACTIVE, now, now);
	}

	public boolean ownedBy(String accountId) {
		return sellerId != null && sellerId.equals(accountId);
	}

	public MarketplaceListing markSold(Instant now) {
		return new MarketplaceListing(id, sellerId, itemKind, itemId, title, description, price, Status.SOLD,
			createdAt, now);
	}

	public MarketplaceListing reopen(Instant now) {
		return new MarketplaceListing(id, sellerId, itemKind, itemId, title, description, price, Status.ACTIVE,
			createdAt, now);
	}

	public MarketplaceListing remove(Instant now) {
		return new MarketplaceListing(id, sellerId, itemKind, itemId, title, description, price, Status.REMOVED,
			createdAt, now);
	}
}
