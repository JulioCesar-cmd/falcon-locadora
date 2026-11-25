package br.com.falcon.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Contrato com tarifas registradas na reserva. As datas previstas são inclusivas.
 *
 * @param multaRegistrada valor apurado na devolução, inclusive zero quando no prazo.
 *     Fica nulo antes da devolução ou em encerramentos anteriores ao controle de multas.
 */
public record Locacao(
    long idLocacao,
    long idCliente,
    long idVeiculo,
    LocalDate dataRetirada,
    LocalDate dataDevolucao,
    String localRetirada,
    String localDevolucao,
    boolean gpsSelecionado,
    boolean seguroSelecionado,
    BigDecimal diariaVeiculoAplicada,
    BigDecimal diariaGpsAplicada,
    BigDecimal diariaSeguroAplicada,
    StatusLocacao status,
    CategoriaVeiculo categoriaAplicada,
    LocalDate retiradaRealEm,
    LocalDate devolucaoRealEm,
    java.time.LocalDateTime canceladaEm,
    String motivoCancelamento,
    java.time.LocalDateTime devolucaoRegistradaEm,
    boolean origemMigracao,
    boolean historico,
    BigDecimal multaRegistrada) {
  public Locacao(
      long id,
      long cliente,
      long veiculo,
      LocalDate inicio,
      LocalDate fim,
      String retirada,
      String devolucao,
      boolean gps,
      boolean seguro,
      BigDecimal diaria,
      BigDecimal tarifaGps,
      BigDecimal tarifaSeguro) {
    this(
        id,
        cliente,
        veiculo,
        inicio,
        fim,
        retirada,
        devolucao,
        gps,
        seguro,
        diaria,
        tarifaGps,
        tarifaSeguro,
        StatusLocacao.CONFIRMADA,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        false,
        null);
  }

  public boolean atrasada(LocalDate hoje) {
    return status == StatusLocacao.EM_USO && dataDevolucao.isBefore(hoje);
  }

  public boolean retiradaPendente(LocalDate dia) {
    return !historico
        && status == StatusLocacao.CONFIRMADA
        && !dia.isBefore(dataRetirada)
        && !dia.isAfter(dataDevolucao);
  }

  public boolean afetaInativacao(LocalDate dia) {
    return !historico
        && (status == StatusLocacao.EM_USO
            || (status == StatusLocacao.CONFIRMADA && !dataDevolucao.isBefore(dia)));
  }

  public String situacao(LocalDate dia) {
    if (historico) return "Histórico anterior";
    if (atrasada(dia)) return "Em uso · Atrasada";
    if (status == StatusLocacao.CONFIRMADA && dataDevolucao.isBefore(dia)) return "Não retirada";
    return status.toString();
  }

  public boolean devolvidaComAtraso() {
    return devolucaoRealEm != null && devolucaoRealEm.isAfter(dataDevolucao);
  }

  /**
   * Confere se a data informada pode ser registrada para uma locação em uso.
   *
   * @param efetiva dia em que o veículo foi entregue
   * @param hoje dia do registro
   * @throws IllegalArgumentException se a devolução for futura ou anterior à retirada
   */
  public void validarDataDevolucao(LocalDate efetiva, LocalDate hoje) {
    if (efetiva.isAfter(hoje))
      throw new IllegalArgumentException("A devolução não pode ser futura.");
    if (efetiva.isBefore(retiradaRealEm))
      throw new IllegalArgumentException("Devolução anterior à retirada real.");
  }

  /** Dias corridos após o prazo, com zero para uma entrega no prazo. */
  public long diasDeAtraso(LocalDate devolucao) {
    return Math.max(0, ChronoUnit.DAYS.between(dataDevolucao, devolucao));
  }

  /** RN21. A multa usa a diária contratada do veículo por dia corrido de atraso. */
  public BigDecimal estimarMulta(LocalDate devolucao) {
    return diariaVeiculoAplicada.multiply(BigDecimal.valueOf(diasDeAtraso(devolucao)));
  }

  /** Soma o valor contratado à multa gravada. Contas antigas mantêm o valor contratado. */
  public BigDecimal totalFinal() {
    return multaRegistrada == null ? total() : total().add(multaRegistrada);
  }

  public boolean bloqueia(LocalDate inicio, LocalDate fim, LocalDate hoje) {
    if (historico || status == StatusLocacao.CANCELADA) return false;
    LocalDate limite =
        status == StatusLocacao.CONCLUIDA ? devolucaoRealEm : atrasada(hoje) ? null : dataDevolucao;
    return !dataRetirada.isAfter(fim) && (limite == null || !limite.isBefore(inicio));
  }

  /** RN02: conta ambas as datas; uma locação no mesmo dia vale uma diária. */
  public long quantidadeDiarias() {
    return ChronoUnit.DAYS.between(dataRetirada, dataDevolucao) + 1;
  }

  public BigDecimal subtotalVeiculo() {
    return diariaVeiculoAplicada.multiply(BigDecimal.valueOf(quantidadeDiarias()));
  }

  public BigDecimal subtotalGps() {
    return diariaGpsAplicada.multiply(BigDecimal.valueOf(quantidadeDiarias()));
  }

  public BigDecimal subtotalSeguro() {
    return diariaSeguroAplicada.multiply(BigDecimal.valueOf(quantidadeDiarias()));
  }

  /** RN04/RN05: usa somente as tarifas guardadas nesta locação. */
  public BigDecimal total() {
    return subtotalVeiculo().add(subtotalGps()).add(subtotalSeguro());
  }
}
