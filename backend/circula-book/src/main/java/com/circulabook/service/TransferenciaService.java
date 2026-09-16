package com.circulabook.service;

import com.circulabook.model.*;
import com.circulabook.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Especialista de Transferência — Tela 8 (UC04 / UC13 / UC24).
 * Concentra RN04 (só o Admin aprova), RN05 (fluxo de status) e RN13
 * (o próprio usuário da comunidade pode solicitar).
 */
@Service
public class TransferenciaService {

    @Autowired private SolicitacaoTransferenciaRepository transferenciaRepository;
    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private BibliotecaRepository bibliotecaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private HistoricoService historicoService;

    public List<SolicitacaoTransferencia> obterTodas() {
        return transferenciaRepository.findAll();
    }

    public List<SolicitacaoTransferencia> obterPendentes() {
        return transferenciaRepository.findByStatusOrderByDataSolicitacaoAsc("PENDENTE");
    }

    /** Card 2 da Tela 8: tudo que já saiu da fila de pendentes. */
    public List<SolicitacaoTransferencia> obterHistorico() {
        return transferenciaRepository.findByStatusInOrderByDataSolicitacaoDesc(
            List.of("APROVADA", "EM_TRANSITO", "CONCLUIDA", "REJEITADA", "CANCELADA"));
    }

    public long contarPorStatus(String status) {
        return transferenciaRepository.countByStatus(status);
    }

    /**
     * UC04 — Solicitar transferência.
     * RN13: o solicitante pode ser um usuário COMUM ou um BIBLIOTECARIO.
     * RN04: apenas exemplares DISPONIVEL podem ser transferidos.
     */
    @Transactional
    public SolicitacaoTransferencia solicitar(Long exemplarId, Long bibliotecaDestinoId,
                                              Long solicitanteId, Long reservaId, String observacoes) {

        Exemplar exemplar = exemplarRepository.findById(exemplarId)
            .orElseThrow(() -> new RuntimeException("Exemplar não encontrado: ID " + exemplarId));

        Biblioteca destino = bibliotecaRepository.findById(bibliotecaDestinoId)
            .orElseThrow(() -> new RuntimeException("Biblioteca não encontrada: ID " + bibliotecaDestinoId));

        Usuario solicitante = usuarioRepository.findById(solicitanteId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + solicitanteId));

        // RN04 — só exemplar disponível pode ser transferido
        if (!"DISPONIVEL".equals(exemplar.getStatus())) {
            throw new RuntimeException("RN04: apenas exemplares com status DISPONIVEL podem ser "
                + "transferidos. Status atual: " + exemplar.getStatus() + ".");
        }

        if (exemplar.getBiblioteca().getId().equals(destino.getId())) {
            throw new RuntimeException("O exemplar já está na biblioteca de destino.");
        }

        // Evita duas solicitações abertas para o mesmo exemplar
        List<SolicitacaoTransferencia> abertas = transferenciaRepository
            .findByExemplarAndStatusIn(exemplar, List.of("PENDENTE", "APROVADA", "EM_TRANSITO"));
        if (!abertas.isEmpty()) {
            throw new RuntimeException("Já existe uma solicitação de transferência em andamento "
                + "para este exemplar.");
        }

        SolicitacaoTransferencia s = new SolicitacaoTransferencia();
        s.setExemplar(exemplar);
        s.setBibliotecaOrigem(exemplar.getBiblioteca());
        s.setBibliotecaDestino(destino);
        s.setSolicitante(solicitante);
        s.setStatus("PENDENTE");
        s.setDataSolicitacao(LocalDateTime.now());

