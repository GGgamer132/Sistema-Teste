package com.circulabook.service;

import com.circulabook.dto.SituacaoUsuarioDTO;
import com.circulabook.model.*;
import com.circulabook.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Especialista de Empréstimo — Telas 4 e 6.
 * Concentra RN01, RN02, RN05, RN06 e RN12.
 */
@Service
public class EmprestimoService {

    public static final int LIMITE_EMPRESTIMOS = 3;   // RN01
    public static final int PRAZO_PADRAO_DIAS   = 14; // RN02
    public static final int DIAS_BLOQUEIO_POR_ATRASO = 2; // RN12

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private EmprestimoRepository emprestimoRepository;
    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private HistoricoService historicoService;

    public List<Emprestimo> obterTodos() {
        return emprestimoRepository.findAll();
    }

    public List<Emprestimo> obterAtivos() {
        return emprestimoRepository.findByStatusInOrderByDataPrevDevolucaoAsc(
            List.of("ATIVO", "ATRASADO"));
    }

    public List<Emprestimo> obterPorUsuario(Long usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + usuarioId));
        return emprestimoRepository.findByUsuario(u);
    }

    /**
     * Alimenta a coluna de alertas da Tela 4: diz se o usuário pode
     * pegar mais um livro, checando RN01 (limite) e RN12 (bloqueio por atraso).
     */
    public SituacaoUsuarioDTO consultarSituacao(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + usuarioId));

        List<Emprestimo> emAberto = emprestimoRepository
            .findByUsuarioAndStatusIn(usuario, List.of("ATIVO", "ATRASADO"));

        boolean bloqueado = usuario.getBloqueadoAte() != null
            && usuario.getBloqueadoAte().isAfter(LocalDateTime.now());

        SituacaoUsuarioDTO dto = new SituacaoUsuarioDTO();
        dto.setUsuarioId(usuario.getId());
        dto.setNome(usuario.getNome());
        dto.setEmail(usuario.getEmail());
        dto.setEmprestimosAtivos(emAberto.size());
        dto.setLimite(LIMITE_EMPRESTIMOS);
        dto.setBloqueado(bloqueado);
        dto.setBloqueadoAte(bloqueado ? usuario.getBloqueadoAte().format(BR) : null);

        if (bloqueado) {
            dto.setApto(false);
            dto.setMotivo("RN12: usuário bloqueado até "
                + usuario.getBloqueadoAte().format(BR) + " por devolução em atraso.");
        } else if (emAberto.size() >= LIMITE_EMPRESTIMOS) {
            dto.setApto(false);
            dto.setMotivo("RN01: o usuário já atingiu o limite de "
                + LIMITE_EMPRESTIMOS + " empréstimos simultâneos.");
        } else {
            dto.setApto(true);
            dto.setMotivo("Nenhuma pendência encontrada.");
        }
        return dto;
    }

    /**
     * UC09 — Registrar empréstimo.
     * Valida RN01 (limite e título repetido), RN12 (bloqueio) e RN05 (status do exemplar).
     */
    @Transactional
    public Emprestimo registrar(Long exemplarId, Long usuarioId, Integer prazoDias) {

        Exemplar exemplar = exemplarRepository.findById(exemplarId)
            .orElseThrow(() -> new RuntimeException("Exemplar não encontrado: ID " + exemplarId));

        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + usuarioId));

        if (Boolean.FALSE.equals(usuario.getAtivo())) {
            throw new RuntimeException("Usuário inativo não pode realizar empréstimos.");
        }

        // RN05 — só exemplar DISPONIVEL ou RESERVADO (para quem reservou) pode sair
        if (!"DISPONIVEL".equals(exemplar.getStatus()) && !"RESERVADO".equals(exemplar.getStatus())) {
            throw new RuntimeException("RN05: exemplar com status " + exemplar.getStatus()
                + " não pode ser emprestado.");
        }

        // RN12 — bloqueio por atraso anterior
        if (usuario.getBloqueadoAte() != null && usuario.getBloqueadoAte().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("RN12: usuário bloqueado para novos empréstimos até "
                + usuario.getBloqueadoAte().format(BR) + ".");
        }

        List<Emprestimo> emAberto = emprestimoRepository
            .findByUsuarioAndStatusIn(usuario, List.of("ATIVO", "ATRASADO"));

        // RN01 — no máximo 3 simultâneos
        if (emAberto.size() >= LIMITE_EMPRESTIMOS) {
            throw new RuntimeException("RN01: limite de " + LIMITE_EMPRESTIMOS
                + " empréstimos simultâneos atingido.");
        }

        // RN01 — não pode ter dois exemplares do mesmo título
        boolean jaTemEsseTitulo = emAberto.stream()
            .anyMatch(e -> e.getExemplar().getLivro().getId().equals(exemplar.getLivro().getId()));
        if (jaTemEsseTitulo) {
            throw new RuntimeException("RN01: o usuário já possui um exemplar de \""
                + exemplar.getLivro().getTitulo() + "\" emprestado.");
        }

        int prazo = (prazoDias != null && prazoDias > 0) ? prazoDias : PRAZO_PADRAO_DIAS;

        Emprestimo emprestimo = new Emprestimo();
        emprestimo.setExemplar(exemplar);
        emprestimo.setUsuario(usuario);
        emprestimo.setBiblioteca(exemplar.getBiblioteca());
        emprestimo.setDataEmprestimo(LocalDateTime.now());
        emprestimo.setDataPrevDevolucao(LocalDateTime.now().plusDays(prazo));
        emprestimo.setStatus("ATIVO");
        emprestimoRepository.save(emprestimo);

        // RN05 — atualiza o Blackboard: o exemplar passa a EMPRESTADO
        exemplar.setStatus("EMPRESTADO");
        exemplarRepository.save(exemplar);

        // RN06 — registra no histórico de circulação
        historicoService.registrar(exemplar, "EMPRESTIMO", usuario, exemplar.getBiblioteca(),
            "Empréstimo de " + prazo + " dias para " + usuario.getNome() + ".");

        System.out.println("[CIRCULA BOOK] Empréstimo registrado: "
            + exemplar.getLivro().getTitulo() + " -> " + usuario.getNome()
            + " | Devolução prevista: " + emprestimo.getDataPrevDevolucao().format(BR));

        return emprestimo;
    }

    /**
     * UC10 — Registrar devolução.
     * Calcula atraso, aplica o bloqueio da RN12 e devolve o exemplar ao acervo (RN05).
     */
    @Transactional
    public Emprestimo devolver(Long emprestimoId, String condicaoExemplar) {

        Emprestimo emprestimo = emprestimoRepository.findById(emprestimoId)
            .orElseThrow(() -> new RuntimeException("Empréstimo não encontrado: ID " + emprestimoId));

        if ("DEVOLVIDO".equals(emprestimo.getStatus())) {
            throw new RuntimeException("Este empréstimo já foi devolvido.");
        }

        LocalDateTime agora = LocalDateTime.now();
        emprestimo.setDataDevolucao(agora);
        emprestimo.setStatus("DEVOLVIDO");

        long diasAtraso = calcularDiasAtraso(emprestimo.getDataPrevDevolucao(), agora);

        Usuario usuario = emprestimo.getUsuario();
        if (diasAtraso > 0) {
            // RN12 — cada dia de atraso soma 2 dias de bloqueio
            long diasBloqueio = diasAtraso * DIAS_BLOQUEIO_POR_ATRASO;
            LocalDateTime base = (usuario.getBloqueadoAte() != null
                                  && usuario.getBloqueadoAte().isAfter(agora))
                                 ? usuario.getBloqueadoAte() : agora;
            usuario.setBloqueadoAte(base.plusDays(diasBloqueio));
            usuarioRepository.save(usuario);

            System.out.println("[CIRCULA BOOK] RN12 aplicada: " + usuario.getNome()
                + " bloqueado por " + diasBloqueio + " dias (até "
                + usuario.getBloqueadoAte().format(BR) + ").");
        }

        emprestimoRepository.save(emprestimo);

        Exemplar exemplar = emprestimo.getExemplar();
        String condicao = (condicaoExemplar != null) ? condicaoExemplar.toUpperCase() : "BOM";

        // RN05 — devolve o exemplar ao acervo conforme a condição informada
        if ("PERDIDO".equals(condicao)) {
            exemplar.setStatus("INDISPONIVEL");
            exemplar.setEstadoConservacao("PERDIDO");
            historicoService.registrar(exemplar, "BAIXA", usuario, exemplar.getBiblioteca(),
                "Exemplar declarado perdido na devolução.");
        } else if ("DANIFICADO".equals(condicao)) {
            exemplar.setStatus("INDISPONIVEL");
            exemplar.setEstadoConservacao("DANIFICADO");
            historicoService.registrar(exemplar, "DEVOLUCAO", usuario, exemplar.getBiblioteca(),
                "Devolvido danificado — retirado de circulação para avaliação.");
        } else {
            exemplar.setStatus("DISPONIVEL");
            exemplar.setEstadoConservacao("BOM");
            historicoService.registrar(exemplar, "DEVOLUCAO", usuario, exemplar.getBiblioteca(),
                diasAtraso > 0
                    ? "Devolução com " + diasAtraso + " dia(s) de atraso."
                    : "Devolução dentro do prazo.");
            notificarProximoDaFila(exemplar);
        }
        exemplarRepository.save(exemplar);

        return emprestimo;
    }

    /**
     * RN03 — quando um exemplar volta para o acervo, o primeiro da fila
     * de espera daquele título é promovido e ganha 3 dias para retirar.
     */
    private void notificarProximoDaFila(Exemplar exemplar) {
        List<Reserva> fila = reservaRepository
            .findByLivroAndStatusOrderByDataReservaAsc(exemplar.getLivro(), "PENDENTE");
        if (fila.isEmpty()) return;

        Reserva proxima = fila.get(0);
        proxima.setStatus("DISPONIVEL");
        proxima.setDataExpiracao(LocalDateTime.now().plusDays(3));
        reservaRepository.save(proxima);

        exemplar.setStatus("RESERVADO");

        historicoService.registrar(exemplar, "RESERVA", proxima.getUsuario(),
            exemplar.getBiblioteca(),
            "Exemplar reservado para " + proxima.getUsuario().getNome()
            + " (1º da fila). Prazo de retirada: 3 dias (RN03).");

        System.out.println("[CIRCULA BOOK] Fila de espera: " + proxima.getUsuario().getNome()
            + " foi notificado sobre \"" + exemplar.getLivro().getTitulo() + "\".");
    }

    public long calcularDiasAtraso(LocalDateTime previsto, LocalDateTime efetivo) {
        if (previsto == null || efetivo == null || !efetivo.isAfter(previsto)) return 0;
        return Duration.between(previsto, efetivo).toDays();
    }
}
