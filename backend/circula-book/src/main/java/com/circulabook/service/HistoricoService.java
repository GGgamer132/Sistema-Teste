package com.circulabook.service;

import com.circulabook.model.*;
import com.circulabook.repository.HistoricoCirculacaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Serviço auxiliar: centraliza o registro de eventos no histórico (RN06).
 * Todos os outros serviços chamam daqui em vez de montar a entidade na mão.
 */
@Service
public class HistoricoService {

    @Autowired
    private HistoricoCirculacaoRepository historicoRepository;

    public void registrar(Exemplar exemplar, String evento, Usuario usuario,
                          Biblioteca biblioteca, String observacoes) {
        HistoricoCirculacao h = new HistoricoCirculacao();
        h.setExemplar(exemplar);
        h.setEvento(evento);
        h.setUsuario(usuario);
        h.setBiblioteca(biblioteca);
        h.setObservacoes(observacoes);
        historicoRepository.save(h);
    }

    public List<HistoricoCirculacao> obterPorExemplar(Exemplar exemplar) {
        return historicoRepository.findByExemplarOrderByDataEventoDesc(exemplar);
    }

    public List<HistoricoCirculacao> obterRecentes() {
        return historicoRepository.findTop50ByOrderByDataEventoDesc();
    }
}
