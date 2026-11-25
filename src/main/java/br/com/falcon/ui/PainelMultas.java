package br.com.falcon.ui;

import static br.com.falcon.ui.MainWindow.*;

import br.com.falcon.model.Locacao;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

/** Consulta valores estimados e multas efetivadas na devolução. */
final class PainelMultas {
  private static final String ESTIMADA = "Estimada";
  private static final String REGISTRADA = "Registrada";
  private final MainWindow janela;
  private String filtro = "Todas";

  PainelMultas(MainWindow janela) {
    this.janela = janela;
  }

  JPanel montar(List<Locacao> contratos, LocalDate hoje) {
    List<Locacao> exibidas = new ArrayList<>();
    BigDecimal estimado = BigDecimal.ZERO;
    BigDecimal registrado = BigDecimal.ZERO;
    JPanel painel = new JPanel(new BorderLayout(0, 12));
    painel.setOpaque(false);

    Class<?>[] tipos = {
      Long.class, String.class, String.class, LocalDate.class,
      LocalDate.class, Long.class, BigDecimal.class, String.class
    };
    DefaultTableModel modelo =
        new DefaultTableModel(
            new String[] {
              "Locação",
              "Cliente",
              "Veículo",
              "Prazo",
              "Devolvida em",
              "Dias de atraso",
              "Multa",
              "Situação"
            },
            0) {
          @Override
          public boolean isCellEditable(int linha, int coluna) {
            return false;
          }

          @Override
          public Class<?> getColumnClass(int coluna) {
            return tipos[coluna];
          }
        };
    for (Locacao locacao : contratos) {
      boolean emAtraso = locacao.atrasada(hoje);
      BigDecimal valor = emAtraso ? locacao.estimarMulta(hoje) : locacao.multaRegistrada();
      if (valor == null || valor.signum() <= 0) continue;
      if (emAtraso) estimado = estimado.add(valor);
      else registrado = registrado.add(valor);
      LocalDate devolucao = locacao.devolucaoRealEm();
      exibidas.add(locacao);
      modelo.addRow(
          new Object[] {
            locacao.idLocacao(),
            janela.cliente(locacao.idCliente()),
            janela.veiculo(locacao.idVeiculo()),
            locacao.dataDevolucao(),
            devolucao,
            locacao.diasDeAtraso(emAtraso ? hoje : devolucao),
            valor,
            emAtraso ? ESTIMADA : REGISTRADA
          });
    }

    JPanel resumo = new JPanel(new GridLayout(1, 2, 12, 0));
    resumo.setOpaque(false);
    resumo.add(cartao("Estimadas até hoje", estimado));
    resumo.add(cartao("Registradas nas devoluções", registrado));

    JTable tabela = new JTable(modelo);
    tabela.setRowHeight(36);
    tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    tabela.getTableHeader().setReorderingAllowed(false);
    tabela.getColumnModel().getColumn(1).setPreferredWidth(160);
    tabela.setDefaultRenderer(
        LocalDate.class,
        new DefaultTableCellRenderer() {
          @Override
          protected void setValue(Object valor) {
            setText(valor == null ? "Pendente" : DATA.format((LocalDate) valor));
          }
        });
    tabela.setDefaultRenderer(
        BigDecimal.class,
        new DefaultTableCellRenderer() {
          @Override
          protected void setValue(Object valor) {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setText(reais((BigDecimal) valor));
          }
        });
    TableRowSorter<DefaultTableModel> ordenacao = new TableRowSorter<>(modelo);
    tabela.setRowSorter(ordenacao);
    JComboBox<String> situacao = new JComboBox<>(new String[] {"Todas", ESTIMADA, REGISTRADA});
    situacao.setSelectedItem(filtro);
    JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    filtros.setOpaque(false);
    JLabel rotulo = label("Situação", 13, NAVY);
    rotulo.setLabelFor(situacao);
    filtros.add(rotulo);
    filtros.add(situacao);

    JPanel topo = new JPanel(new BorderLayout(0, 12));
    topo.setOpaque(false);
    topo.add(resumo, BorderLayout.NORTH);
    topo.add(filtros, BorderLayout.SOUTH);
    painel.add(topo, BorderLayout.NORTH);

    JLabel vazio = label("", 14, MUTED);
    vazio.setHorizontalAlignment(SwingConstants.CENTER);
    JScrollPane lista = new JScrollPane(tabela);
    JPanel centro = new JPanel(new BorderLayout());
    centro.setBackground(Color.WHITE);
    centro.add(lista, BorderLayout.CENTER);
    centro.add(vazio, BorderLayout.NORTH);
    painel.add(centro, BorderLayout.CENTER);
    Runnable filtrar =
        () -> {
          tabela.clearSelection();
          ordenacao.setRowFilter(
              filtro.equals("Todas") ? null : RowFilter.regexFilter("^" + filtro + "$", 7));
          boolean semRegistros = tabela.getRowCount() == 0;
          vazio.setText(
              filtro.equals("Todas")
                  ? "Nenhuma multa para acompanhar."
                  : "Nenhuma multa nesta situação.");
          vazio.setBorder(new EmptyBorder(20, 0, 20, 0));
          vazio.setVisible(semRegistros);
          lista.setVisible(!semRegistros);
        };
    situacao.addActionListener(
        evento -> {
          filtro = (String) situacao.getSelectedItem();
          filtrar.run();
        });
    filtrar.run();

    JPanel rodape = new JPanel(new BorderLayout());
    rodape.setOpaque(false);
    rodape.add(
        label("Registrada indica que o valor foi gravado na devolução.", 12, MUTED),
        BorderLayout.CENTER);
    JPanel acoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    acoes.setOpaque(false);
    JButton abrir =
        janela.botao(
            "Abrir locação",
            () -> {
              int linha = tabela.getSelectedRow();
              if (linha >= 0)
                janela.abrirLocacao(exibidas.get(tabela.convertRowIndexToModel(linha)).idLocacao());
            },
            false);
    JButton conta =
        janela.botao(
            "Abrir conta",
            () -> {
              int linha = tabela.getSelectedRow();
              if (linha >= 0) janela.conta(exibidas.get(tabela.convertRowIndexToModel(linha)));
            },
            true);
    for (JButton botao : List.of(abrir, conta)) {
      botao.setEnabled(false);
      acoes.add(botao);
    }
    tabela
        .getSelectionModel()
        .addListSelectionListener(
            evento -> {
              boolean temSelecao = tabela.getSelectedRow() >= 0;
              abrir.setEnabled(temSelecao);
              conta.setEnabled(temSelecao);
            });
    rodape.add(acoes, BorderLayout.EAST);
    painel.add(rodape, BorderLayout.SOUTH);
    return painel;
  }

  private static JPanel cartao(String titulo, BigDecimal valor) {
    JPanel cartao = new JPanel(new BorderLayout(0, 6));
    cartao.setBackground(Color.WHITE);
    cartao.setBorder(new EmptyBorder(13, 16, 13, 16));
    cartao.add(label(titulo, 13, MUTED), BorderLayout.NORTH);
    cartao.add(label(reais(valor), 24, NAVY), BorderLayout.CENTER);
    return cartao;
  }
}
