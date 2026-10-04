package com.circulabook.controller;

import com.circulabook.config.Ator;
import com.circulabook.model.Usuario;
import com.circulabook.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @GetMapping
    public List<Usuario> obterTodos() {
        return usuarioRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Usuario> obterPorId(@PathVariable Long id) {
        return usuarioRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /** Tela 4 — autocomplete do campo "buscar usuário" (bibliotecário vê só COMUM). */
    @GetMapping("/busca")
    public List<Usuario> buscar(@RequestParam String termo, @AuthenticationPrincipal Jwt jwt) {
        List<Usuario> achados = usuarioRepository
            .findByNomeContainingIgnoreCaseOrEmailContainingIgnoreCase(termo, termo);
        if (!Ator.de(jwt).ehBibliotecario()) return achados;
        return achados.stream().filter(u -> "COMUM".equals(u.getTipo())).toList();
    }

    @GetMapping("/tipo/{tipo}")
    public List<Usuario> obterPorTipo(@PathVariable String tipo, @AuthenticationPrincipal Jwt jwt) {
        // Bibliotecário só pode listar usuários da comunidade
        String filtro = Ator.de(jwt).ehBibliotecario() ? "COMUM" : tipo.toUpperCase();
        return usuarioRepository.findByTipo(filtro);
    }
}
