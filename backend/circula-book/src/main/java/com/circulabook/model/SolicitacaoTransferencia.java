package com.circulabook.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Equivalente da SUGESTAO_REPOSICAO do sistema-estoque-blackboard.
 * O solicitante pode ser um usuário COMUM (RN13) ou um BIBLIOTECARIO;
 * a aprovação é sempre feita por um ADMIN (RN04).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "solicitacao_transferencia")
public class SolicitacaoTransferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "exemplar_id", nullable = false)
    private Exemplar exemplar;

    @ManyToOne
    @JoinColumn(name = "biblioteca_origem_id", nullable = false)
    private Biblioteca bibliotecaOrigem;

    @ManyToOne
    @JoinColumn(name = "biblioteca_destino_id", nullable = false)
    private Biblioteca bibliotecaDestino;

    @ManyToOne
    @JoinColumn(name = "solicitante_id", nullable = false)
    private Usuario solicitante;

    @ManyToOne
    @JoinColumn(name = "aprovador_id")
    private Usuario aprovador;

    @ManyToOne
    @JoinColumn(name = "reserva_id")
    private Reserva reserva;

    // PENDENTE | APROVADA | EM_TRANSITO | CONCLUIDA | REJEITADA | CANCELADA
    @Column(nullable = false, length = 30)
    private String status = "PENDENTE";

    @Column
    private LocalDateTime dataSolicitacao;

    @Column
    private LocalDateTime dataConclusao;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @PrePersist
    public void preencheDataSolicitacao() {
        if (this.dataSolicitacao == null) this.dataSolicitacao = LocalDateTime.now();
    }
}
