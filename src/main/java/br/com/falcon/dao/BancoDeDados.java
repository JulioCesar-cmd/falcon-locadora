package br.com.falcon.dao;

import java.sql.*;

/** Fábrica de conexões JDBC independentes, apropriadas para transações por atendimento. */
public final class BancoDeDados {
  private final String url, usuario, senha;

  /** Guarda o endereço JDBC e as credenciais usadas nas conexões. */
  public BancoDeDados(String url, String usuario, String senha) {
    this.url = url;
    this.usuario = usuario;
    this.senha = senha;
  }

  /** Abre uma conexão independente. O chamador deve fechá-la após o uso. */
  public Connection conectar() throws SQLException {
    return DriverManager.getConnection(url, usuario, senha);
  }

  /** Cria tabelas vazias. O banco e o usuário devem existir antes da primeira execução. */
  public void inicializar() throws SQLException {
    try (Connection c = conectar();
        Statement s = c.createStatement()) {
      s.execute(
          "CREATE TABLE IF NOT EXISTS clientes(id_cliente BIGINT AUTO_INCREMENT PRIMARY KEY,nome"
              + " VARCHAR(120) NOT NULL,documento VARCHAR(40) NOT NULL UNIQUE,contato VARCHAR(160)"
              + " NOT NULL)");
      s.execute(
          "CREATE TABLE IF NOT EXISTS veiculos(id_veiculo BIGINT AUTO_INCREMENT PRIMARY"
              + " KEY,codigo_frota VARCHAR(40) NOT NULL UNIQUE,categoria VARCHAR(30) NOT"
              + " NULL,max_passageiros INT NOT NULL,volume_maximo_m3 DECIMAL(12,3) NOT"
              + " NULL,ar_condicionado BOOLEAN NOT NULL,consumo_medio_km_l DECIMAL(12,3) NOT"
              + " NULL,potencia_cv DECIMAL(12,3) NOT NULL,valor_diaria DECIMAL(12,2) NOT NULL,ativo"
              + " BOOLEAN NOT"
              + " NULL,CHECK(max_passageiros>=0),CHECK(volume_maximo_m3>=0),CHECK(consumo_medio_km_l>=0),CHECK(potencia_cv>=0),CHECK(valor_diaria>0),CHECK(categoria"
              + " IN ('PICKUP','VAN','CAMINHAO_MEDIO','CAMINHAO_GRANDE','TRAILER')))");
      s.execute(
          "CREATE TABLE IF NOT EXISTS locacoes(id_locacao BIGINT AUTO_INCREMENT PRIMARY"
              + " KEY,id_cliente BIGINT NOT NULL,id_veiculo BIGINT NOT NULL,data_retirada DATE NOT"
              + " NULL,data_devolucao DATE NOT NULL,local_retirada VARCHAR(160) NOT"
              + " NULL,local_devolucao VARCHAR(160) NOT NULL,gps_selecionado BOOLEAN NOT"
              + " NULL,seguro_selecionado BOOLEAN NOT NULL,diaria_veiculo_aplicada DECIMAL(12,2)"
              + " NOT NULL,diaria_gps_aplicada DECIMAL(12,2) NOT NULL,diaria_seguro_aplicada"
              + " DECIMAL(12,2) NOT NULL,FOREIGN KEY(id_cliente) REFERENCES"
              + " clientes(id_cliente),FOREIGN KEY(id_veiculo) REFERENCES"
              + " veiculos(id_veiculo),CHECK(data_devolucao>=data_retirada),CHECK(diaria_veiculo_aplicada>0),CHECK(diaria_gps_aplicada>=0),CHECK(diaria_seguro_aplicada>=0))");
      // O índice é criado somente quando não existe, em MySQL e MariaDB.
      boolean existe = false;
      try (ResultSet r =
          c.getMetaData().getIndexInfo(c.getCatalog(), null, "locacoes", false, false)) {
        while (r.next())
          if ("idx_locacoes_periodo".equalsIgnoreCase(r.getString("INDEX_NAME"))) existe = true;
      }
      if (!existe)
        s.execute(
            "CREATE INDEX idx_locacoes_periodo ON"
                + " locacoes(id_veiculo,data_retirada,data_devolucao)");
      Migracao.executar(c);
    }
  }
}
