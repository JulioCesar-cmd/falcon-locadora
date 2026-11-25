package br.com.falcon.dao;

import br.com.falcon.model.Cliente;
import java.sql.*;
import java.util.*;

/** Consultas e persistência de clientes com parâmetros JDBC. */
public class ClienteDAO {
  private final BancoDeDados banco;

  /** Usa o banco informado para manter os cadastros de clientes. */
  public ClienteDAO(BancoDeDados banco) {
    this.banco = banco;
  }

  /** Retorna os clientes em ordem de nome, inclusive os importados com CPF pendente. */
  public List<Cliente> listar() throws SQLException {
    try (Connection c = banco.conectar();
        PreparedStatement s = c.prepareStatement("SELECT * FROM clientes ORDER BY nome");
        ResultSet r = s.executeQuery()) {
      List<Cliente> lista = new ArrayList<>();
      while (r.next()) lista.add(ler(r));
      return lista;
    }
  }

  /** Localiza o cliente pelo ID na conexão fornecida ou retorna nulo. */
  public Cliente buscar(Connection c, long id) throws SQLException {
    try (PreparedStatement s = c.prepareStatement("SELECT * FROM clientes WHERE id_cliente=?")) {
      s.setLong(1, id);
      try (ResultSet r = s.executeQuery()) {
        return r.next() ? ler(r) : null;
      }
    }
  }

  /** Insere ou atualiza o cadastro, verificando a unicidade do CPF dentro da transação. */
  public Cliente salvar(Cliente v) throws SQLException {
    try (Connection c = banco.conectar()) {
      c.setAutoCommit(false);
      try {
        try (PreparedStatement lock =
                c.prepareStatement("SELECT versao FROM falcon_schema WHERE id=1 FOR UPDATE");
            ResultSet rr = lock.executeQuery()) {
          rr.next();
        }
        Cliente antigo = v.idCliente() == 0 ? null : buscar(c, v.idCliente());
        boolean preservar =
            antigo != null
                && (antigo.documento().strip().equals(v.documento().strip())
                    || (!antigo.legado()
                        && antigo
                            .cpfNormalizado()
                            .equals(br.com.falcon.model.Cpf.tentar(v.documento()))));

        String doc =
            preservar ? antigo.documento() : br.com.falcon.model.Cpf.canonico(v.documento());
        String cpf = preservar ? antigo.cpfNormalizado() : doc;
        if (cpf != null && !preservar)
          for (Cliente outro : listar(c))
            if (outro.idCliente() != v.idCliente()
                && cpf.equals(br.com.falcon.model.Cpf.tentar(outro.documento())))
              throw new IllegalArgumentException(
                  "CPF já cadastrado, inclusive em cadastro legado pendente.");
        boolean novo = v.idCliente() == 0;
        String sql =
            novo
                ? "INSERT INTO clientes(nome,documento,contato,cpf_normalizado) VALUES(?,?,?,?)"
                : "UPDATE clientes SET nome=?,documento=?,contato=?,cpf_normalizado=? WHERE"
                    + " id_cliente=?";
        long id = v.idCliente();
        try (PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
          st.setString(1, v.nome());
          st.setString(2, doc);
          st.setString(3, v.contato());
          st.setString(4, cpf);
          if (!novo) st.setLong(5, id);
          if (st.executeUpdate() != 1) throw new SQLException("Cliente não encontrado.");
          if (novo)
            try (ResultSet rr = st.getGeneratedKeys()) {
              rr.next();
              id = rr.getLong(1);
            }
        }
        c.commit();
        return new Cliente(id, v.nome(), doc, v.contato(), cpf);
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e;
      }
    }
  }

  /** Registra como titular de um CPF legado o cliente escolhido, após conferir a pendência. */
  public void designarTitular(long id) throws SQLException {
    try (Connection c = banco.conectar()) {
      c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      c.setAutoCommit(false);
      try {
        try (Statement st = c.createStatement();
            ResultSet r =
                st.executeQuery("SELECT versao FROM falcon_schema WHERE id=1 FOR UPDATE")) {
          r.next();
        }
        Cliente titular = buscar(c, id);
        if (titular == null || !titular.legado())
          throw new IllegalArgumentException("Escolha um cadastro legado pendente.");
        String cpf = br.com.falcon.model.Cpf.canonico(titular.documento());
        List<Cliente> grupo =
            listar(c).stream()
                .filter(v -> cpf.equals(br.com.falcon.model.Cpf.tentar(v.documento())))
                .toList();
        if (grupo.size() < 2 || grupo.stream().anyMatch(v -> !v.legado()))
          throw new IllegalArgumentException("A pendência já foi resolvida ou o cadastro mudou.");
        try (PreparedStatement st =
            c.prepareStatement("UPDATE clientes SET cpf_normalizado=? WHERE id_cliente=?")) {
          st.setString(1, cpf);
          st.setLong(2, id);
          st.executeUpdate();
        }
        c.commit();
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e;
      }
    }
  }

  private List<Cliente> listar(Connection c) throws SQLException {
    List<Cliente> l = new ArrayList<>();
    try (Statement st = c.createStatement();
        ResultSet rr = st.executeQuery("SELECT * FROM clientes")) {
      while (rr.next()) l.add(ler(rr));
    }
    return l;
  }

  private Cliente ler(ResultSet r) throws SQLException {
    return new Cliente(
        r.getLong("id_cliente"),
        r.getString("nome"),
        r.getString("documento"),
        r.getString("contato"),
        r.getString("cpf_normalizado"));
  }
}
