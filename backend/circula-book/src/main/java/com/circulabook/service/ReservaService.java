package com.circulabook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.circulabook.model.*;
import com.circulabook.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Especialista de Reserva (UC03 / RN03).
 *
 * Reserva = fila de espera de UMA biblioteca. A retirada é sempre feita
 * na mesma biblioteca da fila.
 */
@Service
public class ReservaService {

    private static final Logger log = LoggerFactory.getLogger(ReservaService.class);

    private static final List<String> STATUS_ATIVOS = List.of("PENDENTE", "DISPONIVEL");

    @Autowired private ReservaRepository reservaRepository;
    @Autowired private LivroRepository livroRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private BibliotecaRepository bibliotecaRepository;
    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private EstadoExemplarService estadoExemplar;
    @Autowired private EmprestimoRepository emprestimoRepository;

    /** Posição que o usuário ocupará na fila daquela biblioteca (resumo da tela de reserva). */
    public long posicaoNaFila(Long livroId, Long bibliotecaId) {
        Livro livro = livroRepository.findById(livroId)
            .orElseThrow(() -> new RuntimeException("Livro não encontrado: ID " + livroId));
        Biblioteca biblioteca = bibliotecaRepository.findById(bibliotecaId)
            .orElseThrow(() -> new RuntimeException("Biblioteca não encontrada: ID " + bibliotecaId));
        return reservaRepository.countByLivroAndBibliotecaFilaAndStatus(livro, biblioteca, "PENDENTE") + 1;
    }

    /**
     * UC03 — Entrar na fila de espera.
     *
     * Regras:
     *  1) só usuário COMUM, sem reserva ativa do título e sem o título emprestado;
     *  2) a biblioteca precisa ter o título e NENHUM exemplar disponível.
     * A posição devolvida conta só as reservas PENDENTE do título naquela biblioteca.
     */
    @Transactional
    public Reserva criar(Long livroId, Long usuarioId, Long bibliotecaId) {

        Livro livro = livroRepository.findById(livroId)
            .orElseThrow(() -> new RuntimeException("Livro não encontrado: ID " + livroId));

        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + usuarioId));

        if (!"COMUM".equals(usuario.getTipo())) {
            throw new RuntimeException("Somente usuários da comunidade podem fazer reservas.");
        }

        // ── Regra 1: uma reserva ativa por título, e nada de reservar o que já está com você ──
        if (!reservaRepository.findByLivroAndUsuarioAndStatusIn(livro, usuario, STATUS_ATIVOS).isEmpty()) {
            throw new RuntimeException("Você já possui uma reserva ativa para este título.");
        }
        boolean jaEmprestado = emprestimoRepository
            .findByUsuarioAndStatusIn(usuario, List.of("ATIVO", "ATRASADO")).stream()
            .anyMatch(e -> e.getExemplar().getLivro().getId().equals(livro.getId()));
        if (jaEmprestado) {
            throw new RuntimeException("Você já está com um exemplar de \"" + livro.getTitulo()
                + "\" emprestado e não pode reservar o mesmo título.");
        }

        if (bibliotecaId == null) {
            throw new RuntimeException("Informe a biblioteca da reserva.");
        }
        Biblioteca biblioteca = bibliotecaRepository.findById(bibliotecaId)
            .orElseThrow(() -> new RuntimeException("Biblioteca não encontrada: ID " + bibliotecaId));

        // ── Regra 2: reserva é por biblioteca ──
        validarFila(livro, biblioteca);

        Reserva reserva = new Reserva();
        reserva.setLivro(livro);
        reserva.setUsuario(usuario);
        reserva.setBibliotecaFila(biblioteca);
        reserva.setDataReserva(LocalDateTime.now());
        reserva.setDataExpiracao(LocalDateTime.now().plusDays(FilaEsperaService.DIAS_PARA_RETIRADA));
        reserva.setStatus("PENDENTE");
        reservaRepository.save(reserva);
        // Com a fila, os emprestados do título nesta biblioteca viram EMPRESTADO_RESERVADO
        estadoExemplar.sincronizarMarcaDeFila(livro, biblioteca);

        // Entrou por último: a posição é o tamanho da fila daquela biblioteca
        reserva.setPosicaoFila((int) reservaRepository.countByLivroAndBibliotecaFilaAndStatus(
            livro, biblioteca, "PENDENTE"));

        log.info("[CIRCULA BOOK] Reserva criada: " + livro.getTitulo()
            + " para " + usuario.getNome() + " | Biblioteca: " + biblioteca.getNome()
            + " (posição " + reserva.getPosicaoFila() + ")");

        return reserva;
    }

    /** Só entra na fila de biblioteca que tem o título e não tem exemplar livre. */
    private void validarFila(Livro livro, Biblioteca biblioteca) {
        List<Exemplar> exemplares = exemplarRepository.findByLivroAndBiblioteca(livro, biblioteca);
        if (exemplares.isEmpty()) {
            throw new RuntimeException("A " + biblioteca.getNome()
                + " não possui exemplares deste título, então não há fila para entrar.");
        }
        long livres = exemplares.stream().filter(e -> StatusExemplar.DISPONIVEL.equals(e.getStatus())).count();
        if (livres > 0) {
            throw new RuntimeException("A " + biblioteca.getNome() + " tem " + livres
                + " exemplar(es) disponível(is). A reserva só vale quando todos estão emprestados; "
                + "faça o empréstimo presencialmente.");
        }
    }
}
