package com.circulabook;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.*;

/**
 * Roteiros da §9 (AU, ES, U, B, A) executados pela API sobre o seed real da §8,
 * recarregado antes de cada teste. As telas são cobertas pelos specs Playwright;
 * os marcados (T) na spec já têm testes com dados próprios nas outras classes.
 */
class RoteirosSeedTest extends ApoioSeedTest {

    static final String ADMIN = "admin@circulabook.com";
    static final String BIB_C = "bibliotecariocentral@circulabook.com";
    static final String BIB_VI = "bibliotecariovilaisabel@circulabook.com";
    static final String BIB_T = "bibliotecariotijuca@circulabook.com";
    static final String U1 = "usuario1@circulabook.com";
    static final String U2 = "usuario2@circulabook.com";
    static final String U3 = "usuario3@circulabook.com";
    static final String CENTRAL = "Biblioteca Central";
    static final String VI = "Biblioteca Comunitária de Vila Isabel";
    static final String TIJUCA = "Biblioteca Popular da Tijuca";

    /** ES-15: invariante 4.2 e nenhum RESERVADO sem reserva, depois de qualquer roteiro. */
    @AfterEach
    void invariante() {
        assertThat(verificador.violacoes()).isEmpty();
    }

    private MvcResult reservar(String email, String titulo, String fila, String retirada) throws Exception {
        return chamar(POST, "/api/reservas", login(email), "{\"livroId\":" + idLivro(titulo)
            + ",\"bibliotecaFilaId\":" + idBiblioteca(fila) + ",\"bibliotecaDestinoId\":" + idBiblioteca(retirada) + "}");
    }

    private MvcResult emprestar(String bibliotecario, long exemplarId, String leitor) throws Exception {
        return chamar(POST, "/api/emprestimos/registrar", login(bibliotecario),
            "{\"exemplarId\":" + exemplarId + ",\"usuarioId\":" + idUsuario(leitor) + "}");
    }

    private MvcResult devolver(String bibliotecario, long emprestimoId, String condicao) throws Exception {
        return chamar(POST, "/api/emprestimos/devolver", login(bibliotecario),
            "{\"emprestimoId\":" + emprestimoId + ",\"condicaoExemplar\":\"" + condicao + "\"}");
    }

    private long idTransferencia(String titulo) {
        return contar("""
            SELECT MAX(s.id) FROM solicitacao_transferencia s JOIN livro l ON l.id = s.livro_id WHERE l.titulo = ?""", titulo);
    }

    private long idReserva(String email, String titulo) {
        return contar("""
            SELECT MAX(r.id) FROM reserva r JOIN usuario u ON u.id = r.usuario_id JOIN livro l ON l.id = r.livro_id
            WHERE u.email = ? AND l.titulo = ?""", email, titulo);
    }

    private MvcResult avulsa(List<Long> exemplares, String destino) throws Exception {
        String ids = String.join(",", exemplares.stream().map(String::valueOf).toList());
        return chamar(POST, "/api/transferencias/avulsa", login(ADMIN),
            "{\"exemplarIds\":[" + ids + "],\"bibliotecaDestinoId\":" + idBiblioteca(destino) + "}");
    }

    private List<Long> exemplares(String titulo, String biblioteca, String status) {
        return jdbc.queryForList("""
            SELECT e.id FROM exemplar e JOIN livro l ON l.id = e.livro_id JOIN biblioteca b ON b.id = e.biblioteca_id
            WHERE l.titulo = ? AND b.nome = ? AND e.status = ? ORDER BY e.id""", Long.class, titulo, biblioteca, status);
    }

    // ═════════════════════════ 9.1 Autenticação ═════════════════════════

    @Nested
    @DisplayName("AU")
    class Autenticacao {

        @Test
        @DisplayName("AU-01: login de cada usuário da 8.1 devolve token, perfil e biblioteca")
        void au01() throws Exception {
            Map<String, String> esperado = Map.of(
                ADMIN, "ADMIN|null", BIB_C, "BIBLIOTECARIO|" + CENTRAL, BIB_VI, "BIBLIOTECARIO|" + VI,
                BIB_T, "BIBLIOTECARIO|" + TIJUCA, U1, "COMUM|null", U2, "COMUM|null", U3, "COMUM|null");
            for (var e : esperado.entrySet()) {
                MvcResult r = chamar(POST, "/api/auth/login", null, "{\"email\":\"" + e.getKey() + "\",\"senha\":\"senha123\"}");
                assertThat(status(r)).as(e.getKey()).isEqualTo(200);
                assertThat(json(r, "$.token").toString()).isNotBlank();
                assertThat(json(r, "$.usuario.perfil") + "|" + json(r, "$.usuario.bibliotecaNome")).isEqualTo(e.getValue());
            }
        }

        @Test
        @DisplayName("AU-02: senha errada e e-mail inexistente dão 401 com a mesma mensagem")
        void au02() throws Exception {
            MvcResult senha = chamar(POST, "/api/auth/login", null, "{\"email\":\"" + U1 + "\",\"senha\":\"errada1\"}");
            MvcResult email = chamar(POST, "/api/auth/login", null, "{\"email\":\"ninguem@circulabook.com\",\"senha\":\"senha123\"}");
            assertThat(status(senha)).isEqualTo(401);
            assertThat(status(email)).isEqualTo(401);
            assertThat(corpo(senha)).contains("E-mail ou senha inválidos").isEqualTo(corpo(email));
        }

        @Test
        @DisplayName("AU-03: sem token ou com token adulterado -> 401")
        void au03() throws Exception {
            assertThat(status(chamar(GET, "/api/emprestimos", null, null))).isEqualTo(401);
            String token = login(U1);
            String adulterado = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
            assertThat(status(chamar(GET, "/api/auth/me", adulterado, null))).isEqualTo(401);
        }

