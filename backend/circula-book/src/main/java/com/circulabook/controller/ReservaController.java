package com.circulabook.controller;

import com.circulabook.dto.ReservaRequestDTO;
import com.circulabook.model.Reserva;
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

    /** Tela 5 — mostra "você ocupará a posição N na fila". */
    @GetMapping("/posicao/{livroId}")
    public ResponseEntity<?> posicaoNaFila(@PathVariable Long livroId) {
        try {
            return ResponseEntity.ok(Map.of("posicao", reservaService.posicaoNaFila(livroId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Tela 5 — UC03 Fazer reserva (RN03). */
    @PostMapping
    public ResponseEntity<?> criar(@RequestBody ReservaRequestDTO req) {
        try {
            return ResponseEntity.ok(reservaService.criar(
                req.getLivroId(), req.getUsuarioId(), req.getBibliotecaDestinoId()));
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
}
