package com.circulabook.service;

import com.circulabook.model.*;
import com.circulabook.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Especialista de Transferência (UC13 / UC15 / UC24).
 *
 * A transferência só nasce de duas formas:
 *  - criarPedidoDeReserva: consequência de uma reserva com retirada em outra biblioteca;
 *  - criarAvulsa: iniciativa do Admin (nasce aprovada).
 * A aprovação/rejeição é exclusiva do Admin (RN04).
 */
@Service
public class TransferenciaService {

    private static final List<String> RESERVA_ATIVA =
        List.of("PENDENTE", "AGUARDANDO_TRANSFERENCIA", "DISPONIVEL");

    @Autowired private SolicitacaoTransferenciaRepository transferenciaRepository;
    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private BibliotecaRepository bibliotecaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private HistoricoService historicoService;
    @Autowired private FilaEsperaService filaEsperaService;

    public List<SolicitacaoTransferencia> obterTodas() {
        return transferenciaRepository.findAll();
    }

    public List<SolicitacaoTransferencia> obterPendentes() {
        return transferenciaRepository.findByStatusOrderByDataSolicitacaoAsc("PENDENTE");
    }

    /** Tudo que já saiu da fila de pendentes (inclui "aprovada, aguardando exemplar"). */
    public List<SolicitacaoTransferencia> obterHistorico() {
        return transferenciaRepository.findByStatusInOrderByDataSolicitacaoDesc(
            List.of("APROVADA", "EM_TRANSITO", "CONCLUIDA", "REJEITADA", "CANCELADA"));
    }

    public long contarPorStatus(String status) {
        return transferenciaRepository.countByStatus(status);
    }

    /**
     * Chamado pelo ReservaService quando o usuário escolhe retirar em outra biblioteca.
     * O pedido nasce PENDENTE e SEM exemplar: o exemplar é vinculado depois,
     * quando algum for devolvido na biblioteca da fila (FilaEsperaService).
     */
    @Transactional
    public SolicitacaoTransferencia criarPedidoDeReserva(Reserva reserva) {
        SolicitacaoTransferencia s = new SolicitacaoTransferencia();
        s.setLivro(reserva.getLivro());
        s.setExemplar(null);
        s.setBibliotecaOrigem(reserva.getBibliotecaFila());
        s.setBibliotecaDestino(reserva.getBibliotecaDestino());
        s.setSolicitante(reserva.getUsuario());
        s.setReserva(reserva);
        s.setStatus("PENDENTE");
        s.setDataSolicitacao(LocalDateTime.now());
        s.setObservacoes("Pedido gerado pela reserva de " + reserva.getUsuario().getNome()
            + ": fila na " + reserva.getBibliotecaFila().getNome()
            + ", retirada na " + reserva.getBibliotecaDestino().getNome()
            + ". O exemplar será vinculado quando for devolvido.");
        transferenciaRepository.save(s);

        System.out.println("[BLACKBOARD] Pedido de transferência #" + s.getId() + " criado pela reserva #"
            + reserva.getId() + " (aguardando decisão do Admin).");
        return s;
    }

