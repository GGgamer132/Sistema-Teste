package com.circulabook.controller;

import com.circulabook.service.PainelService;
import com.circulabook.service.RelatorioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Painel do Admin (A1). Relatórios (A10) ficam no mesmo prefixo. Só ADMIN. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private PainelService painelService;
    @Autowired private RelatorioService relatorioService;

    /** Números da rede agora (cards e tabelas do dashboard). */
    @GetMapping("/dashboard")
    public PainelService.DashboardDTO dashboard() {
        return painelService.dashboard();
    }

    /** A10 — os seis relatórios (só tabelas), com período opcional (datas inclusive). */
    @GetMapping("/relatorios")
    public ResponseEntity<?> relatorios(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        try {
            return ResponseEntity.ok(relatorioService.gerar(de, ate));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
