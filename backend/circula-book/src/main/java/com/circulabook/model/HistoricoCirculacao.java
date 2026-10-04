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

    // CADASTRO | EMPRESTIMO | DEVOLUCAO | RESERVA | FILA | BAIXA | REATIVACAO
    // TRANSFERENCIA_SAIDA | TRANSFERENCIA_CHEGADA
    @Column(nullable = false, length = 50)
    private String evento;

    // Responsável pelo evento (nulo nas rotinas agendadas)
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

    // Nome de quem fez (usuário do token) ou "Sistema" nas rotinas agendadas.
    // Linhas antigas do seed não têm: o nome sai do usuário vinculado.
    @Column(length = 255)
    private String responsavel;

    @PrePersist
    public void preencheDataEvento() {
        if (this.dataEvento == null) this.dataEvento = LocalDateTime.now();
    }
}
