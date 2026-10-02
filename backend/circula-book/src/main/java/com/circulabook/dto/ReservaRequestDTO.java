package com.circulabook.dto;

/** Corpo do POST /api/reservas. */
public class ReservaRequestDTO {

    private Long livroId;
    private Long usuarioId;
    private Long bibliotecaFilaId;      // onde o usuário entra na fila
    private Long bibliotecaDestinoId;   // onde o usuário vai retirar

    public ReservaRequestDTO() {}

    public Long getLivroId() { return livroId; }
    public void setLivroId(Long livroId) { this.livroId = livroId; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public Long getBibliotecaFilaId() { return bibliotecaFilaId; }
    public void setBibliotecaFilaId(Long bibliotecaFilaId) { this.bibliotecaFilaId = bibliotecaFilaId; }

    public Long getBibliotecaDestinoId() { return bibliotecaDestinoId; }
    public void setBibliotecaDestinoId(Long bibliotecaDestinoId) { this.bibliotecaDestinoId = bibliotecaDestinoId; }
}