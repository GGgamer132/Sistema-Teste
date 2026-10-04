package com.circulabook.controller;

import com.circulabook.config.Ator;
import com.circulabook.service.DemandaService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;

/** Demandas de aquisição (RN07): interesse do COMUM; listagem e decisão do ADMIN. */
@RestController
@RequestMapping("/api/demandas")
public class DemandaController {

    @Autowired
    private DemandaService demandaService;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InteresseRequest(String titulo, String autor) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusRequest(String status) {}

    /** U8 — registrar interesse (usuário do token). */
    @PostMapping
    public ResponseEntity<?> registrar(@RequestBody InteresseRequest req, @AuthenticationPrincipal Jwt jwt) {
        return responder(() -> demandaService.registrarInteresse(Ator.de(jwt).id(), req.titulo(), req.autor()));
    }

    /** U8 — interesses registrados pelo próprio usuário. */
    @GetMapping("/minhas")
    public ResponseEntity<?> minhas(@AuthenticationPrincipal Jwt jwt) {
        return responder(() -> demandaService.meusInteresses(Ator.de(jwt).id()));
    }

    /** A9 — mais pedidas primeiro, com filtro por situação (pagina começa em 0). */
    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "0") int pagina,
                                    @RequestParam(defaultValue = "10") int tamanho) {
        return responder(() -> demandaService.listar(status, pagina, tamanho));
    }

    /** A9 — ABERTA -> EM_ANALISE -> APROVADA | REJEITADA. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> alterarStatus(@PathVariable Long id, @RequestBody StatusRequest req) {
        return responder(() -> demandaService.alterarStatus(id, req.status()));
    }

    private ResponseEntity<?> responder(Supplier<Object> acao) {
        try {
            return ResponseEntity.ok(acao.get());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
