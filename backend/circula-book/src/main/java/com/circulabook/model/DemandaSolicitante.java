package com.circulabook.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** Quem pediu cada demanda (RN07): o mesmo usuário não repete interesse na mesma demanda. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "demanda_solicitante",
       uniqueConstraints = @UniqueConstraint(name = "uk_demanda_usuario", columnNames = {"demanda_id", "usuario_id"}))
public class DemandaSolicitante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "demanda_id", nullable = false)
    private DemandaAquisicao demanda;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    public void preencher() {
        if (this.criadoEm == null) this.criadoEm = LocalDateTime.now();
    }
}
