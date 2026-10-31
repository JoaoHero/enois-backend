package com.socialcoisa.rede.moderation.domain;

public enum SanctionType {
	RESTRICTION,
	/** Suspensão temporária com prazo de reversão: bloqueia como o BAN, mas nasce com
	 data de revisão. Expirado o prazo sem recurso aceito, vira BAN definitivo e os
	 dados entram na fila de purga. */
	SUSPENDED,
	BAN,
	/** Banimento permanente por decisao de admin: como o BAN, e sem caminho de recurso. */
	PERMABAN
}
