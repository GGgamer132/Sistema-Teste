package com.circulabook.service;

import com.circulabook.model.Exemplar;
import com.circulabook.model.StatusExemplar;
import com.circulabook.repository.ExemplarRepository;
import com.circulabook.repository.ReservaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Verificador da invariante da fila (§4.2) e do vínculo de RESERVADO (ES-15).
 * Só existe nos testes; não é exposto pela API.
 */
@TestComponent
public class VerificadorConsistencia {

    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private ReservaRepository reservaRepository;

    /** Lista de violações encontradas (vazia = consistente). */
    public List<String> violacoes() {
        List<String> erros = new ArrayList<>();
        for (Exemplar e : exemplarRepository.findAll()) {
            long fila = reservaRepository.countByLivroAndBibliotecaFilaAndStatus(
                e.getLivro(), e.getBiblioteca(), "PENDENTE");
            String s = e.getStatus();
            if (StatusExemplar.EMPRESTADO_RESERVADO.equals(s) && fila == 0) {
                erros.add("Exemplar nº " + e.getId() + " EMPRESTADO_RESERVADO sem fila");
            }
            if (StatusExemplar.EMPRESTADO.equals(s) && fila > 0) {
                erros.add("Exemplar nº " + e.getId() + " EMPRESTADO com fila de " + fila);
            }
            if (StatusExemplar.RESERVADO.equals(s) && reservaRepository
                    .findFirstByExemplarAndStatusIn(e, List.of("DISPONIVEL", "AGUARDANDO_TRANSFERENCIA"))
                    .isEmpty()) {
                erros.add("Exemplar nº " + e.getId() + " RESERVADO sem reserva associada");
            }
        }
        return erros;
    }
}
