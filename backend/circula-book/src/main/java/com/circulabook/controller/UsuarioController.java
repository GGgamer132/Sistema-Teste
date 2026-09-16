package com.circulabook.controller;

import com.circulabook.model.Usuario;
import com.circulabook.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

    /** Tela 4 — autocomplete do campo "buscar usuário". */
    @GetMapping("/busca")
    public List<Usuario> buscar(@RequestParam String termo) {
        return usuarioRepository
            .findByNomeContainingIgnoreCaseOrEmailContainingIgnoreCase(termo, termo);
    }

    @GetMapping("/tipo/{tipo}")
    public List<Usuario> obterPorTipo(@PathVariable String tipo) {
        return usuarioRepository.findByTipo(tipo.toUpperCase());
    }
}
