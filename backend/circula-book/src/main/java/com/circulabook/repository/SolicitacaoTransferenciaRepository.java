package com.circulabook.repository;

import com.circulabook.model.Exemplar;
import com.circulabook.model.Reserva;
import com.circulabook.model.SolicitacaoTransferencia;
import com.circulabook.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SolicitacaoTransferenciaRepository extends JpaRepository<SolicitacaoTransferencia, Long> {

    List<SolicitacaoTransferencia> findByStatus(String status);

    List<SolicitacaoTransferencia> findByStatusOrderByDataSolicitacaoAsc(String status);

    List<SolicitacaoTransferencia> findByStatusInOrderByDataSolicitacaoDesc(List<String> status);

    List<SolicitacaoTransferencia> findBySolicitante(Usuario solicitante);

    List<SolicitacaoTransferencia> findByExemplarAndStatusIn(Exemplar exemplar, List<String> status);

    // Pedidos de transferência ligados a uma reserva
    Optional<SolicitacaoTransferencia> findFirstByReservaAndStatusIn(Reserva reserva, List<String> status);

    List<SolicitacaoTransferencia> findByReservaAndStatusIn(Reserva reserva, List<String> status);

    long countByStatus(String status);
}