        @Test
        @DisplayName("AU-04 / AU-05: autocadastro cria COMUM (ignora tipo); e-mail repetido e senha curta recusados")
        void au04_au05() throws Exception {
            MvcResult r = chamar(POST, "/api/auth/cadastro", null,
                "{\"nome\":\"Usuário 4\",\"email\":\"usuario4@circulabook.com\",\"senha\":\"senha123\",\"tipo\":\"ADMIN\"}");
            assertThat(status(r)).isEqualTo(200);
            assertThat((String) json(r, "$.usuario.perfil")).isEqualTo("COMUM");
            assertThat((String) json(r, "$.token")).isNotBlank();
            assertThat(status(chamar(POST, "/api/auth/cadastro", null,
                "{\"nome\":\"Outro\",\"email\":\"" + U1 + "\",\"senha\":\"senha123\"}"))).isEqualTo(400);
            assertThat(status(chamar(POST, "/api/auth/cadastro", null,
                "{\"nome\":\"Outro\",\"email\":\"outro@circulabook.com\",\"senha\":\"123\"}"))).isEqualTo(400);
        }

        @Test
        @DisplayName("AU-06: COMUM chamando endpoints de bibliotecário/admin -> 403")
        void au06() throws Exception {
            String u1 = login(U1);
            assertThat(status(chamar(POST, "/api/emprestimos/registrar", u1, "{\"exemplarId\":1,\"usuarioId\":5}"))).isEqualTo(403);
            assertThat(status(chamar(GET, "/api/historico", u1, null))).isEqualTo(403);
            assertThat(status(chamar(GET, "/api/admin/dashboard", u1, null))).isEqualTo(403);
            assertThat(status(chamar(POST, "/api/livros", u1, "{\"titulo\":\"X\",\"autor\":\"Y\"}"))).isEqualTo(403);
        }

        @Test
        @DisplayName("AU-07: usuarioId falso é ignorado; o ator é o do token")
        void au07() throws Exception {
            String u1 = login(U1);
            MvcResult r = chamar(GET, "/api/emprestimos/usuario/" + idUsuario(U2), u1, null);
            assertThat(status(r)).isEqualTo(200);
            assertThat((List<?>) json(r, "$")).isEmpty(); // são os do Usuário 1 (nenhum), não os 3 do Usuário 2
            MvcResult res = chamar(POST, "/api/reservas", u1, "{\"livroId\":" + idLivro("Duna") + ",\"bibliotecaFilaId\":"
                + idBiblioteca(CENTRAL) + ",\"bibliotecaDestinoId\":" + idBiblioteca(CENTRAL) + ",\"usuarioId\":" + idUsuario(U2) + "}");
            assertThat(status(res)).isEqualTo(200);
            assertThat(reserva(U1, "Duna")).startsWith("PENDENTE");
            assertThat(contar("SELECT COUNT(*) FROM reserva r JOIN livro l ON l.id = r.livro_id WHERE r.usuario_id = ? AND l.titulo = 'Duna'",
                idUsuario(U2))).isZero();
        }

        @Test
        @DisplayName("AU-08: /auth/me e /usuarios nunca trazem senhaHash")
        void au08() throws Exception {
            assertThat(corpo(chamar(GET, "/api/auth/me", login(U1), null))).doesNotContain("senha");
            MvcResult lista = chamar(GET, "/api/usuarios/bibliotecarios", login(ADMIN), null);
            assertThat(status(lista)).isEqualTo(200);
            assertThat(corpo(lista)).doesNotContain("senhaHash").doesNotContain("$2a$");
        }
    }

    // ═════════════════════════ 9.2 Estados e invariantes ═════════════════════════

    @Nested
    @DisplayName("ES")
    class Estados {

        @Test
        @DisplayName("ES-01 / ES-02: U1 entra na fila de Duna (T3) e cancela (T12)")
        void es01_es02() throws Exception {
            assertThat(status(reservar(U1, "Duna", CENTRAL, CENTRAL))).isEqualTo(200);
            assertThat(situacoes("Duna", CENTRAL)).containsExactly("EMPRESTADO_RESERVADO", "EMPRESTADO_RESERVADO");
            assertThat(status(chamar(PATCH, "/api/reservas/" + idReserva(U1, "Duna") + "/cancelar", login(U1), null))).isEqualTo(200);
            assertThat(situacoes("Duna", CENTRAL)).containsExactly("EMPRESTADO", "EMPRESTADO");
        }

        @Test
        @DisplayName("ES-03: BibVI empresta o Harry reservado a U3 (T6); R2 RETIRADA")
        void es03() throws Exception {
            long harry = exemplarCom("Harry Potter e a Pedra Filosofal", VI, "RESERVADO");
            assertThat(status(emprestar(BIB_VI, harry, U3))).isEqualTo(200);
            assertThat(statusExemplar(harry)).isEqualTo("EMPRESTADO");
            assertThat(reserva(U3, "Harry Potter e a Pedra Filosofal")).isEqualTo("RETIRADA|" + VI);
        }

        @Test
        @DisplayName("ES-06: BibC devolve o Hobbit de U2 sem T1 aprovada: RESERVADO retido aguardando o Admin")
        void es06() throws Exception {
            long hobbit = exemplarCom("O Hobbit", CENTRAL, "EMPRESTADO_RESERVADO");
            assertThat(status(devolver(BIB_C, emprestimoAberto(U2, "O Hobbit"), "BOM"))).isEqualTo(200);
            assertThat(statusExemplar(hobbit)).isEqualTo("RESERVADO");
            assertThat(reserva(U3, "O Hobbit")).isEqualTo("AGUARDANDO_TRANSFERENCIA|" + VI);
            assertThat(transferencia("O Hobbit")).isEqualTo("PENDENTE|" + hobbit);
            assertThat(notificacoes(ADMIN, "EXEMPLAR_RETIDO")).isEqualTo(1);
        }