    /**
     * Transferência AVULSA do Admin: sem reserva e sem restrição de destino.
     * Nasce aprovada e, como já tem exemplar, entra direto em trânsito.
     */
    @Transactional
    public SolicitacaoTransferencia criarAvulsa(Long exemplarId, Long bibliotecaDestinoId,
                                                Long adminId, String observacoes) {
        Usuario admin = validarAdmin(adminId);

        Exemplar exemplar = exemplarRepository.findById(exemplarId)
            .orElseThrow(() -> new RuntimeException("Exemplar não encontrado: ID " + exemplarId));
        Biblioteca destino = bibliotecaRepository.findById(bibliotecaDestinoId)
            .orElseThrow(() -> new RuntimeException("Biblioteca não encontrada: ID " + bibliotecaDestinoId));

        // RN04 — só exemplar disponível pode ser transferido
        if (!"DISPONIVEL".equals(exemplar.getStatus())) {
            throw new RuntimeException("Apenas exemplares DISPONIVEL podem ser transferidos. "
                + "Status atual: " + exemplar.getStatus() + ".");
        }
        if (Boolean.FALSE.equals(destino.getAtiva())) {
            throw new RuntimeException("A " + destino.getNome() + " está inativa.");
        }
        if (exemplar.getBiblioteca().getId().equals(destino.getId())) {
            throw new RuntimeException("O exemplar já está na biblioteca de destino.");
        }
        List<SolicitacaoTransferencia> abertas = transferenciaRepository.findByExemplarAndStatusIn(
            exemplar, List.of("PENDENTE", "APROVADA", "EM_TRANSITO"));
        if (!abertas.isEmpty()) {
            throw new RuntimeException("Já existe uma transferência em andamento para este exemplar.");
        }

        SolicitacaoTransferencia s = new SolicitacaoTransferencia();
        s.setLivro(exemplar.getLivro());
        s.setExemplar(exemplar);
        s.setBibliotecaOrigem(exemplar.getBiblioteca());
        s.setBibliotecaDestino(destino);
        s.setSolicitante(admin);
        s.setAprovador(admin);
        s.setReserva(null);
        s.setStatus("APROVADA"); // nasce aprovada
        s.setDataSolicitacao(LocalDateTime.now());
        s.setObservacoes(observacoes != null && !observacoes.isBlank()
            ? observacoes
            : "Transferência avulsa criada pelo administrador " + admin.getNome() + ".");
        transferenciaRepository.save(s);

        // Já tem exemplar: segue direto para EM_TRANSITO
        filaEsperaService.despachar(s, admin);

        System.out.println("[BLACKBOARD] Transferência avulsa #" + s.getId() + ": "
            + exemplar.getLivro().getTitulo() + " | " + s.getBibliotecaOrigem().getNome()
            + " -> " + destino.getNome());
        return s;
    }

    /**
     * UC13/UC24 — Aprovar (só ADMIN, RN04).
     *  - sem exemplar ainda  -> APROVADA ("aguardando exemplar");
     *  - exemplar já vinculado (foi devolvido antes da decisão) -> segue direto para EM_TRANSITO.
     */
    @Transactional
    public SolicitacaoTransferencia aprovar(Long id, Long adminId) {
        SolicitacaoTransferencia s = buscarPendente(id);
        Usuario admin = validarAdmin(adminId);

        s.setAprovador(admin);
        if (s.getExemplar() != null) {
            filaEsperaService.despachar(s, admin);
        } else {
            s.setStatus("APROVADA");
            transferenciaRepository.save(s);
        }

        System.out.println("[BLACKBOARD] Transferência #" + id + " aprovada por " + admin.getNome()
            + (s.getExemplar() == null ? " (aguardando exemplar)." : " (em trânsito)."));
        return s;
    }

    /**
     * UC13/UC24 — Rejeitar (só ADMIN, RN04).
     * A reserva NÃO é cancelada: continua na fila com retirada na própria biblioteca da fila.
     */
    @Transactional
    public SolicitacaoTransferencia rejeitar(Long id, Long adminId, String motivo) {
        SolicitacaoTransferencia s = buscarPendente(id);
        Usuario admin = validarAdmin(adminId);

        s.setStatus("REJEITADA");
        s.setAprovador(admin);
        s.setDataConclusao(LocalDateTime.now());

        Reserva reserva = s.getReserva();
        String obs = "Rejeitada pelo administrador" + (motivo != null && !motivo.isBlank() ? ": " + motivo : ".");
        if (reserva != null) {
            obs += " A reserva segue na fila com retirada na " + reserva.getBibliotecaFila().getNome() + ".";
        }
        s.setObservacoes(obs);
        transferenciaRepository.save(s);

        if (reserva != null && RESERVA_ATIVA.contains(reserva.getStatus())) {
            reserva.setBibliotecaDestino(reserva.getBibliotecaFila()); // retirada na origem

            Exemplar exemplar = s.getExemplar();
            if (exemplar != null && "RESERVADO".equals(exemplar.getStatus())) {
                // exemplar já estava separado: libera para retirada na origem (3 dias)
                filaEsperaService.liberarParaRetirada(reserva, exemplar);
            } else {
                reservaRepository.save(reserva); // continua PENDENTE na fila
            }
        }

        System.out.println("[BLACKBOARD] Transferência #" + id + " rejeitada por " + admin.getNome());
        return s;
    }

