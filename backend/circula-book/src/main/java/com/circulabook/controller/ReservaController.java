package com.circulabook.controller;

import com.circulabook.config.Ator;
import com.circulabook.dto.ReservaRequestDTO;
import com.circulabook.service.ReservaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    @Autowired
    private ReservaService reservaService;

    /** Tela de reserva — "você ocupará a posição N na fila" da biblioteca. */
    @GetMapping("/posicao/{livroId}")
    public ResponseEntity<?> posicaoNaFila(@PathVariable Long livroId, @RequestParam Long bibliotecaId) {
        try {
            return ResponseEntity.ok(Map.of("posicao", reservaService.posicaoNaFila(livroId, bibliotecaId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** UC03 — Entrar na fila. O usuário é sempre o do token. */
    @PostMapping
    public ResponseEntity<?> criar(@RequestBody ReservaRequestDTO req,
                                   @AuthenticationPrincipal Jwt jwt) {
        try {
            return ResponseEntity.ok(reservaService.criar(
                req.getLivroId(), Ator.de(jwt).id(), req.getBibliotecaId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
