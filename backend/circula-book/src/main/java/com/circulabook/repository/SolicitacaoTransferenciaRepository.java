package com.circulabook.repository;

import com.circulabook.model.Exemplar;
import com.circulabook.model.SolicitacaoTransferencia;
import com.circulabook.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SolicitacaoTransferenciaRepository extends JpaRepository<SolicitacaoTransferencia, Long> {

    List<SolicitacaoTransferencia> findByStatus(String status);

    List<SolicitacaoTransferencia> findByStatusOrderByDataSolicitacaoAsc(String status);

    List<SolicitacaoTransferencia> findByStatusInOrderByDataSolicitacaoDesc(List<String> status);

    List<SolicitacaoTransferencia> findBySolicitante(Usuario solicitante);

    List<SolicitacaoTransferencia> findByExemplarAndStatusIn(Exemplar exemplar, List<String> status);

    long countByStatus(String status);
}
