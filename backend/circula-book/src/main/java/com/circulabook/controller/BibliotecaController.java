package com.circulabook.controller;

import com.circulabook.model.Biblioteca;
import com.circulabook.repository.BibliotecaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/bibliotecas")
public class BibliotecaController {

    @Autowired
    private BibliotecaRepository bibliotecaRepository;

    @GetMapping
    public List<Biblioteca> obterTodas() {
        return bibliotecaRepository.findByAtivaTrue();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Biblioteca> obterPorId(@PathVariable Long id) {
        return bibliotecaRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Biblioteca criar(@RequestBody Biblioteca biblioteca) {
        return bibliotecaRepository.save(biblioteca);
    }
}
