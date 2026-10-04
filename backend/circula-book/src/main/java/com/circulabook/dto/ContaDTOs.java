package com.circulabook.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Payloads de /api/conta: dados do próprio usuário COMUM (ator sempre do token). */
public final class ContaDTOs {

    private ContaDTOs() {}

    /**
     * Situação do pedido de transferência de uma reserva.
     * etapa: PENDENTE | PENDENTE_COM_EXEMPLAR | APROVADA_AGUARDANDO_EXEMPLAR | EM_TRANSITO
     *        | CONCLUIDA | REJEITADA | CANCELADA
     */
    public record TransferenciaDaReserva(Long id, String status, String etapa, String descricao) {}

    /** Item de GET /api/conta/reservas (só reservas ativas). */
    public record MinhaReserva(
        Long id, Long livroId, String titulo, String autor,
        Long bibliotecaFilaId, String bibliotecaFila,
        Long bibliotecaRetiradaId, String bibliotecaRetirada,
        Integer posicao,             // só quando PENDENTE
        String status,               // PENDENTE | AGUARDANDO_TRANSFERENCIA | DISPONIVEL
        LocalDateTime dataReserva,
        LocalDateTime retireAte,     // só quando DISPONIVEL
        TransferenciaDaReserva transferencia) {}  // null quando a retirada sempre foi na própria fila

    public record MeuEmprestimo(
        Long id, Long exemplarId, Long livroId, String titulo, String autor,
        Long bibliotecaId, String biblioteca,
        LocalDateTime dataEmprestimo, LocalDateTime dataPrevDevolucao,
        long diasAtraso, boolean atrasado) {}

    /** GET /api/conta/emprestimos: ativos + total x/limite + bloqueio por atraso. */
    public record MeusEmprestimos(
        List<MeuEmprestimo> emprestimos, int total, int limite,
        boolean bloqueado, LocalDateTime bloqueadoAte) {}

    /**
     * Item de GET /api/conta/historico.
     * tipo EMPRESTIMO (status DEVOLVIDO; data = empréstimo, dataFim = devolução) ou
     * RESERVA (status RETIRADA | CANCELADA | EXPIRADA; data = entrada na fila,
     * dataFim = prazo vencido quando EXPIRADA).
     */
    public record ItemHistorico(
        String tipo, Long id, Long livroId, String titulo, String autor,
        String biblioteca, String status,
        LocalDateTime data, LocalDateTime dataFim, String detalhe) {}

    /** Página de resultados; {@code pagina} começa em 0. */
    public record Pagina<T>(List<T> itens, int pagina, int tamanho, long totalItens, int totalPaginas) {}
}
