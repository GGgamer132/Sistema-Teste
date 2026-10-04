package com.circulabook.repository;

import com.circulabook.model.DemandaAquisicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DemandaAquisicaoRepository extends JpaRepository<DemandaAquisicao, Long> {

    long countByStatus(String status);
}
