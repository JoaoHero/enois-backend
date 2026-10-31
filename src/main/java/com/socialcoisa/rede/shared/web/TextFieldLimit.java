package com.socialcoisa.rede.shared.web;

/**
 * Limite de tamanho de um campo de texto.
 *
 * Toda coluna de texto no banco tem um teto (`varchar(160)`, `varchar(255)...). Passar dele faz o
 * INSERT falhar com "Data too long for column", que o tradutor de exceções vira **409 CONFLICT** —
 * "Operação conflita com o estado atual dos dados". A pessoa não entende nada: que conflito, com
 * qual estado? (E o log do servidor guardava o motivo, invisível para ela.)
 *
 * Validar aqui devolve **400** dizendo o campo e o teto — o mesmo número que o front mostra no
 * contador de caracteres e que a coluna aceita.
 */
public final class TextFieldLimit {

	private TextFieldLimit() {
	}

	/**
	 * @param code   código do erro do módulo (ex.: `COMPANY_FIELD_TOO_LONG`)
	 * @param field  nome do campo, como a pessoa o conhece
	 * @param value  valor a conferir (nulo passa)
	 * @param max    teto, igual ao da coluna
	 */
	public static void check(String code, String field, String value, int max) {
		if (value != null && value.length() > max) {
			throw new ApiException(400, code,
				"O campo " + field + " aceita no máximo " + milhar(max) + " caracteres.");
		}
	}

	/** 2000 vira "2.000" — é como o número aparece no app (e a pessoa lê melhor assim). */
	private static String milhar(int numero) {
		return String.format("%,d", numero).replace(',', '.');
	}
}
