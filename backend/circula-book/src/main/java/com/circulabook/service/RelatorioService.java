package com.circulabook.service;

import com.circulabook.model.*;
import com.circulabook.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Relatórios gerenciais do Admin (A10) — somente dados tabulares.
 *
 * Período (datas inclusive, opcionais): vale para empréstimos realizados/devolvidos,
 * devoluções com atraso, livros mais emprestados, demandas (data de criação) e
 * transferências (data do pedido). Acervo e empréstimos em atraso ainda em aberto
 * mostram a situação de agora.
 */
@Service
@Transactional(readOnly = true)
public class RelatorioService {

    private static final List<String> EMPRESTIMO_ABERTO = List.of("ATIVO", "ATRASADO");
    private static final List<String> STATUS_TRANSFERENCIA =
        List.of("PENDENTE", "APROVADA", "EM_TRANSITO", "CONCLUIDA", "REJEITADA", "CANCELADA");
    private static final Map<String, String> ROTULO_TRANSFERENCIA = Map.of(
        "PENDENTE", "Aguardando decisão", "APROVADA", "Aprovada, aguardando exemplar",
        "EM_TRANSITO", "Em trânsito", "CONCLUIDA", "Concluída",
        "REJEITADA", "Rejeitada", "CANCELADA", "Cancelada");
    private static final int TOP = 10;

    @Autowired private BibliotecaRepository bibliotecaRepository;
    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private EmprestimoRepository emprestimoRepository;
    @Autowired private SolicitacaoTransferenciaRepository transferenciaRepository;
    @Autowired private DemandaAquisicaoRepository demandaRepository;
    @Autowired private EmprestimoService emprestimoService;

    public record Periodo(LocalDate de, LocalDate ate) {}

    /** Acervo de uma biblioteca: total e quantidade em cada situação (chaves de PainelService.STATUS_EXEMPLAR). */
    public record AcervoLinha(Long bibliotecaId, String biblioteca, long total, Map<String, Long> porStatus) {}

    public record EmprestimosLinha(Long bibliotecaId, String biblioteca, long realizados, long devolvidos,
                                   long devolvidosComAtraso, long emAberto) {}

    public record AtrasoLinha(Long emprestimoId, String leitor, String titulo, String biblioteca,
                              LocalDateTime dataPrevDevolucao, LocalDateTime dataDevolucao,
                              long diasAtraso, String situacao) {}

    public record LivroLinha(Long livroId, String titulo, String autor, long emprestimos) {}

    public record DemandaLinha(Long id, String titulo, String autor, int totalSolicitacoes,
                               String status, String statusRotulo) {}

    public record TransferenciaLinha(String status, String rotulo, long deReserva, long avulsas, long total) {}

    public record RelatoriosDTO(Periodo periodo, List<String> statusExemplar, Map<String, String> rotuloStatusExemplar,
                                List<AcervoLinha> acervo, List<EmprestimosLinha> emprestimos,
                                List<AtrasoLinha> atrasos, List<LivroLinha> maisEmprestados,
                                List<DemandaLinha> demandasMaisPedidas, List<TransferenciaLinha> transferencias) {}

    public RelatoriosDTO gerar(LocalDate de, LocalDate ate) {
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new RuntimeException("A data inicial não pode ser depois da data final.");
        }
        LocalDateTime agora = LocalDateTime.now();
        List<Biblioteca> bibliotecas = bibliotecaRepository.findAll().stream()
            .sorted(Comparator.comparing(Biblioteca::getNome)).toList();
        List<Exemplar> exemplares = exemplarRepository.findAll();
        List<Emprestimo> emprestimos = emprestimoRepository.findAll();

        // 1) Acervo por biblioteca e situação (agora)
        List<AcervoLinha> acervo = new ArrayList<>();
        for (Biblioteca b : bibliotecas) {
            Map<String, Long> porStatus = new LinkedHashMap<>();
            PainelService.STATUS_EXEMPLAR.forEach(s -> porStatus.put(s, 0L));
            long total = 0;
            for (Exemplar e : exemplares) {
                if (!e.getBiblioteca().getId().equals(b.getId())) continue;
                porStatus.merge(e.getStatus(), 1L, Long::sum);
                total++;
            }
            acervo.add(new AcervoLinha(b.getId(), b.getNome(), total, porStatus));
        }

        // 2) Empréstimos no período, por biblioteca
        List<EmprestimosLinha> porBiblioteca = new ArrayList<>();
        for (Biblioteca b : bibliotecas) {
            long realizados = 0, devolvidos = 0, comAtraso = 0, emAberto = 0;
            for (Emprestimo e : emprestimos) {
                if (!e.getBiblioteca().getId().equals(b.getId())) continue;
                if (dentro(e.getDataEmprestimo(), de, ate)) realizados++;
                if (e.getDataDevolucao() != null && dentro(e.getDataDevolucao(), de, ate)) {
                    devolvidos++;
                    if (e.getDataDevolucao().isAfter(e.getDataPrevDevolucao())) comAtraso++;
                }
                if (EMPRESTIMO_ABERTO.contains(e.getStatus())) emAberto++;
            }
            porBiblioteca.add(new EmprestimosLinha(b.getId(), b.getNome(), realizados, devolvidos, comAtraso, emAberto));
        }

