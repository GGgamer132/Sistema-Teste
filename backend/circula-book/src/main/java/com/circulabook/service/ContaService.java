package com.circulabook.service;

import com.circulabook.dto.ContaDTOs.*;
import com.circulabook.model.*;
import com.circulabook.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Dados do próprio usuário COMUM (§7.2: Minhas reservas, Meus empréstimos, Histórico).
 * Só leitura; o id vem sempre do token (o controller nunca aceita id por parâmetro).
 */
@Service
@Transactional(readOnly = true)
public class ContaService {

    private static final List<String> RESERVA_ATIVA =
        List.of("PENDENTE", "AGUARDANDO_TRANSFERENCIA", "DISPONIVEL");
    private static final List<String> RESERVA_ENCERRADA = List.of("RETIRADA", "CANCELADA", "EXPIRADA");
    private static final List<String> EMPRESTIMO_ABERTO = List.of("ATIVO", "ATRASADO");

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private EmprestimoRepository emprestimoRepository;
    @Autowired private SolicitacaoTransferenciaRepository transferenciaRepository;
    @Autowired private EmprestimoService emprestimoService;

    public List<MinhaReserva> minhasReservas(Long usuarioId) {
        Usuario u = buscarUsuario(usuarioId);
        return reservaRepository.findByUsuarioAndStatusIn(u, RESERVA_ATIVA).stream()
            .sorted(Comparator.comparing(Reserva::getDataReserva).reversed())
            .map(this::paraMinhaReserva)
            .toList();
    }

    public MeusEmprestimos meusEmprestimos(Long usuarioId) {
        Usuario u = buscarUsuario(usuarioId);
        LocalDateTime agora = LocalDateTime.now();
        List<MeuEmprestimo> ativos = emprestimoRepository.findByUsuarioAndStatusIn(u, EMPRESTIMO_ABERTO).stream()
            .sorted(Comparator.comparing(Emprestimo::getDataPrevDevolucao))
            .map(e -> {
                long atraso = emprestimoService.calcularDiasAtraso(e.getDataPrevDevolucao(), agora);
                Livro l = e.getExemplar().getLivro();
                return new MeuEmprestimo(e.getId(), e.getExemplar().getId(), l.getId(), l.getTitulo(),
                    l.getAutor(), e.getBiblioteca().getId(), e.getBiblioteca().getNome(),
                    e.getDataEmprestimo(), e.getDataPrevDevolucao(),
                    atraso, e.getDataPrevDevolucao().isBefore(agora));
            })
            .toList();
        boolean bloqueado = u.getBloqueadoAte() != null && u.getBloqueadoAte().isAfter(agora);
        return new MeusEmprestimos(ativos, ativos.size(), EmprestimoService.LIMITE_EMPRESTIMOS,
            bloqueado, bloqueado ? u.getBloqueadoAte() : null);
    }

    /**
     * Empréstimos devolvidos e reservas encerradas, do mais recente ao mais antigo.
     * tipo: EMPRESTIMO, RESERVA ou null (ambos); de/ate filtram a data de início (inclusive).
     */
    public Pagina<ItemHistorico> historico(Long usuarioId, String tipo, LocalDate de, LocalDate ate,
                                           int pagina, int tamanho) {
        Usuario u = buscarUsuario(usuarioId);
        String t = tipo == null || tipo.isBlank() ? null : tipo.trim().toUpperCase();
        if (t != null && !t.equals("EMPRESTIMO") && !t.equals("RESERVA")) {
            throw new RuntimeException("Tipo inválido: use EMPRESTIMO ou RESERVA.");
        }
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new RuntimeException("A data inicial não pode ser depois da data final.");
        }
        if (pagina < 0) throw new RuntimeException("A página começa em 0.");
        if (tamanho < 1 || tamanho > 50) throw new RuntimeException("O tamanho da página deve ser de 1 a 50.");

        List<ItemHistorico> itens = new ArrayList<>();
        if (t == null || t.equals("EMPRESTIMO")) {
            for (Emprestimo e : emprestimoRepository.findByUsuarioAndStatusIn(u, List.of("DEVOLVIDO"))) {
                Livro l = e.getExemplar().getLivro();
                long atraso = emprestimoService.calcularDiasAtraso(e.getDataPrevDevolucao(), e.getDataDevolucao());
                itens.add(new ItemHistorico("EMPRESTIMO", e.getId(), l.getId(), l.getTitulo(), l.getAutor(),
                    e.getBiblioteca().getNome(), e.getStatus(), e.getDataEmprestimo(), e.getDataDevolucao(),
                    atraso > 0 ? "Devolvido com " + atraso + " dia(s) de atraso." : "Devolvido no prazo."));
            }
        }
        if (t == null || t.equals("RESERVA")) {
            for (Reserva r : reservaRepository.findByUsuarioAndStatusIn(u, RESERVA_ENCERRADA)) {
                Livro l = r.getLivro();
                itens.add(new ItemHistorico("RESERVA", r.getId(), l.getId(), l.getTitulo(), l.getAutor(),
                    r.getBibliotecaFila().getNome(), r.getStatus(), r.getDataReserva(),
                    "EXPIRADA".equals(r.getStatus()) ? r.getDataExpiracao() : null,
                    detalheReservaEncerrada(r)));
            }
        }

