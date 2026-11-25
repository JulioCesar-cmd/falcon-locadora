package br.com.falcon;

import static org.junit.jupiter.api.Assertions.*;

import br.com.falcon.dao.*;
import br.com.falcon.model.*;
import br.com.falcon.service.LocadoraService;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Migra uma base realmente no esquema original, incluindo colisões canônicas. */
public class MigracaoTest {
  @Test
  void CA14_migracaoPreservaVinculosELegados() throws Exception {
    String url = System.getProperty("falcon.it.url");
    if (url != null && !url.endsWith("/falcon_test"))
      throw new IllegalArgumentException("Banco isolado obrigatório");
    BancoDeDados b =
        new BancoDeDados(
            url == null
                ? "jdbc:h2:mem:"
                    + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
                : url.replace("/falcon_test", "/falcon_migration_test"),
            url == null ? "sa" : System.getProperty("falcon.it.user", "root"),
            System.getProperty("falcon.it.password", ""));
    Path backups = Files.createTempDirectory("falcon-migracao-teste");
    String anterior = System.getProperty("falcon.backup.dir");
    System.setProperty("falcon.backup.dir", backups.toString());
    try {
      try (Connection c = b.conectar();
          Statement st = c.createStatement()) {
        for (String t : List.of("locacoes", "clientes", "veiculos", "falcon_schema"))
          st.execute("DROP TABLE IF EXISTS " + t);
        String schema =
            new String(
                getClass().getResourceAsStream("/schema-v1.sql").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        for (String sql : schema.split(";")) if (!sql.isBlank()) st.execute(sql);
        st.executeUpdate(
            "INSERT INTO clientes VALUES(11,'CPF"
                + " único','012.345.678-90','a'),(12,'Legado','RG-01','b'),(13,'Colisão"
                + " A','529.982.247-25','c'),(14,'Colisão B','52998224725','d')");
        st.executeUpdate("INSERT INTO veiculos VALUES(21,'MIG-1','VAN',3,12,true,8,150,180,true)");
        st.executeUpdate(
            "INSERT INTO locacoes"
                + " VALUES(31,12,21,'2000-11-21','2000-11-23','A','B',true,true,180,15,40)");
      }
      b.inicializar();
      b.inicializar();
      LocadoraService s =
          new LocadoraService(
              b,
              new BigDecimal("15"),
              new BigDecimal("40"),
              Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC));
      assertEquals(4, s.clientes().size());
      assertEquals(1, s.clientes().stream().filter(c -> !c.legado()).count());
      assertEquals(
          "01234567890",
          s.clientes().stream()
              .filter(c -> c.idCliente() == 11)
              .findFirst()
              .orElseThrow()
              .documento());
      Locacao l = s.locacoes().get(0);
      assertEquals(31, l.idLocacao());
      assertEquals(12, l.idCliente());
      assertEquals(21, l.idVeiculo());
      assertEquals(0, new BigDecimal("705").compareTo(l.total()));
      assertEquals(StatusLocacao.CONFIRMADA, l.status());
      assertEquals(CategoriaVeiculo.VAN, l.categoriaAplicada());
      assertNull(l.retiradaRealEm());
      assertThrows(
          IllegalArgumentException.class,
          () -> s.salvarCliente(new Cliente(0, "Duplicado", "52998224725", "e")));
      Cliente legado = s.salvarCliente(new Cliente(12, "Legado editado", "RG-01", "contato novo"));
      assertTrue(legado.legado());
      s.registrar(
          12, 21, LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 24), "A", "B", false, false);
      assertEquals(2, s.locacoes().size());
      s.designarTitular(13);
      assertThrows(IllegalArgumentException.class, () -> s.designarTitular(14));
      Cliente titular =
          s.clientes().stream().filter(v -> v.idCliente() == 13).findFirst().orElseThrow();
      assertEquals("529.982.247-25", titular.documento());
      assertEquals("52998224725", titular.cpfNormalizado());
      s.salvarCliente(new Cliente(13, "Titular", titular.documento(), "novo contato"));
      assertTrue(s.salvarCliente(new Cliente(14, "Outro", "52998224725", "contato")).legado());
      assertFalse(s.salvarCliente(new Cliente(14, "Outro", "11144477735", "contato")).legado());
      assertTrue(l.origemMigracao());
      assertTrue(l.historico());
      assertFalse(l.bloqueia(l.dataRetirada(), l.dataDevolucao(), l.dataRetirada()));
      assertThrows(IllegalArgumentException.class, () -> s.cancelar(31, "sem evidência"));
      String relatorio = Files.readString(backups.resolve("migracao-fase2-pendencias.txt"));
      assertFalse(relatorio.contains("52998224725"));
      assertFalse(relatorio.contains("RG-01"));
      try (var arquivos = Files.list(backups)) {
        assertTrue(arquivos.anyMatch(p -> p.toString().endsWith(".sql")));
      }
    } finally {
      if (anterior == null) System.clearProperty("falcon.backup.dir");
      else System.setProperty("falcon.backup.dir", anterior);
    }
  }
}
