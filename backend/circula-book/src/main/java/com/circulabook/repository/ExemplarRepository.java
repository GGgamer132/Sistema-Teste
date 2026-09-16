package com.circulabook.repository;

import com.circulabook.model.Biblioteca;
import com.circulabook.model.Exemplar;
import com.circulabook.model.Livro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExemplarRepository extends JpaRepository<Exemplar, Long> {

    List<Exemplar> findByLivro(Livro livro);

    List<Exemplar> findByBiblioteca(Biblioteca biblioteca);

    List<Exemplar> findByLivroAndBiblioteca(Livro livro, Biblioteca biblioteca);

    List<Exemplar> findByLivroAndStatus(Livro livro, String status);

    List<Exemplar> findByLivroAndBibliotecaAndStatus(Livro livro, Biblioteca biblioteca, String status);

    Optional<Exemplar> findByCodigoBarras(String codigoBarras);

    long countByLivro(Livro livro);
}
