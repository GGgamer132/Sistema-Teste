package com.circulabook.controller;

import com.circulabook.model.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.circulabook.model.StatusExemplar.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dashboard (A1) e relatórios (A10) do Admin, conferidos por DIFERENÇA: lê os números,
 * faz operações conhecidas pela API e confere só o quanto cada número mudou.
 */
class PainelAdminTest extends ApoioApiTest {

    private Biblioteca central, vilaIsabel;
    private Usuario ana, bruno, camila;
    private String tokenAdmin, tokenFernanda, tokenCarlos, tokenAna, tokenCamila;

    @BeforeEach
    void montar() throws Exception {
        central = biblioteca("Biblioteca Central");
        vilaIsabel = biblioteca("Biblioteca Vila Isabel");
        tokenAdmin = login(usuario("Roberto Dias", "ADMIN", null));
        tokenFernanda = login(usuario("Fernanda Reis", "BIBLIOTECARIO", central));
        tokenCarlos = login(usuario("Carlos Lima", "BIBLIOTECARIO", vilaIsabel));
        ana = usuario("Ana Souza", "COMUM", null);
        bruno = usuario("Bruno Alves", "COMUM", null);
        camila = usuario("Camila Duarte", "COMUM", null);
        tokenAna = login(ana);
        tokenCamila = login(camila);
        // "Seed" qualquer: o teste só olha diferenças
        Livro outro = livro("Livro do seed");
        exemplar(outro, vilaIsabel, DISPONIVEL);
        emprestimo(exemplar(outro, vilaIsabel, EMPRESTADO), bruno, 3);
    }