        @Test
        @DisplayName("ES-07: expiração de R2 sem fila: RESERVADO -> DISPONIVEL (T7), U3 notificado")
        void es07() throws Exception {
            long harry = exemplarCom("Harry Potter e a Pedra Filosofal", VI, "RESERVADO");
            // "Forçar" a expiração: o prazo de retirada passa para ontem e a rotina roda na hora
            jdbc.update("UPDATE reserva SET data_expiracao = ? WHERE id = ?",
                LocalDateTime.now().minusDays(1), idReserva(U3, "Harry Potter e a Pedra Filosofal"));
            assertThat(status(chamar(POST, "/api/reservas/expirar-vencidas", login(ADMIN), null))).isEqualTo(200);
            assertThat(statusExemplar(harry)).isEqualTo("DISPONIVEL");
            assertThat(reserva(U3, "Harry Potter e a Pedra Filosofal")).startsWith("EXPIRADA");
            assertThat(notificacoes(U3, "RESERVA_EXPIRADA")).isEqualTo(1);
        }

        @Test
        @DisplayName("ES-09: BibVI confirma a chegada do Fahrenheit 451: DISPONIVEL na VI (T11), CONCLUIDA")
        void es09() throws Exception {
            long ex = exemplarCom("Fahrenheit 451", CENTRAL, "EM_TRANSFERENCIA");
            MvcResult r = chamar(PATCH, "/api/transferencias/" + idTransferencia("Fahrenheit 451") + "/confirmar-chegada",
                login(BIB_VI), "{}");
            assertThat(status(r)).isEqualTo(200);
            assertThat(statusExemplar(ex)).isEqualTo("DISPONIVEL");
            assertThat(situacoes("Fahrenheit 451", VI)).containsExactly("DISPONIVEL");
            assertThat(transferencia("Fahrenheit 451")).startsWith("CONCLUIDA");
        }

        @Test
        @DisplayName("ES-11 / ES-12: BibVI reativa o Steve Jobs (T14) e baixa um disponível (T13), que não empresta mais")
        void es11_es12() throws Exception {
            String bibVI = login(BIB_VI);
            long steve = exemplarCom("Steve Jobs", VI, "INDISPONIVEL");
            assertThat(status(chamar(PATCH, "/api/exemplares/" + steve + "/reativar", bibVI, null))).isEqualTo(200);
            assertThat(statusExemplar(steve)).isEqualTo("DISPONIVEL");

            long cao = exemplarCom("O Cão dos Baskerville", VI, "DISPONIVEL");
            assertThat(status(chamar(PATCH, "/api/exemplares/" + cao + "/indisponivel", bibVI, "{\"motivo\":\"Páginas soltas\"}")))
                .isEqualTo(200);
            assertThat(statusExemplar(cao)).isEqualTo("INDISPONIVEL");
            assertThat(status(emprestar(BIB_VI, cao, U1))).isEqualTo(400);
        }

        @Test
        @DisplayName("ES-14: avulsa de exemplar EMPRESTADO ou RESERVADO é recusada")
        void es14() throws Exception {
            MvcResult emprestado = avulsa(List.of(exemplarCom("Duna", CENTRAL, "EMPRESTADO")), TIJUCA);
            MvcResult reservado = avulsa(List.of(exemplarCom("Harry Potter e a Pedra Filosofal", VI, "RESERVADO")), TIJUCA);
            assertThat(status(emprestado)).isEqualTo(400);
            assertThat(status(reservado)).isEqualTo(400);
            assertThat(contar("SELECT COUNT(*) FROM solicitacao_transferencia")).isEqualTo(3); // nada criado
        }
    }

    // ═════════════════════════ 9.3 Usuário comum ═════════════════════════

    @Nested
    @DisplayName("U")
    class Comum {

        @Test
        @DisplayName("U-03 / U-04: biblioteca com exemplar livre ou sem o título não aceita fila")
        void u03_u04() throws Exception {
            MvcResult livre = reservar(U1, "Dom Casmurro", CENTRAL, CENTRAL);
            assertThat(status(livre)).isEqualTo(400);
            assertThat(corpo(livre)).contains("disponível(is)").contains("presencialmente");
            MvcResult semTitulo = reservar(U1, "Duna", TIJUCA, TIJUCA);
            assertThat(status(semTitulo)).isEqualTo(400);
            assertThat(corpo(semTitulo)).contains("não possui exemplares deste título");
        }

        @Test
        @DisplayName("U-05: U1 na fila de Duna da Central: PENDENTE, 1º lugar, sem transferência")
        void u05() throws Exception {
            assertThat(status(chamar(GET, "/api/reservas/posicao/" + idLivro("Duna") + "?bibliotecaId=" + idBiblioteca(CENTRAL),
                login(U1), null))).isEqualTo(200);
            assertThat(status(reservar(U1, "Duna", CENTRAL, CENTRAL))).isEqualTo(200);
            assertThat(reserva(U1, "Duna")).isEqualTo("PENDENTE|" + CENTRAL);
            assertThat(contar("SELECT COUNT(*) FROM solicitacao_transferencia s JOIN livro l ON l.id = s.livro_id WHERE l.titulo = 'Duna'"))
                .isZero();
        }

