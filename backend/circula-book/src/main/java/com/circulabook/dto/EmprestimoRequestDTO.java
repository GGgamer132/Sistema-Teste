package com.circulabook.dto;

/** Corpo do POST /api/emprestimos/registrar (Tela 4). */
public class EmprestimoRequestDTO {

    private Long exemplarId;
    private Long usuarioId;
    private Integer prazoDias; // opcional; padrão 14 (RN02)

    public EmprestimoRequestDTO() {}

    public Long getExemplarId() { return exemplarId; }
    public void setExemplarId(Long exemplarId) { this.exemplarId = exemplarId; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public Integer getPrazoDias() { return prazoDias; }
    public void setPrazoDias(Integer prazoDias) { this.prazoDias = prazoDias; }
}
