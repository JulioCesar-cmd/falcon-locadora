package br.com.falcon.service;

import br.com.falcon.dao.*;
import br.com.falcon.model.*;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Fachada de aplicação: valida entradas e coordena UC01 a UC05, independentemente do Swing. */
public class LocadoraService {
  private Clock clock = Clock.systemDefaultZone();

  /** Data de atendimento usada para validar períodos, retiradas e devoluções. */
  public LocalDate hoje() {
    return LocalDate.now(clock);
  }

  /** Configura as tarifas dos opcionais e as dependências usadas para registrar contratos. */
  public LocadoraService(BancoDeDados b, BigDecimal g, BigDecimal s, Clock clock) {
    this(b, g, s);
    this.clock = Objects.requireNonNull(clock);
  }

  private final BancoDeDados banco;
  private final ClienteDAO clientes;
  private final VeiculoDAO veiculos;
  private final LocacaoDAO locacoes;
  private final BigDecimal gps, seguro;

  /** Configura as tarifas dos opcionais e as dependências usadas para registrar contratos. */
  public LocadoraService(BancoDeDados banco, BigDecimal gps, BigDecimal seguro) {
    this(banco, new ClienteDAO(banco), new VeiculoDAO(banco), new LocacaoDAO(banco), gps, seguro);
  }

  /** Configura as tarifas dos opcionais e as dependências usadas para registrar contratos. */
  public LocadoraService(
      BancoDeDados b,
      ClienteDAO c,
      VeiculoDAO v,
      LocacaoDAO l,
      BigDecimal g,
      BigDecimal seg,
      Clock clock) {
    this(b, c, v, l, g, seg);
    this.clock = clock;
  }

  /** Cria o serviço com os DAOs fornecidos. */
  public LocadoraService(
      BancoDeDados banco,
      ClienteDAO clientes,
      VeiculoDAO veiculos,
      LocacaoDAO locacoes,
      BigDecimal gps,
      BigDecimal seguro) {
    this.banco = banco;
    this.clientes = clientes;
    this.veiculos = veiculos;
    this.locacoes = locacoes;
    this.gps = dinheiro(gps, false);
    this.seguro = dinheiro(seguro, false);
  }

  /** Consulta os cadastros disponíveis para selecionar o titular de uma reserva. */
  public List<Cliente> clientes() throws SQLException {
    return clientes.listar();
  }

  /** Consulta a frota, incluindo veículos que saíram da oferta. */
  public List<Veiculo> veiculos() throws SQLException {
    return veiculos.listar();
  }

  /** Recupera os contratos com suas tarifas originais e eventos de atendimento. */
  public List<Locacao> locacoes() throws SQLException {
    return locacoes.listar();
  }

  /** Diária de GPS usada na confirmação de novas reservas. */
  public BigDecimal tarifaGps() {
    return gps;
  }

  /** Diária de seguro completo usada na confirmação de novas reservas. */
  public BigDecimal tarifaSeguro() {
    return seguro;
  }

  /** Valida os campos de identificação e contato e solicita a gravação do cadastro. */
  public Cliente salvarCliente(Cliente v) throws SQLException {
    return clientes.salvar(
        new Cliente(
            v.idCliente(),
            texto(v.nome(), 120, "Nome"),
            texto(v.documento(), 40, "Documento"),
            texto(v.contato(), 160, "Contato")));
  }

  /** Associa o CPF pendente ao cadastro escolhido pelo atendente. */
  public void designarTitular(long id) throws SQLException {
    clientes.designarTitular(id);
  }

  /** Valida capacidade, consumo, potência e diária antes de gravar o veículo. */
  public Veiculo salvarVeiculo(Veiculo v) throws SQLException {
    if (v.categoria() == null) throw new IllegalArgumentException("Escolha a categoria.");
    if (v.maxPassageiros() < 0)
      throw new IllegalArgumentException("Passageiros não pode ser negativo.");
    decimal(v.volumeMaximoM3(), "Volume");
    decimal(v.consumoMedioKmL(), "Consumo");
    decimal(v.potenciaCv(), "Potência");
    return veiculos.salvar(
        new Veiculo(
            v.idVeiculo(),
            texto(v.codigoFrota(), 40, "Código da frota").toUpperCase(Locale.ROOT),
            v.categoria(),
            v.maxPassageiros(),
            v.volumeMaximoM3(),
            v.arCondicionado(),
            v.consumoMedioKmL(),
            v.potenciaCv(),
            dinheiro(v.valorDiaria(), true),
            v.ativo()));
  }

  /** Retira o veículo das novas reservas e preserva os contratos existentes. */
  public void inativarVeiculo(long id) throws SQLException {
    veiculos.inativar(id);
  }

  private static void decimal(BigDecimal v, String campo) {
    if (v == null
        || v.signum() < 0
        || v.compareTo(new BigDecimal("999999999.999")) > 0
        || v.stripTrailingZeros().scale() > 3)
      throw new IllegalArgumentException(
          campo + ": informe de zero até 999.999.999,999, com no máximo três casas.");
  }

