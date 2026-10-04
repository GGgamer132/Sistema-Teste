package com.circulabook.controller;

import com.circulabook.config.Ator;
import com.circulabook.service.ContaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Dados do próprio usuário COMUM. Não há id na URL nem em parâmetros:
 * o usuário é sempre o do token (RN08).
 */
@RestController
@RequestMapping("/api/conta")
public class ContaController {

    @Autowired
    private ContaService contaService;

    /** Reservas ativas, com posição, prazo de retirada e etapa da transferência. */
    @GetMapping("/reservas")
    public ResponseEntity<?> reservas(@AuthenticationPrincipal Jwt jwt) {
        return responder(() -> contaService.minhasReservas(Ator.de(jwt).id()));
    }

    /** Empréstimos em aberto, total x/3 e bloqueio por atraso (RN12). */
    @GetMapping("/emprestimos")
    public ResponseEntity<?> emprestimos(@AuthenticationPrincipal Jwt jwt) {
        return responder(() -> contaService.meusEmprestimos(Ator.de(jwt).id()));
    }

    /** Empréstimos devolvidos e reservas encerradas, paginado (pagina começa em 0). */
    @GetMapping("/historico")
    public ResponseEntity<?> historico(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(required = false) String tipo,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                       @RequestParam(defaultValue = "0") int pagina,
                                       @RequestParam(defaultValue = "10") int tamanho) {
        return responder(() -> contaService.historico(Ator.de(jwt).id(), tipo, de, ate, pagina, tamanho));
    }

    private ResponseEntity<?> responder(Supplier<Object> acao) {
        try {
            return ResponseEntity.ok(acao.get());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
