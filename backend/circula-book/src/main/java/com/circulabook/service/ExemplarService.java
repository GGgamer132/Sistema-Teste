package com.circulabook.service;

import com.circulabook.dto.CadastroExemplarDTO;
import com.circulabook.model.*;
import com.circulabook.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cadastro de exemplares (UC11 / RN10 / RN11) — Tela 7.
 */
@Service
public class ExemplarService {

    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private LivroRepository livroRepository;
    @Autowired private BibliotecaRepository bibliotecaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private HistoricoService historicoService;

    public List<Exemplar> obterTodos() {
        return exemplarRepository.findAll();
    }

    public List<Exemplar> obterPorBiblioteca(Long bibliotecaId) {
        Biblioteca b = bibliotecaRepository.findById(bibliotecaId)
            .orElseThrow(() -> new RuntimeException("Biblioteca não encontrada: ID " + bibliotecaId));
        return exemplarRepository.findByBiblioteca(b);
    }

    public List<Exemplar> obterPorLivro(Long livroId) {
        Livro l = livroRepository.findById(livroId)
            .orElseThrow(() -> new RuntimeException("Livro não encontrado: ID " + livroId));
        return exemplarRepository.findByLivro(l);
    }

    /** Busca por código de tombo — usado nas telas de empréstimo e devolução. */
    public Exemplar obterPorCodigo(String codigo) {
        return exemplarRepository.findByCodigoBarras(codigo)
            .orElseThrow(() -> new RuntimeException("Exemplar não encontrado: " + codigo));
    }

    /**
     * Cadastra um exemplar, criando o título antes caso seja um livro novo.
     *
     * RN10: exige o ID do bibliotecário responsável e valida que ele pertence
     *       à biblioteca onde o exemplar será cadastrado.
     * RN11: se for o único exemplar do título na rede, nasce INDISPONIVEL.
     */
    @Transactional
    public Exemplar cadastrar(CadastroExemplarDTO dto, Long bibliotecarioId) {

        Usuario bibliotecario = usuarioRepository.findById(bibliotecarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + bibliotecarioId));

        Biblioteca biblioteca = bibliotecaRepository.findById(dto.getBibliotecaId())
            .orElseThrow(() -> new RuntimeException("Biblioteca não encontrada: ID " + dto.getBibliotecaId()));

        // RN10 — só um bibliotecário da própria biblioteca (ou o admin) pode cadastrar
        boolean ehAdmin = "ADMIN".equals(bibliotecario.getTipo());
        boolean ehBibliotecarioDaCasa = "BIBLIOTECARIO".equals(bibliotecario.getTipo())
            && bibliotecario.getBiblioteca() != null
            && bibliotecario.getBiblioteca().getId().equals(biblioteca.getId());

        if (!ehAdmin && !ehBibliotecarioDaCasa) {
            throw new RuntimeException(
                "RN10: apenas um bibliotecário da " + biblioteca.getNome()
                + " pode cadastrar exemplares nesta biblioteca.");
        }

        Livro livro = resolverLivro(dto);

        if (dto.getCodigoBarras() != null && !dto.getCodigoBarras().isBlank()
            && exemplarRepository.findByCodigoBarras(dto.getCodigoBarras()).isPresent()) {
            throw new RuntimeException("Já existe um exemplar com o código " + dto.getCodigoBarras());
        }

        // RN11 — único exemplar do título na rede fica indisponível para empréstimo
        long jaExistentes = exemplarRepository.countByLivro(livro);
        String statusInicial = (jaExistentes == 0) ? "INDISPONIVEL" : "DISPONIVEL";

        Exemplar exemplar = new Exemplar();
        exemplar.setLivro(livro);
        exemplar.setBiblioteca(biblioteca);
        exemplar.setCodigoBarras(dto.getCodigoBarras());
        exemplar.setEstadoConservacao(
            dto.getEstadoConservacao() != null ? dto.getEstadoConservacao() : "NOVO");
        exemplar.setStatus(statusInicial);
        exemplarRepository.save(exemplar);

        historicoService.registrar(exemplar, "CADASTRO", bibliotecario, biblioteca,
            "Exemplar cadastrado com status inicial " + statusInicial
            + (jaExistentes == 0 ? " (RN11: único exemplar do título na rede)." : "."));

        // Se este é o segundo exemplar, o primeiro deixa de ser "único" e é liberado
        if (jaExistentes == 1) {
            liberarExemplarUnico(livro, bibliotecario);
        }

        System.out.println("[CIRCULA BOOK] Exemplar cadastrado: " + livro.getTitulo()
            + " | Biblioteca: " + biblioteca.getNome() + " | Status: " + statusInicial);

        return exemplar;
    }

    /** Ao existir um segundo exemplar, o que estava travado por RN11 volta a circular. */
    private void liberarExemplarUnico(Livro livro, Usuario responsavel) {
        List<Exemplar> indisponiveis = exemplarRepository.findByLivroAndStatus(livro, "INDISPONIVEL");
        for (Exemplar e : indisponiveis) {
            e.setStatus("DISPONIVEL");
            exemplarRepository.save(e);
            historicoService.registrar(e, "CADASTRO", responsavel, e.getBiblioteca(),
                "Liberado para empréstimo: o título deixou de ter exemplar único na rede (RN11).");
        }
    }

    private Livro resolverLivro(CadastroExemplarDTO dto) {
        if (dto.getLivroId() != null) {
            return livroRepository.findById(dto.getLivroId())
                .orElseThrow(() -> new RuntimeException("Livro não encontrado: ID " + dto.getLivroId()));
        }
        if (dto.getTitulo() == null || dto.getTitulo().isBlank()
            || dto.getAutor() == null || dto.getAutor().isBlank()) {
            throw new RuntimeException("Informe um livro já cadastrado ou preencha título e autor.");
        }
        if (dto.getIsbn() != null && !dto.getIsbn().isBlank()) {
            var existente = livroRepository.findByIsbn(dto.getIsbn());
            if (existente.isPresent()) return existente.get();
        }

        Livro novo = new Livro();
        novo.setTitulo(dto.getTitulo());
        novo.setAutor(dto.getAutor());
        novo.setEditora(dto.getEditora());
        novo.setIsbn(dto.getIsbn());
        novo.setAnoPublicacao(dto.getAnoPublicacao());
        novo.setSinopse(dto.getSinopse());
        if (dto.getCategoriaId() != null) {
            novo.setCategoria(categoriaRepository.findById(dto.getCategoriaId()).orElse(null));
        }
        return livroRepository.save(novo);
    }
}