  private static BigDecimal dinheiro(BigDecimal v, boolean positivo) {
    if (v == null
        || v.signum() < 0
        || (positivo && v.signum() == 0)
        || v.compareTo(new BigDecimal("9999999999.99")) > 0)
      throw new IllegalArgumentException("Tarifa inválida.");
    try {
      return v.setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("Tarifas devem ter até duas casas decimais.");
    }
  }

  private static String texto(String s, int max, String nome) {
    if (s == null || s.isBlank() || s.strip().length() > max)
      throw new IllegalArgumentException(nome + ": preencha até " + max + " caracteres.");
    return s.strip();
  }

  private static void periodo(LocalDate inicio, LocalDate fim) {
    if (inicio == null || fim == null || fim.isBefore(inicio))
      throw new IllegalArgumentException("A devolução deve ser igual ou posterior à retirada.");
    if (inicio.getYear() < 1000
        || fim.getYear() > 9999
        || ChronoUnit.DAYS.between(inicio, fim) > 3650)
      throw new IllegalArgumentException(
          "Informe datas válidas e um período de até 3.651 diárias.");
  }

  /** Consulta informativa. A confirmação repete a validação sob bloqueio transacional. */
  public boolean disponivel(long id, LocalDate inicio, LocalDate fim) throws SQLException {
    periodo(inicio, fim);
    LocalDate operacao = hoje();
    if (inicio.isBefore(operacao))
      throw new IllegalArgumentException("A retirada não pode ser anterior a hoje.");
    return veiculos.listar().stream().anyMatch(v -> v.idVeiculo() == id && v.ativo())
        && !conflito(id, inicio, fim, operacao);
  }

  private boolean conflito(long id, LocalDate inicio, LocalDate fim, LocalDate dia)
      throws SQLException {
    try (Connection c = banco.conectar()) {
      return locacoes.haConflito(c, id, inicio, fim, dia);
    }
  }

  /**
   * UC04: bloqueia o veículo, consulta conflitos atuais, registra tarifas e confirma tudo em uma
   * transação.
   */
  public Locacao registrar(
      long cliente,
      long veiculo,
      LocalDate inicio,
      LocalDate fim,
      String retirada,
      String devolucao,
      boolean usarGps,
      boolean usarSeguro)
      throws SQLException {
    periodo(inicio, fim);
    LocalDate operacao = hoje();
    if (inicio.isBefore(operacao))
      throw new IllegalArgumentException("A retirada não pode ser anterior a hoje.");
    retirada = texto(retirada, 160, "Local de retirada");
    devolucao = texto(devolucao, 160, "Local de devolução");
    try (Connection c = banco.conectar()) {
      c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      c.setAutoCommit(false);
      try {
        Veiculo v = veiculos.bloquear(c, veiculo);
        if (v == null || !v.ativo())
          throw new IllegalArgumentException("Veículo inexistente ou inativo.");
        if (clientes.buscar(c, cliente) == null)
          throw new IllegalArgumentException("Selecione um cliente cadastrado.");
        if (locacoes.haConflito(c, veiculo, inicio, fim, operacao))
          throw new IllegalArgumentException(
              "Veículo indisponível no período informado, incluindo retirada e devolução.");
        Locacao l =
            new Locacao(
                0,
                cliente,
                veiculo,
                inicio,
                fim,
                retirada,
                devolucao,
                usarGps,
                usarSeguro,
                v.valorDiaria(),
                usarGps ? gps : new BigDecimal("0.00"),
                usarSeguro ? seguro : new BigDecimal("0.00"),
                StatusLocacao.CONFIRMADA,
                v.categoria(),
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                null);
        long id = locacoes.inserir(c, l);
        Locacao gravada = locacoes.buscar(c, id);
        c.commit();
        return gravada;
      } catch (SQLException | RuntimeException e) {
        try {
          c.rollback();
        } catch (SQLException erro) {
          e.addSuppressed(erro);
        }
        throw e;
      }
    }
  }

  /** Lista e confirmação compartilham a política de intervalos da locação. */
  public List<Veiculo> disponiveis(LocalDate inicio, LocalDate fim) throws SQLException {
    return oferta(inicio, fim).stream()
        .filter(OpcaoVeiculo::livre)
        .map(OpcaoVeiculo::veiculo)
        .toList();
  }

  /** Veículo consultado para o período e motivo de bloqueio, quando houver. */
  public record OpcaoVeiculo(Veiculo veiculo, String impedimento) {
    /** Indica se o veículo pode ser selecionado no período consultado. */
    public boolean livre() {
      return impedimento.isEmpty();
    }
  }

