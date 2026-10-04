package com.circulabook.controller;

import com.circulabook.config.Ator;
import com.circulabook.dto.CadastroExemplarDTO;
import com.circulabook.model.Exemplar;
import com.circulabook.service.ExemplarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/exemplares")
public class ExemplarController {

    @Autowired
    private ExemplarService exemplarService;

    @GetMapping
    public List<Exemplar> obterTodos() {
        return exemplarService.obterTodos();
    }

    @GetMapping("/biblioteca/{bibliotecaId}")
    public ResponseEntity<?> obterPorBiblioteca(@PathVariable Long bibliotecaId) {
        try {
            return ResponseEntity.ok(exemplarService.obterPorBiblioteca(bibliotecaId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/livro/{livroId}")
    public ResponseEntity<?> obterPorLivro(@PathVariable Long livroId) {
        try {
            return ResponseEntity.ok(exemplarService.obterPorLivro(livroId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Telas 4 e 6 — localizar exemplar pelo código de tombo (ex.: EX-00231). */
    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<?> obterPorCodigo(@PathVariable String codigo) {
        try {
            return ResponseEntity.ok(exemplarService.obterPorCodigo(codigo));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Tela 7 — cadastro de exemplar: sempre na biblioteca do bibliotecário logado. */
    @PostMapping
    public ResponseEntity<?> cadastrar(@RequestBody CadastroExemplarDTO dto,
                                       @AuthenticationPrincipal Jwt jwt) {
        Ator ator = Ator.de(jwt);
        dto.setBibliotecaId(ator.bibliotecaId());
        try {
            return ResponseEntity.ok(exemplarService.cadastrar(dto, ator.id()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
