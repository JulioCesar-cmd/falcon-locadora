package br.com.falcon.dao;

import br.com.falcon.model.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Persistência e consultas do ciclo; usa a conexão transacional fornecida pelo serviço. */
public class LocacaoDAO {
  private final BancoDeDados banco;

  /** Usa o banco informado para acessar o histórico de contratos. */
  public LocacaoDAO(BancoDeDados b) {
    banco = b;
  }

  /** Retorna os contratos do mais recente para o mais antigo por número de locação. */
  public List<Locacao> listar() throws SQLException {
    try (Connection c = banco.conectar()) {
      return listar(c);
    }
  }

  /** Retorna os contratos do mais recente para o mais antigo por número de locação. */
  public List<Locacao> listar(Connection c) throws SQLException {
    List<Locacao> lista = new ArrayList<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM locacoes ORDER BY id_locacao DESC")) {
      while (r.next()) lista.add(ler(r));
    }
    return lista;
  }

  /** Lê o contrato na conexão fornecida. Retorna nulo quando o ID inexiste. */
  public Locacao buscar(Connection c, long id) throws SQLException {
    try (PreparedStatement s = c.prepareStatement("SELECT * FROM locacoes WHERE id_locacao=?")) {
      s.setLong(1, id);
      try (ResultSet r = s.executeQuery()) {
        return r.next() ? ler(r) : null;
      }
    }
  }

  /** Verifica se algum contrato do veículo ocupa o período solicitado, inclusive nas datas de limite. */
  public boolean haConflito(Connection c, long veiculo, LocalDate inicio, LocalDate fim)
      throws SQLException {
    return haConflito(c, veiculo, inicio, fim, LocalDate.now());
  }

  /** Verifica se algum contrato do veículo ocupa o período solicitado, inclusive nas datas de limite. */
  public boolean haConflito(
      Connection c, long veiculo, LocalDate inicio, LocalDate fim, LocalDate hoje)
      throws SQLException {
    try (PreparedStatement st =
        c.prepareStatement(
            "SELECT * FROM locacoes WHERE id_veiculo=? AND data_retirada<=? AND status<>'CANCELADA'"
                + " AND historico=FALSE")) {
      st.setLong(1, veiculo);
      st.setDate(2, java.sql.Date.valueOf(fim));
      try (ResultSet r = st.executeQuery()) {
        while (r.next()) if (ler(r).bloqueia(inicio, fim, hoje)) return true;
      }
      return false;
    }
  }

  /** Grava as condições contratadas na transação do chamador e devolve o ID gerado. */
  public long inserir(Connection c, Locacao v) throws SQLException {
    try (PreparedStatement s =
        c.prepareStatement(
            "INSERT INTO"
                + " locacoes(id_cliente,id_veiculo,data_retirada,data_devolucao,local_retirada,local_devolucao,gps_selecionado,seguro_selecionado,diaria_veiculo_aplicada,diaria_gps_aplicada,diaria_seguro_aplicada,status,categoria_aplicada)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
            Statement.RETURN_GENERATED_KEYS)) {
      s.setLong(1, v.idCliente());
      s.setLong(2, v.idVeiculo());
      s.setDate(3, java.sql.Date.valueOf(v.dataRetirada()));
      s.setDate(4, java.sql.Date.valueOf(v.dataDevolucao()));
      s.setString(5, v.localRetirada());
      s.setString(6, v.localDevolucao());
      s.setBoolean(7, v.gpsSelecionado());
      s.setBoolean(8, v.seguroSelecionado());
      s.setBigDecimal(9, v.diariaVeiculoAplicada());
      s.setBigDecimal(10, v.diariaGpsAplicada());
      s.setBigDecimal(11, v.diariaSeguroAplicada());
      s.setString(12, v.status().name());
      s.setString(13, v.categoriaAplicada().name());
      s.executeUpdate();
      try (ResultSet r = s.getGeneratedKeys()) {
        r.next();
        return r.getLong(1);
      }
    }
  }

  /** Grava a transição se o estado anterior ainda coincidir. Na devolução, registra data efetiva e multa. */
  public void atualizarEstado(
      Connection c,
      Locacao l,
      StatusLocacao status,
      LocalDate dataOperacao,
      LocalDateTime instante,
      String motivo)
      throws SQLException {
    try (PreparedStatement s =
        c.prepareStatement(
            "UPDATE locacoes SET"
                + " status=?,retirada_real_em=?,devolucao_real_em=?,cancelada_em=?,motivo_cancelamento=?,devolucao_registrada_em=?,multa_atraso=?"
                + " WHERE id_locacao=? AND status=?")) {
      s.setString(1, status.name());
      LocalDate retirada = status == StatusLocacao.EM_USO ? dataOperacao : l.retiradaRealEm();
      s.setDate(2, retirada == null ? null : java.sql.Date.valueOf(retirada));
      s.setDate(3, status == StatusLocacao.CONCLUIDA ? java.sql.Date.valueOf(dataOperacao) : null);
      s.setTimestamp(4, status == StatusLocacao.CANCELADA ? Timestamp.valueOf(instante) : null);
      s.setString(5, motivo);
      s.setTimestamp(6, status == StatusLocacao.CONCLUIDA ? Timestamp.valueOf(instante) : null);
      s.setBigDecimal(7, status == StatusLocacao.CONCLUIDA ? l.estimarMulta(dataOperacao) : null);
      s.setLong(8, l.idLocacao());
      s.setString(9, l.status().name());
      if (s.executeUpdate() != 1)
        throw new IllegalArgumentException("O estado mudou. Atualize a lista.");
    }
  }

  private static LocalDate data(ResultSet r, String k) throws SQLException {
    java.sql.Date d = r.getDate(k);
    return d == null ? null : d.toLocalDate();
  }

  private Locacao ler(ResultSet r) throws SQLException {
    Timestamp registro = r.getTimestamp("devolucao_registrada_em");
    Timestamp t = r.getTimestamp("cancelada_em");
    return new Locacao(
        r.getLong("id_locacao"),
        r.getLong("id_cliente"),
        r.getLong("id_veiculo"),
        data(r, "data_retirada"),
        data(r, "data_devolucao"),
        r.getString("local_retirada"),
        r.getString("local_devolucao"),
        r.getBoolean("gps_selecionado"),
        r.getBoolean("seguro_selecionado"),
        r.getBigDecimal("diaria_veiculo_aplicada"),
        r.getBigDecimal("diaria_gps_aplicada"),
        r.getBigDecimal("diaria_seguro_aplicada"),
        StatusLocacao.valueOf(r.getString("status")),
        CategoriaVeiculo.valueOf(r.getString("categoria_aplicada")),
        data(r, "retirada_real_em"),
        data(r, "devolucao_real_em"),
        t == null ? null : t.toLocalDateTime(),
        r.getString("motivo_cancelamento"),
        registro == null ? null : registro.toLocalDateTime(),
        r.getBoolean("origem_migracao"),
        r.getBoolean("historico"),
        r.getBigDecimal("multa_atraso"));
  }
}
