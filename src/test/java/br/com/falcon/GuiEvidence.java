package br.com.falcon;

import br.com.falcon.dao.*;
import br.com.falcon.model.*;
import br.com.falcon.service.LocadoraService;
import br.com.falcon.ui.MainWindow;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Roteiro real da interface em banco isolado de demonstração. Não roda no Maven test. */
public final class GuiEvidence {
  static MainWindow janela;
  static Path imagens = Path.of("docs/imagens");

  static <T> T edt(Callable<T> c) throws Exception {
    var tarefa = new java.util.concurrent.FutureTask<>(c);
    SwingUtilities.invokeAndWait(tarefa);
    return tarefa.get();
  }

  static void aguardar(Callable<Boolean> cond) throws Exception {
    for (int i = 0; i < 200; i++) {
      if (cond.call()) {
        Thread.sleep(150);
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("A interface não concluiu a operação.");
  }

  static List<Component> filhos(Container pai) {
    List<Component> r = new ArrayList<>();
    for (Component c : pai.getComponents()) {
      r.add(c);
      if (c instanceof Container p) r.addAll(filhos(p));
    }
    return r;
  }

  static JDialog dialogo(String titulo) {
    for (Window w : Window.getWindows())
      if (w instanceof JDialog d && d.isVisible() && d.getTitle().startsWith(titulo)) return d;
    return null;
  }

  static void clicar(Container p, String nome) {
    for (Component c : filhos(p))
      if (c instanceof JButton b && b.getText().equals(nome)) {
        SwingUtilities.invokeLater(b::doClick);
        return;
      }
    throw new AssertionError("Botão ausente: " + nome);
  }

  static void foto(String nome) throws Exception {
    foto(nome, janela);
  }

  static void foto(String nome, Window janela) throws Exception {
    Thread.sleep(250);
    Rectangle r = edt(() -> janela.getBounds());
    ImageIO.write(
        new Robot().createScreenCapture(r), "png", imagens.resolve(nome + ".png").toFile());
  }

  static void preencher(JDialog d) throws Exception {
    edt(
        () -> {
          List<JTextField> campos =
              filhos(d).stream()
                  .filter(c -> c instanceof JTextField)
                  .map(c -> (JTextField) c)
                  .toList();
          String[] valores = {"28/11/2026", "30/11/2026", "Centro de Manaus", "Campus da UFAM"};
          for (int i = 0; i < campos.size(); i++) campos.get(i).setText(valores[i]);
          for (Component c : filhos(d)) if (c instanceof JCheckBox b) b.setSelected(true);
          return null;
        });
  }

  static void preencherCampos(JDialog d, String... valores) throws Exception {
    edt(
        () -> {
          List<JTextField> campos =
              filhos(d).stream()
                  .filter(c -> c instanceof JTextField)
                  .map(c -> (JTextField) c)
                  .toList();
          if (campos.size() != valores.length) throw new AssertionError("Formulário inesperado");
          for (int i = 0; i < valores.length; i++) campos.get(i).setText(valores[i]);
          return null;
        });
  }

  static void selecionar(String valor) throws Exception {
    edt(
        () -> {
          for (Component c : filhos(janela))
            if (c instanceof JTable t)
              for (int r = 0; r < t.getRowCount(); r++)
                if (String.valueOf(t.getValueAt(r, 0)).equals(valor)) {
                  t.setRowSelectionInterval(r, r);
                  return null;
                }
          throw new AssertionError("Registro ausente na tabela: " + valor);
        });
  }

  static void pagina(String nome) throws Exception {
    edt(
        () -> {
          janela.mostrarPagina(nome);
          return null;
        });
    aguardar(() -> edt(janela::pronto));
  }

  static JComboBox<?> veiculos(JDialog d) {
    return filhos(d).stream()
        .filter(
            c ->
                c instanceof JComboBox<?> box
                    && (box.getItemCount() == 0 || box.getItemAt(0) instanceof Veiculo))
        .map(c -> (JComboBox<?>) c)
        .findFirst()
        .orElseThrow();
  }

  static void escolher(JDialog d) throws Exception {
    aguardar(() -> edt(() -> veiculos(d).getItemCount() > 0));
    edt(
        () -> {
          veiculos(d).setSelectedIndex(0);
          return null;
        });
  }

  static void selecionarCliente(JDialog dialogo, String nome) throws Exception {
    edt(
        () -> {
          for (Component componente : filhos(dialogo)) {
            if (componente instanceof JComboBox<?> lista
                && lista.getSelectedItem() instanceof Cliente) {
              for (int i = 0; i < lista.getItemCount(); i++) {
                if (lista.getItemAt(i) instanceof Cliente cliente && cliente.nome().equals(nome)) {
                  lista.setSelectedIndex(i);
                  return null;
                }
              }
            }
          }
          throw new AssertionError("Cliente ausente: " + nome);
        });
  }

  static void abrirLocacao() throws Exception {
    edt(
        () -> {
          clicar(janela, "Nova locação");
          return null;
        });
    aguardar(() -> edt(() -> dialogo("Nova locação") != null));
  }

  static JDialog capturarDialogo(String titulo, String imagem) throws Exception {
    aguardar(() -> edt(() -> dialogo(titulo) != null));
    JDialog d = edt(() -> dialogo(titulo));
    foto(imagem, d);
    return d;
  }

  static JDialog seletorDeArquivo() {
    for (Window w : Window.getWindows())
      if (w instanceof JDialog d && d.isVisible()
          && filhos(d).stream().anyMatch(JFileChooser.class::isInstance)) return d;
    return null;
  }

  static void mostrarDialogo(String botao, String titulo, String imagem) throws Exception {
    edt(() -> { clicar(janela, botao); return null; });
    JDialog d = capturarDialogo(titulo, imagem);
    edt(() -> { d.dispose(); return null; });
  }

  private static Veiculo exemplo(int id, CategoriaVeiculo categoria, int lugares, String volume,
      boolean ar, String consumo, int potencia, int diaria) {
    return new Veiculo(0, "FAL-00" + id, categoria, lugares, new BigDecimal(volume), ar,
        new BigDecimal(consumo), BigDecimal.valueOf(potencia), BigDecimal.valueOf(diaria), true);
  }

  public static void main(String[] args) throws Exception {
    Files.createDirectories(imagens);
    App.tema();
    BancoDeDados banco =
        new BancoDeDados(
            System.getProperty(
                "falcon.test.url",
                "jdbc:h2:mem:gui;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"),
            System.getProperty("falcon.test.user", "sa"),
            System.getProperty("falcon.test.password", ""));
    banco.inicializar();
    LocadoraService service =
        new LocadoraService(
            banco,
            new BigDecimal("15"),
            new BigDecimal("40"),
            java.time.Clock.fixed(
                java.time.Instant.parse("2026-11-21T12:00:00Z"), java.time.ZoneOffset.UTC));
    Cliente cliente =
        service.salvarCliente(new Cliente(0, "Darth Vader", "01234567890", "contato@exemplo.test"));
    Cliente luke = service.salvarCliente(new Cliente(0, "Luke", "52998224725", "(92) 0000-0000"));
    service.salvarVeiculo(exemplo(1, CategoriaVeiculo.PICKUP, 5, "1.2", true, "9.4", 170, 180));
    service.salvarVeiculo(exemplo(2, CategoriaVeiculo.VAN, 3, "12", true, "8.5", 150, 220));
    service.salvarVeiculo(exemplo(3, CategoriaVeiculo.CAMINHAO_MEDIO, 3, "34", true, "5.2", 190, 250));
    service.salvarVeiculo(exemplo(4, CategoriaVeiculo.CAMINHAO_GRANDE, 2, "58", true, "3.4", 310, 380));
    service.salvarVeiculo(exemplo(5, CategoriaVeiculo.TRAILER, 0, "6.5", false, "0", 0, 95));
    service.registrar(
        cliente.idCliente(),
        service.veiculos().get(0).idVeiculo(),
        LocalDate.of(2026, 11, 28),
        LocalDate.of(2026, 11, 30),
        "Centro de Manaus",
        "Campus da UFAM",
        true,
        true);
    janela =
        edt(
            () -> {
              MainWindow w = new MainWindow(service);
              w.setVisible(true);
              return w;
            });
    aguardar(() -> edt(janela::pronto));
    foto("01-inicio");
    String[] paginas = {"Veículos", "Clientes", "Locações", "Relatório da frota"};
    String[] nomes = {"02-veiculos", "03-clientes", "04-locacoes", "05-relatorio"};
    for (int i = 0; i < paginas.length; i++) {
      pagina(paginas[i]);
      foto(nomes[i]);
    }
    edt(() -> { clicar(janela, "Exportar CSV"); return null; });
    aguardar(() -> edt(() -> seletorDeArquivo() != null));
    JDialog exportacao = edt(GuiEvidence::seletorDeArquivo);
    foto("21-exportar-csv", exportacao);
    edt(() -> { exportacao.dispose(); return null; });
    pagina("Demanda mensal");
    foto("11-demanda");
    pagina("Veículos");
    edt(
        () -> {
          clicar(janela, "Novo veículo");
          return null;
        });
    aguardar(() -> edt(() -> dialogo("Novo veículo") != null));
    foto("06-cadastro-veiculo");
    edt(
        () -> {
          dialogo("Novo veículo").dispose();
          return null;
        });
    selecionar("FAL-001");
    mostrarDialogo("Editar", "Editar veículo", "22-editar-veiculo");
    mostrarDialogo("Remover da oferta", "Remover da oferta", "23-remover-oferta");
    pagina("Clientes");
    selecionar("Darth Vader");
    mostrarDialogo("Editar", "Editar cliente", "24-editar-cliente");
    edt(
        () -> {
          clicar(janela, "Novo cliente");
          return null;
        });
    aguardar(() -> edt(() -> dialogo("Novo cliente") != null));
    JDialog clienteForm = edt(() -> dialogo("Novo cliente"));
    preencherCampos(clienteForm, "Duke", "invalido", "apoio@exemplo.test");
    edt(
        () -> {
          clicar(clienteForm, "Salvar cliente");
          return null;
        });
    aguardar(() -> edt(janela::pronto));
    foto("07-cadastro-cliente");
    if (edt(() -> dialogo("Novo cliente") == null) || service.clientes().size() != 2)
      throw new AssertionError("CPF inválido aceito ou formulário fechado");
    edt(
        () -> {
          clicar(clienteForm, "Gerar exemplo");
          return null;
        });
    String cpfExemplo =
        edt(
            () ->
                filhos(clienteForm).stream()
                    .filter(JTextField.class::isInstance)
                    .map(JTextField.class::cast)
                    .map(JTextField::getText)
                    .filter(valor -> Cpf.tentar(valor) != null)
                    .findFirst()
                    .orElseThrow());
    edt(
        () -> {
          clicar(clienteForm, "Salvar cliente");
          return null;
        });
    aguardar(() -> edt(() -> dialogo("Novo cliente") == null && janela.pronto()));
    if (service.clientes().size() != 3
        || service.clientes().stream().noneMatch(c -> cpfExemplo.equals(c.cpfNormalizado())))
      throw new AssertionError("Cadastro pela interface não persistiu");
    pagina("Locações");
    abrirLocacao();
    JDialog nova = edt(() -> dialogo("Nova locação"));
    selecionarCliente(nova, "Darth Vader");
    preencherCampos(nova, "21/11/2026", "23/11/2026", "Centro de Manaus", "Campus da UFAM");
    escolher(nova);
    edt(
        () -> {
          for (Component c : filhos(nova))
            if (c instanceof JCheckBox b && !b.getText().equals("Apenas disponíveis")) b.doClick();
          return null;
        });
    foto("08-disponibilidade");
    edt(
        () -> {
          clicar(nova, "Confirmar locação");
          return null;
        });
    aguardar(() -> edt(() -> dialogo("Fechamento") != null));
    foto("09-fechamento", edt(() -> dialogo("Fechamento")));
    edt(
        () -> {
          clicar(dialogo("Fechamento"), "Concluir");
          return null;
        });
    aguardar(() -> edt(janela::pronto));
    abrirLocacao();
    JDialog conflito = edt(() -> dialogo("Nova locação"));
    selecionarCliente(conflito, "Luke");
    escolher(conflito);
    // Sem filtro, o serviço ainda precisa recusar a confirmação conflitante.
    edt(
        () -> {
          for (Component c : filhos(conflito))
            if (c instanceof JCheckBox b && b.getText().equals("Apenas disponíveis")) b.doClick();
          return null;
        });
    Thread.sleep(700);
    escolher(conflito);
    preencherCampos(conflito, "21/11/2026", "23/11/2026", "Adrianópolis", "Centro de Manaus");
    Thread.sleep(700);
    escolher(conflito);
    edt(
        () -> {
          clicar(conflito, "Confirmar locação");
          return null;
        });
    aguardar(() -> edt(() -> dialogo("Reserva não confirmada") != null));
    foto("10-conflito");
    if (service.locacoes().size() != 2) throw new AssertionError("Conflito inseriu locação");
    edt(
        () -> {
          dialogo("Reserva não confirmada").dispose();
          conflito.dispose();
          return null;
        });
    pagina("Locações");
    selecionar(String.valueOf(service.locacoes().get(0).idLocacao()));
    edt(
        () -> {
          clicar(janela, "Registrar retirada");
          return null;
        });
    capturarDialogo("Registrar retirada", "25-confirmar-retirada");
    edt(
        () -> {
          clicar(dialogo("Registrar retirada"), UIManager.getString("OptionPane.yesButtonText", JComponent.getDefaultLocale()));
          return null;
        });
    aguardar(() -> edt(janela::pronto));
    foto("12-em-uso");
    if (service.locacoes().get(0).status() != StatusLocacao.EM_USO)
      throw new AssertionError("Retirada não gravada");
    selecionar(String.valueOf(service.locacoes().get(0).idLocacao()));
    edt(
        () -> {
          clicar(janela, "Registrar devolução");
          return null;
        });
    capturarDialogo("Registrar devolução", "26-confirmar-devolucao");
    edt(
        () -> {
          clicar(dialogo("Registrar devolução"), UIManager.getString("OptionPane.yesButtonText", JComponent.getDefaultLocale()));
          return null;
        });
    aguardar(() -> edt(janela::pronto));
    foto("13-concluida");
    if (service.locacoes().get(0).status() != StatusLocacao.CONCLUIDA)
      throw new AssertionError("Devolução não gravada");
    selecionar(String.valueOf(service.locacoes().get(1).idLocacao()));
    edt(() -> { clicar(janela, "Cancelar reserva"); return null; });
    JDialog motivo = capturarDialogo("Motivo do cancelamento", "27-motivo-cancelamento");
    preencherCampos(motivo, "Cliente desistiu da reserva");
    edt(() -> { clicar(motivo, UIManager.getString("OptionPane.okButtonText", JComponent.getDefaultLocale())); return null; });
    JDialog cancelar = capturarDialogo("Cancelar reserva", "28-confirmar-cancelamento");
    edt(() -> { clicar(cancelar, UIManager.getString("OptionPane.yesButtonText", JComponent.getDefaultLocale())); return null; });
    aguardar(() -> edt(janela::pronto));
    pagina("Visão geral");
    edt(
        () -> {
          clicar(janela, "Atualizar");
          return null;
        });
    aguardar(() -> edt(janela::pronto));
    pagina("Locações");
    foto("14-cancelada");
    Locacao noPrazo =
        service.registrar(
            luke.idCliente(),
            service.veiculos().get(4).idVeiculo(),
            service.hoje(),
            service.hoje(),
            "UFAM",
            "Centro de Manaus",
            false,
            false);
    service.retirar(noPrazo.idLocacao());
    service.devolver(noPrazo.idLocacao(), service.hoje());
    Locacao devolvidaNoPrazo = service.locacoes().get(0);
    if (devolvidaNoPrazo.multaRegistrada().signum() != 0
        || devolvidaNoPrazo.totalFinal().compareTo(devolvidaNoPrazo.total()) != 0)
      throw new AssertionError("Locação no prazo recebeu multa.");
    pagina("Visão geral");
    edt(
        () -> {
          clicar(janela, "Atualizar");
          return null;
        });
    aguardar(() -> edt(janela::pronto));
    pagina("Locações");
    selecionar(String.valueOf(noPrazo.idLocacao()));
    mostrarDialogo("Abrir conta completa", "Fechamento", "18-luke-no-prazo");
    LocadoraService anterior =
        new LocadoraService(
            banco,
            new BigDecimal("15"),
            new BigDecimal("40"),
            java.time.Clock.fixed(
                java.time.Instant.parse("2026-11-14T12:00:00Z"), java.time.ZoneOffset.UTC));
    Cliente aurora =
        service.salvarCliente(new Cliente(0, "Han Solo", "12345678909", "aurora@exemplo.test"));
    Locacao atrasada =
        anterior.registrar(
            aurora.idCliente(),
            service.veiculos().get(2).idVeiculo(),
            LocalDate.of(2026, 11, 14),
            LocalDate.of(2026, 11, 18),
            "Manaus",
            "Flores",
            true,
            true);
    anterior.retirar(atrasada.idLocacao());
    Cliente duke =
        service.clientes().stream().filter(c -> c.nome().equals("Duke")).findFirst().orElseThrow();
    Locacao vanDuke =
        service.registrar(
            duke.idCliente(),
            service.veiculos().get(1).idVeiculo(),
            service.hoje(),
            service.hoje(),
            "Centro de Manaus",
            "UFAM",
            false,
            false);
    edt(
        () -> {
          clicar(janela, "Atualizar");
          return null;
        });
    aguardar(() -> edt(janela::pronto));
    selecionar(String.valueOf(vanDuke.idLocacao()));
    foto("19-duke-van");
    selecionar(String.valueOf(atrasada.idLocacao()));
    foto("04-locacoes");
    edt(
        () -> {
          for (Component comp : filhos(janela))
            if (comp instanceof JComboBox<?> box
                && "Movimento do dia".equals(box.getAccessibleContext().getAccessibleName()))
              box.setSelectedItem("Em atraso");
          return null;
        });
    aguardar(
        () ->
            edt(
                () ->
                    filhos(janela).stream()
                        .filter(c -> c instanceof JTable)
                        .map(c -> (JTable) c)
                        .anyMatch(
                            t ->
                                t.getRowCount() == 1
                                    && t.getValueAt(0, 0).equals(atrasada.idLocacao()))));
    foto("15-movimento-do-dia");
    edt(
        () -> {
          for (Component comp : filhos(janela))
            if (comp instanceof JComboBox<?> box
                && "Movimento do dia".equals(box.getAccessibleContext().getAccessibleName()))
              box.setSelectedItem("Devoluções de hoje");
          return null;
        });
    aguardar(
        () ->
            edt(
                () ->
                    filhos(janela).stream()
                        .anyMatch(
                            c ->
                                c instanceof JLabel l
                                    && l.isShowing()
                                    && "Nenhuma locação nesta situação".equals(l.getText()))));
    service.devolver(atrasada.idLocacao(), service.hoje());
    Locacao fechamento =
        service.locacoes().stream()
            .filter(l -> l.idLocacao() == atrasada.idLocacao())
            .findFirst()
            .orElseThrow();
    if (fechamento.multaRegistrada().compareTo(new BigDecimal("750.00")) != 0
        || fechamento.totalFinal().compareTo(new BigDecimal("2275.00")) != 0)
      throw new AssertionError("Fechamento em atraso incorreto.");
    pagina("Visão geral");
    edt(
        () -> {
          clicar(janela, "Atualizar");
          return null;
        });
    aguardar(() -> edt(janela::pronto));
    pagina("Locações");
    edt(
        () -> {
          for (Component comp : filhos(janela))
            if (comp instanceof JComboBox<?> box
                && "Movimento do dia".equals(box.getAccessibleContext().getAccessibleName()))
              box.setSelectedItem("Todo movimento");
          return null;
        });
    selecionar(String.valueOf(atrasada.idLocacao()));
    edt(
        () -> {
          clicar(janela, "Abrir conta completa");
          return null;
        });
    aguardar(() -> edt(() -> dialogo("Fechamento") != null));
    foto("17-multa", edt(() -> dialogo("Fechamento")));
    edt(
        () -> {
          clicar(dialogo("Fechamento"), "Concluir");
          return null;
        });
    aguardar(() -> edt(() -> dialogo("Fechamento") == null));
    Locacao atrasoEmAberto =
        anterior.registrar(
            cliente.idCliente(),
            service.veiculos().get(3).idVeiculo(),
            LocalDate.of(2026, 11, 14),
            LocalDate.of(2026, 11, 19),
            "Centro de Manaus",
            "UFAM",
            false,
            false);
    anterior.retirar(atrasoEmAberto.idLocacao());
    edt(
        () -> {
          clicar(janela, "Atualizar");
          return null;
        });
    aguardar(() -> edt(janela::pronto));
    pagina("Multas");
    edt(
        () -> {
          List<Component> componentes = filhos(janela);
          JTable tabela =
              componentes.stream()
                  .filter(JTable.class::isInstance)
                  .map(JTable.class::cast)
                  .findFirst()
                  .orElseThrow();
          JComboBox<?> situacao =
              componentes.stream()
                  .filter(JComboBox.class::isInstance)
                  .map(JComboBox.class::cast)
                  .findFirst()
                  .orElseThrow();
          if (tabela.getRowCount() != 2) throw new AssertionError("Faltam registros em Multas");
          situacao.setSelectedItem("Estimada");
          if (tabela.getRowCount() != 1
              || !Long.valueOf(atrasoEmAberto.idLocacao()).equals(tabela.getValueAt(0, 0)))
            throw new AssertionError("Filtro de multas estimadas incorreto");
          situacao.setSelectedItem("Registrada");
          if (tabela.getRowCount() != 1
              || !Long.valueOf(atrasada.idLocacao()).equals(tabela.getValueAt(0, 0)))
            throw new AssertionError("Filtro de multas registradas incorreto");
          situacao.setSelectedItem("Todas");
          return null;
        });
    selecionar(String.valueOf(atrasada.idLocacao()));
    foto("20-controle-multas");
    edt(
        () -> {
          clicar(janela, "Abrir conta");
          return null;
        });
    aguardar(() -> edt(() -> dialogo("Fechamento") != null));
    edt(
        () -> {
          clicar(dialogo("Fechamento"), "Concluir");
          return null;
        });
    aguardar(() -> edt(() -> dialogo("Fechamento") == null));
    selecionar(String.valueOf(atrasada.idLocacao()));
    edt(
        () -> {
          clicar(janela, "Abrir locação");
          return null;
        });
    aguardar(
        () ->
            edt(
                () ->
                    filhos(janela).stream()
                        .anyMatch(
                            c ->
                                c instanceof JButton b
                                    && b.isShowing()
                                    && "Nova locação".equals(b.getText()))));
    try (var conexao = banco.conectar();
        var inserir = conexao.prepareStatement(
            "INSERT INTO clientes(nome,documento,contato) VALUES(?,?,?)")) {
      inserir.setString(1, "Cadastro legado A");
      inserir.setString(2, "11144477735");
      inserir.setString(3, "legado-a@exemplo.test");
      inserir.executeUpdate();
      inserir.setString(1, "Cadastro legado B");
      inserir.setString(2, "111.444.777-35");
      inserir.setString(3, "legado-b@exemplo.test");
      inserir.executeUpdate();
    }
    edt(
        () -> {
          clicar(janela, "Atualizar");
          return null;
        });
    aguardar(() -> edt(janela::pronto));
    pagina("Clientes");
    edt(
        () -> {
          clicar(janela, "Resolver pendência de CPF");
          return null;
        });
    aguardar(() -> edt(() -> dialogo("Pendências de CPF") != null));
    JDialog pendencias = edt(() -> dialogo("Pendências de CPF"));
    foto("29-pendencias-cpf", pendencias);
    edt(
        () -> {
          clicar(pendencias, UIManager.getString("OptionPane.okButtonText", JComponent.getDefaultLocale()));
          return null;
        });
    aguardar(() -> edt(() -> dialogo("Confirmar titular") != null));
    foto("30-confirmar-titular", edt(() -> dialogo("Confirmar titular")));
    edt(() -> { dialogo("Confirmar titular").dispose(); return null; });
    for (String opcao : List.of("Não", "Sim")) {
      EventQueue.invokeLater(
          () -> janela.dispatchEvent(new WindowEvent(janela, WindowEvent.WINDOW_CLOSING)));
      aguardar(() -> edt(() -> dialogo("Confirmar saída") != null));
      if (opcao.equals("Não")) foto("16-confirmar-saida", edt(() -> dialogo("Confirmar saída")));
      edt(
          () -> {
            clicar(dialogo("Confirmar saída"), opcao);
            return null;
          });
      aguardar(() -> edt(() -> dialogo("Confirmar saída") == null));
      if (edt(janela::isDisplayable) != opcao.equals("Não"))
        throw new AssertionError("A opção " + opcao + " falhou na confirmação de saída.");
    }
    System.exit(0);
  }
}
