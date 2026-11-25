package br.com.falcon.dao;

import br.com.falcon.model.*;
import java.sql.*;
import java.util.*;

/** Frota persistida. A inativação mantém vínculos com o histórico (RN07). */
public class VeiculoDAO {
  private final BancoDeDados banco;

  /** Usa o banco informado para consultar e manter a frota. */
  public VeiculoDAO(BancoDeDados banco) {
    this.banco = banco;
  }

  /** Retorna a frota ordenada por código, incluindo veículos inativos. */
  public List<Veiculo> listar() throws SQLException {
    try (Connection c = banco.conectar();
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM veiculos ORDER BY codigo_frota")) {
      List<Veiculo> lista = new ArrayList<>();
      while (r.next()) lista.add(ler(r));
      return lista;
    }
  }

  /** O bloqueio da linha serializa confirmações e alterações do mesmo veículo. */
  public Veiculo bloquear(Connection c, long id) throws SQLException {
    try (PreparedStatement s =
        c.prepareStatement("SELECT * FROM veiculos WHERE id_veiculo=? FOR UPDATE")) {
      s.setLong(1, id);
      try (ResultSet r = s.executeQuery()) {
        return r.next() ? ler(r) : null;
      }
    }
  }

  /** Insere um veículo novo ou atualiza o registro identificado pelo ID. */
  public Veiculo salvar(Veiculo v) throws SQLException {
    boolean novo = v.idVeiculo() == 0;
    String campos =
        "codigo_frota=?,categoria=?,max_passageiros=?,volume_maximo_m3=?,ar_condicionado=?,consumo_medio_km_l=?,potencia_cv=?,valor_diaria=?,ativo=?";
    String sql =
        novo
            ? "INSERT INTO"
                  + " veiculos(codigo_frota,categoria,max_passageiros,volume_maximo_m3,ar_condicionado,consumo_medio_km_l,potencia_cv,valor_diaria,ativo)"
                  + " VALUES(?,?,?,?,?,?,?,?,?)"
            : "UPDATE veiculos SET " + campos + " WHERE id_veiculo=?";
    try (Connection c = banco.conectar();
        PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      s.setString(1, v.codigoFrota());
      s.setString(2, v.categoria().name());
      s.setInt(3, v.maxPassageiros());
      s.setBigDecimal(4, v.volumeMaximoM3());
      s.setBoolean(5, v.arCondicionado());
      s.setBigDecimal(6, v.consumoMedioKmL());
      s.setBigDecimal(7, v.potenciaCv());
      s.setBigDecimal(8, v.valorDiaria());
      s.setBoolean(9, v.ativo());
      if (!novo) s.setLong(10, v.idVeiculo());
      if (s.executeUpdate() != 1) throw new SQLException("Veículo não encontrado.");
      long id = v.idVeiculo();
      if (novo)
        try (ResultSet r = s.getGeneratedKeys()) {
          r.next();
          id = r.getLong(1);
        }
      return new Veiculo(
          id,
          v.codigoFrota(),
          v.categoria(),
          v.maxPassageiros(),
          v.volumeMaximoM3(),
          v.arCondicionado(),
          v.consumoMedioKmL(),
          v.potenciaCv(),
          v.valorDiaria(),
          v.ativo());
    }
  }

  /** Grava a situação inativa sem excluir o veículo nem seus vínculos. */
  public void inativar(long id) throws SQLException {
    try (Connection c = banco.conectar();
        PreparedStatement s =
            c.prepareStatement("UPDATE veiculos SET ativo=FALSE WHERE id_veiculo=?")) {
      s.setLong(1, id);
      if (s.executeUpdate() != 1) throw new SQLException("Veículo não encontrado.");
    }
  }

  private Veiculo ler(ResultSet r) throws SQLException {
    return new Veiculo(
        r.getLong("id_veiculo"),
        r.getString("codigo_frota"),
        CategoriaVeiculo.valueOf(r.getString("categoria")),
        r.getInt("max_passageiros"),
        r.getBigDecimal("volume_maximo_m3"),
        r.getBoolean("ar_condicionado"),
        r.getBigDecimal("consumo_medio_km_l"),
        r.getBigDecimal("potencia_cv"),
        r.getBigDecimal("valor_diaria"),
        r.getBoolean("ativo"));
  }
}
