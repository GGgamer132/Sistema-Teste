package com.circulabook.controller;

import com.circulabook.dto.DevolucaoRequestDTO;
import com.circulabook.dto.EmprestimoRequestDTO;
import com.circulabook.model.Emprestimo;
import com.circulabook.service.EmprestimoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/emprestimos")
public class EmprestimoController {

    @Autowired
    private EmprestimoService emprestimoService;

    @GetMapping
    public List<Emprestimo> obterTodos() {
        return emprestimoService.obterTodos();
    }

    /** Tela 6 — lista de empréstimos em aberto, para escolher qual devolver. */
    @GetMapping("/ativos")
    public List<Emprestimo> obterAtivos() {
        return emprestimoService.obterAtivos();
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<?> obterPorUsuario(@PathVariable Long usuarioId) {
        try {
            return ResponseEntity.ok(emprestimoService.obterPorUsuario(usuarioId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Tela 4 — painel de alertas: o usuário está apto? (RN01/RN12) */
    @GetMapping("/situacao/{usuarioId}")
    public ResponseEntity<?> consultarSituacao(@PathVariable Long usuarioId) {
        try {
            return ResponseEntity.ok(emprestimoService.consultarSituacao(usuarioId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Tela 4 — UC09 Registrar empréstimo. */
    @PostMapping("/registrar")
    public ResponseEntity<?> registrar(@RequestBody EmprestimoRequestDTO req) {
        try {
            return ResponseEntity.ok(emprestimoService.registrar(
                req.getExemplarId(), req.getUsuarioId(), req.getPrazoDias()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Tela 6 — UC10 Registrar devolução. */
    @PostMapping("/devolver")
    public ResponseEntity<?> devolver(@RequestBody DevolucaoRequestDTO req) {
        try {
            return ResponseEntity.ok(emprestimoService.devolver(
                req.getEmprestimoId(), req.getCondicaoExemplar()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