    @Test
    @DisplayName("A-01: KPIs do dashboard mudam exatamente com as operações feitas")
    void a01_dashboardPorDiferenca() throws Exception {
        Map<String, Long> d0 = dashboard();

        String bib = chamar(HttpMethod.POST, "/api/bibliotecas", tokenAdmin, "{\"nome\":\"Biblioteca Nova\"}")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        chamar(HttpMethod.PATCH, "/api/bibliotecas/" + id(bib) + "/desativar", tokenAdmin, null)
            .andExpect(status().isOk());
        Map<String, Long> d1 = dashboard();
        assertThat(delta(d0, d1)).containsExactlyInAnyOrderEntriesOf(Map.of("bibliotecasTotal", 1L));

        Livro hobbit = livro("O Hobbit");
        List<Long> ex = cadastrar(hobbit, 3);
        long emp1 = emprestar(ex.get(0), ana);
        emprestar(ex.get(1), bruno);
        emprestimo(exemplar(livro("Atrasado"), central, EMPRESTADO), camila, 20);  // 6 dias de atraso
        Map<String, Long> d2 = dashboard();
        assertThat(delta(d1, d2)).containsExactlyInAnyOrderEntriesOf(Map.of(
            "exemplaresTotal", 4L, "DISPONIVEL", 1L, "EMPRESTADO", 3L,
            "emprestimosAtivos", 3L, "emprestimosAtrasados", 1L));

        // Fila com retirada em outra biblioteca: reserva na fila + pedido pendente + marca de fila
        // (a Central tem 3 Hobbit, então a RN15 permite; o 3º exemplar disponível impede a fila,
        //  por isso ele sai em avulsa antes)
        chamar(HttpMethod.POST, "/api/transferencias/avulsa", tokenAdmin,
            "{\"exemplarIds\":[" + ex.get(2) + "],\"bibliotecaDestinoId\":" + vilaIsabel.getId() + "}")
            .andExpect(status().isOk());
        Map<String, Long> d3 = dashboard();
        assertThat(delta(d2, d3)).containsExactlyInAnyOrderEntriesOf(Map.of(
            "DISPONIVEL", -1L, "EM_TRANSFERENCIA", 1L, "transferenciasEmTransito", 1L));

        long reserva = id(chamar(HttpMethod.POST, "/api/reservas", tokenCamila,
            "{\"livroId\":" + hobbit.getId() + ",\"bibliotecaFilaId\":" + central.getId()
            + ",\"bibliotecaDestinoId\":" + vilaIsabel.getId() + "}")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Map<String, Long> d4 = dashboard();
        assertThat(delta(d3, d4)).containsExactlyInAnyOrderEntriesOf(Map.of(
            "reservasEmFila", 1L, "transferenciasPendentes", 1L,
            "EMPRESTADO", -2L, "EMPRESTADO_RESERVADO", 2L));

        long pedido = transferenciaRepository.findAll().stream()
            .filter(s -> s.getReserva() != null && s.getReserva().getId().equals(reserva))
            .findFirst().orElseThrow().getId();
        chamar(HttpMethod.PATCH, "/api/transferencias/" + pedido + "/aprovar", tokenAdmin, null)
            .andExpect(status().isOk());
        Map<String, Long> d5 = dashboard();
        assertThat(delta(d4, d5)).containsExactlyInAnyOrderEntriesOf(Map.of(
            "transferenciasPendentes", -1L, "transferenciasAguardandoExemplar", 1L));

        devolver(emp1);   // despacha direto para a VI
        Map<String, Long> d6 = dashboard();
        assertThat(delta(d5, d6)).containsExactlyInAnyOrderEntriesOf(Map.of(
            "emprestimosAtivos", -1L, "reservasEmFila", -1L,
            "transferenciasAguardandoExemplar", -1L, "transferenciasEmTransito", 1L,
            "EMPRESTADO_RESERVADO", -2L, "EMPRESTADO", 1L, "EM_TRANSFERENCIA", 1L));

        chamar(HttpMethod.PATCH, "/api/transferencias/" + pedido + "/confirmar-chegada", tokenCarlos, null)
            .andExpect(status().isOk());
        Map<String, Long> d7 = dashboard();
        assertThat(delta(d6, d7)).containsExactlyInAnyOrderEntriesOf(Map.of(
            "transferenciasEmTransito", -1L, "EM_TRANSFERENCIA", -1L, "RESERVADO", 1L, "reservasProntas", 1L));

        String dem = chamar(HttpMethod.POST, "/api/demandas", tokenAna, "{\"titulo\":\"Torto Arado\",\"autor\":\"Itamar\"}")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, Long> d8 = dashboard();
        assertThat(delta(d7, d8)).containsExactlyInAnyOrderEntriesOf(Map.of("demandasAbertas", 1L));
        long demId = Long.parseLong(dem.replaceAll(".*\"demanda\":\\{\"id\":(\\d+).*", "$1"));
        chamar(HttpMethod.PATCH, "/api/demandas/" + demId + "/status", tokenAdmin, "{\"status\":\"EM_ANALISE\"}")
            .andExpect(status().isOk());
        assertThat(delta(d8, dashboard())).containsExactlyInAnyOrderEntriesOf(Map.of(
            "demandasAbertas", -1L, "demandasEmAnalise", 1L));
    }

    @Test
    @DisplayName("A-19: cada relatório muda exatamente com as operações; período filtra")
    void a19_relatoriosPorDiferenca() throws Exception {
        String r0 = relatorios("");
        Livro hobbit = livro("O Hobbit");
        List<Long> ex = cadastrar(hobbit, 2);
        long emp = emprestar(ex.get(0), ana);
        devolver(emp);
        emprestar(ex.get(0), camila);
        Emprestimo atrasado = emprestimo(exemplar(livro("Duna"), central, EMPRESTADO), bruno, 20);    // 6 dias
        Emprestimo devolveTarde = emprestimo(exemplar(livro("Sapiens"), central, EMPRESTADO), ana, 18);
        devolver(devolveTarde.getId());                                                            // 4 dias
        chamar(HttpMethod.POST, "/api/transferencias/avulsa", tokenAdmin,
            "{\"exemplarIds\":[" + ex.get(1) + "],\"bibliotecaDestinoId\":" + vilaIsabel.getId() + "}")
            .andExpect(status().isOk());
        chamar(HttpMethod.POST, "/api/demandas", tokenAna, "{\"titulo\":\"Torto Arado\",\"autor\":\"Itamar\"}")
            .andExpect(status().isOk());
        String r1 = relatorios("");

        // 1) Acervo da Central: +4 exemplares (2 Hobbit, Duna, Sapiens)
        assertThat(num(r1, central, "acervo", "total") - num(r0, central, "acervo", "total")).isEqualTo(4);
        assertThat(statusAcervo(r1, central, "EMPRESTADO") - statusAcervo(r0, central, "EMPRESTADO")).isEqualTo(2);
        assertThat(statusAcervo(r1, central, "DISPONIVEL") - statusAcervo(r0, central, "DISPONIVEL")).isEqualTo(1);
        assertThat(statusAcervo(r1, central, "EM_TRANSFERENCIA") - statusAcervo(r0, central, "EM_TRANSFERENCIA")).isEqualTo(1);

        // 2) Empréstimos da Central: 4 realizados, 2 devolvidos (1 com atraso), 2 em aberto
        assertThat(num(r1, central, "emprestimos", "realizados") - num(r0, central, "emprestimos", "realizados")).isEqualTo(4);
        assertThat(num(r1, central, "emprestimos", "devolvidos") - num(r0, central, "emprestimos", "devolvidos")).isEqualTo(2);
        assertThat(num(r1, central, "emprestimos", "devolvidosComAtraso")
            - num(r0, central, "emprestimos", "devolvidosComAtraso")).isEqualTo(1);
        assertThat(num(r1, central, "emprestimos", "emAberto") - num(r0, central, "emprestimos", "emAberto")).isEqualTo(2);

        // 3) Atrasos: um em aberto (6 dias) e um devolvido com atraso (4 dias)
        List<Map<String, Object>> atrasos = JsonPath.read(r1, "$.atrasos");
        assertThat(atrasos.size() - ((List<?>) JsonPath.read(r0, "$.atrasos")).size()).isEqualTo(2);
        assertThat(atrasos).filteredOn(a -> ((Number) a.get("emprestimoId")).longValue() == atrasado.getId())
            .singleElement().satisfies(a -> {
                assertThat(a.get("situacao")).isEqualTo("Em aberto");
                assertThat(((Number) a.get("diasAtraso")).longValue()).isEqualTo(6);
            });
        assertThat(atrasos).filteredOn(a -> ((Number) a.get("emprestimoId")).longValue() == devolveTarde.getId())
            .singleElement().satisfies(a -> {
                assertThat(a.get("situacao")).isEqualTo("Devolvido com atraso");
                assertThat(((Number) a.get("diasAtraso")).longValue()).isEqualTo(4);
            });

        // 4) Mais emprestados: o Hobbit com 2 empréstimos no topo
        assertThat((String) JsonPath.read(r1, "$.maisEmprestados[0].titulo")).isEqualTo("O Hobbit");
        assertThat((Integer) JsonPath.read(r1, "$.maisEmprestados[0].emprestimos")).isEqualTo(2);

        // 5) Demandas mais pedidas e 6) transferências por situação
        List<String> demandas = JsonPath.read(r1, "$.demandasMaisPedidas[*].titulo");
        assertThat(demandas).contains("Torto Arado");
        assertThat(transf(r1, "EM_TRANSITO", "avulsas") - transf(r0, "EM_TRANSITO", "avulsas")).isEqualTo(1);
        assertThat(transf(r1, "EM_TRANSITO", "total") - transf(r0, "EM_TRANSITO", "total")).isEqualTo(1);

        // Período: amanhã em diante não tem nada novo; o atraso em aberto continua (é a situação atual)
        String amanha = java.time.LocalDate.now().plusDays(1).toString();
        String futuro = relatorios("?de=" + amanha);
        assertThat(num(futuro, central, "emprestimos", "realizados")).isZero();
        assertThat(num(futuro, central, "emprestimos", "devolvidos")).isZero();
        assertThat((List<?>) JsonPath.read(futuro, "$.maisEmprestados")).isEmpty();
        assertThat((List<?>) JsonPath.read(futuro, "$.demandasMaisPedidas")).isEmpty();
        assertThat(transf(futuro, "EM_TRANSITO", "total")).isZero();
        List<String> situacoes = JsonPath.read(futuro, "$.atrasos[*].situacao");
        assertThat(situacoes).containsOnly("Em aberto");
        assertThat(num(futuro, central, "acervo", "total")).isEqualTo(num(r1, central, "acervo", "total"));

        // Só o período do empréstimo de 20 dias atrás
        String dia = java.time.LocalDate.now().minusDays(20).toString();
        String antigo = relatorios("?de=" + dia + "&ate=" + dia);
        assertThat(num(antigo, central, "emprestimos", "realizados")).isEqualTo(1);

        chamar(HttpMethod.GET, "/api/admin/relatorios?de=" + amanha + "&ate=" + java.time.LocalDate.now(), tokenAdmin, null)
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Dashboard e relatórios só para o Admin")
    void permissoes() throws Exception {
        for (String url : List.of("/api/admin/dashboard", "/api/admin/relatorios")) {
            chamar(HttpMethod.GET, url, tokenFernanda, null).andExpect(status().isForbidden());
            chamar(HttpMethod.GET, url, tokenAna, null).andExpect(status().isForbidden());
            chamar(HttpMethod.GET, url, null, null).andExpect(status().isUnauthorized());
        }
    }

    private String relatorios(String query) throws Exception {
        return chamar(HttpMethod.GET, "/api/admin/relatorios" + query, tokenAdmin, null)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** Campo numérico da linha da biblioteca numa seção (0 se a biblioteca não aparece). */
    private static long num(String json, Biblioteca b, String secao, String campo) {
        List<Number> v = JsonPath.read(json, "$." + secao + "[?(@.bibliotecaId == " + b.getId() + ")]." + campo);
        return v.isEmpty() ? 0 : v.get(0).longValue();
    }

    private static long statusAcervo(String json, Biblioteca b, String status) {
        List<Number> v = JsonPath.read(json, "$.acervo[?(@.bibliotecaId == " + b.getId() + ")].porStatus." + status);
        return v.isEmpty() ? 0 : v.get(0).longValue();
    }

    private static long transf(String json, String status, String campo) {
        List<Number> v = JsonPath.read(json, "$.transferencias[?(@.status == '" + status + "')]." + campo);
        return v.isEmpty() ? 0 : v.get(0).longValue();
    }

    // ───────────────────────── Apoio ─────────────────────────

    /** KPIs numéricos + exemplares por status (chave = status), achatados num mapa. */
    private Map<String, Long> dashboard() throws Exception {
        String json = chamar(HttpMethod.GET, "/api/admin/dashboard", tokenAdmin, null)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, Object> raiz = JsonPath.read(json, "$");
        Map<String, Long> m = new HashMap<>();
        raiz.forEach((k, v) -> {
            if (v instanceof Number n) m.put(k, n.longValue());
        });
        List<Map<String, Object>> porStatus = JsonPath.read(json, "$.exemplaresPorStatus");
        for (Map<String, Object> s : porStatus) m.put((String) s.get("status"), ((Number) s.get("total")).longValue());
        return m;
    }

    /** Só o que mudou (depois - antes), sem zeros. */
    static Map<String, Long> delta(Map<String, Long> antes, Map<String, Long> depois) {
        Map<String, Long> d = new HashMap<>();
        depois.forEach((k, v) -> {
            long diff = v - antes.getOrDefault(k, 0L);
            if (diff != 0) d.put(k, diff);
        });
        return d;
    }

    private List<Long> cadastrar(Livro livro, int quantidade) throws Exception {
        String json = chamar(HttpMethod.POST, "/api/exemplares", tokenFernanda,
            "{\"livroId\":" + livro.getId() + ",\"conservacao\":\"BOM\",\"quantidade\":" + quantidade + "}")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(json, "$[*].id");
        return ids.stream().map(Integer::longValue).toList();
    }

    private long emprestar(long exemplarId, Usuario u) throws Exception {
        return id(chamar(HttpMethod.POST, "/api/emprestimos/registrar", tokenFernanda,
            "{\"exemplarId\":" + exemplarId + ",\"usuarioId\":" + u.getId() + "}")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void devolver(long emprestimoId) throws Exception {
        chamar(HttpMethod.POST, "/api/emprestimos/devolver", tokenFernanda,
               "{\"emprestimoId\":" + emprestimoId + ",\"condicaoExemplar\":\"BOM\"}")
            .andExpect(status().isOk());
    }
}
