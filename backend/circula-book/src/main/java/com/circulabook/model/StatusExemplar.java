package com.circulabook.model;

import java.util.Map;
import java.util.Set;

/**
 * Estados do exemplar e a tabela de transições válidas (CONTEXTO §4.1 e §4.3).
 * Os status continuam sendo String (convenção do projeto); aqui ficam as
 * constantes e a única definição de quais mudanças são permitidas.
 */
public final class StatusExemplar {

    public static final String DISPONIVEL = "DISPONIVEL";
    public static final String EMPRESTADO = "EMPRESTADO";
    /** Emprestado e há fila PENDENTE do título nesta biblioteca (§4.2). */
    public static final String EMPRESTADO_RESERVADO = "EMPRESTADO_RESERVADO";
    /** Separado para o 1º da fila, aguardando retirada (ou transferência). */
    public static final String RESERVADO = "RESERVADO";
    public static final String EM_TRANSFERENCIA = "EM_TRANSFERENCIA";
    public static final String INDISPONIVEL = "INDISPONIVEL";

    private StatusExemplar() {}

    /** Origem -> destinos permitidos. Cada par corresponde a uma linha da §4.3. */
    private static final Map<String, Set<String>> TRANSICOES = Map.of(
        DISPONIVEL, Set.of(
            EMPRESTADO,             // T1  empréstimo
            EM_TRANSFERENCIA,       // T8  transferência avulsa
            INDISPONIVEL),          // T13 baixa
        EMPRESTADO, Set.of(
            DISPONIVEL,             // T2  devolução BOM, fila vazia
            EMPRESTADO_RESERVADO,   // T3  alguém entrou na fila
            INDISPONIVEL),          // T13 devolução DANIFICADO
        EMPRESTADO_RESERVADO, Set.of(
            RESERVADO,              // T4  devolução BOM: separado para o 1º da fila
            EMPRESTADO,             // T12 a fila esvaziou
            INDISPONIVEL),          // T13 devolução DANIFICADO
        RESERVADO, Set.of(
            EMPRESTADO_RESERVADO,   // T5  retirada e ainda há fila
            EMPRESTADO,             // T6  retirada e não há mais fila
            DISPONIVEL,             // T7  cancelamento/expiração com fila vazia
            RESERVADO,              // T7b cancelamento/expiração com fila: reatribui
            EM_TRANSFERENCIA,       // T9  transferência de reserva aprovada
            INDISPONIVEL),          // T13 baixa
        EM_TRANSFERENCIA, Set.of(
            RESERVADO,              // T10 chegada com reserva esperando
            DISPONIVEL,             // T11 chegada sem reserva esperando
            INDISPONIVEL),          // T13 chegou danificado
        INDISPONIVEL, Set.of(
            DISPONIVEL,             // T14  reativação
            RESERVADO)              // T14b reativação com fila
    );

    /** T15: estados possíveis na criação do exemplar. */
    private static final Set<String> INICIAIS = Set.of(DISPONIVEL, RESERVADO);

    /** {@code de == null} significa criação do exemplar (T15). */
    public static boolean transicaoValida(String de, String para) {
        if (para == null) return false;
        if (de == null) return INICIAIS.contains(para);
        return TRANSICOES.getOrDefault(de, Set.of()).contains(para);
    }

    public static boolean emprestado(String status) {
        return EMPRESTADO.equals(status) || EMPRESTADO_RESERVADO.equals(status);
    }

    /** Nome legível para mensagens ao usuário. */
    public static String rotulo(String status) {
        if (status == null) return "(novo)";
        return switch (status) {
            case DISPONIVEL -> "Disponível";
            case EMPRESTADO -> "Emprestado";
            case EMPRESTADO_RESERVADO -> "Emprestado (com fila)";
            case RESERVADO -> "Reservado";
            case EM_TRANSFERENCIA -> "Em transferência";
            case INDISPONIVEL -> "Indisponível";
            default -> status;
        };
    }
}