        // 3) Atrasos: em aberto agora + devolvidos com atraso no período
        List<AtrasoLinha> atrasos = new ArrayList<>();
        for (Emprestimo e : emprestimos) {
            boolean aberto = EMPRESTIMO_ABERTO.contains(e.getStatus());
            if (aberto && e.getDataPrevDevolucao().isBefore(agora)) {
                atrasos.add(atraso(e, emprestimoService.calcularDiasAtraso(e.getDataPrevDevolucao(), agora), "Em aberto"));
            } else if (!aberto && e.getDataDevolucao() != null
                       && e.getDataDevolucao().isAfter(e.getDataPrevDevolucao())
                       && dentro(e.getDataDevolucao(), de, ate)) {
                atrasos.add(atraso(e, emprestimoService.calcularDiasAtraso(e.getDataPrevDevolucao(), e.getDataDevolucao()),
                    "Devolvido com atraso"));
            }
        }
        atrasos.sort(Comparator.comparing(AtrasoLinha::situacao).reversed()   // "Em aberto" primeiro
            .thenComparing(AtrasoLinha::diasAtraso, Comparator.reverseOrder()));

        // 4) Livros mais emprestados no período
        Map<Livro, Long> contagem = emprestimos.stream()
            .filter(e -> dentro(e.getDataEmprestimo(), de, ate))
            .collect(Collectors.groupingBy(e -> e.getExemplar().getLivro(), Collectors.counting()));
        List<LivroLinha> maisEmprestados = contagem.entrySet().stream()
            .sorted(Map.Entry.<Livro, Long>comparingByValue().reversed()
                .thenComparing(en -> en.getKey().getTitulo()))
            .limit(TOP)
            .map(en -> new LivroLinha(en.getKey().getId(), en.getKey().getTitulo(), en.getKey().getAutor(), en.getValue()))
            .toList();

        // 5) Demandas mais pedidas (criadas no período)
        List<DemandaLinha> demandas = demandaRepository.findAll().stream()
            .filter(d -> dentro(d.getCriadaEm(), de, ate))
            .sorted(Comparator.comparing((DemandaAquisicao d) -> d.getTotalSolicitacoes() == null ? 0 : d.getTotalSolicitacoes())
                .reversed().thenComparing(DemandaAquisicao::getTitulo))
            .limit(TOP)
            .map(d -> {
                DemandaService.DemandaDTO dto = DemandaService.DemandaDTO.de(d);
                return new DemandaLinha(d.getId(), d.getTitulo(), d.getAutor(), dto.totalSolicitacoes(),
                    dto.status(), dto.statusRotulo());
            })
            .toList();

        // 6) Transferências por situação (pedidas no período)
        List<SolicitacaoTransferencia> transf = transferenciaRepository.findAll().stream()
            .filter(s -> dentro(s.getDataSolicitacao(), de, ate)).toList();
        List<TransferenciaLinha> porStatusTransf = STATUS_TRANSFERENCIA.stream().map(st -> {
            long reserva = transf.stream().filter(s -> st.equals(s.getStatus()) && s.getReserva() != null).count();
            long avulsa = transf.stream().filter(s -> st.equals(s.getStatus()) && s.getReserva() == null).count();
            return new TransferenciaLinha(st, ROTULO_TRANSFERENCIA.get(st), reserva, avulsa, reserva + avulsa);
        }).toList();

        Map<String, String> rotulos = new LinkedHashMap<>();
        PainelService.STATUS_EXEMPLAR.forEach(s -> rotulos.put(s, StatusExemplar.rotulo(s)));
        return new RelatoriosDTO(new Periodo(de, ate), PainelService.STATUS_EXEMPLAR, rotulos,
            acervo, porBiblioteca, atrasos, maisEmprestados, demandas, porStatusTransf);
    }

    private static boolean dentro(LocalDateTime data, LocalDate de, LocalDate ate) {
        if (data == null) return de == null && ate == null;
        if (de != null && data.isBefore(de.atStartOfDay())) return false;
        return ate == null || data.isBefore(ate.plusDays(1).atStartOfDay());
    }

    private static AtrasoLinha atraso(Emprestimo e, long dias, String situacao) {
        return new AtrasoLinha(e.getId(), e.getUsuario().getNome(), e.getExemplar().getLivro().getTitulo(),
            e.getBiblioteca().getNome(), e.getDataPrevDevolucao(), e.getDataDevolucao(), dias, situacao);
    }
}
