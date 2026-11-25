package br.com.falcon.ui;

import static br.com.falcon.ui.MainWindow.*;

import br.com.falcon.model.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;

/** Lista de contratos e ações de atendimento da seleção atual. */
final class PainelLocacoes {
  private final MainWindow janela;
  private List<Locacao> locacoes = List.of();
  private List<Veiculo> veiculos = List.of();

  PainelLocacoes(MainWindow janela) {
    this.janela = janela;
  }

  void mostrarContrato(long id) {
    contratoAtual = id;
    filtroLocacao = "Todas";
    movimento = MOVIMENTOS[0];
    buscaLocacao = "";
  }

  private String filtroLocacao = "Todas", buscaLocacao = "";
  private long contratoAtual;
  private static final String[] ESTADOS = {
    "Todas", "Confirmadas", "Em uso", "Atrasadas", "Concluídas", "Canceladas"
  };
  private static final String[] MOVIMENTOS = {
    "Todo movimento", "Retiradas pendentes", "Devoluções de hoje", "Em atraso"
  };
  private String movimento = MOVIMENTOS[0];

  static boolean inclui(Locacao l, String filtro, LocalDate dia) {
    return switch (filtro) {
      case "Confirmadas" -> !l.historico() && l.status() == StatusLocacao.CONFIRMADA;
      case "Em uso" -> l.status() == StatusLocacao.EM_USO;
      case "Atrasadas", "Em atraso" -> l.atrasada(dia);
      case "Retiradas pendentes" -> l.retiradaPendente(dia);
      case "Devoluções de hoje" ->
          l.status() == StatusLocacao.EM_USO && l.dataDevolucao().equals(dia);
      case "Concluídas" -> l.status() == StatusLocacao.CONCLUIDA;
      case "Canceladas" -> l.status() == StatusLocacao.CANCELADA;
      default -> true;
    };
  }

  private static String periodoCurto(Locacao l) {
    DateTimeFormatter inicial =
        l.dataRetirada().getYear() == l.dataDevolucao().getYear()
            ? DateTimeFormatter.ofPattern("dd/MM")
            : DATA;
    return inicial.format(l.dataRetirada()) + " a " + DATA.format(l.dataDevolucao());
  }

  private boolean ativo(long id) {
    return veiculos.stream().anyMatch(v -> v.idVeiculo() == id && v.ativo());
  }

  private String situacao(Locacao l, LocalDate dia) {
    return l.situacao(dia)
        + (l.afetaInativacao(dia) && !ativo(l.idVeiculo()) ? " · Veículo inativo" : "");
  }

