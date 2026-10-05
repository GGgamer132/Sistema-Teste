package com.circulabook.controller;

import com.circulabook.model.*;
import com.circulabook.repository.*;
import com.circulabook.service.VerificadorConsistencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Permissões de cadastro (§2.3, RN10, RN18) pela API real, com JWT de verdade:
 * 200 no perfil certo, 403 nos demais, 401 sem token, e as validações de cada cadastro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(VerificadorConsistencia.class)
class CadastroPermissoesTest {

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BibliotecaRepository bibliotecaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private LivroRepository livroRepository;
    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private EmprestimoRepository emprestimoRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private VerificadorConsistencia verificador;

    private Biblioteca central, vilaIsabel;
    private Usuario u1, u2;
    private String tokenAdmin, tokenBibVI, tokenBibC, tokenU1;

    @BeforeEach
    void montar() throws Exception {
        central = biblioteca("Biblioteca Central");
        vilaIsabel = biblioteca("Biblioteca Vila Isabel");
        usuario("Admin", "ADMIN", null);
        usuario("Bibliotecário Vila Isabel", "BIBLIOTECARIO", vilaIsabel);
        usuario("Bibliotecário Central", "BIBLIOTECARIO", central);
        u1 = usuario("Usuário 1", "COMUM", null);
        u2 = usuario("Usuário 2", "COMUM", null);
        tokenAdmin = login("admin@teste.com");
        tokenBibVI = login("bibliotecário.vila.isabel@teste.com");
        tokenBibC = login("bibliotecário.central@teste.com");
        tokenU1 = login("usuário.1@teste.com");
    }

    // ───────────────────────── Livros ─────────────────────────

