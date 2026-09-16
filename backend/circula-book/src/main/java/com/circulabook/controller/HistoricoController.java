package com.circulabook.controller;

import com.circulabook.model.HistoricoCirculacao;
import com.circulabook.repository.ExemplarRepository;
import com.circulabook.service.HistoricoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/historico")
public class HistoricoController {

    @Autowired private HistoricoService historicoService;
    @Autowired private ExemplarRepository exemplarRepository;

    /** UC17 — histórico recente de circulação da rede. */
    @GetMapping
    public List<HistoricoCirculacao> obterRecentes() {
        return historicoService.obterRecentes();
    }

    /** UC17 — linha do tempo de um exemplar específico (RN06). */
    @GetMapping("/exemplar/{exemplarId}")
    public ResponseEntity<?> obterPorExemplar(@PathVariable Long exemplarId) {
        return exemplarRepository.findById(exemplarId)
            .<ResponseEntity<?>>map(e -> ResponseEntity.ok(historicoService.obterPorExemplar(e)))
            .orElse(ResponseEntity.badRequest().body("Exemplar não encontrado: ID " + exemplarId));
    }
}
