package com.circulabook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confere que o seed de demonstração (data.sql real, §8) é coerente com as regras e com o
 * roteiro de gravação (§9.0). Ver {@link ApoioSeedTest}.
 */
class SeedDemonstracaoTest extends ApoioSeedTest {

    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Usuários exatamente como na §8.1, senha senha123, todos ativos")
    void usuarios() {
        List<Map<String, Object>> linhas = jdbc.queryForList("""
            SELECT u.nome, u.email, u.tipo, u.ativo, u.senha_hash, b.nome AS biblioteca
            FROM usuario u LEFT JOIN biblioteca b ON b.id = u.biblioteca_id ORDER BY u.id""");
        Map<String, String> esperado = new TreeMap<>(Map.of(
            "admin@circulabook.com", "Admin|ADMIN|null",
            "bibliotecariocentral@circulabook.com", "Bibliotecário Central|BIBLIOTECARIO|Biblioteca Central",
            "bibliotecariovilaisabel@circulabook.com", "Bibliotecário Vila Isabel|BIBLIOTECARIO|Biblioteca Comunitária de Vila Isabel",
            "bibliotecariotijuca@circulabook.com", "Bibliotecário Tijuca|BIBLIOTECARIO|Biblioteca Popular da Tijuca",
            "usuario1@circulabook.com", "Usuário 1|COMUM|null",
            "usuario2@circulabook.com", "Usuário 2|COMUM|null",
            "usuario3@circulabook.com", "Usuário 3|COMUM|null"));
        Map<String, String> real = new TreeMap<>();
        for (Map<String, Object> l : linhas) {
            real.put((String) l.get("email"), l.get("nome") + "|" + l.get("tipo") + "|" + l.get("biblioteca"));
            assertThat((Boolean) l.get("ativo")).as("ativo: " + l.get("email")).isTrue();
            assertThat(passwordEncoder.matches("senha123", (String) l.get("senha_hash")))
                .as("senha de " + l.get("email")).isTrue();
        }
        assertThat(real).isEqualTo(esperado);
        assertThat(contar("SELECT COUNT(*) FROM usuario WHERE bloqueado_ate IS NOT NULL")).isZero();
    }

    @Test
    @DisplayName("RN22 e §9.6: cada biblioteca ativa com exatamente um bibliotecário ativo")
    void umBibliotecarioPorBiblioteca() {
        assertThat(contar("SELECT COUNT(*) FROM biblioteca WHERE ativa")).isEqualTo(3);
        List<Long> porBiblioteca = jdbc.queryForList("""
            SELECT (SELECT COUNT(*) FROM usuario u WHERE u.biblioteca_id = b.id
                    AND u.tipo = 'BIBLIOTECARIO' AND u.ativo)
            FROM biblioteca b WHERE b.ativa""", Long.class);
        assertThat(porBiblioteca).containsExactly(1L, 1L, 1L);
    }

