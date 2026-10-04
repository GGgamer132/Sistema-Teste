package com.circulabook.repository;

import com.circulabook.model.Usuario;
import com.circulabook.model.Biblioteca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByEmailIgnoreCase(String email);

    List<Usuario> findByTipo(String tipo);

    // Usado na tela de empréstimo: busca o usuário digitando parte do nome ou e-mail
    List<Usuario> findByNomeContainingIgnoreCaseOrEmailContainingIgnoreCase(String nome, String email);

    // RN22: destino de transferência precisa de ao menos um bibliotecário ativo
    boolean existsByTipoAndBibliotecaAndAtivoTrue(String tipo, Biblioteca biblioteca);

    // Destinatários de notificações: bibliotecários ativos de uma biblioteca / Admins ativos
    List<Usuario> findByTipoAndBibliotecaAndAtivoTrue(String tipo, Biblioteca biblioteca);

    List<Usuario> findByTipoAndAtivoTrue(String tipo);
}
