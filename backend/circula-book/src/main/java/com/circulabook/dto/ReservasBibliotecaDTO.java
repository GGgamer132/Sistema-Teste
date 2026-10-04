package com.circulabook.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/reservas/biblioteca (B6): reservas prontas para retirada na biblioteca do
 * bibliotecário logado e as filas de espera dela, por título.
 */
public record ReservasBibliotecaDTO(List<AguardandoRetirada> aguardandoRetirada, List<FilaTitulo> filas) {

    public record AguardandoRetirada(Long reservaId, Long usuarioId, String usuario, String email,
                                     Long livroId, String titulo, Long exemplarId,
                                     LocalDateTime retireAte) {}

    public record NaFila(int posicao, Long reservaId, Long usuarioId, String usuario,
                         LocalDateTime dataReserva, String bibliotecaRetirada) {}

    public record FilaTitulo(Long livroId, String titulo, List<NaFila> fila) {}
}
