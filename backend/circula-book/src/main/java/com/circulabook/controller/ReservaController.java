package com.circulabook.controller;

import com.circulabook.config.Ator;
import com.circulabook.dto.ReservaRequestDTO;
import com.circulabook.model.Reserva;
import com.circulabook.repository.ReservaRepository;
import com.circulabook.service.FilaEsperaService;
import com.circulabook.service.ReservaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

    @Autowired
    private ReservaRepository reservaRepository;

    @GetMapping
    public List<Reserva> obterTodas() {
        return reservaService.obterTodas();
    }

    /** As reservas do próprio usuário: o ID do caminho é ignorado, vale o do token. */
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<?> obterPorUsuario(@PathVariable Long usuarioId,
                                             @AuthenticationPrincipal Jwt jwt) {
        try {
            return ResponseEntity.ok(reservaService.obterPorUsuario(Ator.de(jwt).id()));
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

    /**
     * Tela de reserva — destinos possíveis para "retirar em outra biblioteca"
     * (sem a própria biblioteca da fila), com o motivo de cada um bloqueado.
     */
    @GetMapping("/destinos")
    public ResponseEntity<?> destinos(@RequestParam Long livroId, @RequestParam Long bibliotecaFilaId) {
        try {
            return ResponseEntity.ok(reservaService.destinosPossiveis(livroId, bibliotecaFilaId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** UC03 — Entrar na fila (e, se for o caso, pedir retirada em outra biblioteca). */
    @PostMapping
    public ResponseEntity<?> criar(@RequestBody ReservaRequestDTO req,
                                   @AuthenticationPrincipal Jwt jwt) {
        try {
            return ResponseEntity.ok(reservaService.criar(
                req.getLivroId(), Ator.de(jwt).id(),
                req.getBibliotecaFilaId(), req.getBibliotecaDestinoId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** UC07 — Cancelar reserva. */
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Long atorId = Ator.de(jwt).id();
        boolean outroDono = reservaRepository.findById(id)
            .map(r -> r.getUsuario() == null || !atorId.equals(r.getUsuario().getId()))
            .orElse(false);
        if (outroDono) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Você só pode cancelar as suas reservas.");
        }
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