        List<ItemHistorico> filtrados = itens.stream()
            .filter(i -> i.data() != null)
            .filter(i -> de == null || !i.data().toLocalDate().isBefore(de))
            .filter(i -> ate == null || !i.data().toLocalDate().isAfter(ate))
            .sorted(Comparator.comparing(ItemHistorico::data).reversed())
            .toList();

        int total = filtrados.size();
        int inicio = Math.min(pagina * tamanho, total);
        int fim = Math.min(inicio + tamanho, total);
        return new Pagina<>(filtrados.subList(inicio, fim), pagina, tamanho, total,
            (int) Math.ceil(total / (double) tamanho));
    }

    // ───────────────────────── Apoio ─────────────────────────

    private Usuario buscarUsuario(Long id) {
        return usuarioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + id));
    }

    private MinhaReserva paraMinhaReserva(Reserva r) {
        Integer posicao = null;
        if ("PENDENTE".equals(r.getStatus())) {
            List<Reserva> fila = reservaRepository.findByLivroAndBibliotecaFilaAndStatusOrderByDataReservaAsc(
                r.getLivro(), r.getBibliotecaFila(), "PENDENTE");
            for (int i = 0; i < fila.size(); i++) {
                if (fila.get(i).getId().equals(r.getId())) { posicao = i + 1; break; }
            }
        }
        TransferenciaDaReserva transf = transferenciaRepository
            .findFirstByReservaOrderByDataSolicitacaoDescIdDesc(r)
            .map(this::situacao)
            .orElse(null);
        Livro l = r.getLivro();
        return new MinhaReserva(r.getId(), l.getId(), l.getTitulo(), l.getAutor(),
            r.getBibliotecaFila().getId(), r.getBibliotecaFila().getNome(),
            r.getBibliotecaDestino().getId(), r.getBibliotecaDestino().getNome(),
            posicao, r.getStatus(), r.getDataReserva(),
            "DISPONIVEL".equals(r.getStatus()) ? r.getDataExpiracao() : null,
            transf);
    }

    private TransferenciaDaReserva situacao(SolicitacaoTransferencia s) {
        String origem = s.getBibliotecaOrigem().getNome();
        String destino = s.getBibliotecaDestino().getNome();
        boolean comExemplar = s.getExemplar() != null;
        String etapa;
        String descricao;
        switch (s.getStatus()) {
            case "PENDENTE" -> {
                etapa = comExemplar ? "PENDENTE_COM_EXEMPLAR" : "PENDENTE";
                descricao = comExemplar
                    ? "Exemplar separado na " + origem + ", aguardando a aprovação do administrador."
                    : "Transferência para a " + destino + " aguardando a aprovação do administrador.";
            }
            case "APROVADA" -> {
                etapa = "APROVADA_AGUARDANDO_EXEMPLAR";
                descricao = "Transferência aprovada, aguardando um exemplar ser devolvido na " + origem + ".";
            }
            case "EM_TRANSITO" -> {
                etapa = "EM_TRANSITO";
                descricao = "Exemplar a caminho: " + origem + " → " + destino + ".";
            }
            case "CONCLUIDA" -> {
                etapa = "CONCLUIDA";
                descricao = "O exemplar chegou à " + destino + ".";
            }
            case "REJEITADA" -> {
                etapa = "REJEITADA";
                descricao = "Transferência rejeitada pelo administrador: a retirada será na " + origem + ".";
            }
            default -> {
                etapa = s.getStatus();
                descricao = "Transferência cancelada: a retirada será na " + origem + ".";
            }
        }
        return new TransferenciaDaReserva(s.getId(), s.getStatus(), etapa, descricao);
    }

    private String detalheReservaEncerrada(Reserva r) {
        String onde = r.getBibliotecaFila().getId().equals(r.getBibliotecaDestino().getId())
            ? "Fila e retirada na " + r.getBibliotecaFila().getNome()
            : "Fila na " + r.getBibliotecaFila().getNome() + ", retirada na " + r.getBibliotecaDestino().getNome();
        return switch (r.getStatus()) {
            case "RETIRADA" -> onde + ". Livro retirado.";
            case "CANCELADA" -> onde + ". Reserva cancelada.";
            default -> onde + ". O prazo de retirada venceu.";
        };
    }
}
