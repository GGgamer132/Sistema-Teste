package com.circulabook.repository;

import com.circulabook.model.Exemplar;
import com.circulabook.model.HistoricoCirculacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HistoricoCirculacaoRepository extends JpaRepository<HistoricoCirculacao, Long>,
        JpaSpecificationExecutor<HistoricoCirculacao> {

    List<HistoricoCirculacao> findByExemplarOrderByDataEventoDesc(Exemplar exemplar);

    List<HistoricoCirculacao> findTop50ByOrderByDataEventoDesc();

    List<HistoricoCirculacao> findByExemplarOrderByDataEventoAscIdAsc(Exemplar exemplar);
}