    @Test
    @DisplayName("Exemplares com os estados exatos da §8.3")
    void exemplares() {
        // título -> "C:status,status | VI:status" (ordenado); Tijuca não aparece porque está vazia
        Map<String, String> real = new TreeMap<>();
        jdbc.query("""
            SELECT l.titulo, b.id AS bib, string_agg(e.status, ',' ORDER BY e.status) AS st
            FROM exemplar e JOIN livro l ON l.id = e.livro_id JOIN biblioteca b ON b.id = e.biblioteca_id
            GROUP BY l.titulo, b.id ORDER BY l.titulo, b.id""", rs -> {
            String bib = rs.getLong("bib") == 1 ? "C" : rs.getLong("bib") == 2 ? "VI" : "T";
            real.merge(rs.getString("titulo"), bib + ":" + rs.getString("st"), (a, b) -> a + " | " + b);
        });
        String d = "DISPONIVEL";
        Map<String, String> esperado = new TreeMap<>();
        esperado.put("Dom Casmurro", "C:" + d + "," + d + "," + d + " | VI:" + d + "," + d);
        esperado.put("O Cortiço", "C:" + d + "," + d + " | VI:" + d);
        esperado.put("Capitães da Areia", "VI:" + d + "," + d);
        esperado.put("Grande Sertão: Veredas", "C:EMPRESTADO");
        esperado.put("1984", "C:" + d + "," + d + " | VI:" + d);
        esperado.put("Fahrenheit 451", "C:" + d + "," + d + ",EM_TRANSFERENCIA");
        esperado.put("Duna", "C:EMPRESTADO,EMPRESTADO");
        esperado.put("O Hobbit", "C:EMPRESTADO_RESERVADO,INDISPONIVEL");
        esperado.put("Harry Potter e a Pedra Filosofal", "C:" + d + "," + d + " | VI:" + d + ",RESERVADO");
        esperado.put("Assassinato no Expresso do Oriente", "C:" + d + " | VI:" + d + "," + d);
        esperado.put("O Cão dos Baskerville", "VI:" + d + "," + d);
        esperado.put("O Diário de Anne Frank", "C:" + d + "," + d);
        esperado.put("Steve Jobs", "C:" + d + " | VI:INDISPONIVEL");
        esperado.put("Quarto de Despejo", "VI:" + d + "," + d);
        esperado.put("O Pequeno Príncipe", "C:" + d + "," + d + " | VI:" + d + "," + d + "," + d);
        esperado.put("Sapiens", "C:" + d + "," + d + " | VI:" + d);
        esperado.put("Casa-Grande & Senzala", "C:" + d);
        esperado.put("Antologia Poética", "VI:" + d + "," + d);
        esperado.put("Código Limpo (Clean Code)", "C:" + d + "," + d + " | VI:" + d);
        assertThat(real).isEqualTo(esperado);

        assertThat(contar("SELECT COUNT(*) FROM livro")).isEqualTo(20);
        assertThat(contar("SELECT COUNT(*) FROM categoria")).isEqualTo(9);
        assertThat(contar("""
            SELECT COUNT(*) FROM exemplar e JOIN livro l ON l.id = e.livro_id
            WHERE l.titulo = 'Memórias Póstumas de Brás Cubas'""")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM exemplar WHERE biblioteca_id = 3")).as("Tijuca vazia").isZero();
        assertThat(contar("SELECT COUNT(*) FROM livro WHERE isbn IS NOT NULL AND isbn NOT LIKE '978-65-%'")).isZero();
    }

    @Test
    @DisplayName("Invariante 4.2, nenhum RESERVADO sem reserva e EM_TRANSFERENCIA sempre em trânsito")
    void invariantes() {
        assertThat(verificador.violacoes()).isEmpty();
        assertThat(contar("""
            SELECT COUNT(*) FROM exemplar e WHERE e.status = 'EM_TRANSFERENCIA' AND NOT EXISTS (
              SELECT 1 FROM solicitacao_transferencia s WHERE s.exemplar_id = e.id AND s.status = 'EM_TRANSITO')"""))
            .isZero();
        // cada exemplar emprestado tem exatamente um empréstimo em aberto, e vice-versa
        assertThat(contar("""
            SELECT COUNT(*) FROM exemplar e WHERE e.status IN ('EMPRESTADO','EMPRESTADO_RESERVADO')
              AND (SELECT COUNT(*) FROM emprestimo m WHERE m.exemplar_id = e.id AND m.status <> 'DEVOLVIDO') <> 1"""))
            .isZero();
        assertThat(contar("""
            SELECT COUNT(*) FROM emprestimo m JOIN exemplar e ON e.id = m.exemplar_id
            WHERE m.status <> 'DEVOLVIDO' AND e.status NOT IN ('EMPRESTADO','EMPRESTADO_RESERVADO')""")).isZero();
    }

    @Test
    @DisplayName("RN15: nenhuma origem com abertas > total - 1 (Hobbit 1 de 2; Grande Sertão 0 de 1)")
    void rn15() {
        assertThat(contar("""
            SELECT COUNT(*) FROM (
              SELECT s.biblioteca_origem_id, s.livro_id, COUNT(*) AS abertas,
                     (SELECT COUNT(*) FROM exemplar e WHERE e.livro_id = s.livro_id
                        AND e.biblioteca_id = s.biblioteca_origem_id) AS total
              FROM solicitacao_transferencia s WHERE s.status IN ('PENDENTE','APROVADA','EM_TRANSITO')
              GROUP BY s.biblioteca_origem_id, s.livro_id) t
            WHERE t.abertas > t.total - 1""")).isZero();
        assertThat(contar("""
            SELECT COUNT(*) FROM solicitacao_transferencia s JOIN livro l ON l.id = s.livro_id
            WHERE l.titulo = 'Grande Sertão: Veredas'""")).isZero();
    }

    @Test
    @DisplayName("Empréstimos, reservas e transferências da §8.4")
    void movimentacao() {
        // U1 (id 5) zerado; U2 (6) 3/3 com Duna atrasado há 6 dias; U3 (7) 1/3
        assertThat(contar("SELECT COUNT(*) FROM emprestimo WHERE usuario_id = 5")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM emprestimo WHERE usuario_id = 6 AND status <> 'DEVOLVIDO'")).isEqualTo(3);
        assertThat(contar("SELECT COUNT(*) FROM emprestimo WHERE usuario_id = 7 AND status <> 'DEVOLVIDO'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            SELECT l.titulo || '|' || EXTRACT(DAY FROM (CURRENT_TIMESTAMP - m.data_prev_devolucao))
            FROM emprestimo m JOIN exemplar e ON e.id = m.exemplar_id JOIN livro l ON l.id = e.livro_id
            WHERE m.data_prev_devolucao < CURRENT_TIMESTAMP AND m.status <> 'DEVOLVIDO'""", String.class))
            .isEqualTo("Duna|6");
        assertThat(contar("""
            SELECT COUNT(*) FROM emprestimo WHERE status <> 'DEVOLVIDO'
              AND data_prev_devolucao::date - data_emprestimo::date <> 14""")).as("prazo fixo de 14 dias").isZero();
        assertThat(jdbc.queryForList("""
            SELECT l.titulo FROM emprestimo m JOIN exemplar e ON e.id = m.exemplar_id JOIN livro l ON l.id = e.livro_id
            WHERE m.usuario_id = 6 AND m.status = 'DEVOLVIDO' ORDER BY l.titulo""", String.class))
            .containsExactly("O Pequeno Príncipe", "Sapiens");
        assertThat(jdbc.queryForList("""
            SELECT l.titulo FROM emprestimo m JOIN exemplar e ON e.id = m.exemplar_id JOIN livro l ON l.id = e.livro_id
            WHERE m.usuario_id = 7 AND m.status = 'DEVOLVIDO' ORDER BY l.titulo""", String.class))
            .containsExactly("Código Limpo (Clean Code)", "O Diário de Anne Frank");

        // Reservas: R1 (Hobbit, C -> VI, PENDENTE) e R2 (Harry, VI, DISPONIVEL, expira em 2 dias)
        assertThat(jdbc.queryForList("""
            SELECT l.titulo || '|' || r.biblioteca_fila_id || '>' || r.biblioteca_destino_id || '|' || r.status
            FROM reserva r JOIN livro l ON l.id = r.livro_id
            WHERE r.status IN ('PENDENTE','DISPONIVEL','AGUARDANDO_TRANSFERENCIA') ORDER BY r.id""", String.class))
            .containsExactly("O Hobbit|1>2|PENDENTE", "Harry Potter e a Pedra Filosofal|2>2|DISPONIVEL");
        assertThat(contar("""
            SELECT COUNT(*) FROM reserva WHERE status = 'DISPONIVEL'
              AND data_expiracao BETWEEN CURRENT_TIMESTAMP + INTERVAL '47 hours' AND CURRENT_TIMESTAMP + INTERVAL '49 hours'"""))
            .isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM reserva WHERE usuario_id = 5")).isZero();

        // Transferências T1, T2, T3
        assertThat(jdbc.queryForList("""
            SELECT l.titulo || '|' || s.status || '|' || COALESCE(s.exemplar_id::text, 'sem exemplar')
                   || '|' || (s.reserva_id IS NOT NULL)
            FROM solicitacao_transferencia s JOIN livro l ON l.id = s.livro_id ORDER BY s.id""", String.class))
            .containsExactly("O Hobbit|PENDENTE|sem exemplar|true",
                             "Fahrenheit 451|EM_TRANSITO|17|false",
                             "Sapiens|CONCLUIDA|44|false");
    }

    @Test
    @DisplayName("Demandas, notificações e histórico da §8.4 (U1 e BibT sem nada)")
    void demandasNotificacoesHistorico() {
        assertThat(jdbc.queryForList("""
            SELECT d.titulo || '|' || d.total_solicitacoes || '|' || d.status || '|'
                   || (SELECT string_agg(s.usuario_id::text, ',' ORDER BY s.usuario_id)
                       FROM demanda_solicitante s WHERE s.demanda_id = d.id)
            FROM demanda_aquisicao d ORDER BY d.id""", String.class))
            .containsExactly("Ensaio sobre a Cegueira|2|ABERTA|6,7",
                             "Torto Arado|1|EM_ANALISE|7",
                             "A Hora da Estrela|1|APROVADA|6");

        Map<String, Long> porUsuario = new TreeMap<>();
        jdbc.query("""
            SELECT u.email, (SELECT COUNT(*) FROM notificacao n WHERE n.usuario_id = u.id AND NOT n.lida) AS n
            FROM usuario u""", rs -> { porUsuario.put(rs.getString("email"), rs.getLong("n")); });
        assertThat(porUsuario).isEqualTo(new TreeMap<>(Map.of(
            "admin@circulabook.com", 1L,
            "bibliotecariocentral@circulabook.com", 0L,
            "bibliotecariovilaisabel@circulabook.com", 2L,
            "bibliotecariotijuca@circulabook.com", 0L,
            "usuario1@circulabook.com", 0L,
            "usuario2@circulabook.com", 1L,
            "usuario3@circulabook.com", 1L)));

        // U1 sem histórico; leitores nunca aparecem como responsável de empréstimo/devolução
        assertThat(contar("SELECT COUNT(*) FROM historico_circulacao WHERE usuario_id = 5")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM demanda_solicitante WHERE usuario_id = 5")).isZero();
        assertThat(contar("""
            SELECT COUNT(*) FROM historico_circulacao h JOIN usuario u ON u.id = h.usuario_id
            WHERE h.evento IN ('EMPRESTIMO','DEVOLUCAO','CADASTRO') AND u.tipo = 'COMUM'""")).isZero();
        assertThat(contar("""
            SELECT COUNT(*) FROM historico_circulacao h LEFT JOIN usuario u ON u.id = h.usuario_id
            WHERE h.responsavel IS DISTINCT FROM COALESCE(u.nome, 'Sistema')""")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM exemplar e WHERE NOT EXISTS ("
            + "SELECT 1 FROM historico_circulacao h WHERE h.exemplar_id = e.id AND h.evento = 'CADASTRO')")).isZero();
    }

    @Test
    @DisplayName("Sequences ressincronizadas: o próximo id não colide com o seed")
    void sequences() {
        for (String t : List.of("categoria", "biblioteca", "usuario", "livro", "exemplar", "emprestimo", "reserva",
                                "solicitacao_transferencia", "historico_circulacao", "demanda_aquisicao",
                                "demanda_solicitante", "notificacao")) {
            Long ultimo = jdbc.queryForObject("SELECT last_value FROM " + jdbc.queryForObject(
                "SELECT pg_get_serial_sequence(?, 'id')", String.class, t), Long.class);
            assertThat(ultimo).as(t).isEqualTo(contar("SELECT MAX(id) FROM " + t));
        }
    }
}
