package com.circulabook.repository;

import com.circulabook.model.Notificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    Page<Notificacao> findByUsuarioIdOrderByCriadaEmDescIdDesc(Long usuarioId, Pageable pageable);

    long countByUsuarioIdAndLidaFalse(Long usuarioId);

    List<Notificacao> findByUsuarioIdAndLidaFalse(Long usuarioId);

    Optional<Notificacao> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<Notificacao> findByUsuarioIdOrderByIdAsc(Long usuarioId);

    // Rotina de vencimento: no máximo um aviso de cada tipo por empréstimo
    boolean existsByTipoAndReferenciaId(String tipo, Long referenciaId);
}
