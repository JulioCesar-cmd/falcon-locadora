package br.com.falcon.ui;

import br.com.falcon.model.*;
import br.com.falcon.service.LocadoraService;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.nio.file.*;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;

/** Interface Swing: captura entradas e apresenta os resultados dos serviços. */
public class MainWindow extends JFrame {
  static final Color NAVY = new Color(18, 32, 58),
      BLUE = new Color(37, 99, 235),
      BG = new Color(244, 246, 250),
      MUTED = new Color(91, 101, 119);
  static final DateTimeFormatter DATA =
      DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
  private final LocadoraService service;
  private final PainelLocacoes contratos = new PainelLocacoes(this);
  private final PainelMultas multas = new PainelMultas(this);
  private List<Veiculo> veiculos = List.of();
  private List<Cliente> clientes = List.of();
  private List<Locacao> locacoes = List.of();
  private final JPanel conteudo = new JPanel(new BorderLayout(0, 12));
  private final JLabel status = new JLabel("Carregando dados…");
  private String pagina = "Visão geral";
  private boolean ocupado;
  private JTable tabela;
  private final Map<String, JButton> navegacao = new LinkedHashMap<>();

  public MainWindow(LocadoraService service) {
    super("Falcon Locadora · Gestão de locação de veículos");
    this.service = service;
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent evento) {
            confirmarSaida();
          }
        });
    Rectangle tela = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    setSize(Math.min(1220, tela.width), Math.min(860, tela.height));
    setMinimumSize(new Dimension(1000, 650));
    setLocationRelativeTo(null);
    JPanel base = new JPanel(new BorderLayout());
    base.setBackground(BG);
    setContentPane(base);
    JPanel menu = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
    menu.setBackground(NAVY);
    menu.setBorder(new EmptyBorder(0, 14, 0, 8));
    JLabel marca = label("FALCON", 18, Color.WHITE);
    marca.setFont(marca.getFont().deriveFont(Font.BOLD));
    marca.setBorder(new EmptyBorder(0, 0, 0, 16));
    menu.add(marca);
    for (String nome :
        List.of(
            "Visão geral",
            "Veículos",
            "Clientes",
            "Locações",
            "Multas",
            "Relatório da frota",
            "Demanda mensal")) {
      JButton b = botao(nome, () -> mostrarPagina(nome), false);
      b.setForeground(Color.WHITE);
      b.setBorderPainted(false);
      b.setFocusPainted(true);
      navegacao.put(nome, b);
      menu.add(b);
    }
    base.add(menu, BorderLayout.NORTH);
    conteudo.setBackground(BG);
    conteudo.setBorder(new EmptyBorder(16, 22, 10, 22));
    base.add(conteudo, BorderLayout.CENTER);
    status.setBorder(new EmptyBorder(4, 24, 5, 20));
    status.setForeground(MUTED);
    base.add(status, BorderLayout.SOUTH);
    setIconImage(((ImageIcon) duke("x-wing", 64, 48).getIcon()).getImage());
    atualizar();
  }

  private void confirmarSaida() {
    if (ocupado) {
      JOptionPane.showMessageDialog(
          this, "Aguarde a operação em andamento para fechar o programa.");
      return;
    }

    JPanel mensagem = new JPanel(new BorderLayout(0, 12));
    JLabel nave = duke("x-wing", 240, 100);
    nave.setHorizontalAlignment(SwingConstants.CENTER);
    mensagem.add(nave, BorderLayout.NORTH);
    JLabel pergunta = label("Fechar o programa?", 18, NAVY);
    pergunta.setHorizontalAlignment(SwingConstants.CENTER);
    mensagem.add(pergunta, BorderLayout.CENTER);

    String[] opcoes = {"Sim", "Não"};
    int escolha =
        JOptionPane.showOptionDialog(
            this,
            mensagem,
            "Confirmar saída",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            opcoes,
            opcoes[1]);
    if (escolha == JOptionPane.YES_OPTION) dispose();
  }

  static JLabel label(String texto, int size, Color cor) {
    JLabel l = new JLabel(texto);
    l.setFont(new Font("SansSerif", Font.PLAIN, size));
    l.setForeground(cor);
    return l;
  }

  JButton botao(String nome, Runnable action, boolean principal) {
    JButton b = new JButton(nome);
    b.setPreferredSize(new Dimension(b.getPreferredSize().width, 44));
    if (principal) {
      b.setBackground(BLUE);
      b.setForeground(Color.WHITE);
    }
    b.addActionListener(
        e -> {
          if (!ocupado) action.run();
        });
    return b;
  }

  static String reais(BigDecimal n) {
    return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(n);
  }

  private static BigDecimal numero(String s) {
    try {
      return new BigDecimal(s.trim().replace(',', '.'));
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Informe um número válido; use vírgula ou ponto para decimais, sem separador de milhar.");
    }
  }

  static LocalDate data(String s) {
    try {
      return LocalDate.parse(s.strip(), DATA);
    } catch (Exception e) {
      throw new IllegalArgumentException("Informe uma data válida no formato dd/mm/aaaa.");
    }
  }

  private <T> void executar(Callable<T> trabalho, Consumer<T> sucesso) {
    if (ocupado) return;
    ocupado = true;
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    status.setText("Processando…");
    new SwingWorker<T, Void>() {
      @Override
      protected T doInBackground() throws Exception {
        return trabalho.call();
      }

      @Override
      protected void done() {
        ocupado = false;
        setCursor(Cursor.getDefaultCursor());
        try {
          T r = get();
          status.setText(
              "Dados atualizados · "
                  + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
          sucesso.accept(r);
        } catch (Exception e) {
          Throwable t = e;
          while (t.getCause() != null) t = t.getCause();
          String msg = t.getMessage();
          if (t instanceof java.sql.SQLException)
            msg = "Operação não confirmada. Verifique a conexão e tente novamente.";
          if (t.getMessage() != null
              && (t.getMessage().contains("Duplicate")
                  || t.getMessage().contains("Unique index")
                  || t.getMessage().contains("unique")))
            msg = "Código de frota ou documento já cadastrado. Use um valor único.";
          status.setText("Operação não confirmada");
          JOptionPane.showMessageDialog(
              MainWindow.this, msg, "Confira os dados", JOptionPane.WARNING_MESSAGE);
        }
      }
    }.execute();
  }

  private void atualizar() {
    executar(
        () -> new Object[] {service.veiculos(), service.clientes(), service.locacoes()},
        r -> {
          veiculos = convert(r[0]);
          clientes = convert(r[1]);
          locacoes = convert(r[2]);
          render();
        });
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T> convert(Object o) {
    return (List<T>) o;
  }

  private void render() {
    conteudo.removeAll();
    navegacao.forEach((nome, b) -> b.setBackground(nome.equals(pagina) ? BLUE : NAVY));
    status.setText(pagina + " · " + DATA.format(service.hoje()));
    JPanel cab = new JPanel(new BorderLayout());
    cab.setOpaque(false);
    JPanel tit = new JPanel(new BorderLayout(0, 2));
    tit.setOpaque(false);
    String figura =
        switch (pagina) {
          case "Clientes" -> "clientes";
          case "Veículos" -> "veiculos";
          case "Locações" -> "locacao";
          case "Multas" -> "atraso";
          case "Demanda mensal", "Relatório da frota" -> "relatorios";
          default -> "inicio";
        };
    JPanel linhaTitulo = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    linhaTitulo.setOpaque(false);
    linhaTitulo.add(label(pagina, 27, NAVY));
    linhaTitulo.add(Box.createHorizontalStrut(10));
    linhaTitulo.add(duke(figura, 60, 60));
    tit.add(linhaTitulo, BorderLayout.NORTH);
    String sub =
        switch (pagina) {
          case "Veículos" -> "Cadastre a frota e mantenha as especificações atualizadas.";
          case "Clientes" -> "Organize os clientes e os contatos de atendimento.";
          case "Locações" ->
              DATA.format(service.hoje())
                  + " · "
                  + locacoes.size()
                  + (locacoes.size() == 1 ? " registro" : " registros");
          case "Multas" -> "Acompanhe atrasos em aberto e multas registradas nas devoluções.";
          case "Demanda mensal" ->
              "Contratos por categoria e situação no mês da retirada prevista.";
          case "Relatório da frota" ->
              "Todos os veículos, suas especificações e situação cadastral.";
          default -> "Seu atendimento, da escolha do veículo ao fechamento da conta.";
        };
    tit.add(label(sub, 13, MUTED), BorderLayout.SOUTH);
    cab.add(tit, BorderLayout.CENTER);
    JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    direita.setOpaque(false);
    direita.add(botao("Atualizar", this::atualizar, false));
    if (pagina.equals("Locações")) direita.add(botao("Nova locação", this::formLocacao, true));
    cab.add(direita, BorderLayout.EAST);
    conteudo.add(cab, BorderLayout.NORTH);
    if (pagina.equals("Visão geral")) {
      inicio();
    } else if (pagina.equals("Demanda mensal")) demanda();
    else if (pagina.equals("Locações"))
      conteudo.add(contratos.montar(locacoes, veiculos, service.hoje()), BorderLayout.CENTER);
    else if (pagina.equals("Multas"))
      conteudo.add(multas.montar(locacoes, service.hoje()), BorderLayout.CENTER);
    else lista();
    conteudo.revalidate();
    conteudo.repaint();
  }

  private void inicio() {
    JPanel area = new JPanel(new BorderLayout(0, 20));
    area.setOpaque(false);
    JPanel hero = new JPanel(new BorderLayout(18, 12));
    hero.setBackground(Color.WHITE);
    hero.setBorder(new EmptyBorder(20, 24, 20, 24));
    JPanel textos = new JPanel(new GridLayout(0, 1, 0, 10));
    textos.setOpaque(false);
    textos.add(label("Falcon Locadora", 28, NAVY));
    textos.add(label("Gestão de locações em Manaus.", 15, MUTED));
    textos.add(
        label(
            clientes.isEmpty() || veiculos.stream().noneMatch(Veiculo::ativo)
                ? "Comece cadastrando um cliente e um veículo."
                : "Escolha o próximo atendimento.",
            14,
            NAVY));
    JPanel acoes = new JPanel(new FlowLayout(FlowLayout.LEFT));
    acoes.setOpaque(false);
    acoes.add(botao("Nova locação", this::formLocacao, true));
    acoes.add(botao("Cadastrar cliente", () -> formCliente(null), false));
    acoes.add(botao("Cadastrar veículo", () -> formVeiculo(null), false));
    hero.add(textos, BorderLayout.CENTER);
    hero.add(duke("logo", 350, 140), BorderLayout.EAST);
    hero.add(acoes, BorderLayout.SOUTH);
    area.add(hero, BorderLayout.NORTH);
    JPanel agenda = new JPanel(new BorderLayout(0, 12));
    agenda.setBackground(Color.WHITE);
    agenda.setBorder(new EmptyBorder(16, 20, 16, 20));
    LocalDate hoje = service.hoje();
    long atrasos = locacoes.stream().filter(l -> l.atrasada(service.hoje())).count();
    agenda.add(
        label(
            "Hoje · "
                + DATA.format(service.hoje())
                + "   |   "
                + atrasos
                + (atrasos == 1 ? " locação em atraso" : " locações em atraso"),
            16,
            NAVY),
        BorderLayout.NORTH);
    DefaultTableModel m =
        new DefaultTableModel(
            new String[] {"Locação", "Cliente", "Veículo", "Previsão", "Estado"}, 0) {
          public boolean isCellEditable(int r, int c) {
            return false;
          }
        };
    for (Locacao l : locacoes)
      if (l.retiradaPendente(hoje)
          || PainelLocacoes.inclui(l, "Devoluções de hoje", hoje)
          || l.atrasada(hoje))
        m.addRow(
            new Object[] {
              l.idLocacao(),
              cliente(l.idCliente()),
              veiculo(l.idVeiculo()),
              l.atrasada(service.hoje())
                  ? "Devolução atrasada"
                  : l.retiradaPendente(hoje) ? "Retirada pendente" : "Devolução prevista hoje",
              l.status()
            });
    if (m.getRowCount() == 0) {
      JPanel vazio = new JPanel(new FlowLayout());
      vazio.setOpaque(false);
      vazio.add(duke("vazio", 100, 100));
      vazio.add(label("Nenhuma retirada ou devolução prevista para hoje.", 13, MUTED));
      agenda.add(vazio, BorderLayout.CENTER);
    } else {
      JTable t = new JTable(m);
      t.setRowHeight(32);
      agenda.add(new JScrollPane(t), BorderLayout.CENTER);
    }
    agenda.add(
        botao("Ver locações e registrar operações", () -> mostrarPagina("Locações"), false),
        BorderLayout.SOUTH);
    area.add(agenda, BorderLayout.CENTER);
    conteudo.add(area, BorderLayout.CENTER);
  }

  void abrirLocacao(long id) {
    contratos.mostrarContrato(id);
    mostrarPagina("Locações");
  }

  String cliente(long id) {
    return clientes.stream()
        .filter(v -> v.idCliente() == id)
        .map(Cliente::nome)
        .findFirst()
        .orElse("Cliente #" + id);
  }

  String veiculo(long id) {
    return veiculos.stream()
        .filter(v -> v.idVeiculo() == id)
        .map(Veiculo::codigoFrota)
        .findFirst()
        .orElse("Veículo #" + id);
  }

  private void lista() {
    JPanel area = new JPanel(new BorderLayout(0, 14));
    area.setOpaque(false);
    JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    barra.setOpaque(false);
    JTextField busca = new JTextField(21);
    busca.putClientProperty("JTextField.placeholderText", "Buscar na tabela…");
    barra.add(busca);

    boolean frota = pagina.equals("Veículos") || pagina.equals("Relatório da frota");
    DefaultTableModel modelo =
        new DefaultTableModel() {
          @Override
          public boolean isCellEditable(int r, int c) {
            return false;
          }

          @Override
          public Class<?> getColumnClass(int c) {
            if (!frota) return String.class;
            return switch (c) {
              case 2 -> Integer.class;
              case 3, 5, 6, 7 -> BigDecimal.class;
              default -> Object.class;
            };
          }
        };
    if (frota) {
      modelo.setColumnIdentifiers(
          new String[] {
            "Código",
            "Categoria",
            "Passag.",
            "Volume m³",
            "Ar-cond.",
            "km/L",
            "Potência cv",
            "Diária",
            "Situação"
          });
      for (Veiculo v : veiculos)
        modelo.addRow(
            new Object[] {
              v.codigoFrota(),
              v.categoria(),
              v.maxPassageiros(),
              v.volumeMaximoM3(),
              v.arCondicionado() ? "Sim" : "Não",
              v.categoria() == CategoriaVeiculo.TRAILER && v.consumoMedioKmL().signum() == 0
                  ? null
                  : v.consumoMedioKmL(),
              v.categoria() == CategoriaVeiculo.TRAILER && v.potenciaCv().signum() == 0
                  ? null
                  : v.potenciaCv(),
              v.valorDiaria(),
              v.ativo() ? "Ativo" : "Inativo"
            });
    } else if (pagina.equals("Clientes")) {
      modelo.setColumnIdentifiers(
          new String[] {"Nome", "CPF / documento legado", "Contato", "Cadastro"});
      for (Cliente v : clientes)
        modelo.addRow(
            new Object[] {
              v.nome(),
              v.documentoResumido(),
              v.contato(),
              v.legado() ? "Legado · revisar CPF" : "CPF validado"
            });
    }
    tabela = new JTable(modelo);
    tabela.setRowHeight(38);
    tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    tabela.getTableHeader().setReorderingAllowed(false);
    tabela.setShowGrid(false);
    tabela.setFillsViewportHeight(true);
    tabela.setIntercellSpacing(new Dimension(0, 0));
    if (frota) {
      alinharMoeda(tabela, 7);
      DefaultTableCellRenderer numeroFrota =
          new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object valor) {
              setText(textoTabela(valor));
            }
          };
      numeroFrota.setHorizontalAlignment(SwingConstants.RIGHT);
      for (int c : new int[] {3, 5, 6})
        tabela.getColumnModel().getColumn(c).setCellRenderer(numeroFrota);
      int[] larguras = {90, 145, 55, 90, 70, 100, 100, 95, 75};
      for (int i = 0; i < larguras.length; i++) {
        tabela.getColumnModel().getColumn(i).setPreferredWidth(larguras[i]);
      }
    }

    TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(modelo);
    if (frota) {
      sorter.setStringConverter(
          new TableStringConverter() {
            @Override
            public String toString(TableModel model, int row, int column) {
              return textoFrota(model.getValueAt(row, column), column);
            }
          });
    }
    tabela.setRowSorter(sorter);
    JLabel registros = label(quantidadeRegistros(modelo.getRowCount()), 12, MUTED);
    sorter.addRowSorterListener(
        e -> registros.setText(quantidadeRegistros(sorter.getViewRowCount())));
    busca
        .getDocument()
        .addDocumentListener(
            new javax.swing.event.DocumentListener() {
              private void aplicar() {
                sorter.setRowFilter(
                    RowFilter.regexFilter(
                        "(?iu)" + java.util.regex.Pattern.quote(busca.getText())));
              }

              public void insertUpdate(javax.swing.event.DocumentEvent e) {
                aplicar();
              }

              public void removeUpdate(javax.swing.event.DocumentEvent e) {
                aplicar();
              }

              public void changedUpdate(javax.swing.event.DocumentEvent e) {
                aplicar();
              }
            });
    if (pagina.equals("Veículos")) {
      barra.add(botao("Novo veículo", () -> formVeiculo(null), true));
      barra.add(
          botao(
              "Editar",
              () -> {
                int i = selecionado();
                if (i >= 0) formVeiculo(veiculos.get(i));
              },
              false));
      barra.add(
          botao(
              "Remover da oferta",
              () -> {
                int i = selecionado();
                if (i >= 0 && confirmarInativacao(veiculos.get(i)))
                  executar(
                      () -> {
                        service.inativarVeiculo(veiculos.get(i).idVeiculo());
                        return true;
                      },
                      r -> atualizar());
              },
              false));
    } else if (pagina.equals("Clientes")) {
      barra.add(botao("Novo cliente", () -> formCliente(null), true));
      barra.add(
          botao(
              "Editar",
              () -> {
                int i = selecionado();
                if (i >= 0) formCliente(clientes.get(i));
              },
              false));
      if (!gruposPendentes().isEmpty())
        barra.add(botao("Resolver pendência de CPF", this::resolverCpf, false));
    } else barra.add(botao("Exportar CSV", this::exportar, false));
    JPanel ferramentas = new JPanel(new BorderLayout(0, 8));
    ferramentas.setOpaque(false);
    ferramentas.add(barra, BorderLayout.CENTER);
    area.add(ferramentas, BorderLayout.NORTH);
    JScrollPane scroll = new JScrollPane(tabela);
    scroll.setBorder(BorderFactory.createLineBorder(new Color(224, 230, 239)));
    if (modelo.getRowCount() == 0) {
      JPanel vazio = new JPanel(new FlowLayout());
      vazio.add(duke("vazio", 120, 120));
      String mensagem =
          pagina.equals("Relatório da frota")
              ? "Nenhum veículo cadastrado. Abra Veículos para começar."
              : "Nenhum cadastro. Use o botão Novo para começar.";
      vazio.add(label(mensagem, 13, MUTED));
      area.add(vazio, BorderLayout.CENTER);
    } else area.add(scroll, BorderLayout.CENTER);
    area.add(registros, BorderLayout.SOUTH);
    conteudo.add(area, BorderLayout.CENTER);
  }

  private static String quantidadeRegistros(int total) {
    return total + (total == 1 ? " registro" : " registros");
  }

  private static String textoTabela(Object valor) {
    if (valor instanceof BigDecimal numero)
      return numero.stripTrailingZeros().toPlainString().replace('.', ',');
    return valor == null ? "Não se aplica" : valor.toString();
  }

  private static String textoFrota(Object valor, int coluna) {
    return coluna == 7 ? reais((BigDecimal) valor) : textoTabela(valor);
  }

  static void alinharMoeda(JTable t, int col) {
    DefaultTableCellRenderer renderer =
        new DefaultTableCellRenderer() {
          @Override
          protected void setValue(Object valor) {
            super.setValue(valor instanceof BigDecimal n ? reais(n) : valor);
          }
        };
    renderer.setHorizontalAlignment(SwingConstants.RIGHT);
    t.getColumnModel().getColumn(col).setCellRenderer(renderer);
    TableCellRenderer original = t.getTableHeader().getDefaultRenderer();
    t.getTableHeader()
        .setDefaultRenderer(
            (table, value, selected, focus, row, column) -> {
              Component header =
                  original.getTableCellRendererComponent(
                      table, value, selected, focus, row, column);
              if (header instanceof JLabel label)
                label.setHorizontalAlignment(
                    table.convertColumnIndexToModel(column) == col
                        ? SwingConstants.RIGHT
                        : SwingConstants.LEFT);
              return header;
            });
  }

  private boolean confirmarInativacao(Veiculo v) {
    LocalDate dia = service.hoje();
    StringBuilder resumo = new StringBuilder("Inativar " + v.codigoFrota() + "?");
    List<Locacao> afetadas =
        locacoes.stream()
            .filter(l -> l.idVeiculo() == v.idVeiculo() && l.afetaInativacao(dia))
            .toList();
    if (afetadas.isEmpty()) resumo.append("\nNenhuma locação pendente.");
    else {
      resumo.append(
          "\nAs locações abaixo continuam registradas. Novas retiradas exigirão reativação.\n");
      for (Locacao l : afetadas)
        resumo
            .append("\n#")
            .append(l.idLocacao())
            .append("  ")
            .append(cliente(l.idCliente()))
            .append("  ·  ")
            .append(l.situacao(dia))
            .append("  ·  ")
            .append(DATA.format(l.dataRetirada()))
            .append(" a ")
            .append(DATA.format(l.dataDevolucao()));
    }
    JTextArea texto = new JTextArea(resumo.toString(), Math.min(10, 3 + afetadas.size()), 46);
    texto.setEditable(false);
    texto.setLineWrap(true);
    texto.setWrapStyleWord(true);
    return JOptionPane.showConfirmDialog(
            this, new JScrollPane(texto), "Remover da oferta", JOptionPane.YES_NO_OPTION)
        == JOptionPane.YES_OPTION;
  }

  private List<Cliente> gruposPendentes() {
    Map<String, List<Cliente>> grupos = new LinkedHashMap<>();
    for (Cliente c : clientes) {
      String cpf = Cpf.tentar(c.documento());
      if (cpf != null) grupos.computeIfAbsent(cpf, k -> new ArrayList<>()).add(c);
    }
    return grupos.values().stream()
        .filter(g -> g.size() > 1 && g.stream().allMatch(Cliente::legado))
        .flatMap(List::stream)
        .toList();
  }

  private void resolverCpf() {
    List<Cliente> opcoes = gruposPendentes();
    String[] nomes =
        opcoes.stream()
            .map(c -> c.documentoResumido() + " · #" + c.idCliente() + " · " + c.nome())
            .toArray(String[]::new);
    Object item =
        JOptionPane.showInputDialog(
            this,
            "Selecione o titular após conferir os documentos.",
            "Pendências de CPF",
            JOptionPane.QUESTION_MESSAGE,
            null,
            nomes,
            nomes[0]);
    if (item == null) return;
    Cliente eleito = opcoes.get(Arrays.asList(nomes).indexOf(item));
    if (JOptionPane.showConfirmDialog(
            this,
            "Designar " + eleito.nome() + " como titular? Cada cliente conservará seu histórico.",
            "Confirmar titular",
            JOptionPane.YES_NO_OPTION)
        == JOptionPane.YES_OPTION)
      executar(
          () -> {
            service.designarTitular(eleito.idCliente());
            return true;
          },
          r -> atualizar());
  }

  private int selecionado() {
    if (tabela.getSelectedRow() < 0) {
      JOptionPane.showMessageDialog(this, "Selecione uma linha na tabela.");
      return -1;
    }
    return tabela.convertRowIndexToModel(tabela.getSelectedRow());
  }

  private static final class Campos extends JPanel implements Scrollable {
    Campos() {
      super(new GridLayout(0, 2, 14, 10));
    }

    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
      return 16;
    }

    public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
      return Math.max(16, r.height - 24);
    }

    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

  private final class Form {
    final JDialog janela;
    final JPanel campos = new Campos();
    final JPanel acoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

    Form(String titulo) {
      janela = new JDialog(MainWindow.this, titulo, true);
      JPanel p = new JPanel(new BorderLayout(0, 24));
      p.setBorder(new EmptyBorder(26, 28, 26, 28));
      JScrollPane rolagem = new JScrollPane(campos);
      rolagem.setBorder(null);
      rolagem.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      rolagem.getVerticalScrollBar().setUnitIncrement(16);
      p.add(rolagem, BorderLayout.CENTER);
      String img =
          titulo.contains("cliente")
              ? "clientes"
              : titulo.contains("veículo") ? "veiculos" : "locacao";
      JPanel topo = new JPanel(new BorderLayout());
      topo.add(label(titulo, 21, NAVY), BorderLayout.CENTER);
      topo.add(duke(img, 80, 80), BorderLayout.EAST);
      p.add(topo, BorderLayout.NORTH);
      p.add(acoes, BorderLayout.SOUTH);
      janela.setContentPane(p);
      acoes.add(botao("Cancelar", janela::dispose, false));
    }

    <T extends JComponent> T campo(String nome, T c) {
      JPanel p = new JPanel(new BorderLayout(0, 6));
      p.add(label(nome, 12, MUTED), BorderLayout.NORTH);
      p.add(c, BorderLayout.CENTER);
      campos.add(p);
      return c;
    }

    JTextField texto(String nome, String valor) {
      return campo(nome, new JTextField(valor, 21));
    }

    void mostrar() {
      janela.pack();
      Dimension limite =
          GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().getSize();
      janela.setSize(
          Math.min(limite.width, Math.max(750, janela.getWidth() + 20)),
          Math.min(
              limite.height - 20,
              janela.getHeight() + (janela.getTitle().equals("Nova locação") ? 75 : 16)));
      janela.setMinimumSize(
          new Dimension(Math.min(600, limite.width), Math.min(440, limite.height)));
      janela.setLocationRelativeTo(MainWindow.this);
      janela
          .getRootPane()
          .registerKeyboardAction(
              e -> janela.dispose(),
              KeyStroke.getKeyStroke("ESCAPE"),
              JComponent.WHEN_IN_FOCUSED_WINDOW);
      janela.setVisible(true);
    }

    void confirmar(String titulo, Runnable r) {
      acoes.add(
          botao(
              titulo,
              () -> {
                try {
                  r.run();
                } catch (RuntimeException e) {
                  JOptionPane.showMessageDialog(
                      janela, e.getMessage(), "Confira os dados", JOptionPane.WARNING_MESSAGE);
                }
              },
              true));
    }
  }

  private void formCliente(Cliente v) {
    Form f = new Form(v == null ? "Novo cliente" : "Editar cliente");
    JTextField nome = f.texto("Nome completo *", v == null ? "" : v.nome()),
        doc =
            f.texto(
                v != null && v.legado()
                    ? "Documento legado (mantenha ou troque por CPF) *"
                    : "CPF (11 dígitos ou 000.000.000-00) *",
                v == null ? "" : v.documento()),
        contato = f.texto("Contato (telefone ou e-mail) *", v == null ? "" : v.contato());
    Map<JTextField, JLabel> erros = new LinkedHashMap<>();
    for (JTextField campo : List.of(nome, doc, contato)) {
      JLabel erro = label(" ", 12, new Color(150, 28, 28));
      ((JPanel) campo.getParent()).add(erro, BorderLayout.SOUTH);
      erros.put(campo, erro);
    }
    doc.setToolTipText(
        "CPF válido, com ou sem pontuação. Documentos legados podem ser mantidos ao editar.");
    if (v == null) {
      JPanel campoCpf = (JPanel) doc.getParent();
      JPanel entradaCpf = new JPanel(new BorderLayout(6, 0));
      entradaCpf.setOpaque(false);
      JButton exemplo =
          botao(
              "Gerar exemplo",
              () -> {
                Set<String> cadastrados = new HashSet<>();
                for (Cliente cliente : clientes) {
                  cadastrados.add(Cpf.tentar(cliente.documento()));
                  cadastrados.add(cliente.cpfNormalizado());
                }
                String cpf;
                do {
                  cpf = Cpf.gerarExemplo();
                } while (cadastrados.contains(cpf) || cpf.equals(doc.getText()));
                doc.setText(cpf);
                erros.get(doc).setText(" ");
                doc.requestFocusInWindow();
              },
              false);
      exemplo.setToolTipText("Preenche um CPF com dígitos válidos para demonstração.");
      entradaCpf.add(doc, BorderLayout.CENTER);
      entradaCpf.add(exemplo, BorderLayout.EAST);
      campoCpf.add(entradaCpf, BorderLayout.CENTER);
    }
    f.confirmar(
        "Salvar cliente",
        () -> {
          Cliente novo =
              new Cliente(
                  v == null ? 0 : v.idCliente(), nome.getText(), doc.getText(), contato.getText());
          executar(
              () -> {
                try {
                  return new Object[] {service.salvarCliente(novo), null};
                } catch (IllegalArgumentException e) {
                  return new Object[] {null, e.getMessage()};
                }
              },
              r -> {
                if (r[1] != null) {
                  String aviso = r[1].toString();
                  JTextField campo =
                      aviso.startsWith("Nome") ? nome : aviso.startsWith("Contato") ? contato : doc;
                  erros.values().forEach(e -> e.setText(" "));
                  erros
                      .get(campo)
                      .setText("<html><body style='width:215px'>" + html(aviso) + "</body></html>");
                  campo.requestFocusInWindow();
                  f.campos.revalidate();
                } else {
                  f.janela.dispose();
                  atualizar();
                  status.setIcon(duke("sucesso", 28, 28).getIcon());
                }
              });
        });
    f.mostrar();
  }

  private void formVeiculo(Veiculo v) {
    Form f = new Form(v == null ? "Novo veículo" : "Editar veículo");
    JTextField codigo = f.texto("Código único da frota *", v == null ? "" : v.codigoFrota());
    JComboBox<CategoriaVeiculo> categoria =
        f.campo("Categoria *", new JComboBox<>(CategoriaVeiculo.values()));
    if (v != null) categoria.setSelectedItem(v.categoria());
    JTextField
        passageiros = f.texto("Máximo de passageiros *", v == null ? "0" : "" + v.maxPassageiros()),
        volume =
            f.texto("Volume máximo (m³) *", v == null ? "0" : v.volumeMaximoM3().toPlainString()),
        consumo =
            f.texto(
                "Consumo (km/L; trailer sem motor: 0) *",
                v == null ? "0" : v.consumoMedioKmL().toPlainString()),
        potencia =
            f.texto(
                "Potência (cv; trailer sem motor: 0) *",
                v == null ? "0" : v.potenciaCv().toPlainString()),
        diaria =
            f.texto(
                "Valor por diária (R$) *", v == null ? "180,00" : v.valorDiaria().toPlainString());
    JCheckBox
        ar =
            f.campo(
                "Conforto",
                new JCheckBox("Possui ar-condicionado", v != null && v.arCondicionado())),
        ativo =
            f.campo("Situação cadastral", new JCheckBox("Ativo na oferta", v == null || v.ativo()));
    f.confirmar(
        "Salvar veículo",
        () -> {
          int n;
          try {
            n = Integer.parseInt(passageiros.getText().strip());
          } catch (Exception e) {
            throw new IllegalArgumentException("Passageiros: informe um número inteiro.");
          }
          Veiculo novo =
              new Veiculo(
                  v == null ? 0 : v.idVeiculo(),
                  codigo.getText(),
                  (CategoriaVeiculo) categoria.getSelectedItem(),
                  n,
                  numero(volume.getText()),
                  ar.isSelected(),
                  numero(consumo.getText()),
                  numero(potencia.getText()),
                  numero(diaria.getText()),
                  ativo.isSelected());
          if (v != null && v.ativo() && !novo.ativo() && !confirmarInativacao(v)) return;
          executar(
              () -> service.salvarVeiculo(novo),
              r -> {
                f.janela.dispose();
                atualizar();
              });
        });
    f.mostrar();
  }

  private void formLocacao() {
    if (clientes.isEmpty() || veiculos.stream().noneMatch(Veiculo::ativo)) {
      JOptionPane.showMessageDialog(
          this, "Cadastre um cliente e um veículo ativo antes de registrar a locação.");
      return;
    }
    Form f = new Form("Nova locação");
    JTextField inicio = f.texto("1 · Retirada (dd/mm/aaaa) *", DATA.format(service.hoje())),
        fim = f.texto("Devolução prevista (dd/mm/aaaa) *", DATA.format(service.hoje().plusDays(2)));
    JComboBox<Cliente> cli =
        f.campo("2 · Cliente cadastrado *", new JComboBox<>(clientes.toArray(Cliente[]::new)));
    JComboBox<Veiculo> vei = f.campo("3 · Veículo *", new JComboBox<>());
    JCheckBox apenas = f.campo("Filtro de veículos", new JCheckBox("Apenas disponíveis", true));
    JLabel disp = f.campo("Disponibilidade", label("Conferindo período…", 12, MUTED));
    JTextField localA = f.texto("Local de retirada *", ""),
        localB = f.texto("Local de devolução *", "");
    JCheckBox gps = f.campo("GPS por diária", new JCheckBox(reais(service.tarifaGps()))),
        seguro = f.campo("Seguro por diária", new JCheckBox(reais(service.tarifaSeguro())));
    JLabel estimativa = f.campo("Estimativa contratada", label("Selecione um veículo.", 13, BLUE));
    JLabel detalhe =
        f.campo(
            "Diárias inclusivas",
            label("<html>Retirada e devolução contam como dias.<br>&nbsp;</html>", 12, MUTED));
    f.campo("Devolução em atraso", label("Multa por dia: uma diária do veículo.", 12, MUTED));
    Map<Long, String> impedimentos = new HashMap<>();
    final int[] versao = {0};
    final boolean[] carregando = {false};
    Runnable preco =
        () -> {
          try {
            Veiculo v = (Veiculo) vei.getSelectedItem();
            long dias =
                java.time.temporal.ChronoUnit.DAYS.between(
                        data(inicio.getText()), data(fim.getText()))
                    + 1;
            if (v != null) {
              String motivo = impedimentos.getOrDefault(v.idVeiculo(), "");
              disp.setText(
                  motivo.isEmpty() ? "Disponível no período" : "<html>" + html(motivo) + "</html>");
              disp.setIcon(duke(motivo.isEmpty() ? "locacao" : "indisponivel", 24, 24).getIcon());
            }
            if (v == null || dias <= 0) {
              estimativa.setText("Selecione um veículo disponível.");
              return;
            }
            BigDecimal diaria =
                v.valorDiaria()
                    .add(gps.isSelected() ? service.tarifaGps() : BigDecimal.ZERO)
                    .add(seguro.isSelected() ? service.tarifaSeguro() : BigDecimal.ZERO);
            estimativa.setText(
                dias
                    + (dias == 1 ? " diária · total " : " diárias · total ")
                    + reais(diaria.multiply(BigDecimal.valueOf(dias))));
            detalhe.setText(
                "<html>Veículo "
                    + reais(v.valorDiaria())
                    + " + GPS "
                    + reais(gps.isSelected() ? service.tarifaGps() : BigDecimal.ZERO)
                    + "<br>Seguro "
                    + reais(seguro.isSelected() ? service.tarifaSeguro() : BigDecimal.ZERO)
                    + " / dia</html>");
          } catch (RuntimeException e) {
            estimativa.setText("Confira as datas.");
          }
        };
    Runnable consultar =
        () -> {
          int token = ++versao[0];
          Veiculo anterior = (Veiculo) vei.getSelectedItem();
          vei.removeAllItems();
          preco.run();
          try {
            LocalDate a = data(inicio.getText()), b = data(fim.getText());
            boolean filtro = apenas.isSelected();
            disp.setText("Conferindo disponibilidade…");
            carregando[0] = true;
            new SwingWorker<List<LocadoraService.OpcaoVeiculo>, Void>() {
              protected List<LocadoraService.OpcaoVeiculo> doInBackground() throws Exception {
                return service.oferta(a, b);
              }

              protected void done() {
                if (token != versao[0] || !f.janela.isDisplayable()) return;
                carregando[0] = false;
                try {
                  List<LocadoraService.OpcaoVeiculo> oferta = get();
                  impedimentos.clear();
                  for (var opcao : oferta)
                    impedimentos.put(opcao.veiculo().idVeiculo(), opcao.impedimento());
                  List<Veiculo> livres =
                      oferta.stream()
                          .filter(o -> !filtro || o.livre())
                          .map(LocadoraService.OpcaoVeiculo::veiculo)
                          .toList();
                  for (Veiculo v : livres) vei.addItem(v);
                  vei.setSelectedIndex(-1);
                  if (anterior != null)
                    for (int i = 0; i < vei.getItemCount(); i++)
                      if (vei.getItemAt(i).idVeiculo() == anterior.idVeiculo())
                        vei.setSelectedIndex(i);
                  disp.setText(
                      livres.isEmpty()
                          ? "<html>Nenhum veículo disponível. "
                              + html(
                                  oferta.isEmpty()
                                      ? "Confira a frota ativa."
                                      : oferta.get(0).veiculo().codigoFrota()
                                          + " · "
                                          + oferta.get(0).impedimento())
                              + "</html>"
                          : livres.size()
                              + (livres.size() == 1 ? " veículo." : " veículos.")
                              + " Escolha uma opção.");
                  disp.setIcon(
                      duke(livres.isEmpty() ? "indisponivel" : "locacao", 26, 26).getIcon());
                  preco.run();
                } catch (Exception e) {
                  disp.setText("Falha ao consultar. Confira datas e conexão.");
                }
              }
            }.execute();
          } catch (RuntimeException e) {
            carregando[0] = false;
            disp.setText(e.getMessage());
          }
        };
    javax.swing.Timer atraso = new javax.swing.Timer(350, e -> consultar.run());
    atraso.setRepeats(false);
    javax.swing.event.DocumentListener listener =
        new javax.swing.event.DocumentListener() {
          private void mudou() {
            versao[0]++;
            vei.removeAllItems();
            disp.setText("Período alterado; atualizando lista…");
            preco.run();
            atraso.restart();
          }

          public void insertUpdate(javax.swing.event.DocumentEvent e) {
            mudou();
          }

          public void removeUpdate(javax.swing.event.DocumentEvent e) {
            mudou();
          }

          public void changedUpdate(javax.swing.event.DocumentEvent e) {
            mudou();
          }
        };
    inicio.getDocument().addDocumentListener(listener);
    fim.getDocument().addDocumentListener(listener);
    apenas.addActionListener(e -> consultar.run());
    vei.addActionListener(e -> preco.run());
    gps.addActionListener(e -> preco.run());
    seguro.addActionListener(e -> preco.run());
    f.acoes.add(botao("Consultar disponibilidade", consultar, false));
    f.confirmar(
        "Confirmar locação",
        () -> {
          if (carregando[0] || vei.getSelectedItem() == null)
            throw new IllegalArgumentException("Aguarde a consulta e selecione um veículo.");
          LocalDate a = data(inicio.getText()), b = data(fim.getText());
          Cliente cliente = (Cliente) cli.getSelectedItem();
          Veiculo v = (Veiculo) vei.getSelectedItem();
          String la = localA.getText(), lb = localB.getText();
          boolean g = gps.isSelected(), seg = seguro.isSelected();
          executar(
              () -> {
                try {
                  return new Object[] {
                    service.registrar(cliente.idCliente(), v.idVeiculo(), a, b, la, lb, g, seg),
                    null
                  };
                } catch (IllegalArgumentException e) {
                  return new Object[] {null, e.getMessage()};
                }
              },
              r -> {
                if (r[1] != null) {
                  disp.setText("<html>" + html(r[1].toString()) + "</html>");
                  consultar.run();
                  JOptionPane.showMessageDialog(
                      f.janela, r[1], "Reserva não confirmada", JOptionPane.WARNING_MESSAGE);
                } else {
                  f.janela.dispose();
                  pagina = "Locações";
                  contratos.mostrarContrato(((Locacao) r[0]).idLocacao());
                  atualizar();
                  conta((Locacao) r[0]);
                }
              });
        });
    f.janela.addWindowListener(
        new java.awt.event.WindowAdapter() {
          public void windowClosed(java.awt.event.WindowEvent e) {
            atraso.stop();
            versao[0]++;
          }
        });
    SwingUtilities.invokeLater(consultar);
    f.mostrar();
  }

  void conta(Locacao v) {
    JDialog d = new JDialog(this, "Fechamento · Locação #" + v.idLocacao(), true);
    JPanel p = new JPanel(new BorderLayout(0, 20));
    p.setBorder(new EmptyBorder(28, 32, 28, 32));
    JPanel topo = new JPanel(new GridLayout(0, 1, 0, 8));
    topo.add(
        label(
            "Resumo da locação #" + v.idLocacao() + " · " + v.situacao(service.hoje()), 22, NAVY));
    if (v.status() == StatusLocacao.CANCELADA)
      topo.add(
          label(
              "<html><body style='width:420px'>Cancelada: "
                  + v.canceladaEm().format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm"))
                  + " · "
                  + html(v.motivoCancelamento())
                  + "</body></html>",
              12,
              MUTED));
    if (v.retiradaRealEm() != null)
      topo.add(
          label(
              "Retirada real: "
                  + DATA.format(v.retiradaRealEm())
                  + " · Devolução: "
                  + (v.devolucaoRealEm() == null ? "pendente" : DATA.format(v.devolucaoRealEm()))
                  + (v.devolvidaComAtraso() || v.atrasada(service.hoje()) ? " · ATRASO" : ""),
              12,
              MUTED));
    topo.add(label(cliente(v.idCliente()) + " · " + veiculo(v.idVeiculo()), 14, MUTED));
    topo.add(
        label(
            DATA.format(v.dataRetirada())
                + " a "
                + DATA.format(v.dataDevolucao())
                + " · "
                + v.quantidadeDiarias()
                + (v.quantidadeDiarias() == 1 ? " diária" : " diárias"),
            14,
            MUTED));
    p.add(topo, BorderLayout.NORTH);
    LocalDate hoje = service.hoje();
    boolean atrasada = v.atrasada(hoje);
    p.add(duke(atrasada ? "atraso" : "conta", 100, 120), BorderLayout.EAST);
    String valoresDevolucao =
        v.multaRegistrada() != null
            ? "\nMulta registrada: " + reais(v.multaRegistrada())
                + "\nTotal a pagar: " + reais(v.totalFinal())
            : atrasada
                ? "\nMulta estimada até hoje: " + reais(v.estimarMulta(hoje))
                : "";
    String texto =
        "ESTADO: "
            + v.situacao(service.hoje())
            + (v.devolucaoRegistradaEm() == null
                ? ""
                : "\nRegistro da devolução: "
                    + v.devolucaoRegistradaEm()
                        .format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm")))
            + "\n"
            + (v.status() == StatusLocacao.CANCELADA
                ? "VALOR ORIGINAL DA LOCAÇÃO · cancelada em "
                    + v.canceladaEm().format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm"))
                    + " · Motivo: "
                    + v.motivoCancelamento()
                    + "\n"
                : "")
            + "Retirada real: "
            + (v.retiradaRealEm() == null ? "sem registro" : DATA.format(v.retiradaRealEm()))
            + " · Devolução real: "
            + (v.devolucaoRealEm() == null ? "sem registro" : DATA.format(v.devolucaoRealEm()))
            + "\n\nRETIRADA: "
            + v.localRetirada()
            + "\nDEVOLUÇÃO: "
            + v.localDevolucao()
            + "\n\nVEÍCULO\n"
            + v.quantidadeDiarias()
            + " × "
            + reais(v.diariaVeiculoAplicada())
            + " = "
            + reais(v.subtotalVeiculo())
            + "\n\nGPS "
            + (v.gpsSelecionado() ? "selecionado" : "não selecionado")
            + "\n"
            + v.quantidadeDiarias()
            + " × "
            + reais(v.diariaGpsAplicada())
            + " = "
            + reais(v.subtotalGps())
            + "\n\nSEGURO COMPLETO "
            + (v.seguroSelecionado() ? "selecionado" : "não selecionado")
            + "\n"
            + v.quantidadeDiarias()
            + " × "
            + reais(v.diariaSeguroAplicada())
            + " = "
            + reais(v.subtotalSeguro())
            + "\n\nTarifas registradas na confirmação da locação."
            + valoresDevolucao;
    JTextArea detalhes = new JTextArea(texto, 17, 46);
    detalhes.setEditable(false);
    detalhes.setLineWrap(true);
    detalhes.setWrapStyleWord(true);
    detalhes.setFont(new Font("SansSerif", Font.PLAIN, 14));
    p.add(new JScrollPane(detalhes), BorderLayout.CENTER);
    JPanel rodape = new JPanel(new BorderLayout(12, 12));
    rodape.add(
        label(
            (v.status() == StatusLocacao.CANCELADA
                    ? "Valor original da locação cancelada  "
                    : "TOTAL CONTRATADO  ")
                + reais(v.total()),
            25,
            BLUE),
        BorderLayout.NORTH);
    if (v.multaRegistrada() != null || atrasada) {
      JLabel multa =
          label(
              v.multaRegistrada() != null
                  ? "Multa " + reais(v.multaRegistrada()) + " · Total a pagar " + reais(v.totalFinal())
                  : "Multa estimada até hoje " + reais(v.estimarMulta(hoje)),
              17,
              NAVY);
      rodape.add(multa, BorderLayout.CENTER);
    }
    JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    botoes.add(
        botao(
            "Imprimir",
            () -> {
              try {
                JTextArea imp =
                    new JTextArea(
                        "FALCON LOCADORA\nLocação #"
                            + v.idLocacao()
                            + "\nCliente: "
                            + cliente(v.idCliente())
                            + "\nVeículo: "
                            + veiculo(v.idVeiculo())
                            + "\nPeríodo: "
                            + DATA.format(v.dataRetirada())
                            + " a "
                            + DATA.format(v.dataDevolucao())
                            + "\nDiárias: "
                            + v.quantidadeDiarias()
                            + "\n\n"
                            + texto
                            + "\n\nTOTAL CONTRATADO: "
                            + reais(v.total()));
                imp.print();
              } catch (Exception e) {
                JOptionPane.showMessageDialog(d, "Não foi possível imprimir: " + e.getMessage());
              }
            },
            false));
    botoes.add(botao("Concluir", d::dispose, true));
    rodape.add(botoes, BorderLayout.SOUTH);
    p.add(rodape, BorderLayout.SOUTH);
    d.setContentPane(p);
    d.pack();
    d.setSize(
        d.getWidth(),
        Math.min(
            d.getHeight(),
            GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height));
    d.setLocationRelativeTo(this);
    d.setVisible(true);
  }

  private void exportar() {
    JFileChooser escolha = new JFileChooser();
    escolha.setSelectedFile(new java.io.File("relatorio-frota.csv"));
    if (escolha.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
    Path arquivo = escolha.getSelectedFile().toPath();
    if (Files.exists(arquivo)
        && JOptionPane.showConfirmDialog(
                this, "Substituir o arquivo existente?", "Exportar", JOptionPane.YES_NO_OPTION)
            != JOptionPane.YES_OPTION) return;
    StringBuilder csv = new StringBuilder("\ufeff");
    for (int c = 0; c < tabela.getColumnCount(); c++)
      csv.append(aspas(tabela.getColumnName(c)))
          .append(c + 1 == tabela.getColumnCount() ? "\n" : ";");
    for (int r = 0; r < tabela.getRowCount(); r++)
      for (int c = 0; c < tabela.getColumnCount(); c++)
        csv.append(aspas(textoFrota(tabela.getValueAt(r, c), c)))
            .append(c + 1 == tabela.getColumnCount() ? "\n" : ";");
    executar(
        () -> {
          Files.writeString(arquivo, csv);
          return true;
        },
        r -> JOptionPane.showMessageDialog(this, "Relatório exportado."));
  }

  private static String aspas(String s) {
    if (s.matches("^[=+@\\-\\t\\r].*")) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  static String html(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static final Map<String, ImageIcon> ICONES = new HashMap<>();

  static JLabel duke(String nome, int w, int h) {
    String chave = nome + ":" + w + ":" + h;
    ImageIcon icon = ICONES.get(chave);
    if (icon == null) {
      java.net.URL url = MainWindow.class.getResource("/duke/" + nome + ".png");
      if (url == null) return new JLabel();
      ImageIcon original = new ImageIcon(url);
      double f =
          Math.min((double) w / original.getIconWidth(), (double) h / original.getIconHeight());
      icon =
          new ImageIcon(
              original
                  .getImage()
                  .getScaledInstance(
                      Math.max(1, (int) (original.getIconWidth() * f)),
                      Math.max(1, (int) (original.getIconHeight() * f)),
                      Image.SCALE_SMOOTH));
      ICONES.put(chave, icon);
    }
    JLabel l = new JLabel(icon);
    l.getAccessibleContext().setAccessibleName("Duke · " + nome);
    return l;
  }

  void operacao(String tipo, Locacao l, LocalDate efetiva) {
    String motivo = null;
    if (tipo.equals("cancelar")) {
      motivo =
          JOptionPane.showInputDialog(
              this, "Informe o motivo do cancelamento.", "Motivo do cancelamento",
              JOptionPane.QUESTION_MESSAGE);
      if (motivo == null) return;
    }
    String acao =
        tipo.equals("cancelar")
            ? "Cancelar reserva"
            : tipo.equals("retirar") ? "Registrar retirada" : "Registrar devolução";
    String mensagem = acao + " #" + l.idLocacao() + " em " + DATA.format(efetiva) + "?";
    if (tipo.equals("devolver")) {
      l.validarDataDevolucao(efetiva, service.hoje());
      BigDecimal multa = l.estimarMulta(efetiva);
      mensagem +=
          "\nValor contratado: " + reais(l.total())
              + "\nMulta: " + reais(multa)
              + "\nTotal a pagar: " + reais(l.total().add(multa));
    }
    if (JOptionPane.showConfirmDialog(
            this,
            mensagem,
            acao,
            JOptionPane.YES_NO_OPTION)
        != JOptionPane.YES_OPTION) return;
    String m = motivo;
    executar(
        () -> {
          try {
            if (tipo.equals("cancelar")) service.cancelar(l.idLocacao(), m);
            else if (tipo.equals("retirar")) service.retirar(l.idLocacao());
            else service.devolver(l.idLocacao(), efetiva);
            return "";
          } catch (IllegalArgumentException e) {
            return e.getMessage();
          }
        },
        erro -> {
          atualizar();
          if (!erro.isEmpty())
            JOptionPane.showMessageDialog(
                this, erro, "Operação recusada", JOptionPane.WARNING_MESSAGE);
          else status.setIcon(duke("sucesso", 28, 28).getIcon());
        });
  }

  private void demanda() {
    JPanel area = new JPanel(new BorderLayout(0, 12));
    area.setBackground(Color.WHITE);
    area.setBorder(new EmptyBorder(16, 18, 16, 18));
    JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT));
    filtros.setOpaque(false);
    LocalDate hoje = service.hoje();
    JComboBox<String> mes =
        new JComboBox<>(
            new String[] {
              "Janeiro",
              "Fevereiro",
              "Março",
              "Abril",
              "Maio",
              "Junho",
              "Julho",
              "Agosto",
              "Setembro",
              "Outubro",
              "Novembro",
              "Dezembro"
            });
    mes.setSelectedIndex(hoje.getMonthValue() - 1);
    JSpinner ano = new JSpinner(new SpinnerNumberModel(hoje.getYear(), 1000, 9999, 1));
    ano.setEditor(new JSpinner.NumberEditor(ano, "0000"));
    filtros.add(new JLabel("Mês da retirada prevista"));
    filtros.add(mes);
    filtros.add(new JLabel("Ano"));
    filtros.add(ano);
    JPanel dados = new JPanel(new BorderLayout(0, 10));
    dados.setOpaque(false);
    Runnable carregar =
        () -> {
          try {
            ano.commitEdit();
            YearMonth periodo = YearMonth.of((Integer) ano.getValue(), mes.getSelectedIndex() + 1);
            executar(
                () ->
                    service.locacoes().stream()
                        .filter(
                            l ->
                                l.status() != StatusLocacao.CANCELADA
                                    && YearMonth.from(l.dataRetirada()).equals(periodo))
                        .toList(),
                contratos -> {
                  Map<CategoriaVeiculo, Long> map = new EnumMap<>(CategoriaVeiculo.class);
                  Map<CategoriaVeiculo, int[]> contagens = new EnumMap<>(CategoriaVeiculo.class);
                  for (CategoriaVeiculo cat : CategoriaVeiculo.values()) {
                    map.put(cat, 0L);
                    contagens.put(cat, new int[4]);
                  }
                  for (Locacao l : contratos) {
                    map.merge(l.categoriaAplicada(), 1L, Long::sum);
                    int coluna =
                        l.historico()
                            ? 3
                            : switch (l.status()) {
                              case CONFIRMADA -> 0;
                              case EM_USO -> 1;
                              case CONCLUIDA -> 2;
                              default -> throw new IllegalStateException();
                            };
                    contagens.get(l.categoriaAplicada())[coluna]++;
                  }
                  dados.removeAll();
                  long total = map.values().stream().mapToLong(Long::longValue).sum();
                  dados.add(
                      label(
                          "Retiradas previstas em "
                              + mes.getSelectedItem()
                              + " de "
                              + periodo.getYear()
                              + " · Total: "
                              + total
                              + " · Canceladas excluídas",
                          15,
                          NAVY),
                      BorderLayout.NORTH);
                  DefaultTableModel model =
                      new DefaultTableModel(
                          new String[] {
                            "Categoria",
                            "Confirmadas",
                            "Em uso",
                            "Concluídas",
                            "Históricos",
                            "Total"
                          },
                          0) {
                        public boolean isCellEditable(int r, int c) {
                          return false;
                        }
                      };
                  for (CategoriaVeiculo cat : CategoriaVeiculo.values()) {
                    int[] n = contagens.get(cat);
                    model.addRow(new Object[] {cat, n[0], n[1], n[2], n[3], map.get(cat)});
                  }
                  JPanel centro = new JPanel(new BorderLayout(12, 0));
                  Barras grafico = new Barras(map);
                  grafico.setPreferredSize(new Dimension(300, 240));
                  centro.add(grafico, BorderLayout.WEST);
                  JTable tab = new JTable(model);
                  tab.setRowHeight(32);
                  tab.getColumnModel().getColumn(0).setPreferredWidth(145);
                  centro.add(new JScrollPane(tab), BorderLayout.CENTER);
                  dados.add(centro, BorderLayout.CENTER);
                  dados.add(
                      label(
                          total == 0
                              ? "Nenhuma locação não cancelada neste mês. Todas as categorias têm"
                                  + " valor zero."
                              : "Contratos por categoria na confirmação · Categorias aproximadas na"
                                  + " migração: "
                                  + contratos.stream().filter(Locacao::origemMigracao).count(),
                          12,
                          MUTED),
                      BorderLayout.SOUTH);
                  dados.revalidate();
                  dados.repaint();
                });
          } catch (java.text.ParseException | RuntimeException e) {
            JOptionPane.showMessageDialog(this, "Confira o ano selecionado.");
          }
        };
    filtros.add(botao("Consultar mês", carregar, true));
    area.add(filtros, BorderLayout.NORTH);
    area.add(dados, BorderLayout.CENTER);
    conteudo.add(area, BorderLayout.CENTER);
    SwingUtilities.invokeLater(carregar);
  }

  private static final class Barras extends JPanel {
    private final Map<CategoriaVeiculo, Long> valores;

    Barras(Map<CategoriaVeiculo, Long> m) {
      valores = m;
      setBackground(Color.WHITE);
      setPreferredSize(new Dimension(390, 300));
      getAccessibleContext()
          .setAccessibleName(
              "Gráfico de locações por categoria, com os mesmos valores da tabela ao lado.");
    }

    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D a = (Graphics2D) g.create();
      a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      long max = Math.max(1, valores.values().stream().mapToLong(Long::longValue).max().orElse(1));
      int y = 36;
      for (CategoriaVeiculo cat : CategoriaVeiculo.values()) {
        a.setColor(NAVY);
        a.drawString(cat.toString(), 12, y);
        int largura = (int) ((getWidth() - 65) * valores.get(cat) / max);
        a.setColor(BLUE);
        a.fillRoundRect(12, y + 7, Math.max(0, largura), 20, 8, 8);
        a.setColor(NAVY);
        a.drawString(String.valueOf(valores.get(cat)), 20 + largura, y + 22);
        y += Math.min(65, Math.max(34, (getHeight() - 35) / 5));
      }
      a.dispose();
    }
  }

  /** Disponibiliza as páginas para o roteiro automatizado de apresentação, sem alterar dados. */
  public void mostrarPagina(String nome) {
    pagina = nome;
    render();
  }

  public boolean pronto() {
    return !ocupado;
  }
}
