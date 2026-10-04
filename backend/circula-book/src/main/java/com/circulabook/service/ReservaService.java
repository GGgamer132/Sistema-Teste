package com.circulabook.service;

import com.circulabook.dto.DestinoRetiradaDTO;
import com.circulabook.dto.ReservasBibliotecaDTO;
import com.circulabook.model.*;
import com.circulabook.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
    @Autowired private EstadoExemplarService estadoExemplar;
    @Autowired private EmprestimoRepository emprestimoRepository;
    @Autowired private RegrasTransferenciaService regras;

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
     * UC03 — Entrar na fila de espera (§5.1).
     *
     * Regras:
     *  1) só usuário COMUM, sem reserva ativa do título e sem o título emprestado;
     *  2) a biblioteca da fila precisa ter o título e NENHUM exemplar disponível;
     *  3) retirar em outra biblioteca exige destino sem nenhum exemplar do título,
     *     que a origem não fique sem o livro (RN15) e destino apto a receber (RN22);
     *  4) retirar em outra biblioteca gera um pedido de transferência (PENDENTE, sem exemplar).
     * A posição devolvida conta só as reservas PENDENTE do título naquela biblioteca.
     */
    @Transactional
    public Reserva criar(Long livroId, Long usuarioId, Long bibliotecaFilaId, Long bibliotecaDestinoId) {

        Livro livro = livroRepository.findById(livroId)
            .orElseThrow(() -> new RuntimeException("Livro não encontrado: ID " + livroId));

        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + usuarioId));

        if (!"COMUM".equals(usuario.getTipo())) {
            throw new RuntimeException("Somente usuários da comunidade podem fazer reservas.");
        }
        if (Boolean.FALSE.equals(usuario.getAtivo())) {
            throw new RuntimeException("Usuário inativo não pode fazer reservas.");
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

        // ── Regra 2: reserva é por biblioteca ──
        validarFila(livro, fila);

        // ── Regra 3: retirada em outra biblioteca ──
        boolean comTransferencia = !fila.getId().equals(destino.getId());
        if (comTransferencia) {
            String motivo = motivoBloqueioRetirada(livro, fila, destino);
            if (motivo != null) throw new RuntimeException(motivo);
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
        // T3: com a fila, os emprestados do título nesta biblioteca viram EMPRESTADO_RESERVADO
        estadoExemplar.sincronizarMarcaDeFila(livro, fila);

        // ── Regra 4: a transferência é consequência da reserva ──
        if (comTransferencia) {
            transferenciaService.criarPedidoDeReserva(reserva);
        }

        // Entrou por último: a posição é o tamanho da fila daquela biblioteca
        reserva.setPosicaoFila((int) reservaRepository.countByLivroAndBibliotecaFilaAndStatus(
            livro, fila, "PENDENTE"));

        System.out.println("[CIRCULA BOOK] Reserva criada: " + livro.getTitulo()
            + " para " + usuario.getNome() + " | Fila: " + fila.getNome()
            + " (posição " + reserva.getPosicaoFila() + ") | Retirada: " + destino.getNome()
            + (comTransferencia ? " (com pedido de transferência)" : ""));

        return reserva;
    }

    /**
     * Tela de reserva — "retirar em outra biblioteca": todas as bibliotecas ativas,
     * menos a da fila, cada uma dizendo se pode ser escolhida e, se não, por quê.
     * Recusa (400) se a fila em si não é válida (sem o título ou com exemplar disponível).
     */
    public List<DestinoRetiradaDTO> destinosPossiveis(Long livroId, Long bibliotecaFilaId) {
        Livro livro = livroRepository.findById(livroId)
            .orElseThrow(() -> new RuntimeException("Livro não encontrado: ID " + livroId));
        Biblioteca fila = bibliotecaRepository.findById(bibliotecaFilaId)
            .orElseThrow(() -> new RuntimeException("Biblioteca não encontrada: ID " + bibliotecaFilaId));
        validarFila(livro, fila);

        List<DestinoRetiradaDTO> destinos = new ArrayList<>();
        for (Biblioteca b : bibliotecaRepository.findByAtivaTrue()) {
            if (b.getId().equals(fila.getId())) continue;
            String motivo = motivoBloqueioRetirada(livro, fila, b);
            destinos.add(new DestinoRetiradaDTO(b.getId(), b.getNome(), motivo == null, motivo));
        }
        destinos.sort(Comparator.comparing(DestinoRetiradaDTO::nome));
        return destinos;
    }

    /**
     * B6 — painel do bibliotecário: reservas DISPONIVEL com retirada na biblioteca
     * (prazo mais próximo primeiro) e a fila PENDENTE de cada título nela.
     */
    public ReservasBibliotecaDTO painelBiblioteca(Long bibliotecaId) {
        List<ReservasBibliotecaDTO.AguardandoRetirada> prontas = reservaRepository.findByStatus("DISPONIVEL").stream()
            .filter(r -> r.getBibliotecaDestino().getId().equals(bibliotecaId))
            .sorted(Comparator.comparing(Reserva::getDataExpiracao))
            .map(r -> new ReservasBibliotecaDTO.AguardandoRetirada(r.getId(), r.getUsuario().getId(),
                r.getUsuario().getNome(), r.getUsuario().getEmail(), r.getLivro().getId(),
                r.getLivro().getTitulo(), r.getExemplar() != null ? r.getExemplar().getId() : null,
                r.getDataExpiracao()))
            .toList();

        java.util.Map<Livro, List<Reserva>> porTitulo = new java.util.TreeMap<>(
            Comparator.comparing(Livro::getTitulo).thenComparing(Livro::getId));
        reservaRepository.findByStatus("PENDENTE").stream()
            .filter(r -> r.getBibliotecaFila().getId().equals(bibliotecaId))
            .forEach(r -> porTitulo.computeIfAbsent(r.getLivro(), k -> new ArrayList<>()).add(r));
        List<ReservasBibliotecaDTO.FilaTitulo> filas = new ArrayList<>();
        porTitulo.forEach((livro, lista) -> {
            lista.sort(Comparator.comparing(Reserva::getDataReserva));
            List<ReservasBibliotecaDTO.NaFila> fila = new ArrayList<>();
            for (int i = 0; i < lista.size(); i++) {
                Reserva r = lista.get(i);
                fila.add(new ReservasBibliotecaDTO.NaFila(i + 1, r.getId(), r.getUsuario().getId(),
                    r.getUsuario().getNome(), r.getDataReserva(), r.getBibliotecaDestino().getNome()));
            }
            filas.add(new ReservasBibliotecaDTO.FilaTitulo(livro.getId(), livro.getTitulo(), fila));
        });
        return new ReservasBibliotecaDTO(prontas, filas);
    }

    /** §5.1 item 1: só entra na fila de biblioteca que tem o título e não tem exemplar livre. */
    private void validarFila(Livro livro, Biblioteca fila) {
        List<Exemplar> naFila = exemplarRepository.findByLivroAndBiblioteca(livro, fila);
        if (naFila.isEmpty()) {
            throw new RuntimeException("A " + fila.getNome()
                + " não possui exemplares deste título, então não há fila para entrar.");
        }
        long livres = naFila.stream().filter(e -> StatusExemplar.DISPONIVEL.equals(e.getStatus())).count();
        if (livres > 0) {
            throw new RuntimeException("A " + fila.getNome() + " tem " + livres
                + " exemplar(es) disponível(is). A reserva só vale quando todos estão emprestados; "
                + "faça o empréstimo presencialmente.");
        }
    }

    /**
     * §5.1 item 4: por que não dá para retirar no destino (ou null se dá).
     * Ordem: destino já tem o título; origem ficaria sem o livro (RN15); destino sem bibliotecário (RN22).
     */
    private String motivoBloqueioRetirada(Livro livro, Biblioteca fila, Biblioteca destino) {
        if (!exemplarRepository.findByLivroAndBiblioteca(livro, destino).isEmpty()) {
            return "A " + destino.getNome() + " já possui exemplares deste título. "
                + "Para retirar lá, entre na fila dessa biblioteca ou faça o empréstimo presencial.";
        }
        String motivo = regras.motivoBloqueioOrigem(livro, fila);
        return motivo != null ? motivo : regras.motivoBloqueioDestino(destino);
    }

    /**
     * UC07 — Cancelar reserva (§5.2, casos de borda).
     *  - pedido PENDENTE/APROVADA é cancelado; o exemplar já separado (RESERVADO) atende
     *    o próximo da fila (T7b) ou fica DISPONIVEL (T7);
     *  - pedido EM_TRANSITO não é tocado: a viagem continua. A reserva fica CANCELADA
     *    (ainda apontando para o exemplar, só como registro) e o exemplar segue
     *    EM_TRANSFERENCIA; na chegada, como a reserva não está mais aguardando, o
     *    exemplar entra no acervo do destino como DISPONIVEL (T11) ou atende o 1º da
     *    fila de lá (T10) — ver TransferenciaService.confirmarChegada;
     *  - se a fila esvaziou, os emprestados voltam a EMPRESTADO (T12).
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
        if (exemplar != null && StatusExemplar.RESERVADO.equals(exemplar.getStatus())) {
            filaEsperaService.liberar(exemplar, "A reserva de " + reserva.getUsuario().getNome()
                + " foi cancelada."); // T7 (fila vazia) ou T7b (reatribui ao próximo)
        }
        // T12: se a fila esvaziou, os emprestados voltam a EMPRESTADO
        estadoExemplar.sincronizarMarcaDeFila(reserva.getLivro(), reserva.getBibliotecaFila());
        return reserva;
    }
}