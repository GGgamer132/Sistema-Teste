package com.circulabook.controller;

import com.circulabook.config.Ator;
import com.circulabook.dto.TransferenciaAvulsaRequestDTO;
import com.circulabook.model.SolicitacaoTransferencia;
import com.circulabook.service.TransferenciaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transferencias")
public class TransferenciaController {

    @Autowired
    private TransferenciaService transferenciaService;

    /** Admin vê todas; o usuário comum, só as ligadas às próprias reservas. */
    @GetMapping
    public List<SolicitacaoTransferencia> obterTodas(@AuthenticationPrincipal Jwt jwt) {
        Ator ator = Ator.de(jwt);
        List<SolicitacaoTransferencia> todas = transferenciaService.obterTodas();
        if (!ator.ehComum()) return todas;
        return todas.stream()
            .filter(s -> s.getReserva() != null && s.getReserva().getUsuario() != null
                && ator.id().equals(s.getReserva().getUsuario().getId()))
            .toList();
    }

    /** Pedidos aguardando decisão do Admin. */
    @GetMapping("/pendentes")
    public List<SolicitacaoTransferencia> obterPendentes() {
        return transferenciaService.obterPendentes();
    }

    /** Histórico (aprovadas aguardando exemplar, em trânsito, concluídas, rejeitadas, canceladas). */
    @GetMapping("/historico")
    public List<SolicitacaoTransferencia> obterHistorico() {
        return transferenciaService.obterHistorico();
    }

    /** Contadores do painel do Admin. */
    @GetMapping("/resumo")
    public Map<String, Long> obterResumo() {
        return Map.of(
            "pendentes",  transferenciaService.contarPorStatus("PENDENTE"),
            "aguardandoExemplar", transferenciaService.contarPorStatus("APROVADA"),
            "emTransito", transferenciaService.contarPorStatus("EM_TRANSITO"),
            "concluidas", transferenciaService.contarPorStatus("CONCLUIDA"),
            "rejeitadas", transferenciaService.contarPorStatus("REJEITADA")
        );
    }

    /** Transferência avulsa do Admin: nasce aprovada e sem restrição de destino. */
    @PostMapping("/avulsa")
    public ResponseEntity<?> criarAvulsa(@RequestBody TransferenciaAvulsaRequestDTO req,
                                         @AuthenticationPrincipal Jwt jwt) {
        try {
            return ResponseEntity.ok(transferenciaService.criarAvulsa(
                req.getExemplarId(), req.getBibliotecaDestinoId(),
                Ator.de(jwt).id(), req.getObservacoes()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Aprovar (somente ADMIN, RN04). */
    @PatchMapping("/{id}/aprovar")
    public ResponseEntity<?> aprovar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        try {
            return ResponseEntity.ok(transferenciaService.aprovar(id, Ator.de(jwt).id()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Rejeitar (somente ADMIN, RN04). */
    @PatchMapping("/{id}/rejeitar")
    public ResponseEntity<?> rejeitar(@PathVariable Long id,
                                      @RequestParam(required = false) String motivo,
                                      @AuthenticationPrincipal Jwt jwt) {
        try {
            return ResponseEntity.ok(transferenciaService.rejeitar(id, Ator.de(jwt).id(), motivo));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Confirmar chegada no destino (só EM_TRANSITO); o service valida que o bibliotecário é do destino. */
    @PatchMapping("/{id}/confirmar-chegada")
    public ResponseEntity<?> confirmarChegada(@PathVariable Long id,
                                              @AuthenticationPrincipal Jwt jwt) {
        try {
            return ResponseEntity.ok(transferenciaService.confirmarChegada(id, Ator.de(jwt).id()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}