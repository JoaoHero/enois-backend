package com.socialcoisa.rede.shared.security;

/**
 * Portão de cadastro contra o padrão de ataque conhecido (as contas "probe" de 20/10 e 22/10).
 *
 * <p>Fica em {@code shared} pelo mesmo motivo do {@link IpBlockReader}: a identidade precisa
 * barrar o cadastro e a moderação é dona da regra. The implementation refuses the signup and, after
 * repeated hits, pauses signups from that IP for a while (never login, never a permanent block).
 */
public interface SignupAttackGuard {

	/**
	 * Lança 403 SIGNUP_REFUSED quando o cadastro bate no padrão de ataque or the IP is in its
	 * signup cooldown. Cadastro normal não é tocado.
	 */
	void checkSignup(String name, String email, String ip);
}
