package br.com.falcon.dao;

import br.com.falcon.model.Cpf;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Migração retomável. MariaDB confirma DDL implicitamente; o backup precede qualquer alteração. */
public final class Migracao {
  private Migracao() {}

  /** Consulta a versão do esquema e aplica as migrações pendentes na conexão informada. */
  public static void executar(Connection c) throws SQLException {
    try (Statement s = c.createStatement()) {
      s.execute("CREATE TABLE IF NOT EXISTS falcon_schema(id INT PRIMARY KEY,versao INT NOT NULL)");
      int versao;
      try (ResultSet r = s.executeQuery("SELECT versao FROM falcon_schema WHERE id=1")) {
        versao = r.next() ? r.getInt(1) : 0;
      }
      if (versao >= 2) {
        if (versao == 2) atualizar21(c);
        if (versao < 4) atualizarMulta(c);
        return;
      }
      try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM falcon_schema WHERE id=1")) {
        r.next();
        if (r.getInt(1) == 0) s.executeUpdate("INSERT INTO falcon_schema VALUES(1,1)");
      }
    }
    List<String> integridade = integridade(c);
    Path dir = Path.of(System.getProperty("falcon.backup.dir", "dados/backups"));
    try {
      Files.createDirectories(dir);
      Path backup =
          dir.resolve(
              "antes-fase2-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".sql");
      StringBuilder sql =
          new StringBuilder(
              "-- Backup lógico antes da fase 2. Contém dados pessoais: mantenha privado.\n"
                  + "-- Restaurar apenas sobre cópia com esquema compatível, nunca sobre o banco em"
                  + " uso.\n");
      for (String tabela : List.of("clientes", "veiculos", "locacoes"))
        try (Statement s = c.createStatement();
            ResultSet r = s.executeQuery("SELECT * FROM " + tabela)) {
          ResultSetMetaData m = r.getMetaData();
          while (r.next()) {
            sql.append("INSERT INTO ").append(tabela).append("(");
            for (int i = 1; i <= m.getColumnCount(); i++)
              sql.append(i == 1 ? "" : ",").append(m.getColumnName(i));
            sql.append(") VALUES(");
            for (int i = 1; i <= m.getColumnCount(); i++) {
              Object o = r.getObject(i);
              sql.append(i == 1 ? "" : ",");
              if (o == null) sql.append("NULL");
              else if (o instanceof Number) sql.append(o);
              else if (o instanceof Boolean b) sql.append(b ? 1 : 0);
              else
                sql.append("'")
                    .append(o.toString().replace("\\", "\\\\").replace("'", "''"))
                    .append("'");
            }
            sql.append(");\n");
          }
        }
      Files.writeString(backup, sql, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    } catch (java.io.IOException e) {
      throw new SQLException(
          "Não foi possível salvar backup. Migração interrompida antes de alterar dados.", e);
    }
    coluna(c, "clientes", "cpf_normalizado", "VARCHAR(11)");
    coluna(c, "locacoes", "status", "VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADA'");
    coluna(c, "locacoes", "categoria_aplicada", "VARCHAR(30)");
    coluna(c, "locacoes", "retirada_real_em", "DATE");
    coluna(c, "locacoes", "devolucao_real_em", "DATE");
    coluna(c, "locacoes", "cancelada_em", "TIMESTAMP NULL");
    coluna(c, "locacoes", "motivo_cancelamento", "VARCHAR(500)");
    campos21(c);
    coluna(c, "locacoes", "multa_atraso", "DECIMAL(19,2)");
    c.setAutoCommit(false);
    List<String> pendencias = new ArrayList<>();
    try {
      Map<String, List<Long>> grupos = new HashMap<>();
      Map<Long, String> documentos = new LinkedHashMap<>();
      try (Statement s = c.createStatement();
          ResultSet r = s.executeQuery("SELECT id_cliente,documento FROM clientes")) {
        while (r.next()) {
          long id = r.getLong(1);
          String doc = r.getString(2);
          documentos.put(id, doc);
          String cpf = Cpf.tentar(doc);
          if (cpf != null) grupos.computeIfAbsent(cpf, k -> new ArrayList<>()).add(id);
        }
      }
      try (Statement s = c.createStatement()) {
        s.executeUpdate("UPDATE clientes SET cpf_normalizado=NULL");
      }
      for (var e : documentos.entrySet()) {
        String cpf = Cpf.tentar(e.getValue());
        if (cpf != null && grupos.get(cpf).size() == 1) {
          try (PreparedStatement s =
              c.prepareStatement(
                  "UPDATE clientes SET documento=?,cpf_normalizado=? WHERE id_cliente=?")) {
            s.setString(1, cpf);
            s.setString(2, cpf);
            s.setLong(3, e.getKey());
            s.executeUpdate();
          }
        } else
          pendencias.add(
              "Cliente ID "
                  + e.getKey()
                  + ": "
                  + (cpf == null ? "documento legado" : "CPF equivalente em colisão")
                  + "; documento omitido.");
      }
      // Categoria histórica anterior não existe: aproximação documentada pelo cadastro atual.
      try (Statement s = c.createStatement()) {
        s.executeUpdate(
            "UPDATE locacoes SET origem_migracao=TRUE, historico=(data_devolucao<CURRENT_DATE),"
                + " categoria_aplicada=(SELECT categoria FROM veiculos WHERE"
                + " veiculos.id_veiculo=locacoes.id_veiculo) WHERE categoria_aplicada IS NULL");
      }
      if (!integridade.equals(integridade(c)))
        throw new SQLException("Migração alterou vínculos ou valores: versão 2 não será ativada.");
      c.commit();
    } catch (SQLException | RuntimeException e) {
      c.rollback();
      throw e;
    } finally {
      c.setAutoCommit(true);
    }
    boolean existe = false;
    try (ResultSet r =
        c.getMetaData().getIndexInfo(c.getCatalog(), null, "clientes", false, false)) {
      while (r.next())
        if ("idx_cliente_cpf".equalsIgnoreCase(r.getString("INDEX_NAME"))) existe = true;
    }
    try (Statement s = c.createStatement()) {
      if (!existe) s.execute("CREATE UNIQUE INDEX idx_cliente_cpf ON clientes(cpf_normalizado)");
    }
    verificarRestricoes(c);
    try {
      Files.writeString(
          dir.resolve("migracao-fase2-pendencias.txt"),
          "Categoria de locações antigas: aproximação pelo veículo atual.\n"
              + String.join("\n", pendencias),
          StandardCharsets.UTF_8);
    } catch (java.io.IOException e) {
      throw new SQLException("Não foi possível registrar relatório da migração.", e);
    }
    try (Statement s = c.createStatement()) {
      s.executeUpdate("UPDATE falcon_schema SET versao=4 WHERE id=1");
    }
  }

  private static void campos21(Connection c) throws SQLException {
    coluna(c, "locacoes", "origem_migracao", "BOOLEAN NOT NULL DEFAULT FALSE");
    coluna(c, "locacoes", "historico", "BOOLEAN NOT NULL DEFAULT FALSE");
    coluna(c, "locacoes", "devolucao_registrada_em", "TIMESTAMP NULL");
  }

  private static void atualizar21(Connection c) throws SQLException {
    campos21(c);
    try (Statement s = c.createStatement()) {
      s.executeUpdate("UPDATE falcon_schema SET versao=3 WHERE id=1");
    }
  }

  private static void atualizarMulta(Connection c) throws SQLException {
    coluna(c, "locacoes", "multa_atraso", "DECIMAL(19,2)");
    try (Statement s = c.createStatement()) {
      s.executeUpdate("UPDATE falcon_schema SET versao=4 WHERE id=1");
    }
  }

  private static void verificarRestricoes(Connection c) throws SQLException {
    String schema =
        c.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2")
            ? c.getSchema()
            : c.getCatalog();
    try (PreparedStatement q =
        c.prepareStatement(
            "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=?"
                + " AND TABLE_NAME='locacoes' AND CONSTRAINT_NAME='ck_locacao_estado'")) {
      q.setString(1, schema);
      try (ResultSet r = q.executeQuery()) {
        if (r.next()) return;
      }
    }
    try (Statement s = c.createStatement()) {
      s.execute(
          "ALTER TABLE locacoes ADD CONSTRAINT ck_locacao_estado CHECK (categoria_aplicada IS NOT"
              + " NULL AND categoria_aplicada IN"
              + " ('PICKUP','VAN','CAMINHAO_MEDIO','CAMINHAO_GRANDE','TRAILER') AND"
              + " ((status='CONFIRMADA' AND retirada_real_em IS NULL AND devolucao_real_em IS NULL"
              + " AND cancelada_em IS NULL AND motivo_cancelamento IS NULL) OR (status='CANCELADA'"
              + " AND retirada_real_em IS NULL AND devolucao_real_em IS NULL AND cancelada_em IS"
              + " NOT NULL AND motivo_cancelamento IS NOT NULL AND"
              + " CHAR_LENGTH(TRIM(motivo_cancelamento))>0) OR (status='EM_USO' AND"
              + " retirada_real_em IS NOT NULL AND devolucao_real_em IS NULL AND cancelada_em IS"
              + " NULL AND motivo_cancelamento IS NULL) OR (status='CONCLUIDA' AND retirada_real_em"
              + " IS NOT NULL AND devolucao_real_em IS NOT NULL AND"
              + " devolucao_real_em>=retirada_real_em AND cancelada_em IS NULL AND"
              + " motivo_cancelamento IS NULL)))");
    }
  }

  private static void coluna(Connection c, String tabela, String nome, String tipo)
      throws SQLException {
    try (ResultSet r = c.getMetaData().getColumns(c.getCatalog(), null, tabela, nome)) {
      if (r.next()) return;
    }
    try (Statement s = c.createStatement()) {
      s.execute("ALTER TABLE " + tabela + " ADD COLUMN " + nome + " " + tipo);
    }
  }

  private static List<String> integridade(Connection c) throws SQLException {
    List<String> l = new ArrayList<>();
    for (String t : List.of("clientes", "veiculos"))
      try (Statement s = c.createStatement();
          ResultSet r =
              s.executeQuery(
                  "SELECT "
                      + (t.equals("clientes") ? "id_cliente" : "id_veiculo")
                      + " FROM "
                      + t
                      + " ORDER BY 1")) {
        while (r.next()) l.add(t + ":" + r.getLong(1));
      }
    try (Statement s = c.createStatement();
        ResultSet r =
            s.executeQuery(
                "SELECT"
                    + " id_locacao,id_cliente,id_veiculo,data_retirada,data_devolucao,diaria_veiculo_aplicada,diaria_gps_aplicada,diaria_seguro_aplicada"
                    + " FROM locacoes ORDER BY id_locacao")) {
      while (r.next()) {
        StringBuilder b = new StringBuilder();
        for (int i = 1; i <= 8; i++) b.append(r.getString(i)).append('|');
        l.add(b.toString());
      }
    }
    return l;
  }
}
