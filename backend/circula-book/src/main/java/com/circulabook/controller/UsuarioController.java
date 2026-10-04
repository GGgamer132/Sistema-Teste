package com.circulabook.controller;

import com.circulabook.config.Ator;
import com.circulabook.dto.CadastroDTOs.BibliotecarioRequest;
import com.circulabook.model.Usuario;
import com.circulabook.repository.UsuarioRepository;
import com.circulabook.service.RedeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RedeService redeService;

    /** A6 — bibliotecários da rede (só ADMIN). */
    @GetMapping("/bibliotecarios")
    public List<Usuario> listarBibliotecarios() {
        return redeService.listarBibliotecarios();
    }

    /** A6 — cadastrar bibliotecário com biblioteca obrigatória e senha inicial (só ADMIN). */
    @PostMapping("/bibliotecarios")
    public ResponseEntity<?> criarBibliotecario(@RequestBody BibliotecarioRequest req) {
        return responder(() -> redeService.criarBibliotecario(req));
    }

    @PatchMapping("/bibliotecarios/{id}/desativar")
    public ResponseEntity<?> desativarBibliotecario(@PathVariable Long id) {
        return responder(() -> redeService.definirBibliotecarioAtivo(id, false));
    }

    @PatchMapping("/bibliotecarios/{id}/ativar")
    public ResponseEntity<?> ativarBibliotecario(@PathVariable Long id) {
        return responder(() -> redeService.definirBibliotecarioAtivo(id, true));
    }

    private ResponseEntity<?> responder(Supplier<?> acao) {
        try {
            return ResponseEntity.ok(acao.get());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

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
