package br.com.falcon.model;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Canonização estrita e validação dos dois dígitos verificadores do CPF. */
public final class Cpf {
  private Cpf() {}

  public static String canonico(String valor) {
    if (valor != null) valor = valor.strip();
    if (valor == null || !valor.matches("(?:[0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2})"))
      throw new IllegalArgumentException("CPF: informe 11 dígitos ou 000.000.000-00.");
    String n = valor.replace(".", "").replace("-", "");
    if (n.chars().distinct().count() == 1)
      throw new IllegalArgumentException("CPF: sequência repetida não é válida.");
    for (int k = 9; k <= 10; k++) {
      if (digito(n, k) != n.charAt(k) - '0')
        throw new IllegalArgumentException("CPF: dígitos verificadores inválidos.");
    }
    return n;
  }

  /** Gera um número com dígitos verificadores válidos para demonstrar o formulário. */
  public static String gerarExemplo() {
    String base;
    do {
      base = String.format(Locale.ROOT, "%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
    } while (base.chars().distinct().count() == 1);
    String parcial = base + digito(base, 9);
    return parcial + digito(parcial, 10);
  }

  private static int digito(String numero, int posicao) {
    int soma = 0;
    for (int i = 0; i < posicao; i++) {
      soma += (numero.charAt(i) - '0') * (posicao + 1 - i);
    }
    int resto = (soma * 10) % 11;
    return resto == 10 ? 0 : resto;
  }

  public static String tentar(String v) {
    try {
      return canonico(v);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