    @Test
    @DisplayName("Livro: só o Admin cria/edita; ISBN único; novo livro aparece como sem exemplares")
    void livros() throws Exception {
        String corpo = "{\"titulo\":\"Livro Novo\",\"autor\":\"Autora X\",\"isbn\":\"978-65-00000-01-1\"}";
        chamar(HttpMethod.POST, "/api/livros", null, corpo).andExpect(status().isUnauthorized());
        chamar(HttpMethod.POST, "/api/livros", tokenBibVI, corpo).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/livros", tokenU1, corpo).andExpect(status().isForbidden());
        String json = chamar(HttpMethod.POST, "/api/livros", tokenAdmin, corpo)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titulo").value("Livro Novo"))
            .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));

        chamar(HttpMethod.POST, "/api/livros", tokenAdmin,
               "{\"titulo\":\"Outro\",\"autor\":\"Y\",\"isbn\":\"978-65-00000-01-1\"}")
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Já existe um livro com o ISBN")));
        chamar(HttpMethod.POST, "/api/livros", tokenAdmin, "{\"titulo\":\"Sem autor\"}")
            .andExpect(status().isBadRequest());

        String edicao = "{\"titulo\":\"Livro Novo (2ª ed.)\",\"autor\":\"Autora X\",\"isbn\":\"978-65-00000-01-1\",\"anoPublicacao\":2024}";
        chamar(HttpMethod.PUT, "/api/livros/" + id, tokenBibVI, edicao).andExpect(status().isForbidden());
        chamar(HttpMethod.PUT, "/api/livros/" + id, tokenAdmin, edicao)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titulo").value("Livro Novo (2ª ed.)"))
            .andExpect(jsonPath("$.anoPublicacao").value(2024));

        // Sem termo: o LIKE com "escape ''" gerado pelo Hibernate não funciona no H2 (no PostgreSQL sim;
        // a busca por termo é coberta pelo Playwright contra o banco real)
        chamar(HttpMethod.GET, "/api/livros/busca", tokenU1, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].situacao").value("INDISPONIVEL"))
            .andExpect(jsonPath("$[0].totalExemplares").value(0));
    }

    // ───────────────────────── Categorias ─────────────────────────

    @Test
    @DisplayName("Categoria: só o Admin cria/edita; nome repetido é recusado")
    void categorias() throws Exception {
        String corpo = "{\"nome\":\"Poesia\",\"descricao\":\"Versos\"}";
        chamar(HttpMethod.POST, "/api/categorias", null, corpo).andExpect(status().isUnauthorized());
        chamar(HttpMethod.POST, "/api/categorias", tokenBibVI, corpo).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/categorias", tokenU1, corpo).andExpect(status().isForbidden());
        String json = chamar(HttpMethod.POST, "/api/categorias", tokenAdmin, corpo)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));

        chamar(HttpMethod.POST, "/api/categorias", tokenAdmin, "{\"nome\":\"poesia\"}")
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Já existe a categoria")));
        chamar(HttpMethod.PUT, "/api/categorias/" + id, tokenBibC, "{\"nome\":\"Poemas\"}")
            .andExpect(status().isForbidden());
        chamar(HttpMethod.PUT, "/api/categorias/" + id, tokenAdmin, "{\"nome\":\"Poemas\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Poemas"));
        chamar(HttpMethod.GET, "/api/categorias", tokenU1, null).andExpect(status().isOk());
    }

    // ───────────────────────── Bibliotecas ─────────────────────────

    @Test
    @DisplayName("Biblioteca: só o Admin cria/edita/desativa; desativar só marca inativa")
    void bibliotecas() throws Exception {
        String corpo = "{\"nome\":\"Biblioteca Popular da Tijuca\",\"endereco\":\"Rua X, 1\",\"email\":\"tijuca@teste.com\",\"telefone\":\"(21) 3000-0000\"}";
        chamar(HttpMethod.POST, "/api/bibliotecas", null, corpo).andExpect(status().isUnauthorized());
        chamar(HttpMethod.POST, "/api/bibliotecas", tokenBibVI, corpo).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/bibliotecas", tokenU1, corpo).andExpect(status().isForbidden());
        String json = chamar(HttpMethod.POST, "/api/bibliotecas", tokenAdmin, corpo)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ativa").value(true))
            .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));

        chamar(HttpMethod.POST, "/api/bibliotecas", tokenAdmin, "{\"nome\":\"\"}").andExpect(status().isBadRequest());
        chamar(HttpMethod.PUT, "/api/bibliotecas/" + id, tokenBibVI, corpo).andExpect(status().isForbidden());
        chamar(HttpMethod.PUT, "/api/bibliotecas/" + id, tokenAdmin,
               "{\"nome\":\"Biblioteca da Tijuca\",\"endereco\":\"Rua Y, 2\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Biblioteca da Tijuca"));

        chamar(HttpMethod.PATCH, "/api/bibliotecas/" + id + "/desativar", tokenBibC, null)
            .andExpect(status().isForbidden());
        chamar(HttpMethod.PATCH, "/api/bibliotecas/" + id + "/desativar", tokenAdmin, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.ativa").value(false));

        chamar(HttpMethod.GET, "/api/bibliotecas", tokenU1, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", not(hasItem((int) id))));
        chamar(HttpMethod.GET, "/api/bibliotecas/todas", tokenBibVI, null).andExpect(status().isForbidden());
        chamar(HttpMethod.GET, "/api/bibliotecas/todas", tokenAdmin, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem((int) id)));
        chamar(HttpMethod.PATCH, "/api/bibliotecas/" + id + "/ativar", tokenAdmin, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.ativa").value(true));
    }

    // ───────────────────────── Bibliotecários ─────────────────────────

    @Test
    @DisplayName("Bibliotecário: só o Admin lista/cria/ativa; biblioteca obrigatória; não cria COMUM; sem hash")
    void bibliotecarios() throws Exception {
        chamar(HttpMethod.GET, "/api/usuarios/bibliotecarios", null, null).andExpect(status().isUnauthorized());
        chamar(HttpMethod.GET, "/api/usuarios/bibliotecarios", tokenBibVI, null).andExpect(status().isForbidden());
        chamar(HttpMethod.GET, "/api/usuarios/bibliotecarios", tokenU1, null).andExpect(status().isForbidden());
        chamar(HttpMethod.GET, "/api/usuarios/bibliotecarios", tokenAdmin, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].tipo", everyItem(is("BIBLIOTECARIO"))))
            .andExpect(content().string(not(containsString("senhaHash"))));

        String corpo = "{\"nome\":\"Bibliotecário Tijuca\",\"email\":\"bibliotecariotijuca@teste.com\",\"senha\":\"abc123\",\"bibliotecaId\":"
            + central.getId() + "}";
        chamar(HttpMethod.POST, "/api/usuarios/bibliotecarios", tokenBibVI, corpo).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/usuarios/bibliotecarios", tokenU1, corpo).andExpect(status().isForbidden());
        String json = chamar(HttpMethod.POST, "/api/usuarios/bibliotecarios", tokenAdmin, corpo)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tipo").value("BIBLIOTECARIO"))
            .andExpect(jsonPath("$.biblioteca.id").value(central.getId()))
            .andExpect(content().string(not(containsString("senhaHash"))))
            .andExpect(content().string(not(containsString("$2a$"))))
            .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(json.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
        assertThat(login("bibliotecariotijuca@teste.com")).isNotBlank();

        chamar(HttpMethod.POST, "/api/usuarios/bibliotecarios", tokenAdmin, corpo)
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("e-mail")));
        chamar(HttpMethod.POST, "/api/usuarios/bibliotecarios", tokenAdmin,
               "{\"nome\":\"Sem Biblioteca\",\"email\":\"sem@teste.com\",\"senha\":\"abc123\"}")
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("biblioteca")));
        chamar(HttpMethod.POST, "/api/usuarios/bibliotecarios", tokenAdmin,
               "{\"nome\":\"Curta\",\"email\":\"curta@teste.com\",\"senha\":\"123\",\"bibliotecaId\":" + central.getId() + "}")
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("6")));
        chamar(HttpMethod.POST, "/api/usuarios/bibliotecarios", tokenAdmin,
               "{\"nome\":\"Comum\",\"email\":\"comum@teste.com\",\"senha\":\"abc123\",\"bibliotecaId\":"
                   + central.getId() + ",\"tipo\":\"COMUM\"}")
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("só cadastra bibliotecários")));
        assertThat(usuarioRepository.findByEmailIgnoreCase("comum@teste.com")).isEmpty();

        chamar(HttpMethod.PATCH, "/api/usuarios/bibliotecarios/" + id + "/desativar", tokenAdmin, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        chamar(HttpMethod.POST, "/api/auth/login", null, "{\"email\":\"bibliotecariotijuca@teste.com\",\"senha\":\"abc123\"}")
            .andExpect(status().isForbidden());
        chamar(HttpMethod.PATCH, "/api/usuarios/bibliotecarios/" + u1.getId() + "/desativar", tokenAdmin, null)
            .andExpect(status().isBadRequest());
    }

    // ───────────────────────── Exemplares ─────────────────────────

    @Test
    @DisplayName("Exemplar: só o bibliotecário, na própria biblioteca, de livro existente, 1 a 50")
    void exemplares() throws Exception {
        Livro livro = livro("Memórias Póstumas de Brás Cubas");
        String corpo = "{\"livroId\":" + livro.getId() + ",\"conservacao\":\"BOM\",\"quantidade\":3}";

        chamar(HttpMethod.POST, "/api/exemplares", null, corpo).andExpect(status().isUnauthorized());
        chamar(HttpMethod.POST, "/api/exemplares", tokenAdmin, corpo).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/exemplares", tokenU1, corpo).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/exemplares", tokenBibVI,
               "{\"livroId\":" + livro.getId() + ",\"bibliotecaId\":" + central.getId() + "}")
            .andExpect(status().isForbidden());

        chamar(HttpMethod.POST, "/api/exemplares", tokenBibVI, corpo)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[*].status", everyItem(is("DISPONIVEL"))))
            .andExpect(jsonPath("$[*].estadoConservacao", everyItem(is("BOM"))))
            .andExpect(jsonPath("$[*].biblioteca.id", everyItem(is(vilaIsabel.getId().intValue()))));

        chamar(HttpMethod.POST, "/api/exemplares", tokenBibVI, "{\"livroId\":" + livro.getId() + ",\"quantidade\":0}")
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("1 a 50")));
        chamar(HttpMethod.POST, "/api/exemplares", tokenBibVI, "{\"livroId\":" + livro.getId() + ",\"quantidade\":51}")
            .andExpect(status().isBadRequest());
        chamar(HttpMethod.POST, "/api/exemplares", tokenBibVI, "{\"quantidade\":1}")
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("livro já cadastrado")));
        chamar(HttpMethod.POST, "/api/exemplares", tokenBibVI,
               "{\"titulo\":\"Livro inventado\",\"autor\":\"Ninguém\",\"quantidade\":1}")
            .andExpect(status().isBadRequest());
        chamar(HttpMethod.POST, "/api/exemplares", tokenBibVI,
               "{\"livroId\":" + livro.getId() + ",\"conservacao\":\"DANIFICADO\"}")
            .andExpect(status().isBadRequest());
        assertThat(livroRepository.count()).isEqualTo(1);
        // Sem quantidade: cria 1, conservação padrão NOVO
        chamar(HttpMethod.POST, "/api/exemplares", tokenBibVI, "{\"livroId\":" + livro.getId() + "}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].estadoConservacao").value("NOVO"));
        assertThat(verificador.violacoes()).isEmpty();
    }

    @Test
    @DisplayName("Exemplar com fila (T15): os primeiros atendem a fila em ordem; o resto fica DISPONIVEL")
    void exemplaresComFila() throws Exception {
        Livro livro = livro("Harry Potter");
        Exemplar emprestado = exemplar(livro, vilaIsabel, StatusExemplar.EMPRESTADO_RESERVADO);
        emprestimoAtivo(emprestado, u2);
        Reserva primeira = reserva(livro, u1, vilaIsabel, LocalDateTime.now().minusDays(3));
        Reserva segunda = reserva(livro, usuario("Usuário 3", "COMUM", null), vilaIsabel,
                                  LocalDateTime.now().minusDays(1));
        assertThat(verificador.violacoes()).isEmpty();

        chamar(HttpMethod.POST, "/api/exemplares", tokenBibVI,
               "{\"livroId\":" + livro.getId() + ",\"conservacao\":\"NOVO\",\"quantidade\":3}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("RESERVADO"))
            .andExpect(jsonPath("$[1].status").value("RESERVADO"))
            .andExpect(jsonPath("$[2].status").value("DISPONIVEL"));

        assertThat(primeira.getStatus()).isEqualTo("DISPONIVEL");
        assertThat(segunda.getStatus()).isEqualTo("DISPONIVEL");
        assertThat(exemplarRepository.findById(emprestado.getId()).orElseThrow().getStatus())
            .isEqualTo(StatusExemplar.EMPRESTADO); // a fila esvaziou
        assertThat(verificador.violacoes()).isEmpty();
    }

    // ───────────────────────── Empréstimo, devolução, gerenciar exemplar ─────────────────────────

    @Test
    @DisplayName("Empréstimo/devolução só do bibliotecário da biblioteca; Admin e COMUM recebem 403")
    void emprestimoEDevolucao() throws Exception {
        Livro livro = livro("Dom Casmurro");
        Exemplar naVi = exemplar(livro, vilaIsabel, StatusExemplar.DISPONIVEL);
        Exemplar naCentral = exemplar(livro, central, StatusExemplar.DISPONIVEL);
        String corpoVi = "{\"exemplarId\":" + naVi.getId() + ",\"usuarioId\":" + u1.getId() + "}";

        chamar(HttpMethod.POST, "/api/emprestimos/registrar", tokenAdmin, corpoVi).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/emprestimos/registrar", tokenU1, corpoVi).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/emprestimos/registrar", tokenBibVI,
               "{\"exemplarId\":" + naCentral.getId() + ",\"usuarioId\":" + u1.getId() + "}")
            .andExpect(status().isForbidden());
        String json = chamar(HttpMethod.POST, "/api/emprestimos/registrar", tokenBibVI, corpoVi)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long empId = Long.parseLong(json.replaceAll("^\\{\"id\":(\\d+).*", "$1"));

        String devolucao = "{\"emprestimoId\":" + empId + ",\"condicaoExemplar\":\"BOM\"}";
        chamar(HttpMethod.POST, "/api/emprestimos/devolver", tokenAdmin, devolucao).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/emprestimos/devolver", tokenU1, devolucao).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/emprestimos/devolver", tokenBibC, devolucao).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/emprestimos/devolver", tokenBibVI, devolucao).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Marcar indisponível/reativar: só o bibliotecário da biblioteca do exemplar")
    void gerenciarExemplar() throws Exception {
        Exemplar e = exemplar(livro("O Alienista"), vilaIsabel, StatusExemplar.DISPONIVEL);
        String url = "/api/exemplares/" + e.getId();
        chamar(HttpMethod.PATCH, url + "/indisponivel", null, null).andExpect(status().isUnauthorized());
        chamar(HttpMethod.PATCH, url + "/indisponivel", tokenBibC, null).andExpect(status().isForbidden());
        chamar(HttpMethod.PATCH, url + "/indisponivel", tokenAdmin, null).andExpect(status().isForbidden());
        chamar(HttpMethod.PATCH, url + "/indisponivel", tokenBibVI, "{\"motivo\":\"Molhado\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INDISPONIVEL"));
        chamar(HttpMethod.PATCH, url + "/reativar", tokenBibC, null).andExpect(status().isForbidden());
        chamar(HttpMethod.PATCH, url + "/reativar", tokenBibVI, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISPONIVEL"));
    }

    // ───────────────────────── Apoio ─────────────────────────

    private ResultActions chamar(HttpMethod metodo, String url, String token, String corpo) throws Exception {
        MockHttpServletRequestBuilder req = request(metodo, url).contentType(MediaType.APPLICATION_JSON);
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (corpo != null) req.content(corpo);
        return mvc.perform(req);
    }

    private String login(String email) throws Exception {
        String senha = email.startsWith("bibliotecariotijuca") ? "abc123" : "senha123";
        String json = chamar(HttpMethod.POST, "/api/auth/login", null,
                             "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private Biblioteca biblioteca(String nome) {
        Biblioteca b = new Biblioteca();
        b.setNome(nome);
        return bibliotecaRepository.save(b);
    }

    private Usuario usuario(String nome, String tipo, Biblioteca biblioteca) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(nome.toLowerCase().replace(' ', '.') + "@teste.com");
        u.setSenhaHash(passwordEncoder.encode("senha123"));
        u.setTipo(tipo);
        u.setBiblioteca(biblioteca);
        return usuarioRepository.save(u);
    }

    private Livro livro(String titulo) {
        Livro l = new Livro();
        l.setTitulo(titulo);
        l.setAutor("Autor de " + titulo);
        return livroRepository.save(l);
    }

    private Exemplar exemplar(Livro livro, Biblioteca biblioteca, String status) {
        Exemplar e = new Exemplar();
        e.setLivro(livro);
        e.setBiblioteca(biblioteca);
        e.setStatus(status);
        e.setEstadoConservacao("BOM");
        return exemplarRepository.save(e);
    }

    private void emprestimoAtivo(Exemplar exemplar, Usuario usuario) {
        Emprestimo emp = new Emprestimo();
        emp.setExemplar(exemplar);
        emp.setUsuario(usuario);
        emp.setBiblioteca(exemplar.getBiblioteca());
        emp.setDataEmprestimo(LocalDateTime.now().minusDays(2));
        emp.setDataPrevDevolucao(LocalDateTime.now().plusDays(12));
        emp.setStatus("ATIVO");
        emprestimoRepository.save(emp);
    }

    private Reserva reserva(Livro livro, Usuario usuario, Biblioteca biblioteca, LocalDateTime data) {
        Reserva r = new Reserva();
        r.setLivro(livro);
        r.setUsuario(usuario);
        r.setBibliotecaFila(biblioteca);
        r.setBibliotecaDestino(biblioteca);
        r.setDataReserva(data);
        r.setDataExpiracao(data.plusDays(3));
        r.setStatus("PENDENTE");
        return reservaRepository.save(r);
    }
}