    /**
     * UC15 — Confirmar chegada (só EM_TRANSITO).
     * Quem confirma: Admin ou bibliotecário da biblioteca de destino.
     *  - reserva aguardando -> exemplar RESERVADO e 3 dias para retirar no destino;
     *  - avulsa ou reserva já cancelada -> exemplar DISPONIVEL no destino.
     */
    @Transactional
    public SolicitacaoTransferencia confirmarChegada(Long id, Long responsavelId) {
        SolicitacaoTransferencia s = transferenciaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Solicitação não encontrada: ID " + id));

        if (!"EM_TRANSITO".equals(s.getStatus()) || s.getExemplar() == null) {
            throw new RuntimeException("Só é possível confirmar a chegada de uma transferência "
                + "em trânsito. Status atual: " + s.getStatus() + ".");
        }

        Usuario responsavel = usuarioRepository.findById(responsavelId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + responsavelId));

        boolean ehAdmin = "ADMIN".equals(responsavel.getTipo());
        boolean ehDoDestino = "BIBLIOTECARIO".equals(responsavel.getTipo())
            && responsavel.getBiblioteca() != null
            && responsavel.getBiblioteca().getId().equals(s.getBibliotecaDestino().getId());
        if (!ehAdmin && !ehDoDestino) {
            throw new RuntimeException("Apenas um bibliotecário da "
                + s.getBibliotecaDestino().getNome() + " pode confirmar a chegada.");
        }

        Exemplar exemplar = s.getExemplar();
        exemplar.setBiblioteca(s.getBibliotecaDestino()); // agora pertence ao destino

        s.setStatus("CONCLUIDA");
        s.setDataConclusao(LocalDateTime.now());
        transferenciaRepository.save(s);

        historicoService.registrar(exemplar, "TRANSFERENCIA_CHEGADA", responsavel,
            s.getBibliotecaDestino(),
            "Chegada confirmada na " + s.getBibliotecaDestino().getNome() + ".");

        Reserva reserva = s.getReserva();
        if (reserva != null && "AGUARDANDO_TRANSFERENCIA".equals(reserva.getStatus())) {
            exemplar.setStatus("RESERVADO");
            exemplarRepository.save(exemplar);
            filaEsperaService.liberarParaRetirada(reserva, exemplar);
        } else {
            exemplar.setStatus("DISPONIVEL");
            exemplarRepository.save(exemplar);
            filaEsperaService.promoverProximo(exemplar);
        }

        System.out.println("[BLACKBOARD] Transferência #" + id + " concluída. Exemplar agora em "
            + s.getBibliotecaDestino().getNome());
        return s;
    }

    /**
     * Chamado quando a reserva é cancelada: pedidos ainda não despachados são cancelados.
     * Pedido EM_TRANSITO não é tocado: o exemplar chega ao destino como DISPONIVEL.
     */
    @Transactional
    public void cancelarPorReserva(Reserva reserva) {
        List<SolicitacaoTransferencia> abertas = transferenciaRepository
            .findByReservaAndStatusIn(reserva, List.of("PENDENTE", "APROVADA"));

        for (SolicitacaoTransferencia s : abertas) {
            s.setStatus("CANCELADA");
            s.setDataConclusao(LocalDateTime.now());
            s.setObservacoes("Cancelada: o usuário cancelou a reserva.");
            transferenciaRepository.save(s);
        }
    }

    private SolicitacaoTransferencia buscarPendente(Long id) {
        SolicitacaoTransferencia s = transferenciaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Solicitação não encontrada: ID " + id));
        if (!"PENDENTE".equals(s.getStatus())) {
            throw new RuntimeException("Esta solicitação já foi processada (status: "
                + s.getStatus() + ").");
        }
        return s;
    }

    /** RN04 — a decisão é exclusiva do Admin da rede. */
    private Usuario validarAdmin(Long adminId) {
        Usuario admin = usuarioRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + adminId));
        if (!"ADMIN".equals(admin.getTipo())) {
            throw new RuntimeException("Apenas o Administrador da Rede pode realizar esta ação.");
        }
        return admin;
    }
}