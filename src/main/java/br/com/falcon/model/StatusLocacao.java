package br.com.falcon.model;

/** Estados persistidos do ciclo operacional; atraso é calculado, não é estado. */
public enum StatusLocacao {
  CONFIRMADA("Confirmada"),
  EM_USO("Em uso"),
  CANCELADA("Cancelada"),
  CONCLUIDA("Concluída");
  private final String rotulo;

  StatusLocacao(String r) {
    rotulo = r;
  }

  @Override
  public String toString() {
    return rotulo;
  }
}
