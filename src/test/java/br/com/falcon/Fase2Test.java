package br.com.falcon;

import static org.junit.jupiter.api.Assertions.*;

import br.com.falcon.dao.*;
import br.com.falcon.model.*;
import br.com.falcon.service.LocadoraService;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

/** CA12–CA25 e migração, com data operacional fixa por ação. */
public class Fase2Test {
  BancoDeDados banco;
  LocadoraService s;
  Cliente cliente;
  Veiculo veiculo;

  static BigDecimal n(String v) {
    return new BigDecimal(v);
  }

  LocadoraService dia(int dia) {
    return new LocadoraService(
        banco,
        n("15"),
        n("40"),
        Clock.fixed(Instant.parse("2026-11-" + dia + "T12:00:00Z"), ZoneOffset.UTC));
  }

  @BeforeEach
  void preparar() throws Exception {
    String url = System.getProperty("falcon.it.url");
    if (url != null && !url.endsWith("/falcon_test"))
      throw new IllegalArgumentException("Banco de teste obrigatório.");
    banco =
        new BancoDeDados(
            url == null
                ? "jdbc:h2:mem:"
                    + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
                : url,
            url == null ? "sa" : System.getProperty("falcon.it.user", "root"),
            System.getProperty("falcon.it.password", ""));
    banco.inicializar();
    if (url != null)
      try (Connection c = banco.conectar();
          Statement st = c.createStatement()) {
        st.executeUpdate("DELETE FROM locacoes");
        st.executeUpdate("DELETE FROM veiculos");
        st.executeUpdate("DELETE FROM clientes");
      }
    s = dia(21);
    cliente = s.salvarCliente(new Cliente(0, "Teste", "012.345.678-90", "teste@exemplo.test"));
    veiculo = s.salvarVeiculo(v(CategoriaVeiculo.VAN));
  }

  Veiculo v(CategoriaVeiculo cat) {
    return new Veiculo(
        veiculo == null ? 0 : veiculo.idVeiculo(),
        "FAL-1",
        cat,
        3,
        n("12"),
        true,
        n("8"),
        n("150"),
        n("180"),
        true);
  }

  LocalDate d(int dia) {
    return LocalDate.of(2026, 11, dia);
  }

  Locacao reservar(int a, int b) throws Exception {
    return s.registrar(cliente.idCliente(), veiculo.idVeiculo(), d(a), d(b), "A", "B", true, true);
  }

  Locacao ler(long id) throws Exception {
    return s.locacoes().stream().filter(l -> l.idLocacao() == id).findFirst().orElseThrow();
  }

  @Test
  void CA12_cpfEstrito() {
    for (String doc : List.of("abc", "11111111111", "01234567891", "012.34567890", " 01234567890 "))
      assertThrows(
          IllegalArgumentException.class, () -> s.salvarCliente(new Cliente(0, "T", doc, "x")));
    assertEquals("01234567890", cliente.documento());
  }

