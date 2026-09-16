package com.circulabook.service;

import com.circulabook.model.*;
import com.circulabook.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Especialista de Reserva — Tela 5 (UC03 / UC07 / RN03).
 */
@Service
public class ReservaService {

    public static final int DIAS_PARA_RETIRADA = 3; // RN03

    @Autowired private ReservaRepository reservaRepository;
    @Autowired private LivroRepository livroRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private BibliotecaRepository bibliotecaRepository;
    @Autowired private ExemplarRepository exemplarRepository;

    public List<Reserva> obterTodas() {
        return reservaRepository.findAll();
    }

    public List<Reserva> obterPorUsuario(Long usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + usuarioId));
        return reservaRepository.findByUsuario(u);
    }

    /** Posição que o usuário ocupará na fila — exibido no resumo da Tela 5. */
    public long posicaoNaFila(Long livroId) {
        Livro livro = livroRepository.findById(livroId)
            .orElseThrow(() -> new RuntimeException("Livro não encontrado: ID " + livroId));
        return reservaRepository.countByLivroAndStatus(livro, "PENDENTE") + 1;
    }

    /**
     * UC03 — Fazer reserva.
     * RN03: só é permitido reservar quando NÃO há exemplar disponível na rede.
     */
    @Transactional
    public Reserva criar(Long livroId, Long usuarioId, Long bibliotecaDestinoId) {

        Livro livro = livroRepository.findById(livroId)
            .orElseThrow(() -> new RuntimeException("Livro não encontrado: ID " + livroId));

        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + usuarioId));

        Biblioteca destino = bibliotecaRepository.findById(bibliotecaDestinoId)
            .orElseThrow(() -> new RuntimeException("Biblioteca não encontrada: ID " + bibliotecaDestinoId));

        // RN03 — havendo exemplar livre, o caminho correto é o empréstimo direto
        long disponiveis = exemplarRepository.findByLivroAndStatus(livro, "DISPONIVEL").size();
        if (disponiveis > 0) {
            throw new RuntimeException(
                "RN03: há exemplar disponível na rede. A reserva só vale quando todos estão emprestados.");
        }

        // Evita reserva duplicada do mesmo título pelo mesmo usuário
        List<Reserva> jaTem = reservaRepository.findByLivroAndUsuarioAndStatusIn(
            livro, usuario, List.of("PENDENTE", "DISPONIVEL"));
        if (!jaTem.isEmpty()) {
            throw new RuntimeException("Você já possui uma reserva ativa para este título.");
        }

        Reserva reserva = new Reserva();
        reserva.setLivro(livro);
        reserva.setUsuario(usuario);
        reserva.setBibliotecaDestino(destino);
        reserva.setDataReserva(LocalDateTime.now());
        reserva.setDataExpiracao(LocalDateTime.now().plusDays(DIAS_PARA_RETIRADA));
        reserva.setStatus("PENDENTE");
        reservaRepository.save(reserva);

        System.out.println("[CIRCULA BOOK] Reserva criada: " + livro.getTitulo()
            + " para " + usuario.getNome() + " | Retirada em " + destino.getNome());

        return reserva;
    }

    /** UC07 — Cancelar reserva. */
    @Transactional
    public Reserva cancelar(Long reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada: ID " + reservaId));
        reserva.setStatus("CANCELADA");
        return reservaRepository.save(reserva);
    }
}