        @Test
        @DisplayName("U-06: Duna na Central com retirada em outra: VI e Tijuca liberadas; cria pedido PENDENTE sem exemplar")
        void u06() throws Exception {
            MvcResult d = chamar(GET, "/api/reservas/destinos?livroId=" + idLivro("Duna") + "&bibliotecaFilaId="
                + idBiblioteca(CENTRAL), login(U1), null);
            assertThat(status(d)).isEqualTo(200);
            List<String> liberados = json(d, "$[?(@.permitido == true)].nome");
            List<String> todos = json(d, "$[*].nome");
            assertThat(liberados).containsExactlyInAnyOrder(VI, TIJUCA);
            assertThat(todos).doesNotContain(CENTRAL);

            assertThat(status(reservar(U1, "Duna", CENTRAL, TIJUCA))).isEqualTo(200);
            assertThat(reserva(U1, "Duna")).isEqualTo("PENDENTE|" + TIJUCA);
            assertThat(transferencia("Duna")).isEqualTo("PENDENTE|-");
        }

        @Test
        @DisplayName("U-07: Grande Sertão (1 só exemplar na Central) não sai para outra biblioteca")
        void u07() throws Exception {
            MvcResult r = reservar(U1, "Grande Sertão: Veredas", CENTRAL, VI);
            assertThat(status(r)).isEqualTo(400);
            assertThat(corpo(r)).contains("só 1 exemplar");
        }

        @Test
        @DisplayName("U-09: U2 não reserva o Hobbit que tem emprestado; U3 não reserva o Hobbit de novo")
        void u09() throws Exception {
            MvcResult u2 = reservar(U2, "O Hobbit", CENTRAL, CENTRAL);
            assertThat(status(u2)).isEqualTo(400);
            assertThat(corpo(u2)).contains("já está com um exemplar");
            MvcResult u3 = reservar(U3, "O Hobbit", CENTRAL, CENTRAL);
            assertThat(status(u3)).isEqualTo(400);
            assertThat(corpo(u3)).contains("já possui uma reserva ativa");
        }

        @Test
        @DisplayName("U-11: U3 cancela R1 com T1 PENDENTE e, noutro caso, APROVADA: transferência CANCELADA")
        void u11() throws Exception {
            assertThat(status(chamar(PATCH, "/api/reservas/" + idReserva(U3, "O Hobbit") + "/cancelar", login(U3), null)))
                .isEqualTo(200);
            assertThat(transferencia("O Hobbit")).isEqualTo("CANCELADA|-");
            assertThat(situacoes("O Hobbit", CENTRAL)).containsExactly("EMPRESTADO", "INDISPONIVEL"); // fila esvaziou
        }

        @Test
        @DisplayName("U-11 (aprovada): cancelar com T1 aprovada aguardando exemplar também cancela o pedido")
        void u11Aprovada() throws Exception {
            assertThat(status(chamar(PATCH, "/api/transferencias/" + idTransferencia("O Hobbit") + "/aprovar", login(ADMIN), null)))
                .isEqualTo(200);
            assertThat(status(chamar(PATCH, "/api/reservas/" + idReserva(U3, "O Hobbit") + "/cancelar", login(U3), null)))
                .isEqualTo(200);
            assertThat(transferencia("O Hobbit")).isEqualTo("CANCELADA|-");
        }

        @Test
        @DisplayName("U-12 (*): cancelar com T1 EM_TRANSITO: a viagem continua e o exemplar chega à VI DISPONIVEL")
        void u12() throws Exception {
            chamar(PATCH, "/api/transferencias/" + idTransferencia("O Hobbit") + "/aprovar", login(ADMIN), null);
            long hobbit = exemplarCom("O Hobbit", CENTRAL, "EMPRESTADO_RESERVADO");
            assertThat(status(devolver(BIB_C, emprestimoAberto(U2, "O Hobbit"), "BOM"))).isEqualTo(200);
            assertThat(transferencia("O Hobbit")).isEqualTo("EM_TRANSITO|" + hobbit);
            assertThat(status(chamar(PATCH, "/api/reservas/" + idReserva(U3, "O Hobbit") + "/cancelar", login(U3), null)))
                .isEqualTo(200);
            assertThat(transferencia("O Hobbit")).startsWith("EM_TRANSITO");
            assertThat(status(chamar(PATCH, "/api/transferencias/" + idTransferencia("O Hobbit") + "/confirmar-chegada",
                login(BIB_VI), "{}"))).isEqualTo(200);
            assertThat(statusExemplar(hobbit)).isEqualTo("DISPONIVEL");
            assertThat(situacoes("O Hobbit", VI)).containsExactly("DISPONIVEL");
        }

        @Test
        @DisplayName("U-14: U1 soma ao Ensaio (2 -> 3), é barrado na repetição; título novo de U1 e U2 soma (RN07)")
        void u14() throws Exception {
            String u1 = login(U1);
            MvcResult r = chamar(POST, "/api/demandas", u1, "{\"titulo\":\"Ensaio sobre a Cegueira\",\"autor\":\"José Saramago\"}");
            assertThat(status(r)).isEqualTo(200);
            assertThat(contar("SELECT total_solicitacoes FROM demanda_aquisicao WHERE titulo = 'Ensaio sobre a Cegueira'")).isEqualTo(3);
            MvcResult repetido = chamar(POST, "/api/demandas", u1, "{\"titulo\":\"ensaio sobre a cegueira\",\"autor\":\"Jose Saramago\"}");
            assertThat(status(repetido)).isEqualTo(400);
            assertThat(corpo(repetido)).contains("já registrou interesse");

            assertThat(status(chamar(POST, "/api/demandas", u1, "{\"titulo\":\"Vidas Secas\",\"autor\":\"Graciliano Ramos\"}"))).isEqualTo(200);
            assertThat(status(chamar(POST, "/api/demandas", login(U2), "{\"titulo\":\"Vidas Secas\",\"autor\":\"Graciliano Ramos\"}")))
                .isEqualTo(200);
            assertThat(contar("SELECT total_solicitacoes FROM demanda_aquisicao WHERE titulo = 'Vidas Secas'")).isEqualTo(2);
        }

