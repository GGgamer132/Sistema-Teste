package com.circulabook.dto;

/** Corpo do POST /api/reservas (Tela 5). */
public class ReservaRequestDTO {

    private Long livroId;
    private Long usuarioId;
    private Long bibliotecaDestinoId;

    public ReservaRequestDTO() {}

    public Long getLivroId() { return livroId; }
    public void setLivroId(Long livroId) { this.livroId = livroId; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public Long getBibliotecaDestinoId() { return bibliotecaDestinoId; }
    public void setBibliotecaDestinoId(Long bibliotecaDestinoId) { this.bibliotecaDestinoId = bibliotecaDestinoId; }
}
