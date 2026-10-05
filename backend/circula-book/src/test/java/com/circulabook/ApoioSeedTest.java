package com.circulabook;

import com.circulabook.service.VerificadorConsistencia;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Base dos testes que rodam sobre o seed REAL (data.sql) no PostgreSQL local, num schema
 * separado ("seed_teste") para não mexer no banco do backend em execução.
 * Antes de cada teste o seed é recarregado do zero, então cada roteiro parte do estado da §8.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/circula_book_db?currentSchema=seed_teste",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.datasource.username=circula_user",
    "spring.datasource.password=circula_senha_123",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.default_schema=seed_teste",
    "spring.datasource.hikari.connection-init-sql=CREATE SCHEMA IF NOT EXISTS seed_teste",
    "spring.jpa.defer-datasource-initialization=true",
    "spring.sql.init.mode=always",
    "spring.sql.init.encoding=UTF-8"
})
@AutoConfigureMockMvc
@Import(VerificadorConsistencia.class)
public abstract class ApoioSeedTest {

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MockMvc mvc;
    @Autowired protected VerificadorConsistencia verificador;
    @Autowired private DataSource dataSource;

    /** Volta ao seed da §8: esvazia todas as tabelas (reiniciando ids) e roda o data.sql de novo. */
    @BeforeEach
    void recarregarSeed() {
        List<String> tabelas = jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'seed_teste'", String.class);
        jdbc.execute("TRUNCATE " + String.join(", ", tabelas) + " RESTART IDENTITY CASCADE");
        ResourceDatabasePopulator seed = new ResourceDatabasePopulator(new ClassPathResource("data.sql"));
        seed.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        seed.execute(dataSource);
    }

    protected long contar(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    // ───────────────────────── API ─────────────────────────

    protected MvcResult chamar(HttpMethod metodo, String url, String token, String corpo) throws Exception {
        MockHttpServletRequestBuilder req = request(metodo, url).contentType(MediaType.APPLICATION_JSON);
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (corpo != null) req.content(corpo);
        MvcResult r = mvc.perform(req).andReturn();
        r.getResponse().setCharacterEncoding("UTF-8");
        return r;
    }

    protected static int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    protected static String corpo(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected static <T> T json(MvcResult r, String caminho) throws Exception {
        return JsonPath.read(corpo(r), caminho);
    }

    protected String login(String email) throws Exception {
        MvcResult r = chamar(HttpMethod.POST, "/api/auth/login", null,
            "{\"email\":\"" + email + "\",\"senha\":\"senha123\"}");
        if (status(r) != 200) throw new IllegalStateException("login de " + email + ": " + status(r));
        return json(r, "$.token");
    }

    // ───────────────────────── Dados do seed, por nome ─────────────────────────

    protected long idLivro(String titulo) {
        return contar("SELECT id FROM livro WHERE titulo = ?", titulo);
    }

    protected long idBiblioteca(String nome) {
        return contar("SELECT id FROM biblioteca WHERE nome = ?", nome);
    }

    protected long idUsuario(String email) {
        return contar("SELECT id FROM usuario WHERE email = ?", email);
    }

    /** Situação dos exemplares de um título numa biblioteca, em ordem alfabética. */
    protected List<String> situacoes(String titulo, String biblioteca) {
        return jdbc.queryForList("""
            SELECT e.status FROM exemplar e JOIN livro l ON l.id = e.livro_id JOIN biblioteca b ON b.id = e.biblioteca_id
            WHERE l.titulo = ? AND b.nome = ? ORDER BY e.status""", String.class, titulo, biblioteca);
    }

    protected long exemplarCom(String titulo, String biblioteca, String status) {
        return contar("""
            SELECT MIN(e.id) FROM exemplar e JOIN livro l ON l.id = e.livro_id JOIN biblioteca b ON b.id = e.biblioteca_id
            WHERE l.titulo = ? AND b.nome = ? AND e.status = ?""", titulo, biblioteca, status);
    }

    protected String statusExemplar(long id) {
        return jdbc.queryForObject("SELECT status FROM exemplar WHERE id = ?", String.class, id);
    }

    /** Empréstimo em aberto do leitor para o título. */
    protected long emprestimoAberto(String email, String titulo) {
        return contar("""
            SELECT MIN(m.id) FROM emprestimo m JOIN usuario u ON u.id = m.usuario_id
              JOIN exemplar e ON e.id = m.exemplar_id JOIN livro l ON l.id = e.livro_id
            WHERE u.email = ? AND l.titulo = ? AND m.status <> 'DEVOLVIDO'""", email, titulo);
    }

    /** Reserva mais recente do leitor para o título: "status|biblioteca de retirada". */
    protected String reserva(String email, String titulo) {
        return jdbc.queryForObject("""
            SELECT r.status || '|' || b.nome FROM reserva r JOIN usuario u ON u.id = r.usuario_id
              JOIN livro l ON l.id = r.livro_id JOIN biblioteca b ON b.id = r.biblioteca_destino_id
            WHERE u.email = ? AND l.titulo = ? ORDER BY r.id DESC LIMIT 1""", String.class, email, titulo);
    }

    /** Transferência mais recente do título: "status|exemplar (ou -)". */
    protected String transferencia(String titulo) {
        return jdbc.queryForObject("""
            SELECT s.status || '|' || COALESCE(s.exemplar_id::text, '-') FROM solicitacao_transferencia s
              JOIN livro l ON l.id = s.livro_id WHERE l.titulo = ? ORDER BY s.id DESC LIMIT 1""",
            String.class, titulo);
    }

    protected long notificacoes(String email, String tipo) {
        return contar("""
            SELECT COUNT(*) FROM notificacao n JOIN usuario u ON u.id = n.usuario_id
            WHERE u.email = ? AND n.tipo = ?""", email, tipo);
    }
}
