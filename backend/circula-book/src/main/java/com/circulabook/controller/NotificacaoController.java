package com.circulabook.controller;

import com.circulabook.config.Ator;
import com.circulabook.service.NotificacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Notificações in-app (§6). Sempre do usuário do token, qualquer perfil;
 * a notificação de outro usuário é tratada como inexistente (404).
 */
@RestController
@RequestMapping("/api/notificacoes")
public class NotificacaoController {

    @Autowired
    private NotificacaoService notificacaoService;

    /** Mais recentes primeiro; pagina começa em 0. */
    @GetMapping
    public ResponseEntity<?> listar(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(defaultValue = "0") int pagina,
                                    @RequestParam(defaultValue = "20") int tamanho) {
        try {
            return ResponseEntity.ok(notificacaoService.listar(Ator.de(jwt).id(), pagina, tamanho));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/nao-lidas/contagem")
    public Map<String, Long> contagem(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("naoLidas", notificacaoService.contarNaoLidas(Ator.de(jwt).id()));
    }

    @PatchMapping("/{id}/lida")
    public ResponseEntity<?> marcarLida(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        try {
            return ResponseEntity.ok(notificacaoService.marcarLida(id, Ator.de(jwt).id()));
        } catch (NotificacaoService.NaoEncontradaException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PatchMapping("/marcar-todas-lidas")
    public Map<String, Integer> marcarTodas(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("marcadas", notificacaoService.marcarTodasLidas(Ator.de(jwt).id()));
    }

    /**
     * Dispara na hora a rotina de "vence em 2 dias" / "atrasado" (só ADMIN; demonstração e testes).
     * O agendador já roda a cada minuto e a rotina não repete avisos.
     */
    @PostMapping("/verificar-vencimentos")
    public Map<String, Integer> verificarVencimentos() {
        return Map.of("criadas", notificacaoService.verificarVencimentos());
    }
}
