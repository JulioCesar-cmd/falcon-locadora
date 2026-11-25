package br.com.falcon;

import br.com.falcon.dao.BancoDeDados;
import br.com.falcon.service.LocadoraService;
import br.com.falcon.ui.MainWindow;
import com.formdev.flatlaf.FlatLightLaf;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.Locale;
import java.util.Properties;
import javax.swing.*;

/** Ponto de entrada. Configuração externa por arquivo ou variáveis de ambiente. */
public final class App {
  private App() {}

  public static void tema() {
    Locale portugues = Locale.forLanguageTag("pt-BR");
    JComponent.setDefaultLocale(portugues);
    FlatLightLaf.setup();
    UIManager.getDefaults().setDefaultLocale(portugues);
    UIManager.put("Component.arc", 10);
    UIManager.put("Button.arc", 10);
    UIManager.put("TextComponent.arc", 8);
    UIManager.put("Component.focusWidth", 1);
    UIManager.put("defaultFont", new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 13));
    UIManager.put("Table.selectionBackground", new java.awt.Color(222, 233, 255));
    UIManager.put("Table.selectionForeground", new java.awt.Color(19, 32, 52));
  }

  public static void main(String[] args) {
    tema();
    try {
      Properties p = new Properties();
      Path arquivo = Path.of("config.properties");
      if (java.util.Arrays.asList(args).contains("--configurar")) {
        final boolean[] salvo = {false};
        SwingUtilities.invokeAndWait(() -> salvo[0] = configurar(arquivo));
        if (!salvo[0]) return;
      }
      if (Files.exists(arquivo))
        try (var in = Files.newInputStream(arquivo)) {
          p.load(in);
        }
      String url =
          valor(
              p,
              "db.url",
              "FALCON_DB_URL",
              "jdbc:h2:file:./dados/falcon;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
      boolean demo = java.util.Arrays.asList(args).contains("--demo");
      if (!demo && url.equals("jdbc:h2:file:./dados/falcon;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
          && !Files.exists(Path.of("dados/falcon.mv.db"))) {
        int[] escolha = {-1};
        SwingUtilities.invokeAndWait(() -> escolha[0] = escolherBanco());
        if (escolha[0] < 0) return;
        demo = escolha[0] == 0;
      }
      if (demo) {
        abrirDemonstracao();
        return;
      }
      BancoDeDados banco =
          new BancoDeDados(
              url,
              valor(p, "db.user", "FALCON_DB_USER", url.startsWith("jdbc:h2:") ? "sa" : "falcon"),
              valor(p, "db.password", "FALCON_DB_PASSWORD", ""));
      banco.inicializar();
      LocadoraService service =
          new LocadoraService(
              banco,
              new BigDecimal(p.getProperty("tarifa.gps", "15.00")),
              new BigDecimal(p.getProperty("tarifa.seguro", "40.00")));
      SwingUtilities.invokeLater(() -> new MainWindow(service).setVisible(true));
    } catch (Exception e) {
      System.err.println("Falha ao iniciar: " + e.getMessage());
      SwingUtilities.invokeLater(
          () ->
              JOptionPane.showMessageDialog(
                  null,
                  "Não foi possível iniciar o Falcon.\n\n"
                      + "Confira usuário e senha em config.properties.\n"
                      + "Ao atualizar da fase 1, consulte ATUALIZACAO_FASE_2.md (permissão"
                      + " ALTER).\n"
                      + "Pasta usada: "
                      + Path.of("").toAbsolutePath()
                      + "\n\n"
                      + "Detalhe: "
                      + e.getMessage(),
                  "Falcon · Conexão com o banco",
                  JOptionPane.ERROR_MESSAGE));
    }
  }

  /** Primeira execução guiada: salva a configuração privada na pasta de trabalho. */
  static int escolherBanco() {
    String[] opcoes = {"Demonstração", "Banco local"};
    return JOptionPane.showOptionDialog(null,
        "Explore os cadastros de exemplo ou abra o banco local para salvar seus dados.",
        "Falcon · Primeiro acesso", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
        null, opcoes, opcoes[0]);
  }

  static void abrirDemonstracao() throws Exception {
    BancoDeDados demo = new BancoDeDados(
        "jdbc:h2:mem:demonstracao;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    try (var conexao = demo.conectar(); var comando = conexao.createStatement()) {
      comando.execute("RUNSCRIPT FROM 'classpath:demonstracao.sql'");
    }
    var data = java.time.Clock.fixed(java.time.Instant.parse("2026-11-21T12:00:00Z"),
        java.time.ZoneOffset.UTC);
    var servico = new LocadoraService(demo, new BigDecimal("15"), new BigDecimal("40"), data);
    SwingUtilities.invokeLater(() -> {
      MainWindow janela = new MainWindow(servico);
      janela.setTitle("Falcon Locadora · Demonstração de 21/11/2026");
      janela.setVisible(true);
    });
  }

  private static boolean configurar(Path arquivo) {
    JTextField url = new JTextField("jdbc:mariadb://localhost:3306/falcon", 32),
        usuario = new JTextField("falcon", 20);
    JPasswordField senha = new JPasswordField(20);
    JPanel painel = new JPanel(new java.awt.GridLayout(0, 1, 0, 7));
    painel.add(new JLabel("Conecte o Falcon ao banco local já preparado."));
    painel.add(new JLabel("Endereço JDBC"));
    painel.add(url);
    painel.add(new JLabel("Usuário"));
    painel.add(usuario);
    painel.add(new JLabel("Senha do banco"));
    painel.add(senha);
    painel.add(new JLabel("A configuração será salva em: " + arquivo.toAbsolutePath()));
    if (JOptionPane.showConfirmDialog(
            null,
            painel,
            "Falcon · Primeira conexão",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE)
        != JOptionPane.OK_OPTION) return false;
    char[] segredo = senha.getPassword();
    try {
      Properties p = new Properties();
      p.setProperty("db.url", url.getText().strip());
      p.setProperty("db.user", usuario.getText().strip());
      p.setProperty("db.password", new String(segredo));
      p.setProperty("tarifa.gps", "15.00");
      p.setProperty("tarifa.seguro", "40.00");
      try (var out = Files.newOutputStream(arquivo)) {
        p.store(out, "Configuração local privada. Não publicar no Git.");
      }
      return true;
    } catch (Exception e) {
      JOptionPane.showMessageDialog(
          null, "Não foi possível salvar a configuração: " + e.getMessage());
      return false;
    } finally {
      java.util.Arrays.fill(segredo, '\0');
    }
  }

  private static String valor(Properties p, String chave, String env, String padrao) {
    String v = System.getenv(env);
    return v != null ? v : p.getProperty(chave, padrao);
  }
}
