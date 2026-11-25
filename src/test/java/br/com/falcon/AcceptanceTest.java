package br.com.falcon;

import static org.junit.jupiter.api.Assertions.*;

import br.com.falcon.dao.*;
import br.com.falcon.model.*;
import br.com.falcon.service.LocadoraService;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

/** Aceitação das regras RN01–RN07 usando JDBC real e H2 em modo MySQL. */
public class AcceptanceTest {
  BancoDeDados banco;
  LocadoraService service;
  Cliente cliente;
  Veiculo veiculo;
  final LocalDate a = LocalDate.of(2026, 11, 21), b = LocalDate.of(2026, 11, 23);

  static BigDecimal n(String s) {
    return new BigDecimal(s);
  }

  @BeforeEach
  void preparar() throws Exception {
    String real = System.getProperty("falcon.it.url");
    if (real != null && !real.endsWith("/falcon_test"))
      throw new IllegalArgumentException(
          "A integração exige um banco isolado chamado falcon_test.");
    banco =
        new BancoDeDados(
            real == null
                ? "jdbc:h2:mem:"
                    + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
                : real,
            real == null ? "sa" : System.getProperty("falcon.it.user", "root"),
            System.getProperty("falcon.it.password", ""));
    banco.inicializar();
    if (real != null)
      try (Connection c = banco.conectar();
          Statement st = c.createStatement()) {
        st.executeUpdate("DELETE FROM locacoes");
        st.executeUpdate("DELETE FROM veiculos");
        st.executeUpdate("DELETE FROM clientes");
      }
    service =
        new LocadoraService(
            banco,
            n("15.00"),
            n("40.00"),
            java.time.Clock.fixed(
                java.time.Instant.parse("2026-11-20T12:00:00Z"), java.time.ZoneOffset.UTC));
    cliente =
        service.salvarCliente(
            new Cliente(0, "Cliente de teste", "01234567890", "contato@exemplo.test"));
    veiculo = service.salvarVeiculo(v(0, "FAL-001", true, n("180.00")));
  }

  Veiculo v(long id, String codigo, boolean ativo, BigDecimal diaria) {
    return new Veiculo(
        id, codigo, CategoriaVeiculo.VAN, 3, n("12"), true, n("8.5"), n("150"), diaria, ativo);
  }

  Locacao reservar(LocalDate x, LocalDate y, boolean gps, boolean seguro) throws Exception {
    return service.registrar(
        cliente.idCliente(),
        veiculo.idVeiculo(),
        x,
        y,
        "Manaus · Centro",
        "Manaus · Flores",
        gps,
        seguro);
  }

  @Test
  void CA01_codigoUnico() throws Exception {
    assertThrows(SQLException.class, () -> service.salvarVeiculo(v(0, "FAL-001", true, n("100"))));
    assertEquals(1, service.veiculos().size());
  }

  @Test
  void CA02_alterarDiaria() throws Exception {
    service.salvarVeiculo(v(veiculo.idVeiculo(), "FAL-001", true, n("200")));
    assertEquals(0, n("200").compareTo(service.veiculos().get(0).valorDiaria()));
  }

  @Test
  void CA03_inativacaoPreservaHistorico() throws Exception {
    reservar(a, b, true, true);
    service.inativarVeiculo(veiculo.idVeiculo());
    assertFalse(service.veiculos().get(0).ativo());
    assertEquals(1, service.locacoes().size());
    assertThrows(
        IllegalArgumentException.class, () -> reservar(b.plusDays(1), b.plusDays(2), false, false));
  }

  @Test
  void CA04_documentoUnicoEContatoEditavel() throws Exception {
    service.salvarCliente(
        new Cliente(cliente.idCliente(), cliente.nome(), cliente.documento(), "novo contato"));
    assertEquals("novo contato", service.clientes().get(0).contato());
    assertEquals("01234567890", service.clientes().get(0).documento());
    assertThrows(
        IllegalArgumentException.class,
        () -> service.salvarCliente(new Cliente(0, "Outro", cliente.documento(), "x")));
  }

  @Test
  void CA05_mesmoDiaEDataInvertida() throws Exception {
    assertEquals(1, reservar(a, a, false, false).quantidadeDiarias());
    assertThrows(IllegalArgumentException.class, () -> reservar(b, a, false, false));
    assertEquals(1, service.locacoes().size());
  }

  @Test
  void CA06_bloqueiaSobreposicoesInclusiveBorda() throws Exception {
    reservar(a, b, false, false);
    assertThrows(
        IllegalArgumentException.class, () -> reservar(a.plusDays(1), b.plusDays(1), false, false));
    assertThrows(IllegalArgumentException.class, () -> reservar(b, b, false, false));
    assertThrows(IllegalArgumentException.class, () -> reservar(a.minusDays(1), a, false, false));
    assertEquals(1, service.locacoes().size());
  }

