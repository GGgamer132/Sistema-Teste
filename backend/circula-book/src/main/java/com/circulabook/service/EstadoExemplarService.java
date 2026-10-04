package com.circulabook.service;

import com.circulabook.model.Biblioteca;
import com.circulabook.model.Exemplar;
import com.circulabook.model.Livro;
import com.circulabook.model.StatusExemplar;
import com.circulabook.repository.ExemplarRepository;
import com.circulabook.repository.ReservaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Máquina de estados do exemplar (CONTEXTO §4).
 *
 * É o ÚNICO lugar que altera {@code exemplar.status}: os demais especialistas
 * pedem a mudança aqui, que recusa qualquer transição fora da tabela da §4.3.
 * Toda transição aceita vira uma linha no histórico de circulação (RN06), com o
 * evento deduzido da transição e a observação de quem pediu a mudança.
 * Também mantém a invariante da fila (§4.2) por meio de sincronizarMarcaDeFila.
 */
@Service
public class EstadoExemplarService {

    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private HistoricoService historicoService;

    /**
     * Muda o status do exemplar validando a transição.
     * Exemplar ainda sem status (recém-criado) só pode nascer DISPONIVEL ou RESERVADO (T15).
     */
    @Transactional
    public Exemplar mudarStatus(Exemplar exemplar, String novoStatus) {
        return mudarStatus(exemplar, novoStatus, null);
    }

    /**
     * Igual a {@link #mudarStatus(Exemplar, String)}, com a observação que vai ao histórico
     * (null = descrição padrão da transição).
     */
    @Transactional
    public Exemplar mudarStatus(Exemplar exemplar, String novoStatus, String observacao) {
        String atual = exemplar.getStatus();
        if (!StatusExemplar.transicaoValida(atual, novoStatus)) {
            throw new RuntimeException("Mudança de situação não permitida para o Exemplar nº "
                + exemplar.getId() + ": de " + StatusExemplar.rotulo(atual)
                + " para " + StatusExemplar.rotulo(novoStatus) + ".");
        }
        exemplar.setStatus(novoStatus);
        Exemplar salvo = exemplarRepository.save(exemplar);
        historicoService.registrar(salvo, evento(atual, novoStatus), salvo.getBiblioteca(),
            observacao != null ? observacao : descricaoPadrao(salvo, atual, novoStatus));
        return salvo;
    }

    /** Evento do histórico para cada transição da §4.3. */
    static String evento(String de, String para) {
        if (de == null) return "CADASTRO";                                   // T15
        if (StatusExemplar.INDISPONIVEL.equals(para)) {
            if (StatusExemplar.emprestado(de)) return "DEVOLUCAO";            // devolução DANIFICADO
            if (StatusExemplar.EM_TRANSFERENCIA.equals(de)) return "TRANSFERENCIA_CHEGADA"; // chegou danificado
            return "BAIXA";                                                   // T13
        }
        if (StatusExemplar.INDISPONIVEL.equals(de)) return "REATIVACAO";     // T14, T14b
        if (StatusExemplar.EM_TRANSFERENCIA.equals(de)) return "TRANSFERENCIA_CHEGADA"; // T10, T11
        if (StatusExemplar.EM_TRANSFERENCIA.equals(para)) return "TRANSFERENCIA_SAIDA"; // T8, T9
        if (StatusExemplar.emprestado(de) && StatusExemplar.emprestado(para)) return "FILA"; // T3, T12
        if (StatusExemplar.emprestado(de)) return "DEVOLUCAO";               // T2, T4
        if (StatusExemplar.emprestado(para)) return "EMPRESTIMO";            // T1, T5, T6
        return "RESERVA";                                                    // T7, T7b
    }

    private static String descricaoPadrao(Exemplar e, String de, String para) {
        if (StatusExemplar.EMPRESTADO_RESERVADO.equals(para) && StatusExemplar.EMPRESTADO.equals(de)) {
            return "Formou-se fila de espera para o título na " + e.getBiblioteca().getNome()
                + ": quando este exemplar voltar, ele fica com o próximo da fila.";
        }
        if (StatusExemplar.EMPRESTADO.equals(para) && StatusExemplar.EMPRESTADO_RESERVADO.equals(de)) {
            return "A fila de espera do título na " + e.getBiblioteca().getNome() + " esvaziou.";
        }
        return "Situação: " + (de == null ? "novo" : StatusExemplar.rotulo(de).toLowerCase())
            + " → " + StatusExemplar.rotulo(para).toLowerCase() + ".";
    }

    /** Há reservas PENDENTE do título na fila desta biblioteca? */
    public boolean temFila(Livro livro, Biblioteca biblioteca) {
        return reservaRepository.countByLivroAndBibliotecaFilaAndStatus(livro, biblioteca, "PENDENTE") > 0;
    }

    /**
     * Invariante da fila (§4.2): com fila PENDENTE, todo exemplar emprestado do título
     * nesta biblioteca fica EMPRESTADO_RESERVADO (T3); sem fila, EMPRESTADO (T12).
     * Chamar ao final de toda operação que mexe em fila ou empréstimo.
     */
    @Transactional
    public void sincronizarMarcaDeFila(Livro livro, Biblioteca biblioteca) {
        if (livro == null || biblioteca == null) return;
        String alvo = temFila(livro, biblioteca)
            ? StatusExemplar.EMPRESTADO_RESERVADO
            : StatusExemplar.EMPRESTADO;
        for (Exemplar e : exemplarRepository.findByLivroAndBiblioteca(livro, biblioteca)) {
            if (StatusExemplar.emprestado(e.getStatus()) && !alvo.equals(e.getStatus())) {
                mudarStatus(e, alvo);
            }
        }
    }
}