  @Test
  void CA13_cpfUnicoCanonico() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> s.salvarCliente(new Cliente(0, "Outro", "01234567890", "x")));
    s.salvarCliente(new Cliente(cliente.idCliente(), "Nome editado", "01234567890", "novo"));
    assertEquals("novo", s.clientes().get(0).contato());
  }

  @Test
  void CA15_cancelamentoComMotivoPreservaConta() throws Exception {
    Locacao l = reservar(21, 23);
    assertThrows(IllegalArgumentException.class, () -> s.cancelar(l.idLocacao(), " "));
    s.cancelar(l.idLocacao(), "Cliente desistiu");
    Locacao atual = ler(l.idLocacao());
    assertEquals(StatusLocacao.CANCELADA, atual.status());
    assertNotNull(atual.canceladaEm());
    assertEquals(l.total(), atual.total());
    assertTrue(s.disponivel(veiculo.idVeiculo(), d(21), d(23)));
  }

  @Test
  void CA16_transicoesRepetidasRecusadas() throws Exception {
    Locacao l = reservar(21, 23);
    s.retirar(l.idLocacao());
    assertThrows(IllegalArgumentException.class, () -> s.cancelar(l.idLocacao(), "x"));
    assertThrows(IllegalArgumentException.class, () -> s.retirar(l.idLocacao()));
    s.devolver(l.idLocacao());
    assertThrows(IllegalArgumentException.class, () -> s.devolver(l.idLocacao()));
  }

  @Test
  void CA16_corridaRetiradaCancelamento() throws Exception {
    Locacao l = reservar(21, 23);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch inicio = new CountDownLatch(1);
    try {
      Callable<Boolean> retirada =
          () -> {
            inicio.await();
            try {
              s.retirar(l.idLocacao());
              return true;
            } catch (IllegalArgumentException e) {
              return false;
            }
          };
      Callable<Boolean> cancelar =
          () -> {
            inicio.await();
            try {
              s.cancelar(l.idLocacao(), "x");
              return true;
            } catch (IllegalArgumentException e) {
              return false;
            }
          };
      Future<Boolean> a = pool.submit(retirada), b = pool.submit(cancelar);
      inicio.countDown();
      assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void CA17_fronteiraECancelamento() throws Exception {
    Locacao l = reservar(21, 23);
    assertTrue(s.disponiveis(d(23), d(23)).isEmpty());
    assertEquals(1, s.disponiveis(d(24), d(24)).size());
    s.cancelar(l.idLocacao(), "x");
    assertEquals(1, s.disponiveis(d(23), d(23)).size());
  }

  @Test
  void CA18_ativosEPeriodoValidado() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> s.disponiveis(d(20), d(23)));
    assertThrows(IllegalArgumentException.class, () -> s.disponiveis(d(24), d(23)));
    s.inativarVeiculo(veiculo.idVeiculo());
    assertTrue(s.disponiveis(d(21), d(23)).isEmpty());
  }

  @Test
  void CA19_rechecagemNaConfirmacao() throws Exception {
    assertEquals(1, s.disponiveis(d(21), d(23)).size());
    reservar(21, 23);
    assertThrows(IllegalArgumentException.class, () -> reservar(21, 23));
    assertEquals(1, s.locacoes().size());
  }

  @Test
  void CA20_retiradaSoNoPeriodo() throws Exception {
    Locacao l = reservar(22, 24);
    assertThrows(IllegalArgumentException.class, () -> s.retirar(l.idLocacao()));
    dia(22).retirar(l.idLocacao());
    assertEquals(d(22), ler(l.idLocacao()).retiradaRealEm());
    assertThrows(IllegalArgumentException.class, () -> dia(23).retirar(l.idLocacao()));
    assertEquals(d(22), ler(l.idLocacao()).retiradaRealEm());
  }

  @Test
  void CA21_devolucaoAntecipadaMantemValor() throws Exception {
    Locacao l = reservar(21, 24);
    s.retirar(l.idLocacao());
    dia(22).devolver(l.idLocacao());
    assertEquals(l.total(), ler(l.idLocacao()).total());
    assertFalse(dia(22).disponivel(veiculo.idVeiculo(), d(22), d(22)));
    assertTrue(dia(22).disponivel(veiculo.idVeiculo(), d(23), d(23)));
  }

  @Test
  void CA22_atrasoBloqueiaAteDiaSeguinte() throws Exception {
    Locacao l = reservar(21, 23);
    s.retirar(l.idLocacao());
    assertTrue(ler(l.idLocacao()).atrasada(d(24)));
    assertTrue(dia(24).disponiveis(d(28), d(29)).isEmpty());
    assertNull(ler(l.idLocacao()).multaRegistrada());
    assertEquals(0, n("180").compareTo(ler(l.idLocacao()).estimarMulta(d(24))));
    Veiculo atualizado = new Veiculo(veiculo.idVeiculo(), veiculo.codigoFrota(),
        veiculo.categoria(), 3, n("12"), true, n("8"), n("150"), n("300"), true);
    s.salvarVeiculo(atualizado);
    dia(26).devolver(l.idLocacao(), d(25));
    Locacao concluida = ler(l.idLocacao());
    assertTrue(concluida.devolvidaComAtraso());
    assertEquals(0, n("360").compareTo(concluida.multaRegistrada()));
    assertEquals(0, n("1065").compareTo(concluida.totalFinal()));
    assertTrue(dia(25).disponivel(veiculo.idVeiculo(), d(26), d(26)));
    assertEquals(l.total(), ler(l.idLocacao()).total());
  }

  @Test
  void CA23_relatorioExcluiCanceladasEZeros() throws Exception {
    reservar(21, 21);
    Locacao cancelada = reservar(22, 22);
    s.cancelar(cancelada.idLocacao(), "x");
    s.salvarVeiculo(v(CategoriaVeiculo.PICKUP));
    reservar(23, 23);
    var map = s.demanda(YearMonth.of(2026, 11));
    assertEquals(1L, map.get(CategoriaVeiculo.VAN));
    assertEquals(1L, map.get(CategoriaVeiculo.PICKUP));
    assertEquals(0L, map.get(CategoriaVeiculo.TRAILER));
    assertEquals(
        0L, s.demanda(YearMonth.of(2026, 12)).values().stream().mapToLong(Long::longValue).sum());
  }

  @Test
  void CA24_categoriaHistoricaEReabertura() throws Exception {
    Locacao l = reservar(21, 23);
    s.salvarVeiculo(v(CategoriaVeiculo.PICKUP));
    s.retirar(l.idLocacao());
    LocadoraService outra = dia(21);
    assertEquals(StatusLocacao.EM_USO, outra.locacoes().get(0).status());
    assertEquals(CategoriaVeiculo.VAN, outra.locacoes().get(0).categoriaAplicada());
    assertEquals(l.total(), outra.locacoes().get(0).total());
  }

  @Test
  void CA25_atrasoComReservaPosterior() throws Exception {
    Locacao a = reservar(21, 23), b = reservar(24, 26);
    s.retirar(a.idLocacao());
    assertThrows(IllegalArgumentException.class, () -> dia(24).retirar(b.idLocacao()));
    dia(24).devolver(a.idLocacao());
    assertThrows(IllegalArgumentException.class, () -> dia(24).retirar(b.idLocacao()));
    dia(25).retirar(b.idLocacao());
    assertEquals(d(25), ler(b.idLocacao()).retiradaRealEm());
  }

  @Test
  void devolucaoPermitidaAposInativacao() throws Exception {
    Locacao l = reservar(21, 23);
    s.retirar(l.idLocacao());
    s.inativarVeiculo(veiculo.idVeiculo());
    s.devolver(l.idLocacao());
    assertEquals(StatusLocacao.CONCLUIDA, ler(l.idLocacao()).status());
  }

  @Test
  void falhaDepoisDaTransicaoFazRollback() throws Exception {
    Locacao l = reservar(21, 23);
    LocacaoDAO falha =
        new LocacaoDAO(banco) {
          @Override
          public void atualizarEstado(
              Connection c,
              Locacao l,
              StatusLocacao alvo,
              LocalDate dia,
              LocalDateTime hora,
              String motivo)
              throws SQLException {
            super.atualizarEstado(c, l, alvo, dia, hora, motivo);
            throw new SQLException("Falha depois do UPDATE");
          }
        };
    LocadoraService svc =
        new LocadoraService(
            banco,
            new ClienteDAO(banco),
            new VeiculoDAO(banco),
            falha,
            n("15"),
            n("40"),
            Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC));
    assertThrows(SQLException.class, () -> svc.retirar(l.idLocacao()));
    assertEquals(StatusLocacao.CONFIRMADA, ler(l.idLocacao()).status());
    assertNull(ler(l.idLocacao()).retiradaRealEm());
  }

  @Test
  void bancoRejeitaEstadoIncoerente() throws Exception {
    Locacao l = reservar(21, 23);
    try (Connection c = banco.conectar();
        Statement st = c.createStatement()) {
      assertThrows(
          SQLException.class,
          () ->
              st.executeUpdate(
                  "UPDATE locacoes SET status='CONCLUIDA' WHERE id_locacao=" + l.idLocacao()));
    }
  }

  @Test
  void devolucaoInformadaDepoisDaEntrega() throws Exception {
    Locacao l = reservar(21, 23);
    s.retirar(l.idLocacao());
    LocadoraService amanha = dia(24);
    assertFalse(amanha.oferta(d(24), d(24)).get(0).livre());
    assertEquals(
        "Em uso, sem data de retorno confirmada", amanha.oferta(d(24), d(24)).get(0).impedimento());
    assertThrows(IllegalArgumentException.class, () -> amanha.devolver(l.idLocacao(), d(20)));
    assertThrows(IllegalArgumentException.class, () -> amanha.devolver(l.idLocacao(), d(25)));
    amanha.devolver(l.idLocacao(), d(23));
    Locacao finalizada = ler(l.idLocacao());
    assertEquals(d(24), finalizada.devolucaoRegistradaEm().toLocalDate());
    assertEquals(d(23), finalizada.devolucaoRealEm());
    assertFalse(finalizada.devolvidaComAtraso());
    assertEquals(0, finalizada.multaRegistrada().signum());
    assertEquals(finalizada.total(), finalizada.totalFinal());
    assertTrue(amanha.disponivel(veiculo.idVeiculo(), d(24), d(24)));
    assertEquals(l.total(), finalizada.total());
  }

  @Test
  void movimentoIncluiReservaRetiravelEDevolucaoAtrasada() throws Exception {
    Locacao uso = reservar(21, 22), reserva = reservar(24, 26);
    assertTrue(uso.retiradaPendente(d(22)));
    assertEquals("Não retirada", uso.situacao(d(23)));
    s.retirar(uso.idLocacao());
    assertTrue(ler(uso.idLocacao()).afetaInativacao(d(24)));
    assertTrue(reserva.afetaInativacao(d(24)));
    assertTrue(ler(uso.idLocacao()).atrasada(d(24)));
    assertTrue(s.oferta(d(24), d(24)).get(0).impedimento().contains("26/11/2026"));
  }

  @Test
  void cpfComEspacosEmNovoCliente() throws Exception {
    Cliente novo = s.salvarCliente(new Cliente(0, "Pessoa", " 529.982.247-25 ", "teste"));
    assertEquals("52998224725", novo.cpfNormalizado());
    assertEquals("***.982.***-**", novo.documentoResumido());
  }

  @Test
  void demandaUsaMesDaRetiradaMesmoNaVirada() throws Exception {
    s.registrar(
        cliente.idCliente(), veiculo.idVeiculo(), d(30), d(30).plusDays(2), "A", "B", false, false);
    assertEquals(1L, s.demanda(YearMonth.of(2026, 11)).get(CategoriaVeiculo.VAN));
    assertEquals(0L, s.demanda(YearMonth.of(2026, 12)).get(CategoriaVeiculo.VAN));
  }

  @Test
  void bancoEmArquivoReabreComCadastros(@org.junit.jupiter.api.io.TempDir java.nio.file.Path pasta)
      throws Exception {
    String url = "jdbc:h2:file:" + pasta.resolve("falcon") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
    BancoDeDados primeira = new BancoDeDados(url, "sa", "");
    primeira.inicializar();
    new LocadoraService(primeira, n("15"), n("40"))
        .salvarCliente(new Cliente(0, "Arquivo", "52998224725", "contato"));
    BancoDeDados outra = new BancoDeDados(url, "sa", "");
    outra.inicializar();
    assertEquals("Arquivo", new ClienteDAO(outra).listar().get(0).nome());
  }
}