        @Test
        @DisplayName("U-15: U3 tem 1 não lida; marcar todas zera; U1 sem notificações")
        void u15() throws Exception {
            String u3 = login(U3);
            assertThat(corpo(chamar(GET, "/api/notificacoes/nao-lidas/contagem", u3, null))).contains("1");
            assertThat(status(chamar(PATCH, "/api/notificacoes/marcar-todas-lidas", u3, null))).isEqualTo(200);
            assertThat(corpo(chamar(GET, "/api/notificacoes/nao-lidas/contagem", u3, null))).contains("0");
            assertThat((Integer) json(chamar(GET, "/api/notificacoes", login(U1), null), "$.totalItens")).isZero();
        }
    }

    // ═════════════════════════ 9.4 Bibliotecário ═════════════════════════

    @Nested
    @DisplayName("B")
    class Bibliotecario {

        @Test
        @DisplayName("B-01: cada bibliotecário vê só os empréstimos da própria biblioteca")
        void b01() throws Exception {
            assertThat((List<?>) json(chamar(GET, "/api/emprestimos/ativos", login(BIB_C), null), "$")).hasSize(4);
            assertThat((List<?>) json(chamar(GET, "/api/emprestimos/ativos", login(BIB_VI), null), "$")).isEmpty();
        }

        @Test
        @DisplayName("B-03 / B-04: U2 (3/3 e atrasado) não pega; U1 pega 1 Dom Casmurro mas não um 2º")
        void b03_b04() throws Exception {
            List<Long> dom = exemplares("Dom Casmurro", CENTRAL, "DISPONIVEL");
            assertThat(status(emprestar(BIB_C, dom.get(0), U2))).isEqualTo(400);
            assertThat(status(emprestar(BIB_C, dom.get(0), U1))).isEqualTo(200);
            MvcResult segundo = emprestar(BIB_C, dom.get(1), U1);
            assertThat(status(segundo)).isEqualTo(400);
            assertThat(corpo(segundo)).contains("já possui um exemplar");
        }

        @Test
        @DisplayName("B-05 / B-06: Harry reservado a U3 não vai para U1; vai para U3 com prazo de 14 dias")
        void b05_b06() throws Exception {
            long harry = exemplarCom("Harry Potter e a Pedra Filosofal", VI, "RESERVADO");
            MvcResult outro = emprestar(BIB_VI, harry, U1);
            assertThat(status(outro)).isEqualTo(400);
            assertThat(corpo(outro)).contains("reservado para Usuário 3");
            MvcResult r = emprestar(BIB_VI, harry, U3);
            assertThat(status(r)).isEqualTo(200);
            LocalDateTime prev = LocalDateTime.parse(json(r, "$.dataPrevDevolucao"));
            assertThat(ChronoUnit.HOURS.between(LocalDateTime.now(), prev)).isBetween(14 * 24L - 1, 14 * 24L);
            assertThat(reserva(U3, "Harry Potter e a Pedra Filosofal")).startsWith("RETIRADA");
        }

        @Test
        @DisplayName("B-07 / B-08: devolver Duna de U2 com 6 dias de atraso bloqueia 12 dias; só Bom/Danificado")
        void b07_b08() throws Exception {
            long emp = emprestimoAberto(U2, "Duna");
            assertThat(status(devolver(BIB_C, emp, "PERDIDO"))).isEqualTo(400);
            assertThat(status(devolver(BIB_C, emp, "BOM"))).isEqualTo(200);
            LocalDateTime ate = jdbc.queryForObject("SELECT bloqueado_ate FROM usuario WHERE email = ?", LocalDateTime.class, U2);
            assertThat(ChronoUnit.HOURS.between(LocalDateTime.now(), ate)).isBetween(12 * 24L - 1, 12 * 24L);
            assertThat(notificacoes(U2, "BLOQUEIO")).isEqualTo(1);
        }

        @Test
        @DisplayName("B-09: Grande Sertão devolvido Danificado fica INDISPONIVEL e não promove a fila")
        void b09() throws Exception {
            assertThat(status(reservar(U1, "Grande Sertão: Veredas", CENTRAL, CENTRAL))).isEqualTo(200);
            assertThat(status(devolver(BIB_C, emprestimoAberto(U2, "Grande Sertão: Veredas"), "DANIFICADO"))).isEqualTo(200);
            assertThat(situacoes("Grande Sertão: Veredas", CENTRAL)).containsExactly("INDISPONIVEL");
            assertThat(reserva(U1, "Grande Sertão: Veredas")).startsWith("PENDENTE");
        }

        @Test
        @DisplayName("B-10 (*): com U1 na fila de Duna, a devolução Bom separa para U1 (T4) com 3 dias")
        void b10() throws Exception {
            assertThat(status(reservar(U1, "Duna", CENTRAL, CENTRAL))).isEqualTo(200);
            assertThat(status(devolver(BIB_C, emprestimoAberto(U3, "Duna"), "BOM"))).isEqualTo(200);
            assertThat(situacoes("Duna", CENTRAL)).containsExactly("EMPRESTADO", "RESERVADO");
            assertThat(reserva(U1, "Duna")).isEqualTo("DISPONIVEL|" + CENTRAL);
            LocalDateTime exp = jdbc.queryForObject("SELECT data_expiracao FROM reserva WHERE id = ?", LocalDateTime.class,
                idReserva(U1, "Duna"));
            assertThat(ChronoUnit.HOURS.between(LocalDateTime.now(), exp)).isBetween(3 * 24L - 1, 3 * 24L);
        }

