package br.com.falcon.model;

/** As cinco categorias de frota previstas no RF01. */
public enum CategoriaVeiculo {
  PICKUP("Pickup"),
  VAN("Van"),
  CAMINHAO_MEDIO("Caminhão médio"),
  CAMINHAO_GRANDE("Caminhão grande"),
  TRAILER("Trailer");
  private final String descricao;

  CategoriaVeiculo(String descricao) {
    this.descricao = descricao;
  }

  @Override
  public String toString() {
    return descricao;
  }
}
