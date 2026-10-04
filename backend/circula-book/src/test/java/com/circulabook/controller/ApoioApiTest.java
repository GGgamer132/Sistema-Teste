package com.circulabook.controller;

import com.circulabook.model.*;
import com.circulabook.repository.*;
import com.circulabook.service.VerificadorConsistencia;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes pela API real (MockMvc + JWT de verdade): cada teste monta os
 * próprios dados com os métodos de apoio abaixo (o data.sql não roda nos testes).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(VerificadorConsistencia.class)
abstract class ApoioApiTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected BibliotecaRepository bibliotecaRepository;
    @Autowired protected UsuarioRepository usuarioRepository;
    @Autowired protected LivroRepository livroRepository;
    @Autowired protected ExemplarRepository exemplarRepository;
    @Autowired protected EmprestimoRepository emprestimoRepository;
    @Autowired protected ReservaRepository reservaRepository;
    @Autowired protected SolicitacaoTransferenciaRepository transferenciaRepository;
    @Autowired protected VerificadorConsistencia verificador;

    protected ResultActions chamar(HttpMethod metodo, String url, String token, String corpo) throws Exception {
        MockHttpServletRequestBuilder req = request(metodo, url).contentType(MediaType.APPLICATION_JSON);
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (corpo != null) req.content(corpo);
        return mvc.perform(req);
    }

    protected String login(Usuario u) throws Exception {
        String json = chamar(HttpMethod.POST, "/api/auth/login", null,
                             "{\"email\":\"" + u.getEmail() + "\",\"senha\":\"senha123\"}")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    /** Invariante da fila (§4.2) e vínculo de RESERVADO (ES-15). */
    protected void consistente() {
        assertThat(verificador.violacoes()).isEmpty();
    }

    protected Biblioteca biblioteca(String nome) {
        Biblioteca b = new Biblioteca();
        b.setNome(nome);
        return bibliotecaRepository.save(b);
    }

    protected Usuario usuario(String nome, String tipo, Biblioteca biblioteca) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(nome.toLowerCase().replace(' ', '.') + "@teste.com");
        u.setSenhaHash(passwordEncoder.encode("senha123"));
        u.setTipo(tipo);
        u.setBiblioteca(biblioteca);
        return usuarioRepository.save(u);
    }

    protected Livro livro(String titulo) {
        Livro l = new Livro();
        l.setTitulo(titulo);
        l.setAutor("Autor de " + titulo);
        return livroRepository.save(l);
    }

    protected Exemplar exemplar(Livro livro, Biblioteca biblioteca, String status) {
        Exemplar e = new Exemplar();
        e.setLivro(livro);
        e.setBiblioteca(biblioteca);
        e.setStatus(status);
        e.setEstadoConservacao("BOM");
        return exemplarRepository.save(e);
    }

    /** Empréstimo em aberto feito há {@code diasAtras} dias (prazo de 14). */
    protected Emprestimo emprestimo(Exemplar exemplar, Usuario usuario, int diasAtras) {
        Emprestimo emp = new Emprestimo();
        emp.setExemplar(exemplar);
        emp.setUsuario(usuario);
        emp.setBiblioteca(exemplar.getBiblioteca());
        emp.setDataEmprestimo(LocalDateTime.now().minusDays(diasAtras));
        emp.setDataPrevDevolucao(LocalDateTime.now().minusDays(diasAtras).plusDays(14));
        emp.setStatus(diasAtras > 14 ? "ATRASADO" : "ATIVO");
        return emprestimoRepository.save(emp);
    }

    protected static long id(String json) {
        return Long.parseLong(json.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }
}
