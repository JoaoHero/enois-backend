package com.socialcoisa.rede.contributions.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.socialcoisa.rede.contributions.domain.SealType;
import com.socialcoisa.rede.contributions.domain.UserSeal;

public interface UserSealRepository {

	UserSeal save(UserSeal seal);

	Optional<UserSeal> findByUserAndType(String userId, SealType type);

	List<UserSeal> findActiveByUser(String userId);

	/** Todos os selos ativos, para o painel de contribuintes verificado. */
	List<UserSeal> findAllActive();

	/** IDs de quem tem pelo menos um selo ativo — usado em lote nas projeções do feed. */
	Set<String> findActiveUserIds(Collection<String> userIds);

	/** Se a pessoa tem algum selo ativo (checagem pontual, ex.: resposta de publicação). */
	boolean hasActiveSeal(String userId);
}