  @Test
  void CA07_outroPeriodoEOutroVeiculo() throws Exception {
    reservar(a, b, false, false);
    reservar(b.plusDays(1), b.plusDays(1), false, false);
    Veiculo outro = service.salvarVeiculo(v(0, "FAL-002", true, n("100")));
    service.registrar(cliente.idCliente(), outro.idVeiculo(), a, b, "A", "B", false, false);
    assertEquals(3, service.locacoes().size());
  }

  @Test
  void CA08_combinacoesDeOpcionais() throws Exception {
    String[] valores = {"540", "585", "660", "705"};
    for (int i = 0; i < 4; i++) {
      LocalDate inicio = a.plusDays(i * 5L);
      Locacao l = reservar(inicio, inicio.plusDays(2), (i & 1) != 0, (i & 2) != 0);
      assertEquals(0, n(valores[i]).compareTo(l.total()));
      assertEquals(3, l.quantidadeDiarias());
    }
  }

  @Test
  void CA09_tarifasPersistidas() throws Exception {
    Locacao l = reservar(a, b, true, true);
    service.salvarVeiculo(v(veiculo.idVeiculo(), "FAL-001", true, n("999")));
    LocadoraService reiniciado = new LocadoraService(banco, n("100"), n("100"));
    Locacao recuperada = reiniciado.locacoes().get(0);
    assertEquals(l.idLocacao(), recuperada.idLocacao());
    assertEquals(0, n("705").compareTo(recuperada.total()));
    assertEquals(1, reiniciado.clientes().size());
  }

  @Test
  void CA11_falhaAposInsertGeraRollback() throws Exception {
    LocacaoDAO falha =
        new LocacaoDAO(banco) {
          @Override
          public long inserir(Connection c, Locacao l) throws SQLException {
            super.inserir(c, l);
            throw new SQLException("Falha simulada após INSERT");
          }
        };
    LocadoraService s =
        new LocadoraService(
            banco,
            new ClienteDAO(banco),
            new VeiculoDAO(banco),
            falha,
            n("15"),
            n("40"),
            java.time.Clock.fixed(
                java.time.Instant.parse("2026-11-20T12:00:00Z"), java.time.ZoneOffset.UTC));
    assertThrows(
        SQLException.class,
        () -> s.registrar(cliente.idCliente(), veiculo.idVeiculo(), a, b, "A", "B", true, true));
    assertEquals(0, service.locacoes().size());
    assertTrue(service.disponivel(veiculo.idVeiculo(), a, b));
  }

  @Test
  void confirmacaoRepeteConsultaAnterior() throws Exception {
    assertTrue(service.disponivel(veiculo.idVeiculo(), a, b));
    reservar(a, b, false, false);
    assertThrows(IllegalArgumentException.class, () -> reservar(a, b, false, false));
  }

  @Test
  void duasConfirmacoesSimultaneasSoUmaVence() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch inicio = new CountDownLatch(1);
    Callable<Boolean> tentar =
        () -> {
          inicio.await();
          try {
            reservar(a, b, true, true);
            return true;
          } catch (IllegalArgumentException e) {
            return false;
          }
        };
    try {
      Future<Boolean> f1 = executor.submit(tentar), f2 = executor.submit(tentar);
      inicio.countDown();
      assertNotEquals(f1.get(10, TimeUnit.SECONDS), f2.get(10, TimeUnit.SECONDS));
      assertEquals(1, service.locacoes().size());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void dadosInvalidosNaoGeramLocacao() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.registrar(999, veiculo.idVeiculo(), a, b, "A", "B", false, false));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.registrar(cliente.idCliente(), 999, a, b, "A", "B", false, false));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.registrar(
                cliente.idCliente(), veiculo.idVeiculo(), a, b, " ", "B", false, false));
    assertEquals(0, service.locacoes().size());
  }

  @Test
  void trailerSemMotorAceitaZero() throws Exception {
    Veiculo t =
        new Veiculo(
            0, "TR-1", CategoriaVeiculo.TRAILER, 0, n("20"), false, n("0"), n("0"), n("99"), true);
    assertNotNull(service.salvarVeiculo(t));
    assertThrows(
        IllegalArgumentException.class, () -> service.salvarVeiculo(v(0, "NEG", true, n("0"))));
  }

  @Test
  void rejeitaPrecisaoMonetariaSemArredondamentoSilencioso() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.salvarVeiculo(v(0, "PREC", true, n("180.001"))));
  }
}
