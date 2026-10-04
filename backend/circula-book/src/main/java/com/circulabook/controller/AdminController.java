package com.circulabook.controller;

import com.circulabook.service.PainelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** Painel do Admin (A1). Relatórios (A10) ficam no mesmo prefixo. Só ADMIN. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private PainelService painelService;

    /** Números da rede agora (cards e tabelas do dashboard). */
    @GetMapping("/dashboard")
    public PainelService.DashboardDTO dashboard() {
        return painelService.dashboard();
    }
}
