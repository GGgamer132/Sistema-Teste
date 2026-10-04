package com.circulabook.controller;

import com.circulabook.config.Ator;
import com.circulabook.dto.CadastroExemplarDTO;
import com.circulabook.model.Exemplar;
import com.circulabook.service.ExemplarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    /**
     * T13 — marcar o exemplar como indisponível (só DISPONIVEL ou RESERVADO).
     * Corpo opcional: {"motivo": "..."}. Só o bibliotecário da biblioteca do exemplar.
     */
    @PatchMapping("/{id}/indisponivel")
    public ResponseEntity<?> marcarIndisponivel(@PathVariable Long id,
                                                @RequestBody(required = false) Map<String, String> corpo,
                                                @AuthenticationPrincipal Jwt jwt) {
        Ator ator = Ator.de(jwt);
        try {
            ResponseEntity<?> negado = negarSeOutraBiblioteca(id, ator);
            if (negado != null) return negado;
            String motivo = corpo != null ? corpo.get("motivo") : null;
            return ResponseEntity.ok(exemplarService.marcarIndisponivel(id, ator.id(), motivo));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** T14/T14b — reativar um exemplar INDISPONIVEL (com fila do título, já fica RESERVADO). */
    @PatchMapping("/{id}/reativar")
    public ResponseEntity<?> reativar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Ator ator = Ator.de(jwt);
        try {
            ResponseEntity<?> negado = negarSeOutraBiblioteca(id, ator);
            if (negado != null) return negado;
            return ResponseEntity.ok(exemplarService.reativar(id, ator.id()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** O bibliotecário só gerencia exemplares da própria biblioteca. */
    private ResponseEntity<?> negarSeOutraBiblioteca(Long exemplarId, Ator ator) {
        Exemplar ex = exemplarService.buscar(exemplarId);
        if (ex.getBiblioteca() == null || !Objects.equals(ex.getBiblioteca().getId(), ator.bibliotecaId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Só é possível gerenciar exemplares da sua biblioteca.");
        }
        return null;
    }

    /**
     * B4 — cadastrar exemplares de livro existente na biblioteca do bibliotecário logado.
     * Corpo: {livroId, conservacao (NOVO|BOM|USADO), quantidade (1-50)}; devolve a lista criada.
     * Informar bibliotecaId de outra biblioteca -> 403.
     */
    @PostMapping
    public ResponseEntity<?> cadastrar(@RequestBody CadastroExemplarDTO dto,
                                       @AuthenticationPrincipal Jwt jwt) {
        Ator ator = Ator.de(jwt);
        if (dto.getBibliotecaId() != null && !Objects.equals(dto.getBibliotecaId(), ator.bibliotecaId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Só é possível cadastrar exemplares na sua biblioteca.");
        }
        try {
            return ResponseEntity.ok(exemplarService.cadastrar(dto, ator.id()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
