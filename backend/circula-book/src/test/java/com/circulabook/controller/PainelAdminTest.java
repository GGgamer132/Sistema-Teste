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
    @DisplayName("Dashboard só para o Admin")
    void permissoes() throws Exception {
        chamar(HttpMethod.GET, "/api/admin/dashboard", tokenFernanda, null).andExpect(status().isForbidden());
        chamar(HttpMethod.GET, "/api/admin/dashboard", tokenAna, null).andExpect(status().isForbidden());
        chamar(HttpMethod.GET, "/api/admin/dashboard", null, null).andExpect(status().isUnauthorized());
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
