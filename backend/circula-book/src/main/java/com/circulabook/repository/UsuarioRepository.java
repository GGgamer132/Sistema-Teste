package com.circulabook.repository;

import com.circulabook.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    List<Usuario> findByTipo(String tipo);

    // Usado na tela de empréstimo: busca o usuário digitando parte do nome ou e-mail
    List<Usuario> findByNomeContainingIgnoreCaseOrEmailContainingIgnoreCase(String nome, String email);
}
