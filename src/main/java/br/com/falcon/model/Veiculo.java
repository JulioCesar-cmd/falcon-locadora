package br.com.falcon.model;

import java.math.BigDecimal;

/** Estado cadastral de um veículo; disponibilidade depende também das datas solicitadas. */
public record Veiculo(
    long idVeiculo,
    String codigoFrota,
    CategoriaVeiculo categoria,
    int maxPassageiros,
    BigDecimal volumeMaximoM3,
    boolean arCondicionado,
    BigDecimal consumoMedioKmL,
    BigDecimal potenciaCv,
    BigDecimal valorDiaria,
    boolean ativo) {
  @Override
  public String toString() {
    return codigoFrota + " · " + categoria;
  }
}
