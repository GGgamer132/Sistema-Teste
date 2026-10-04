package com.circulabook.dto;

/** Corpo do POST /api/transferencias/avulsa (somente ADMIN). */
public class TransferenciaAvulsaRequestDTO {

    private Long exemplarId;
    private Long bibliotecaDestinoId;
    private String observacoes; // opcional

    public TransferenciaAvulsaRequestDTO() {}

    public Long getExemplarId() { return exemplarId; }
    public void setExemplarId(Long exemplarId) { this.exemplarId = exemplarId; }

    public Long getBibliotecaDestinoId() { return bibliotecaDestinoId; }
    public void setBibliotecaDestinoId(Long bibliotecaDestinoId) { this.bibliotecaDestinoId = bibliotecaDestinoId; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
}