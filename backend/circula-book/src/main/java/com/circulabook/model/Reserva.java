package com.circulabook.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * O usuário reserva um TÍTULO (não um exemplar específico)
 * para retirada em uma biblioteca de sua preferência.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reserva")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "livro_id", nullable = false)
    private Livro livro;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "biblioteca_destino_id", nullable = false)
    private Biblioteca bibliotecaDestino;

    @Column
    private LocalDateTime dataReserva;

    @Column(nullable = false)
    private LocalDateTime dataExpiracao;

    // PENDENTE | DISPONIVEL | RETIRADA | CANCELADA | EXPIRADA
    @Column(nullable = false, length = 30)
    private String status;

    @PrePersist
    public void preencheDataReserva() {
        if (this.dataReserva == null) this.dataReserva = LocalDateTime.now();
    }
}