        @Test
        @DisplayName("B-11 / B-13 / B-16: BibVI não devolve/empresta da Central; BibC não confirma chegada da VI; BibT não cadastra na Central")
        void b11_b13_b16() throws Exception {
            assertThat(status(devolver(BIB_VI, emprestimoAberto(U2, "Duna"), "BOM"))).isEqualTo(403);
            assertThat(status(emprestar(BIB_VI, exemplarCom("Dom Casmurro", CENTRAL, "DISPONIVEL"), U1))).isEqualTo(403);
            assertThat(status(chamar(PATCH, "/api/transferencias/" + idTransferencia("Fahrenheit 451") + "/confirmar-chegada",
                login(BIB_C), "{}"))).isEqualTo(403);
            assertThat(status(chamar(POST, "/api/exemplares", login(BIB_T), "{\"livroId\":" + idLivro("Duna")
                + ",\"bibliotecaId\":" + idBiblioteca(CENTRAL) + ",\"quantidade\":1}"))).isEqualTo(403);
            assertThat(situacoes("Duna", CENTRAL)).hasSize(2);
        }

        @Test
        @DisplayName("B-12 / ES-10: ciclo do Hobbit: aprovar -> devolver -> a receber -> chegada -> empréstimo a U3")
        void b12() throws Exception {
            long t1 = idTransferencia("O Hobbit");
            assertThat(status(chamar(PATCH, "/api/transferencias/" + t1 + "/aprovar", login(ADMIN), null))).isEqualTo(200);
            long hobbit = exemplarCom("O Hobbit", CENTRAL, "EMPRESTADO_RESERVADO");
            assertThat(status(devolver(BIB_C, emprestimoAberto(U2, "O Hobbit"), "BOM"))).isEqualTo(200);
            assertThat(statusExemplar(hobbit)).isEqualTo("EM_TRANSFERENCIA");
            assertThat(transferencia("O Hobbit")).isEqualTo("EM_TRANSITO|" + hobbit);

            String bibVI = login(BIB_VI);
            assertThat(corpo(chamar(GET, "/api/transferencias/biblioteca/a-receber", bibVI, null))).contains("O Hobbit");
            assertThat(status(chamar(PATCH, "/api/transferencias/" + t1 + "/confirmar-chegada", bibVI, "{}"))).isEqualTo(200);
            assertThat(statusExemplar(hobbit)).isEqualTo("RESERVADO");
            assertThat(situacoes("O Hobbit", VI)).containsExactly("RESERVADO");
            assertThat(reserva(U3, "O Hobbit")).isEqualTo("DISPONIVEL|" + VI);

            assertThat(status(emprestar(BIB_VI, hobbit, U3))).isEqualTo(200);
            assertThat(statusExemplar(hobbit)).isEqualTo("EMPRESTADO");
            assertThat(reserva(U3, "O Hobbit")).startsWith("RETIRADA");
        }

        @Test
        @DisplayName("B-14 / B-15: BibT cadastra 3 exemplares e o 1º Memórias Póstumas, que fica DISPONIVEL e emprestável")
        void b14_b15() throws Exception {
            String bibT = login(BIB_T);
            assertThat(status(chamar(POST, "/api/exemplares", bibT, "{\"livroId\":" + idLivro("Sapiens")
                + ",\"conservacao\":\"NOVO\",\"quantidade\":3}"))).isEqualTo(200);
            assertThat(situacoes("Sapiens", TIJUCA)).containsExactly("DISPONIVEL", "DISPONIVEL", "DISPONIVEL");
            assertThat(status(chamar(POST, "/api/exemplares", bibT, "{\"livroId\":999999,\"quantidade\":1}"))).isEqualTo(400);

            MvcResult r = chamar(POST, "/api/exemplares", bibT, "{\"livroId\":" + idLivro("Memórias Póstumas de Brás Cubas")
                + ",\"conservacao\":\"BOM\",\"quantidade\":1}");
            assertThat(status(r)).isEqualTo(200);
            long memorias = ((Number) json(r, "$[0].id")).longValue();
            assertThat(statusExemplar(memorias)).isEqualTo("DISPONIVEL");
            assertThat(status(emprestar(BIB_T, memorias, U1))).isEqualTo(200);
        }

        @Test
        @DisplayName("B-17 (*): com U1 na fila de Duna, exemplar novo na Central nasce RESERVADO para U1 (T15)")
        void b17() throws Exception {
            assertThat(status(reservar(U1, "Duna", CENTRAL, CENTRAL))).isEqualTo(200);
            MvcResult r = chamar(POST, "/api/exemplares", login(BIB_C), "{\"livroId\":" + idLivro("Duna")
                + ",\"conservacao\":\"NOVO\",\"quantidade\":1}");
            assertThat(status(r)).isEqualTo(200);
            assertThat(statusExemplar(((Number) json(r, "$[0].id")).longValue())).isEqualTo("RESERVADO");
            assertThat(reserva(U1, "Duna")).isEqualTo("DISPONIVEL|" + CENTRAL);
        }

        @Test
        @DisplayName("B-19: bibliotecário não cria livro nem categoria (403)")
        void b19() throws Exception {
            String bibC = login(BIB_C);
            assertThat(status(chamar(POST, "/api/livros", bibC, "{\"titulo\":\"X\",\"autor\":\"Y\"}"))).isEqualTo(403);
            assertThat(status(chamar(POST, "/api/categorias", bibC, "{\"nome\":\"Z\"}"))).isEqualTo(403);
        }

