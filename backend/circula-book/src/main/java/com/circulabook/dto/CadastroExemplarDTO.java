package com.circulabook.dto;

/**
 * Corpo do POST /api/exemplares (Tela 7).
 * Se livroId vier preenchido, usa o livro existente.
 * Caso contrário, cria o título novo com os campos abaixo.
 */
public class CadastroExemplarDTO {

    // Opção A: livro já cadastrado
    private Long livroId;

    // Opção B: novo título
    private String titulo;
    private String autor;
    private String editora;
    private String isbn;
    private Integer anoPublicacao;
    private Long categoriaId;
    private String sinopse;

    // Dados do exemplar
    private String codigoBarras;
    private Long bibliotecaId;
    private String estadoConservacao; // NOVO | BOM | USADO

    public CadastroExemplarDTO() {}

    public Long getLivroId() { return livroId; }
    public void setLivroId(Long livroId) { this.livroId = livroId; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getAutor() { return autor; }
    public void setAutor(String autor) { this.autor = autor; }

    public String getEditora() { return editora; }
    public void setEditora(String editora) { this.editora = editora; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public Integer getAnoPublicacao() { return anoPublicacao; }
    public void setAnoPublicacao(Integer anoPublicacao) { this.anoPublicacao = anoPublicacao; }

    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }

    public String getSinopse() { return sinopse; }
    public void setSinopse(String sinopse) { this.sinopse = sinopse; }

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }

    public Long getBibliotecaId() { return bibliotecaId; }
    public void setBibliotecaId(Long bibliotecaId) { this.bibliotecaId = bibliotecaId; }

    public String getEstadoConservacao() { return estadoConservacao; }
    public void setEstadoConservacao(String estadoConservacao) { this.estadoConservacao = estadoConservacao; }
}
