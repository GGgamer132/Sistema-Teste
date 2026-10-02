package com.circulabook.controller;

import com.circulabook.dto.ReservaRequestDTO;
import com.circulabook.model.Reserva;
import com.circulabook.service.FilaEsperaService;
import com.circulabook.service.ReservaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private FilaEsperaService filaEsperaService;

    @GetMapping
    public List<Reserva> obterTodas() {
        return reservaService.obterTodas();
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<?> obterPorUsuario(@PathVariable Long usuarioId) {
        try {
            return ResponseEntity.ok(reservaService.obterPorUsuario(usuarioId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Tela de reserva — "você ocupará a posição N na fila" (por biblioteca). */
    @GetMapping("/posicao/{livroId}")
    public ResponseEntity<?> posicaoNaFila(@PathVariable Long livroId,
                                           @RequestParam(required = false) Long bibliotecaId) {
        try {
            return ResponseEntity.ok(Map.of("posicao", reservaService.posicaoNaFila(livroId, bibliotecaId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** UC03 — Entrar na fila (e, se for o caso, pedir retirada em outra biblioteca). */
    @PostMapping
    public ResponseEntity<?> criar(@RequestBody ReservaRequestDTO req) {
        try {
            return ResponseEntity.ok(reservaService.criar(
                req.getLivroId(), req.getUsuarioId(),
                req.getBibliotecaFilaId(), req.getBibliotecaDestinoId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** UC07 — Cancelar reserva. */
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(reservaService.cancelar(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Dispara a verificação de retiradas vencidas na hora.
     * Útil na demonstração (o prazo real é de 3 dias); em produção o agendador já faz isso a cada minuto.
     */
    @PostMapping("/expirar-vencidas")
    public ResponseEntity<?> expirarVencidas() {
        filaEsperaService.expirarVencidas();
        return ResponseEntity.ok(Map.of("mensagem", "Verificação de reservas vencidas concluída."));
    }
}