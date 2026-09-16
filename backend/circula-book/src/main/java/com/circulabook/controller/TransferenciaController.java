package com.circulabook.controller;

import com.circulabook.dto.TransferenciaRequestDTO;
import com.circulabook.model.SolicitacaoTransferencia;
import com.circulabook.service.TransferenciaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transferencias")
public class TransferenciaController {

    @Autowired
    private TransferenciaService transferenciaService;

    @GetMapping
    public List<SolicitacaoTransferencia> obterTodas() {
        return transferenciaService.obterTodas();
    }

    /** Tela 8 — card 1: solicitações aguardando decisão do Admin. */
    @GetMapping("/pendentes")
    public List<SolicitacaoTransferencia> obterPendentes() {
        return transferenciaService.obterPendentes();
    }

    /** Tela 8 — card 2: histórico recente. */
    @GetMapping("/historico")
    public List<SolicitacaoTransferencia> obterHistorico() {
        return transferenciaService.obterHistorico();
    }

    /** Tela 8 — card lateral com os contadores. */
    @GetMapping("/resumo")
    public Map<String, Long> obterResumo() {
        return Map.of(
            "pendentes",  transferenciaService.contarPorStatus("PENDENTE"),
            "emTransito", transferenciaService.contarPorStatus("EM_TRANSITO"),
            "concluidas", transferenciaService.contarPorStatus("CONCLUIDA"),
            "rejeitadas", transferenciaService.contarPorStatus("REJEITADA")
        );
    }

    /** UC04 — Solicitar transferência (RN13: usuário comum também pode). */
    @PostMapping
    public ResponseEntity<?> solicitar(@RequestBody TransferenciaRequestDTO req) {
        try {
            return ResponseEntity.ok(transferenciaService.solicitar(
                req.getExemplarId(), req.getBibliotecaDestinoId(),
                req.getSolicitanteId(), req.getReservaId(), req.getObservacoes()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** UC13/UC24 — Aprovar (somente ADMIN, RN04). */
    @PatchMapping("/{id}/aprovar")
    public ResponseEntity<?> aprovar(@PathVariable Long id, @RequestParam Long adminId) {
        try {
            return ResponseEntity.ok(transferenciaService.aprovar(id, adminId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** UC13/UC24 — Rejeitar (somente ADMIN, RN04). */
    @PatchMapping("/{id}/rejeitar")
    public ResponseEntity<?> rejeitar(@PathVariable Long id,
                                      @RequestParam Long adminId,
                                      @RequestParam(required = false) String motivo) {
        try {
            return ResponseEntity.ok(transferenciaService.rejeitar(id, adminId, motivo));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** UC15 — Confirmar chegada do exemplar no destino (RN05). */
    @PatchMapping("/{id}/confirmar-chegada")
    public ResponseEntity<?> confirmarChegada(@PathVariable Long id,
                                              @RequestParam Long responsavelId) {
        try {
            return ResponseEntity.ok(transferenciaService.confirmarChegada(id, responsavelId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
