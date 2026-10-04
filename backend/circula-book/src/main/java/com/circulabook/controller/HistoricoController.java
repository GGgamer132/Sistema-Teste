package com.circulabook.controller;

import com.circulabook.service.HistoricoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Histórico de circulação da rede (RN06, A4) — só ADMIN. */
@RestController
@RequestMapping("/api/historico")
public class HistoricoController {

    @Autowired private HistoricoService historicoService;

    /** Eventos recentes, mais novos primeiro (pagina começa em 0); filtros opcionais. */
    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(required = false) String evento,
                                    @RequestParam(required = false) Long bibliotecaId,
                                    @RequestParam(required = false) Long exemplarId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                    @RequestParam(defaultValue = "0") int pagina,
                                    @RequestParam(defaultValue = "20") int tamanho) {
        try {
            return ResponseEntity.ok(historicoService.listar(evento, bibliotecaId, exemplarId, de, ate, pagina, tamanho));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Eventos possíveis com o nome legível (para o filtro da tela). */
    @GetMapping("/eventos")
    public List<Map<String, String>> eventos() {
        return HistoricoService.ROTULO_EVENTO.entrySet().stream()
            .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
            .map(e -> Map.of("codigo", e.getKey(), "rotulo", e.getValue()))
            .toList();
    }

    /** Linha do tempo de um exemplar, do mais antigo ao mais recente. */
    @GetMapping("/exemplar/{exemplarId}")
    public ResponseEntity<?> linhaDoTempo(@PathVariable Long exemplarId) {
        try {
            return ResponseEntity.ok(historicoService.linhaDoTempo(exemplarId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
