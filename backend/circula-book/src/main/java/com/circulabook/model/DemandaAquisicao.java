package com.circulabook.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Quando um usuário busca um livro que não existe no acervo,
 * ele registra interesse. O administrador usa isso para decidir compras (RN07).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "demanda_aquisicao")
public class DemandaAquisicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(length = 255)
    private String autor;

    @Column(length = 20)
    private String isbn;

    @Column(nullable = false)
    private Integer totalSolicitacoes = 1;

    // ABERTA | EM_ANALISE | APROVADA | REJEITADA
    @Column(nullable = false, length = 30)
    private String status = "ABERTA";

    @Column
    private LocalDateTime criadaEm;

    @Column
    private LocalDateTime atualizadaEm;

    @PrePersist
    public void preencheDatas() {
        LocalDateTime agora = LocalDateTime.now();
        if (this.criadaEm == null) this.criadaEm = agora;
        if (this.atualizadaEm == null) this.atualizadaEm = agora;
    }
}