        @Test
        @DisplayName("B-20: reservas aguardando retirada da VI mostram U3/Harry; sino do BibVI avisa")
        void b20() throws Exception {
            MvcResult r = chamar(GET, "/api/reservas/biblioteca", login(BIB_VI), null);
            assertThat(status(r)).isEqualTo(200);
            assertThat(corpo(r)).contains("Usuário 3").contains("Harry Potter e a Pedra Filosofal");
            assertThat(notificacoes(BIB_VI, "RETIRADA_NA_BIBLIOTECA")).isEqualTo(1);
        }
    }

    // ═════════════════════════ 9.5 Administrador ═════════════════════════

    @Nested
    @DisplayName("A")
    class Administrador {

        @Test
        @DisplayName("A-01: KPIs do dashboard batem com o seed")
        void a01() throws Exception {
            MvcResult r = chamar(GET, "/api/admin/dashboard", login(ADMIN), null);
            assertThat(status(r)).isEqualTo(200);
            Map<String, Object> d = json(r, "$");
            assertThat(d).containsEntry("bibliotecasAtivas", 3).containsEntry("exemplaresTotal", 50)
                .containsEntry("emprestimosAtivos", 4).containsEntry("emprestimosAtrasados", 1)
                .containsEntry("reservasEmFila", 1).containsEntry("reservasProntas", 1)
                .containsEntry("transferenciasPendentes", 1).containsEntry("transferenciasEmTransito", 1)
                .containsEntry("demandasAbertas", 1).containsEntry("demandasEmAnalise", 1);
        }

        @Test
        @DisplayName("A-02 / A-03: T1 aparece com o título sem exemplar; aprovada fica aguardando exemplar")
        void a02_a03() throws Exception {
            String admin = login(ADMIN);
            MvcResult p = chamar(GET, "/api/transferencias/pedidos-pendentes", admin, null);
            assertThat(corpo(p)).contains("O Hobbit");
            assertThat((List<?>) json(p, "$")).hasSize(1);
            assertThat(status(chamar(PATCH, "/api/transferencias/" + idTransferencia("O Hobbit") + "/aprovar", admin, null)))
                .isEqualTo(200);
            assertThat(transferencia("O Hobbit")).isEqualTo("APROVADA|-");
            assertThat(notificacoes(U3, "PEDIDO_APROVADO")).isEqualTo(1);
        }

        @Test
        @DisplayName("A-04: rejeitar T1: R1 segue PENDENTE com retirada na Central; U3 notificado")
        void a04() throws Exception {
            assertThat(status(chamar(PATCH, "/api/transferencias/" + idTransferencia("O Hobbit") + "/rejeitar", login(ADMIN), "{}")))
                .isEqualTo(200);
            assertThat(transferencia("O Hobbit")).startsWith("REJEITADA");
            assertThat(reserva(U3, "O Hobbit")).isEqualTo("PENDENTE|" + CENTRAL);
            assertThat(notificacoes(U3, "PEDIDO_REJEITADO")).isEqualTo(1);
        }

        @Test
        @DisplayName("A-05 (*) / A-06: devolvido antes, aprovar envia direto; 2ª aprovação 'já foi processada'; BibC 403")
        void a05_a06() throws Exception {
            long hobbit = exemplarCom("O Hobbit", CENTRAL, "EMPRESTADO_RESERVADO");
            devolver(BIB_C, emprestimoAberto(U2, "O Hobbit"), "BOM");
            long t1 = idTransferencia("O Hobbit");
            assertThat(status(chamar(PATCH, "/api/transferencias/" + t1 + "/aprovar", login(BIB_C), null))).isEqualTo(403);
            assertThat(status(chamar(PATCH, "/api/transferencias/" + t1 + "/aprovar", login(ADMIN), null))).isEqualTo(200);
            assertThat(transferencia("O Hobbit")).isEqualTo("EM_TRANSITO|" + hobbit);
            MvcResult de_novo = chamar(PATCH, "/api/transferencias/" + t1 + "/aprovar", login(ADMIN), null);
            assertThat(status(de_novo)).isEqualTo(400);
            assertThat(corpo(de_novo)).contains("já foi processada");
        }

        @Test
        @DisplayName("A-07: U1 não reserva o Hobbit com retirada na VI (capacidade da Central esgotada)")
        void a07() throws Exception {
            MvcResult r = reservar(U1, "O Hobbit", CENTRAL, VI);
            assertThat(status(r)).isEqualTo(400);
            assertThat(corpo(r)).contains("deixaria sem o livro");
        }

        @Test
        @DisplayName("A-08 / A-10 / A-09 / A-11: lote tudo ou nada; depois 1984 + 2 Dom para a Tijuca; Dom para a VI permitido")
        void a08_a11() throws Exception {
            List<Long> l1984 = exemplares("1984", CENTRAL, "DISPONIVEL");
            List<Long> dom = exemplares("Dom Casmurro", CENTRAL, "DISPONIVEL");
            MvcResult tudoOuNada = avulsa(List.of(l1984.get(0), l1984.get(1), dom.get(0)), TIJUCA);
            assertThat(status(tudoOuNada)).isEqualTo(400);
            assertThat(corpo(tudoOuNada)).contains("Nenhuma transferência foi criada").contains("1984");
            assertThat(contar("SELECT COUNT(*) FROM solicitacao_transferencia")).isEqualTo(3);

            assertThat(status(avulsa(List.of(l1984.get(0), dom.get(0), dom.get(1)), TIJUCA))).isEqualTo(200);
            assertThat(contar("SELECT COUNT(*) FROM solicitacao_transferencia s JOIN biblioteca b ON b.id = s.biblioteca_destino_id "
                + "WHERE b.nome = ? AND s.status = 'EM_TRANSITO'", TIJUCA)).isEqualTo(3);

            assertThat(status(avulsa(List.of(dom.get(2)), VI))).isEqualTo(400); // a Central ficaria sem Dom Casmurro
            assertThat(status(avulsa(List.of(exemplares("Dom Casmurro", VI, "DISPONIVEL").get(0)), CENTRAL))).isEqualTo(200);
        }

