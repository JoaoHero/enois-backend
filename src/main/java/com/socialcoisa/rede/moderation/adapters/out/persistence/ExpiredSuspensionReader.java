package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.time.Instant;
import java.util.List;

import com.socialcoisa.rede.moderation.domain.UserSanction;

/** Leitura das suspensoes vencidas para o fechamento automatico da graca. */
public interface ExpiredSuspensionReader {

	List<UserSanction> expiredActive(Instant cutoff, int limit);
}