  JPanel montar(List<Locacao> contratos, List<Veiculo> frota, LocalDate dia) {
    this.locacoes = contratos;
    this.veiculos = frota;
    JPanel area = new JPanel(new BorderLayout(0, 12));
    area.setOpaque(false);
    JPanel cab = new JPanel(new BorderLayout(0, 10));
    cab.setOpaque(false);
    JPanel cards = new JPanel(new GridLayout(1, 4, 12, 0));
    cards.setOpaque(false);
    for (String nome : List.of("Confirmadas", "Em uso", "Atrasadas", "Concluídas")) {
      long n = locacoes.stream().filter(l -> inclui(l, nome, dia)).count();
      boolean alerta = nome.equals("Atrasadas") && n > 0;
      JPanel card =
          superficie(
              new BorderLayout(0, 2), alerta ? new Color(253, 228, 228) : Color.WHITE, 10, 16);
      card.add(
          label(
              nome.equals("Confirmadas") ? "Reservas confirmadas" : nome,
              12,
              alerta ? new Color(153, 27, 27) : MUTED),
          BorderLayout.NORTH);
      JLabel valor = label(String.valueOf(n), 23, alerta ? new Color(153, 27, 27) : NAVY);
      valor.setFont(valor.getFont().deriveFont(Font.BOLD));
      card.add(valor, BorderLayout.CENTER);
      cards.add(card);
    }
    cab.add(cards, BorderLayout.NORTH);
    JPanel filtros = new JPanel(new BorderLayout(8, 0));
    filtros.setOpaque(false);
    JTextField busca = new JTextField(buscaLocacao, 13);
    busca.putClientProperty("JTextField.placeholderText", "Cliente ou veículo");
    busca.getAccessibleContext().setAccessibleName("Buscar locações");
    filtros.add(busca, BorderLayout.WEST);
    JPanel estados = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
    estados.setOpaque(false);
    JComboBox<String> agenda = new JComboBox<>(MOVIMENTOS);
    agenda.setSelectedItem(movimento);
    agenda.setToolTipText("Movimento do dia");
    agenda.getAccessibleContext().setAccessibleName("Movimento do dia");
    filtros.add(agenda, BorderLayout.EAST);
    filtros.add(estados, BorderLayout.CENTER);
    cab.add(filtros, BorderLayout.SOUTH);
    area.add(cab, BorderLayout.NORTH);

    DefaultTableModel modelo =
        new DefaultTableModel(
            new String[] {"Nº", "Cliente", "Veículo", "Período", "Total contratado", "Estado"}, 0) {
          public boolean isCellEditable(int r, int c) {
            return false;
          }
        };
    for (Locacao l : locacoes)
      modelo.addRow(
          new Object[] {
            l.idLocacao(),
            janela.cliente(l.idCliente()),
            janela.veiculo(l.idVeiculo()),
            periodoCurto(l),
            reais(l.total()),
            situacao(l, dia)
          });
    JTable grade =
        new JTable(modelo) {
          public String getToolTipText(java.awt.event.MouseEvent e) {
            int r = rowAtPoint(e.getPoint()), c = columnAtPoint(e.getPoint());
            return r >= 0 && c >= 0 ? String.valueOf(getValueAt(r, c)) : null;
          }
        };
    grade.setName("Tabela de locações");
    grade.setSelectionBackground(new Color(225, 235, 255));
    grade.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    grade.setFillsViewportHeight(true);
    grade.setRowHeight(44);
    grade.getTableHeader().setReorderingAllowed(false);
    int[] larguras = {36, 125, 68, 143, 106, 116};
    grade.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    for (int i = 0; i < larguras.length; i++)
      grade.getColumnModel().getColumn(i).setPreferredWidth(larguras[i]);
    alinharMoeda(grade, 4);
    grade.getColumnModel().getColumn(5).setCellRenderer(new EstadoRenderer());
    TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(modelo);
    sorter.setComparator(0, Comparator.comparingLong(x -> ((Number) x).longValue()));
    grade.setRowSorter(sorter);
    JPanel lista = new JPanel(new CardLayout());
    lista.add(new JScrollPane(grade), "grade");
    JPanel vazio = superficie(new GridBagLayout(), Color.WHITE, 12, 12);
    JLabel mensagem = label("Nenhuma locação nesta situação", 14, MUTED);
    mensagem.setIcon(duke("vazio", 56, 56).getIcon());
    vazio.add(mensagem);
    lista.add(vazio, "vazio");
    JPanel detalhe = new JPanel(new BorderLayout());
    detalhe.setOpaque(false);
    JPanel lateral = detalhe;
    lateral.setPreferredSize(new Dimension(335, 0));
    JPanel corpo = new JPanel(new BorderLayout(12, 0));
    corpo.setOpaque(false);
    corpo.add(lista, BorderLayout.CENTER);
    corpo.add(lateral, BorderLayout.EAST);
    area.add(corpo, BorderLayout.CENTER);
    grade
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (e.getValueIsAdjusting()) return;
              int linha = grade.getSelectedRow();
              Locacao l = linha < 0 ? null : locacoes.get(grade.convertRowIndexToModel(linha));
              if (l != null) contratoAtual = l.idLocacao();
              detalhe.removeAll();
              detalhe.add(detalheLocacao(l, dia), BorderLayout.CENTER);
              detalhe.revalidate();
              detalhe.repaint();
            });
    Runnable filtrar =
        () -> {
          long anterior = contratoAtual;
          String termo = buscaLocacao.strip().toLowerCase(Locale.ROOT);
          sorter.setRowFilter(
              new RowFilter<DefaultTableModel, Integer>() {
                public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> e) {
                  Locacao l = locacoes.get(e.getIdentifier());
                  String texto =
                      l.idLocacao()
                          + " "
                          + e.getStringValue(1)
                          + " "
                          + e.getStringValue(2)
                          + " "
                          + e.getStringValue(5);
                  return inclui(l, filtroLocacao, dia)
                      && inclui(l, movimento, dia)
                      && texto.toLowerCase(Locale.ROOT).contains(termo);
                }
              });
          boolean tem = grade.getRowCount() > 0;
          ((CardLayout) lista.getLayout()).show(lista, tem ? "grade" : "vazio");
          lateral.setVisible(tem);
          if (tem) {
            int pos = 0;
            for (int r = 0; r < grade.getRowCount(); r++)
              if (((Number) grade.getValueAt(r, 0)).longValue() == anterior) pos = r;
            grade.setRowSelectionInterval(pos, pos);
          }
        };
    ButtonGroup grupo = new ButtonGroup();
    for (String nome : ESTADOS) {
      long n = locacoes.stream().filter(l -> inclui(l, nome, dia)).count();
      JToggleButton b = new JToggleButton(nome + " · " + n);
      b.putClientProperty("JButton.buttonType", "roundRect");
      b.putClientProperty(
          "FlatLaf.style",
          "selectedBackground: #12203A; selectedForeground: #FFFFFF; margin: 2,6,2,6");
      b.setPreferredSize(new Dimension(b.getPreferredSize().width, 44));
      grupo.add(b);
      estados.add(b);
      b.setSelected(nome.equals(filtroLocacao));
      b.addActionListener(
          e -> {
            filtroLocacao = nome;
            filtrar.run();
          });
    }
    agenda.addActionListener(
        e -> {
          movimento = (String) agenda.getSelectedItem();
          filtrar.run();
        });
    busca
        .getDocument()
        .addDocumentListener(
            new javax.swing.event.DocumentListener() {
              void mudou() {
                buscaLocacao = busca.getText();
                filtrar.run();
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
            });
    filtrar.run();
    return area;
  }

  private JPanel detalheLocacao(Locacao l, LocalDate dia) {
    JPanel painel = superficie(new GridBagLayout(), Color.WHITE, 6, 12);
    if (l == null) return painel;
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets = new Insets(0, 0, 3, 0);
    Consumer<JComponent> linha =
        item -> {
          painel.add(item, c);
          c.gridy++;
        };
    linha.accept(label("LOCAÇÃO " + l.idLocacao(), 11, MUTED));
    JLabel nome = label(janela.cliente(l.idCliente()), 20, NAVY);
    nome.setToolTipText(nome.getText());
    linha.accept(nome);
    linha.accept(label(janela.veiculo(l.idVeiculo()) + " · " + l.categoriaAplicada(), 13, MUTED));
    String aviso = situacao(l, dia);
    if (l.atrasada(dia)) {
      long atraso = l.diasDeAtraso(dia);
      aviso =
          "Atrasada há "
              + atraso
              + (atraso == 1 ? " dia" : " dias")
              + "\nPrevisão "
              + DATA.format(l.dataDevolucao())
              + (ativo(l.idVeiculo()) ? "" : " · Veículo inativo");
    }
    JLabel estado =
        label(
            "<html>" + html(aviso).replace("\n", "<br>") + "</html>",
            12,
            l.atrasada(dia) ? new Color(153, 27, 27) : NAVY);
    estado.setOpaque(true);
    estado.setBackground(corEstado(aviso));
    estado.setBorder(new EmptyBorder(6, 8, 6, 8));
    if (l.atrasada(dia)) {
      estado.setIcon(duke("atraso", 32, 32).getIcon());
      estado.setIconTextGap(8);
    }
    linha.accept(estado);
    if (l.atrasada(dia)) {
      linha.accept(
          valorLinha(
              "Multa estimada", l.diasDeAtraso(dia), l.diariaVeiculoAplicada(), l.estimarMulta(dia)));
    }
    linha.accept(
        valorLinha(
            "Veículo", l.quantidadeDiarias(), l.diariaVeiculoAplicada(), l.subtotalVeiculo()));
    linha.accept(valorLinha("GPS", l.quantidadeDiarias(), l.diariaGpsAplicada(), l.subtotalGps()));
    linha.accept(
        valorLinha("Seguro", l.quantidadeDiarias(), l.diariaSeguroAplicada(), l.subtotalSeguro()));
    linha.accept(new JSeparator());
    JPanel total = new JPanel(new BorderLayout());
    total.setOpaque(false);
    total.add(
        label(
            l.status() == StatusLocacao.CANCELADA ? "Valor original" : "Total contratado",
            13,
            NAVY),
        BorderLayout.WEST);
    total.add(label(reais(l.total()), 19, BLUE), BorderLayout.EAST);
    total.setBorder(new EmptyBorder(4, 12, 4, 12));
    if (l.multaRegistrada() != null) {
      linha.accept(label("Multa registrada: " + reais(l.multaRegistrada()), 12, MUTED));
      linha.accept(label("Total a pagar: " + reais(l.totalFinal()), 13, NAVY));
    }
    boolean emUso = l.status() == StatusLocacao.EM_USO,
        confirmada = l.status() == StatusLocacao.CONFIRMADA && !l.historico();
    JTextField efetiva = new JTextField(DATA.format(dia));
    if (l.devolucaoRealEm() != null)
      linha.accept(label("Devolvida em " + DATA.format(l.devolucaoRealEm()), 12, MUTED));
    JButton operar =
        janela.botao(
            emUso ? "Registrar devolução" : "Registrar retirada",
            () -> {
              try {
                janela.operacao(
                    emUso ? "devolver" : "retirar", l, emUso ? data(efetiva.getText()) : dia);
              } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(janela, e.getMessage());
                efetiva.requestFocusInWindow();
              }
            },
            true);
    boolean retiradaLivre =
        l.retiradaPendente(dia)
            && ativo(l.idVeiculo())
            && locacoes.stream()
                .noneMatch(
                    o ->
                        o.idLocacao() != l.idLocacao()
                            && o.idVeiculo() == l.idVeiculo()
                            && (o.status() == StatusLocacao.EM_USO
                                || dia.equals(o.devolucaoRealEm())));
    operar.setEnabled(emUso || retiradaLivre);
    if (!operar.isEnabled())
      operar.setToolTipText(
          "A retirada exige reserva dentro do período e veículo ativo e livre hoje.");
    JPanel acoes = new JPanel(new GridLayout(0, 1, 0, 4));
    acoes.setOpaque(false);
    acoes.setBorder(new EmptyBorder(5, 10, 8, 10));
    acoes.add(operar);
    acoes.add(janela.botao("Abrir conta completa", () -> janela.conta(l), false));
    JButton cancelar =
        janela.botao("Cancelar reserva", () -> janela.operacao("cancelar", l, dia), false);
    cancelar.setEnabled(confirmada);
    if (!confirmada) cancelar.setToolTipText("Somente reservas confirmadas podem ser canceladas.");
    acoes.add(cancelar);
    JPanel topo = new JPanel(new BorderLayout());
    topo.setBackground(Color.WHITE);
    JPanel ficha = new JPanel(new BorderLayout());
    ficha.add(painel, BorderLayout.NORTH);
    topo.add(ficha, BorderLayout.NORTH);
    JScrollPane scroll = new JScrollPane(topo);
    scroll.setBorder(null);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    JPanel bloco = superficie(new BorderLayout(), Color.WHITE, 0, 0);
    bloco.add(scroll, BorderLayout.CENTER);
    JPanel rodape = new JPanel(new BorderLayout());
    rodape.setBackground(Color.WHITE);
    if (emUso) {
      JPanel datas = new JPanel(new BorderLayout(0, 3));
      datas.setOpaque(false);
      datas.setBorder(new EmptyBorder(3, 10, 0, 10));
      JLabel titulo = label("Data efetiva da devolução", 12, MUTED);
      titulo.setLabelFor(efetiva);
      efetiva.setToolTipText("De " + DATA.format(l.retiradaRealEm()) + " a " + DATA.format(dia));
      datas.add(titulo, BorderLayout.NORTH);
      datas.add(efetiva, BorderLayout.CENTER);
      rodape.add(datas, BorderLayout.NORTH);
    }
    rodape.add(acoes, BorderLayout.CENTER);
    ficha.add(rodape, BorderLayout.CENTER);
    bloco.add(total, BorderLayout.SOUTH);
    return bloco;
  }

  private static JPanel valorLinha(String nome, long dias, BigDecimal diaria, BigDecimal subtotal) {
    JPanel p = new JPanel(new BorderLayout());
    p.setOpaque(false);
    p.add(label(nome + " · " + dias + " × " + reais(diaria), 12, MUTED), BorderLayout.WEST);
    p.add(label(reais(subtotal), 12, NAVY), BorderLayout.EAST);
    return p;
  }

  private static Color corEstado(String nome) {
    if (nome.contains("Atrasada")) return new Color(253, 228, 228);
    if (nome.startsWith("Confirmada")) return new Color(224, 233, 255);
    if (nome.startsWith("Em uso")) return new Color(221, 245, 230);
    return new Color(231, 234, 240);
  }

  private static final class EstadoRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(
        JTable t, Object valor, boolean selecionado, boolean foco, int r, int c) {
      super.getTableCellRendererComponent(t, valor, selecionado, foco, r, c);

      JPanel fundo = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 9));
      fundo.setBackground(selecionado ? t.getSelectionBackground() : t.getBackground());
      JLabel chip = new JLabel(valor.toString());
      chip.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
      chip.setOpaque(true);
      chip.setBackground(corEstado(valor.toString()));
      chip.setForeground(NAVY);
      chip.setBorder(new EmptyBorder(3, 6, 3, 6));
      fundo.add(chip);
      return fundo;
    }
  }

  private static JPanel superficie(
      LayoutManager layout, Color fundo, int vertical, int horizontal) {
    JPanel p = new JPanel(layout);
    p.setBackground(fundo);
    p.setBorder(
        BorderFactory.createCompoundBorder(
            new com.formdev.flatlaf.ui.FlatLineBorder(
                new Insets(0, 0, 0, 0), new Color(220, 227, 237), 1, 14),
            new EmptyBorder(vertical, horizontal, vertical, horizontal)));
    return p;
  }
}
