package com.circulabook.repository;

import com.circulabook.model.Emprestimo;
import com.circulabook.model.Exemplar;
import com.circulabook.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmprestimoRepository extends JpaRepository<Emprestimo, Long> {

    List<Emprestimo> findByUsuario(Usuario usuario);

    List<Emprestimo> findByStatus(String status);

    // RN01: conta quantos exemplares o usuário tem em mãos agora
    List<Emprestimo> findByUsuarioAndStatusIn(Usuario usuario, List<String> status);

    // Empréstimo aberto de um exemplar (usado na devolução)
    Optional<Emprestimo> findFirstByExemplarAndStatusIn(Exemplar exemplar, List<String> status);

    List<Emprestimo> findByStatusInOrderByDataPrevDevolucaoAsc(List<String> status);
}
