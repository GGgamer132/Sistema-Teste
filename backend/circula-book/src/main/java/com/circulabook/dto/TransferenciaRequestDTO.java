package com.circulabook.dto;

/** Corpo do POST /api/transferencias (Tela 8 / RN13). */
public class TransferenciaRequestDTO {

    private Long exemplarId;
    private Long bibliotecaDestinoId;
    private Long solicitanteId;
    private Long reservaId;     // opcional
    private String observacoes; // opcional

    public TransferenciaRequestDTO() {}

    public Long getExemplarId() { return exemplarId; }
    public void setExemplarId(Long exemplarId) { this.exemplarId = exemplarId; }

    public Long getBibliotecaDestinoId() { return bibliotecaDestinoId; }
    public void setBibliotecaDestinoId(Long bibliotecaDestinoId) { this.bibliotecaDestinoId = bibliotecaDestinoId; }

    public Long getSolicitanteId() { return solicitanteId; }
    public void setSolicitanteId(Long solicitanteId) { this.solicitanteId = solicitanteId; }

    public Long getReservaId() { return reservaId; }
    public void setReservaId(Long reservaId) { this.reservaId = reservaId; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
}