        @Test
        @DisplayName("A-12 / A-13: Admin cria categoria e livro (sem exemplares) e edita; bibliotecário recebe 403")
        void a12_a13() throws Exception {
            String admin = login(ADMIN);
            MvcResult cat = chamar(POST, "/api/categorias", admin, "{\"nome\":\"Teatro\"}");
            assertThat(status(cat)).isEqualTo(200);
            MvcResult livro = chamar(POST, "/api/livros", admin, "{\"titulo\":\"Auto da Compadecida\",\"autor\":\"Ariano Suassuna\","
                + "\"categoriaId\":" + json(cat, "$.id") + "}");
            assertThat(status(livro)).isEqualTo(200);
            long id = ((Number) json(livro, "$.id")).longValue();
            assertThat(contar("SELECT COUNT(*) FROM exemplar WHERE livro_id = ?", id)).isZero();
            String edicao = "{\"titulo\":\"Auto da Compadecida (revisado)\",\"autor\":\"Ariano Suassuna\"}";
            assertThat(status(chamar(PUT, "/api/livros/" + id, admin, edicao))).isEqualTo(200);
            assertThat(status(chamar(PUT, "/api/livros/" + id, login(BIB_C), edicao))).isEqualTo(403);
        }

        @Test
        @DisplayName("A-14 / A-16: nova biblioteca e seu bibliotecário no padrão da 8.1; ele só vê a nova")
        void a14_a16() throws Exception {
            String admin = login(ADMIN);
            MvcResult bib = chamar(POST, "/api/bibliotecas", admin, "{\"nome\":\"Biblioteca do Méier\",\"endereco\":\"Rua A, 1\"}");
            assertThat(status(bib)).isEqualTo(200);
            Object idBib = json(bib, "$.id");
            assertThat(status(chamar(PUT, "/api/bibliotecas/" + idBib, admin,
                "{\"nome\":\"Biblioteca do Méier\",\"endereco\":\"Rua B, 2\"}"))).isEqualTo(200);
            assertThat(status(chamar(POST, "/api/usuarios/bibliotecarios", admin, "{\"nome\":\"Bibliotecário Méier\","
                + "\"email\":\"bibliotecariomeier@circulabook.com\",\"senha\":\"senha123\",\"bibliotecaId\":" + idBib + "}"))).isEqualTo(200);
            MvcResult me = chamar(GET, "/api/auth/me", login("bibliotecariomeier@circulabook.com"), null);
            assertThat(json(me, "$.perfil").toString()).isEqualTo("BIBLIOTECARIO");
            assertThat(json(me, "$.bibliotecaNome").toString()).isEqualTo("Biblioteca do Méier");
        }

        @Test
        @DisplayName("A-15: Admin não cria COMUM, exemplar, empréstimo nem devolução")
        void a15() throws Exception {
            String admin = login(ADMIN);
            assertThat(status(chamar(POST, "/api/exemplares", admin, "{\"livroId\":1,\"quantidade\":1}"))).isEqualTo(403);
            assertThat(status(chamar(POST, "/api/emprestimos/registrar", admin, "{\"exemplarId\":1,\"usuarioId\":5}"))).isEqualTo(403);
            assertThat(status(chamar(POST, "/api/emprestimos/devolver", admin, "{\"emprestimoId\":1}"))).isEqualTo(403);
            assertThat(status(chamar(POST, "/api/usuarios/bibliotecarios", admin,
                "{\"nome\":\"Comum\",\"email\":\"comum@circulabook.com\",\"senha\":\"senha123\",\"tipo\":\"COMUM\"}"))).isEqualTo(400);
            assertThat(contar("SELECT COUNT(*) FROM usuario WHERE email = 'comum@circulabook.com'")).isZero();
        }

        @Test
        @DisplayName("A-17: demandas por mais pedidas (Ensaio primeiro) e troca de situação")
        void a17() throws Exception {
            String admin = login(ADMIN);
            MvcResult r = chamar(GET, "/api/demandas", admin, null);
            assertThat(status(r)).isEqualTo(200);
            assertThat(corpo(r).indexOf("Ensaio sobre a Cegueira")).isLessThan(corpo(r).indexOf("Torto Arado"));
            long torto = contar("SELECT id FROM demanda_aquisicao WHERE titulo = 'Torto Arado'");
            assertThat(status(chamar(PATCH, "/api/demandas/" + torto + "/status", admin, "{\"status\":\"APROVADA\"}"))).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT status FROM demanda_aquisicao WHERE id = ?", String.class, torto)).isEqualTo("APROVADA");
        }

        @Test
        @DisplayName("A-18 / A-20: linha do tempo do Hobbit do seed; sino do Admin com novo pedido e retenção urgente")
        void a18_a20() throws Exception {
            String admin = login(ADMIN);
            long hobbit = exemplarCom("O Hobbit", CENTRAL, "EMPRESTADO_RESERVADO");
            List<String> eventos = json(chamar(GET, "/api/historico/exemplar/" + hobbit, admin, null), "$.eventos[*].evento");
            assertThat(eventos).containsExactly("CADASTRO", "EMPRESTIMO", "FILA");
            assertThat(notificacoes(ADMIN, "NOVO_PEDIDO")).isEqualTo(1);
            devolver(BIB_C, emprestimoAberto(U2, "O Hobbit"), "BOM");
            assertThat(notificacoes(ADMIN, "EXEMPLAR_RETIDO")).isEqualTo(1);
        }
    }
}
