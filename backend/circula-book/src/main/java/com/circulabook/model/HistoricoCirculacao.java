package com.circulabook.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Rastreia cada evento na vida de um exemplar (RN06).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "historico_circulacao")
public class HistoricoCirculacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "exemplar_id", nullable = false)
    private Exemplar exemplar;

    // CADASTRO | EMPRESTIMO | DEVOLUCAO | RESERVA
    // TRANSFERENCIA_SAIDA | TRANSFERENCIA_CHEGADA | BAIXA
    @Column(nullable = false, length = 50)
    private String evento;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "biblioteca_id")
    private Biblioteca biblioteca;

    @Column
    private LocalDateTime dataEvento;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @PrePersist
    public void preencheDataEvento() {
        if (this.dataEvento == null) this.dataEvento = LocalDateTime.now();
    }
}
