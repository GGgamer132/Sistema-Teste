package com.circulabook.service;

import com.circulabook.model.*;
import com.circulabook.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Especialista de Reserva (UC03 / UC07 / RN03).
 *
 * Reserva = fila de espera de UMA biblioteca. O usuário pode retirar na própria
 * biblioteca da fila ou em outra que não possua o título (nesse caso nasce
 * também um pedido de transferência para o Admin avaliar).
 */
@Service
public class ReservaService {

    private static final List<String> STATUS_ATIVOS =
        List.of("PENDENTE", "AGUARDANDO_TRANSFERENCIA", "DISPONIVEL");

    @Autowired private ReservaRepository reservaRepository;
    @Autowired private LivroRepository livroRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private BibliotecaRepository bibliotecaRepository;
    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private TransferenciaService transferenciaService;
    @Autowired private FilaEsperaService filaEsperaService;

    public List<Reserva> obterTodas() {
        return reservaRepository.findAll();
    }

    public List<Reserva> obterPorUsuario(Long usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + usuarioId));
        List<Reserva> minhas = reservaRepository.findByUsuario(u);
        // O usuário não enxerga a fila dos outros: a posição vem calculada daqui
        for (Reserva r : minhas) {
            if ("PENDENTE".equals(r.getStatus())) {
                List<Reserva> fila = reservaRepository.findByLivroAndBibliotecaFilaAndStatusOrderByDataReservaAsc(
                    r.getLivro(), r.getBibliotecaFila(), "PENDENTE");
                int pos = 1;
                for (Reserva o : fila) {
                    if (o.getId().equals(r.getId())) break;
                    pos++;
                }
                r.setPosicaoFila(pos);
            }
        }
        return minhas;
    }

    /**
     * Posição que o usuário ocupará na fila DAQUELA biblioteca (resumo da Tela 5).
     * Sem bibliotecaId, mantém o cálculo global antigo (compatibilidade).
     */
    public long posicaoNaFila(Long livroId, Long bibliotecaId) {
        Livro livro = livroRepository.findById(livroId)
            .orElseThrow(() -> new RuntimeException("Livro não encontrado: ID " + livroId));

        if (bibliotecaId == null) {
            return reservaRepository.countByLivroAndStatus(livro, "PENDENTE") + 1;
        }
        Biblioteca fila = bibliotecaRepository.findById(bibliotecaId)
            .orElseThrow(() -> new RuntimeException("Biblioteca não encontrada: ID " + bibliotecaId));
        return reservaRepository.countByLivroAndBibliotecaFilaAndStatus(livro, fila, "PENDENTE") + 1;
    }

    /**
     * UC03 — Entrar na fila de espera.
     *
     * Regras:
     *  1) a biblioteca da fila precisa ter o título e NENHUM exemplar disponível;
     *  2) retirar em outra biblioteca exige que ela não tenha nenhum exemplar do título;
     *  3) retirar em outra biblioteca gera um pedido de transferência (PENDENTE, sem exemplar).
     */
    @Transactional
    public Reserva criar(Long livroId, Long usuarioId, Long bibliotecaFilaId, Long bibliotecaDestinoId) {

        Livro livro = livroRepository.findById(livroId)
            .orElseThrow(() -> new RuntimeException("Livro não encontrado: ID " + livroId));

        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + usuarioId));

        if (Boolean.FALSE.equals(usuario.getAtivo())) {
            throw new RuntimeException("Usuário inativo não pode fazer reservas.");
        }

        // Compatibilidade: se só um dos ids vier, a retirada é na própria biblioteca da fila
        Long filaId = (bibliotecaFilaId != null) ? bibliotecaFilaId : bibliotecaDestinoId;
        if (filaId == null) {
            throw new RuntimeException("Informe a biblioteca da fila de espera.");
        }
        Long retiradaId = (bibliotecaDestinoId != null) ? bibliotecaDestinoId : filaId;

        Biblioteca fila = bibliotecaRepository.findById(filaId)
            .orElseThrow(() -> new RuntimeException("Biblioteca não encontrada: ID " + filaId));
        Biblioteca destino = bibliotecaRepository.findById(retiradaId)
            .orElseThrow(() -> new RuntimeException("Biblioteca não encontrada: ID " + retiradaId));

        // ── Regra 1: reserva é por biblioteca ──
        List<Exemplar> naFila = exemplarRepository.findByLivroAndBiblioteca(livro, fila);
        if (naFila.isEmpty()) {
            throw new RuntimeException("A " + fila.getNome()
                + " não possui exemplares deste título, então não há fila para entrar.");
        }
        long livres = naFila.stream().filter(e -> "DISPONIVEL".equals(e.getStatus())).count();
        if (livres > 0) {
            throw new RuntimeException("A " + fila.getNome() + " tem " + livres
                + " exemplar(es) disponível(is). A reserva só vale quando todos estão emprestados; "
                + "faça o empréstimo presencialmente.");
        }

        // ── Regra 2: retirada em outra biblioteca ──
        boolean comTransferencia = !fila.getId().equals(destino.getId());
        if (comTransferencia) {
            if (Boolean.FALSE.equals(destino.getAtiva())) {
                throw new RuntimeException("A " + destino.getNome() + " está inativa.");
            }
            if (!exemplarRepository.findByLivroAndBiblioteca(livro, destino).isEmpty()) {
                throw new RuntimeException("A " + destino.getNome() + " já possui exemplares deste título. "
                    + "Para retirar lá, entre na fila dessa biblioteca ou faça o empréstimo presencial.");
            }
        }

        // Um usuário não pode ter duas reservas ativas do mesmo título
        List<Reserva> jaTem = reservaRepository.findByLivroAndUsuarioAndStatusIn(livro, usuario, STATUS_ATIVOS);
        if (!jaTem.isEmpty()) {
            throw new RuntimeException("Você já possui uma reserva ativa para este título.");
        }

        Reserva reserva = new Reserva();
        reserva.setLivro(livro);
        reserva.setUsuario(usuario);
        reserva.setBibliotecaFila(fila);
        reserva.setBibliotecaDestino(destino);
        reserva.setDataReserva(LocalDateTime.now());
        reserva.setDataExpiracao(LocalDateTime.now().plusDays(FilaEsperaService.DIAS_PARA_RETIRADA));
        reserva.setStatus("PENDENTE");
        reservaRepository.save(reserva);

        // ── Regra 3: a transferência é consequência da reserva ──
        if (comTransferencia) {
            transferenciaService.criarPedidoDeReserva(reserva);
        }

        System.out.println("[CIRCULA BOOK] Reserva criada: " + livro.getTitulo()
            + " para " + usuario.getNome() + " | Fila: " + fila.getNome()
            + " | Retirada: " + destino.getNome()
            + (comTransferencia ? " (com pedido de transferência)" : ""));

        return reserva;
    }

    /**
     * UC07 — Cancelar reserva.
     *  - pedido de transferência ainda não despachado é cancelado;
     *  - exemplar já separado (RESERVADO) é liberado e atende o próximo da fila;
     *  - se o exemplar já está em trânsito, a viagem continua e ele chega como DISPONIVEL.
     */
    @Transactional
    public Reserva cancelar(Long reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada: ID " + reservaId));

        if (!STATUS_ATIVOS.contains(reserva.getStatus())) {
            throw new RuntimeException("Esta reserva não pode mais ser cancelada (status: "
                + reserva.getStatus() + ").");
        }

        // Marca CANCELADA antes de liberar exemplares, para ela não ser promovida de novo
        reserva.setStatus("CANCELADA");
        reservaRepository.save(reserva);

        transferenciaService.cancelarPorReserva(reserva);

        Exemplar exemplar = reserva.getExemplar();
        if (exemplar != null && "RESERVADO".equals(exemplar.getStatus())) {
            filaEsperaService.liberarExemplar(exemplar);
        }
        return reserva;
    }
}