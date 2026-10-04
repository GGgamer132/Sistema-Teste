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
    public static final int PRAZO_EMPRESTIMO_DIAS = 14; // RN02: prazo fixo, em dias corridos
    public static final int DIAS_BLOQUEIO_POR_ATRASO = 2; // RN12

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private EmprestimoRepository emprestimoRepository;
    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private HistoricoService historicoService;
    @Autowired private FilaEsperaService filaEsperaService;

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
    public Emprestimo registrar(Long exemplarId, Long usuarioId) {

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
     // Exemplar RESERVADO só sai para quem reservou, e só depois de liberado para retirada
        Reserva reservaDoExemplar = null;
        if ("RESERVADO".equals(exemplar.getStatus())) {
            reservaDoExemplar = reservaRepository
                .findFirstByExemplarAndStatus(exemplar, "DISPONIVEL")
                .orElseThrow(() -> new RuntimeException(
                    "RN05: este exemplar está separado para uma reserva e ainda não foi "
                    + "liberado para retirada."));
            if (!reservaDoExemplar.getUsuario().getId().equals(usuario.getId())) {
                throw new RuntimeException("RN05: este exemplar está reservado para "
                    + reservaDoExemplar.getUsuario().getNome() + ".");
            }
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

        Emprestimo emprestimo = new Emprestimo();
        emprestimo.setExemplar(exemplar);
        emprestimo.setUsuario(usuario);
        emprestimo.setBiblioteca(exemplar.getBiblioteca());
        emprestimo.setDataEmprestimo(LocalDateTime.now());
        emprestimo.setDataPrevDevolucao(LocalDateTime.now().plusDays(PRAZO_EMPRESTIMO_DIAS));
        emprestimo.setStatus("ATIVO");
        emprestimoRepository.save(emprestimo);

        // RN05 — atualiza o Blackboard: o exemplar passa a EMPRESTADO
        exemplar.setStatus("EMPRESTADO");
        exemplarRepository.save(exemplar);
     // A reserva que originou este empréstimo foi retirada
        if (reservaDoExemplar != null) {
            reservaDoExemplar.setStatus("RETIRADA");
            reservaRepository.save(reservaDoExemplar);
        }

        // RN06 — registra no histórico de circulação
        historicoService.registrar(exemplar, "EMPRESTIMO", usuario, exemplar.getBiblioteca(),
            "Empréstimo de " + PRAZO_EMPRESTIMO_DIAS + " dias para " + usuario.getNome() + ".");

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

        String condicao = (condicaoExemplar != null) ? condicaoExemplar.toUpperCase() : "BOM";
        if (!"BOM".equals(condicao) && !"DANIFICADO".equals(condicao)) {
            throw new RuntimeException("A condição do exemplar na devolução deve ser Bom ou Danificado.");
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

        // RN05 — devolve o exemplar ao acervo conforme a condição informada
        if ("DANIFICADO".equals(condicao)) {
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
            filaEsperaService.promoverProximo(exemplar);
        }
        exemplarRepository.save(exemplar);

        return emprestimo;
    }


    public long calcularDiasAtraso(LocalDateTime previsto, LocalDateTime efetivo) {
        if (previsto == null || efetivo == null || !efetivo.isAfter(previsto)) return 0;
        return Duration.between(previsto, efetivo).toDays();
    }
}
