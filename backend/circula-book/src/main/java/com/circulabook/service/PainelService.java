package com.circulabook.service;

import com.circulabook.model.StatusExemplar;
import com.circulabook.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Dashboard do Admin (A1): números da rede agora, só leitura. */
@Service
@Transactional(readOnly = true)
public class PainelService {

    /** Ordem de exibição dos status do exemplar. */
    public static final List<String> STATUS_EXEMPLAR = List.of(
        StatusExemplar.DISPONIVEL, StatusExemplar.EMPRESTADO, StatusExemplar.EMPRESTADO_RESERVADO,
        StatusExemplar.RESERVADO, StatusExemplar.EM_TRANSFERENCIA, StatusExemplar.INDISPONIVEL);

    private static final List<String> EMPRESTIMO_ABERTO = List.of("ATIVO", "ATRASADO");

    @Autowired private BibliotecaRepository bibliotecaRepository;
    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private EmprestimoRepository emprestimoRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private SolicitacaoTransferenciaRepository transferenciaRepository;
    @Autowired private DemandaAquisicaoRepository demandaRepository;

    public record ContagemStatus(String status, String rotulo, long total) {}

    public record DashboardDTO(
        long bibliotecasAtivas, long bibliotecasTotal,
        long exemplaresTotal, List<ContagemStatus> exemplaresPorStatus,
        long emprestimosAtivos, long emprestimosAtrasados,
        long reservasEmFila, long reservasProntas,
        long transferenciasPendentes, long transferenciasAguardandoExemplar, long transferenciasEmTransito,
        long demandasAbertas, long demandasEmAnalise) {}

    public DashboardDTO dashboard() {
        List<ContagemStatus> porStatus = STATUS_EXEMPLAR.stream()
            .map(s -> new ContagemStatus(s, StatusExemplar.rotulo(s), exemplarRepository.countByStatus(s)))
            .toList();
        return new DashboardDTO(
            bibliotecaRepository.countByAtivaTrue(), bibliotecaRepository.count(),
            exemplarRepository.count(), porStatus,
            emprestimoRepository.countByStatusIn(EMPRESTIMO_ABERTO),
            // atraso derivado da data prevista (o status ATRASADO pode estar desatualizado)
            emprestimoRepository.countByStatusInAndDataPrevDevolucaoBefore(EMPRESTIMO_ABERTO, LocalDateTime.now()),
            reservaRepository.countByStatus("PENDENTE"),
            reservaRepository.countByStatus("DISPONIVEL"),
            transferenciaRepository.countByStatus("PENDENTE"),
            transferenciaRepository.countByStatusAndExemplarIsNull("APROVADA"),
            transferenciaRepository.countByStatus("EM_TRANSITO"),
            demandaRepository.countByStatus(DemandaService.ABERTA),
            demandaRepository.countByStatus(DemandaService.EM_ANALISE));
    }
}
