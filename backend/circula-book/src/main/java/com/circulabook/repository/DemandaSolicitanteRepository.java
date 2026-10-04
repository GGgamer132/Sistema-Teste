package com.circulabook.repository;

import com.circulabook.model.DemandaAquisicao;
import com.circulabook.model.DemandaSolicitante;
import com.circulabook.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DemandaSolicitanteRepository extends JpaRepository<DemandaSolicitante, Long> {

    boolean existsByDemandaAndUsuario(DemandaAquisicao demanda, Usuario usuario);

    List<DemandaSolicitante> findByUsuarioOrderByCriadoEmDesc(Usuario usuario);
}
