package com.circulabook.controller;

import com.circulabook.dto.TransferenciaAvulsaRequestDTO;
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
    public ResponseEntity<?> criarAvulsa(@RequestBody TransferenciaAvulsaRequestDTO req) {
        try {
            return ResponseEntity.ok(transferenciaService.criarAvulsa(
                req.getExemplarId(), req.getBibliotecaDestinoId(),
                req.getAdminId(), req.getObservacoes()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Aprovar (somente ADMIN, RN04). */
    @PatchMapping("/{id}/aprovar")
    public ResponseEntity<?> aprovar(@PathVariable Long id, @RequestParam Long adminId) {
        try {
            return ResponseEntity.ok(transferenciaService.aprovar(id, adminId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Rejeitar (somente ADMIN, RN04). */
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

    /** Confirmar chegada do exemplar no destino (só transferências EM_TRANSITO). */
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