        if (reservaId != null) {
            Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new RuntimeException("Reserva não encontrada: ID " + reservaId));
            s.setReserva(reserva);
        }

        String origemPapel = "COMUM".equals(solicitante.getTipo())
            ? "usuário da comunidade" : "bibliotecário";
        s.setObservacoes(observacoes != null && !observacoes.isBlank()
            ? observacoes
            : "Solicitado por " + solicitante.getNome() + " (" + origemPapel + ") — RN13.");

        transferenciaRepository.save(s);

        // RN05 — o exemplar fica reservado enquanto o Admin não decide
        exemplar.setStatus("RESERVADO");
        exemplarRepository.save(exemplar);

        System.out.println("[BLACKBOARD] Nova solicitação de transferência #" + s.getId()
            + ": " + exemplar.getLivro().getTitulo()
            + " | " + s.getBibliotecaOrigem().getNome() + " -> " + destino.getNome()
            + " | Solicitante: " + solicitante.getNome());

        return s;
    }

    /**
     * UC13/UC24 — Aprovar. Só um ADMIN pode executar (RN04).
     * O exemplar passa para EM_TRANSFERENCIA (RN05).
     */
    @Transactional
    public SolicitacaoTransferencia aprovar(Long id, Long adminId) {
        SolicitacaoTransferencia s = buscarPendente(id);
        Usuario admin = validarAdmin(adminId);

        s.setStatus("EM_TRANSITO");
        s.setAprovador(admin);
        transferenciaRepository.save(s);

        Exemplar exemplar = s.getExemplar();
        exemplar.setStatus("EM_TRANSFERENCIA");
        exemplarRepository.save(exemplar);

        historicoService.registrar(exemplar, "TRANSFERENCIA_SAIDA", admin,
            s.getBibliotecaOrigem(),
            "Transferência aprovada por " + admin.getNome() + ". Destino: "
            + s.getBibliotecaDestino().getNome() + ".");

        System.out.println("[BLACKBOARD] Transferência #" + id + " aprovada por " + admin.getNome());
        return s;
    }

    /** UC13/UC24 — Rejeitar. O exemplar volta a ficar disponível (RN05). */
    @Transactional
    public SolicitacaoTransferencia rejeitar(Long id, Long adminId, String motivo) {
        SolicitacaoTransferencia s = buscarPendente(id);
        Usuario admin = validarAdmin(adminId);

        s.setStatus("REJEITADA");
        s.setAprovador(admin);
        s.setDataConclusao(LocalDateTime.now());
        if (motivo != null && !motivo.isBlank()) {
            s.setObservacoes("Rejeitada: " + motivo);
        }
        transferenciaRepository.save(s);

        Exemplar exemplar = s.getExemplar();
        exemplar.setStatus("DISPONIVEL");
        exemplarRepository.save(exemplar);

        System.out.println("[BLACKBOARD] Transferência #" + id + " rejeitada por " + admin.getNome());
        return s;
    }

    /**
     * UC15 — Confirmar chegada do exemplar no destino.
     * RN05: EM_TRANSFERENCIA -> DISPONIVEL, agora vinculado à nova biblioteca.
     */
    @Transactional
    public SolicitacaoTransferencia confirmarChegada(Long id, Long responsavelId) {
        SolicitacaoTransferencia s = transferenciaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Solicitação não encontrada: ID " + id));

        if (!"EM_TRANSITO".equals(s.getStatus()) && !"APROVADA".equals(s.getStatus())) {
            throw new RuntimeException("Só é possível confirmar a chegada de uma transferência "
                + "aprovada. Status atual: " + s.getStatus() + ".");
        }

        Usuario responsavel = usuarioRepository.findById(responsavelId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + responsavelId));

        Exemplar exemplar = s.getExemplar();
        exemplar.setBiblioteca(s.getBibliotecaDestino());
        exemplar.setStatus("DISPONIVEL");
        exemplarRepository.save(exemplar);

        s.setStatus("CONCLUIDA");
        s.setDataConclusao(LocalDateTime.now());
        transferenciaRepository.save(s);

        historicoService.registrar(exemplar, "TRANSFERENCIA_CHEGADA", responsavel,
            s.getBibliotecaDestino(),
            "Chegada confirmada na " + s.getBibliotecaDestino().getNome() + ".");

        System.out.println("[BLACKBOARD] Transferência #" + id + " concluída. Exemplar agora em "
            + s.getBibliotecaDestino().getNome());
        return s;
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

    /** RN04 — a aprovação é exclusiva do Admin da rede. */
    private Usuario validarAdmin(Long adminId) {
        Usuario admin = usuarioRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + adminId));
        if (!"ADMIN".equals(admin.getTipo())) {
            throw new RuntimeException("RN04: apenas o Administrador da Rede pode aprovar ou "
                + "rejeitar transferências.");
        }
        return admin;
    }
}
