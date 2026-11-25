package br.com.falcon.model;

/** CPF normalizado nulo identifica documento legado, preservado sem perder vínculos. */
public record Cliente(
    long idCliente, String nome, String documento, String contato, String cpfNormalizado) {
  public Cliente(long id, String nome, String doc, String contato) {
    this(id, nome, doc, contato, null);
  }

  public boolean legado() {
    return cpfNormalizado == null;
  }

  public String documentoResumido() {
    String cpf = Cpf.tentar(documento);
    return cpf == null ? "Documento legado" : "***." + cpf.substring(3, 6) + ".***-**";
  }

  @Override
  public String toString() {
    return nome + (legado() ? " · documento legado" : " · CPF cadastrado");
  }
}
