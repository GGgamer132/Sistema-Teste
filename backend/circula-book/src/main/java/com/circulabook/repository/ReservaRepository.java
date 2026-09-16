package com.circulabook.repository;

import com.circulabook.model.Livro;
import com.circulabook.model.Reserva;
import com.circulabook.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    List<Reserva> findByUsuario(Usuario usuario);

    List<Reserva> findByStatus(String status);

    // Fila de espera de um título, em ordem de chegada (RN03)
    List<Reserva> findByLivroAndStatusOrderByDataReservaAsc(Livro livro, String status);

    List<Reserva> findByLivroAndUsuarioAndStatusIn(Livro livro, Usuario usuario, List<String> status);

    long countByLivroAndStatus(Livro livro, String status);
}