  /** Lista os veículos ativos e o motivo de eventual indisponibilidade no período inclusivo. */
  public List<OpcaoVeiculo> oferta(LocalDate inicio, LocalDate fim) throws SQLException {
    periodo(inicio, fim);
    LocalDate dia = hoje();
    if (inicio.isBefore(dia))
      throw new IllegalArgumentException("A retirada não pode ser anterior a hoje.");
    Map<Long, List<Locacao>> impedimentos = new HashMap<>();
    for (Locacao l : locacoes.listar())
      if (l.bloqueia(inicio, fim, dia))
        impedimentos.computeIfAbsent(l.idVeiculo(), k -> new ArrayList<>()).add(l);
    List<OpcaoVeiculo> resultado = new ArrayList<>();
    for (Veiculo v : veiculos.listar())
      if (v.ativo()) {
        List<Locacao> conflitos = impedimentos.getOrDefault(v.idVeiculo(), List.of());
        String motivo = "";
        if (conflitos.stream().anyMatch(l -> l.atrasada(dia)))
          motivo = "Em uso, sem data de retorno confirmada";
        else if (!conflitos.isEmpty()) {
          LocalDate ultimo =
              conflitos.stream()
                  .map(l -> l.devolucaoRealEm() == null ? l.dataDevolucao() : l.devolucaoRealEm())
                  .max(LocalDate::compareTo)
                  .orElseThrow();
          motivo =
              "Ocupado até "
                  + ultimo.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu"));
        }
        resultado.add(new OpcaoVeiculo(v, motivo));
      }
    return resultado;
  }

  /** Conta os contratos por categoria gravada e mês da retirada prevista, excluindo cancelamentos. */
  public Map<CategoriaVeiculo, Long> demanda(YearMonth mes) throws SQLException {
    Map<CategoriaVeiculo, Long> resultado = new EnumMap<>(CategoriaVeiculo.class);
    for (CategoriaVeiculo cat : CategoriaVeiculo.values()) resultado.put(cat, 0L);
    for (Locacao l : locacoes.listar())
      if (l.status() != StatusLocacao.CANCELADA && YearMonth.from(l.dataRetirada()).equals(mes))
        resultado.merge(l.categoriaAplicada(), 1L, Long::sum);
    return resultado;
  }

  /** Cancela uma reserva confirmada e grava o motivo informado. */
  public void cancelar(long id, String motivo) throws SQLException {
    transicao(id, StatusLocacao.CANCELADA, texto(motivo, 500, "Motivo do cancelamento"), null);
  }

  /** Registra a retirada no dia da operação, após conferir o estado e a ocupação do veículo. */
  public void retirar(long id) throws SQLException {
    transicao(id, StatusLocacao.EM_USO, null, null);
  }

  /** Registra a devolução no dia da operação. */
  public void devolver(long id) throws SQLException {
    transicao(id, StatusLocacao.CONCLUIDA, null, null);
  }

  /**
   * Encerra a locação em uso e grava a multa calculada pela data efetiva da devolução.
   * A data e a hora do lançamento são registradas separadamente.
   *
   * @param id código da locação
   * @param efetiva dia em que o veículo foi efetivamente devolvido
   * @throws IllegalArgumentException se a situação ou a data não permitir a devolução
   * @throws SQLException se a atualização do banco falhar
   */
  public void devolver(long id, LocalDate efetiva) throws SQLException {
    if (efetiva == null) throw new IllegalArgumentException("Informe a data efetiva.");
    transicao(id, StatusLocacao.CONCLUIDA, null, efetiva);
  }

  private void transicao(long id, StatusLocacao alvo, String motivo, LocalDate efetiva)
      throws SQLException {
    LocalDateTime instante = LocalDateTime.now(clock);
    LocalDate dia = instante.toLocalDate();
    try (Connection c = banco.conectar()) {
      c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      c.setAutoCommit(false);
      try {
        Locacao antes = locacoes.buscar(c, id);
        if (antes == null) throw new IllegalArgumentException("Locação não encontrada.");
        Veiculo v = veiculos.bloquear(c, antes.idVeiculo());
        Locacao l = locacoes.buscar(c, id);
        if (l.historico())
          throw new IllegalArgumentException(
              "Histórico anterior disponível somente para consulta.");
        if (alvo == StatusLocacao.CONCLUIDA) {
          if (l.status() != StatusLocacao.EM_USO)
            throw new IllegalArgumentException("Somente uma locação em uso pode ser devolvida.");
          if (efetiva == null) efetiva = dia;
          l.validarDataDevolucao(efetiva, dia);
        } else {
          if (l.status() != StatusLocacao.CONFIRMADA)
            throw new IllegalArgumentException(
                "A ação exige uma locação confirmada e ainda não retirada. Atualize a lista.");
          if (alvo == StatusLocacao.EM_USO) {
            if (!v.ativo())
              throw new IllegalArgumentException(
                  "Veículo inativo. Reative o cadastro antes de registrar a retirada.");
            if (dia.isBefore(l.dataRetirada()) || dia.isAfter(l.dataDevolucao()))
              throw new IllegalArgumentException(
                  "A retirada deve ocorrer hoje, dentro do período previsto.");
            for (Locacao outra : locacoes.listar(c))
              if (outra.idVeiculo() == l.idVeiculo()
                  && outra.idLocacao() != id
                  && (outra.status() == StatusLocacao.EM_USO
                      || dia.equals(outra.devolucaoRealEm())))
                throw new IllegalArgumentException(
                    "Veículo em uso ou devolvido hoje por outra locação. Confira o histórico.");
          }
        }
        locacoes.atualizarEstado(c, l, alvo, efetiva == null ? dia : efetiva, instante, motivo);
        c.commit();
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e;
      }
    }
  }
}
