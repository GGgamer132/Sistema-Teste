package com.circulabook.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Notificação in-app (RN20, §6). Cada linha pertence a UM usuário; eventos para
 * "bibliotecários da biblioteca" ou "Admins" geram uma linha por destinatário.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notificacao", indexes = {
    @Index(name = "idx_notificacao_usuario", columnList = "usuario_id, lida")
})
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 1000)
    private String mensagem;

    // Ver NotificacaoService (constantes TIPO_*)
    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false)
    private Boolean lida = false;

    @Column(nullable = false)
    private LocalDateTime criadaEm;

    // Tela relacionada no front (ex.: /minhas-reservas); opcional
    @Column(length = 200)
    private String link;

    // Id do objeto de origem (empréstimo, reserva...), usado para não repetir avisos da rotina
    @JsonIgnore
    @Column
    private Long referenciaId;

    @PrePersist
    public void preencher() {
        if (this.criadaEm == null) this.criadaEm = LocalDateTime.now();
        if (this.lida == null) this.lida = false;
    }
}
