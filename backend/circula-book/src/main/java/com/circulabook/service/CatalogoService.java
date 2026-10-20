package com.circulabook.service;

import com.circulabook.dto.CadastroDTOs.CategoriaRequest;
import com.circulabook.dto.CadastroDTOs.LivroRequest;
import com.circulabook.model.Categoria;
import com.circulabook.model.Livro;
import com.circulabook.repository.CategoriaRepository;
import com.circulabook.repository.LivroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro do catálogo (RN18): livros e categorias, feitos só pelo Admin
 * (a restrição de perfil fica no SecurityConfig).
 */
@Service
public class CatalogoService {

    @Autowired private LivroRepository livroRepository;
    @Autowired private CategoriaRepository categoriaRepository;

    @Transactional
    public Livro criarLivro(LivroRequest req) {
        return salvarLivro(new Livro(), req);
    }

    @Transactional
    public Livro atualizarLivro(Long id, LivroRequest req) {
        Livro livro = livroRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Livro não encontrado: ID " + id));
        return salvarLivro(livro, req);
    }

    private Livro salvarLivro(Livro livro, LivroRequest req) {
        String titulo = limpar(req.titulo());
        String autor = limpar(req.autor());
        if (titulo == null) throw new RuntimeException("Informe o título do livro.");
        if (autor == null) throw new RuntimeException("Informe o autor do livro.");

        String isbn = limpar(req.isbn());
        if (isbn != null) {
            livroRepository.findByIsbn(isbn)
                .filter(outro -> !outro.getId().equals(livro.getId()))
                .ifPresent(outro -> {
                    throw new RuntimeException("Já existe um livro com o ISBN " + isbn
                        + ": \"" + outro.getTitulo() + "\".");
                });
        }
        if (req.anoPublicacao() != null && (req.anoPublicacao() < 0 || req.anoPublicacao() > 9999)) {
            throw new RuntimeException("Ano de publicação inválido.");
        }

        Categoria categoria = null;
        if (req.categoriaId() != null) {
            categoria = categoriaRepository.findById(req.categoriaId())
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada: ID " + req.categoriaId()));
        }

        livro.setTitulo(titulo);
        livro.setAutor(autor);
        livro.setIsbn(isbn);
        livro.setEditora(limpar(req.editora()));
        livro.setAnoPublicacao(req.anoPublicacao());
        livro.setCategoria(categoria);
        livro.setSinopse(limpar(req.sinopse()));
        return livroRepository.save(livro);
    }

    @Transactional
    public Categoria criarCategoria(CategoriaRequest req) {
        return salvarCategoria(new Categoria(), req);
    }

    @Transactional
    public Categoria atualizarCategoria(Long id, CategoriaRequest req) {
        Categoria categoria = categoriaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Categoria não encontrada: ID " + id));
        return salvarCategoria(categoria, req);
    }

    private Categoria salvarCategoria(Categoria categoria, CategoriaRequest req) {
        String nome = limpar(req.nome());
        if (nome == null) throw new RuntimeException("Informe o nome da categoria.");
        categoriaRepository.findByNomeIgnoreCase(nome)
            .filter(outra -> !outra.getId().equals(categoria.getId()))
            .ifPresent(outra -> { throw new RuntimeException("Já existe a categoria \"" + outra.getNome() + "\"."); });
        categoria.setNome(nome);
        categoria.setDescricao(limpar(req.descricao()));
        return categoriaRepository.save(categoria);
    }

    static String limpar(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
