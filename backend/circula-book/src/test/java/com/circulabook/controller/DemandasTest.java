package com.circulabook.controller;

import com.circulabook.model.DemandaAquisicao;
import com.circulabook.model.Notificacao;
import com.circulabook.model.Usuario;
import com.circulabook.repository.DemandaAquisicaoRepository;
import com.circulabook.repository.NotificacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static com.circulabook.service.NotificacaoService.TIPO_NOVA_DEMANDA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Demandas de aquisição (RN07, U8, A9): U-14, transições, permissões e aviso ao Admin. */
class DemandasTest extends ApoioApiTest {

    @Autowired private DemandaAquisicaoRepository demandaRepository;
    @Autowired private NotificacaoRepository notificacaoRepository;

    private Usuario admin, rita, u1, u2, u3;
    private String tokenAdmin, tokenBibVI, tokenU1, tokenU2, tokenU3;

    @BeforeEach
    void montar() throws Exception {
        admin = usuario("Admin", "ADMIN", null);
        rita = usuario("Rita Admin", "ADMIN", null);
        Usuario bibVI = usuario("BibliotecÃ¡rio Vila Isabel", "BIBLIOTECARIO", biblioteca("Biblioteca Vila Isabel"));
        u1 = usuario("UsuÃ¡rio 1", "COMUM", null);
        u2 = usuario("UsuÃ¡rio 2", "COMUM", null);
        u3 = usuario("UsuÃ¡rio 3", "COMUM", null);
        tokenAdmin = login(admin);
        tokenBibVI = login(bibVI);
        tokenU1 = login(u1);
        tokenU2 = login(u2);
        tokenU3 = login(u3);
    }

    @Test
    @DisplayName("U-14: cria ABERTA com 1; outro usuário incrementa (caixa/acento/espaço); mesmo usuário barrado")
    void u14_consolidacao() throws Exception {
        interesse(tokenU1, "Torto Arado", "Itamar Vieira Junior")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nova").value(true))
            .andExpect(jsonPath("$.demanda.status").value("ABERTA"))
            .andExpect(jsonPath("$.demanda.totalSolicitacoes").value(1));

        // U2 escreve diferente: caixa, acento e espaços extras
        interesse(tokenU2, "  TORTO   árado ", "itamar  vieira júnior")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nova").value(false))
            .andExpect(jsonPath("$.demanda.totalSolicitacoes").value(2))
            .andExpect(jsonPath("$.mensagem").value(containsString("2 pessoas")));

        // U1 de novo (também escrito diferente): barrada, contador não muda
        interesse(tokenU1, "torto arado", "ITAMAR VIEIRA JUNIOR")
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Você já registrou interesse neste livro."));

        List<DemandaAquisicao> todas = demandaRepository.findAll();
        assertThat(todas).hasSize(1);
        assertThat(todas.get(0).getTotalSolicitacoes()).isEqualTo(2);
        assertThat(todas.get(0).getTitulo()).isEqualTo("Torto Arado");

        chamar(HttpMethod.GET, "/api/demandas/minhas", tokenU2, null)
            .andExpect(jsonPath("$[0].titulo").value("Torto Arado"))
            .andExpect(jsonPath("$[0].statusRotulo").value("Aberta"));
        chamar(HttpMethod.GET, "/api/demandas/minhas", tokenU3, null).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Incremento vale em qualquer situação e não muda a situação")
    void incrementoEmOutraSituacao() throws Exception {
        long id = demandaId(interesse(tokenU1, "Vidas Secas", "Graciliano Ramos"));
        mudarStatus(id, "EM_ANALISE").andExpect(status().isOk());
        mudarStatus(id, "APROVADA").andExpect(status().isOk());
        interesse(tokenU2, "vidas secas", "graciliano ramos")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.demanda.status").value("APROVADA"))
            .andExpect(jsonPath("$.demanda.totalSolicitacoes").value(2));
    }

    @Test
    @DisplayName("Campos obrigatórios")
    void validacao() throws Exception {
        interesse(tokenU1, "  ", "Autor").andExpect(status().isBadRequest())
            .andExpect(content().string("Informe o título do livro."));
        interesse(tokenU1, "Título", null).andExpect(status().isBadRequest())
            .andExpect(content().string("Informe o autor do livro."));
        assertThat(demandaRepository.count()).isZero();
    }

    @Test
    @DisplayName("A-17: transições válidas e inválidas, listagem por mais pedidas com filtro")
    void a17_transicoes() throws Exception {
        long a = demandaId(interesse(tokenU1, "A Hora da Estrela", "Clarice Lispector"));
        long b = demandaId(interesse(tokenU1, "Ensaio sobre a Cegueira", "José Saramago"));
        interesse(tokenU2, "Ensaio sobre a cegueira", "Jose Saramago").andExpect(status().isOk());
        interesse(tokenU3, "ENSAIO SOBRE A CEGUEIRA", "josé saramago").andExpect(status().isOk());

        chamar(HttpMethod.GET, "/api/demandas", tokenAdmin, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItens").value(2))
            .andExpect(jsonPath("$.itens[0].id").value(b))          // mais pedida primeiro
            .andExpect(jsonPath("$.itens[0].totalSolicitacoes").value(3))
            .andExpect(jsonPath("$.itens[0].proximosStatus", contains("EM_ANALISE")));

        mudarStatus(a, "APROVADA").andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Não é possível passar de Aberta para Aprovada")));
        mudarStatus(a, "XYZ").andExpect(status().isBadRequest());
        mudarStatus(a, "EM_ANALISE").andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("EM_ANALISE"))
            .andExpect(jsonPath("$.proximosStatus", containsInAnyOrder("APROVADA", "REJEITADA")));
        mudarStatus(a, "ABERTA").andExpect(status().isBadRequest());
        mudarStatus(a, "REJEITADA").andExpect(status().isOk());
        mudarStatus(a, "EM_ANALISE").andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("não muda mais de situação")));
        mudarStatus(999999, "EM_ANALISE").andExpect(status().isBadRequest());

        chamar(HttpMethod.GET, "/api/demandas?status=REJEITADA", tokenAdmin, null)
            .andExpect(jsonPath("$.totalItens").value(1))
            .andExpect(jsonPath("$.itens[0].statusRotulo").value("Rejeitada"));
        chamar(HttpMethod.GET, "/api/demandas?status=OUTRO", tokenAdmin, null).andExpect(status().isBadRequest());
        chamar(HttpMethod.GET, "/api/demandas?tamanho=1&pagina=1", tokenAdmin, null)
            .andExpect(jsonPath("$.itens[0].id").value(a)).andExpect(jsonPath("$.totalPaginas").value(2));
    }

    @Test
    @DisplayName("Permissões: só COMUM registra; só ADMIN lista e decide")
    void permissoes() throws Exception {
        interesse(tokenAdmin, "X", "Y").andExpect(status().isForbidden());
        interesse(tokenBibVI, "X", "Y").andExpect(status().isForbidden());
        interesse(null, "X", "Y").andExpect(status().isUnauthorized());
        long id = demandaId(interesse(tokenU1, "X", "Y"));
        for (String t : new String[]{tokenU1, tokenBibVI}) {
            chamar(HttpMethod.GET, "/api/demandas", t, null).andExpect(status().isForbidden());
            mudarStatus(id, "EM_ANALISE", t).andExpect(status().isForbidden());
        }
        chamar(HttpMethod.GET, "/api/demandas/minhas", tokenAdmin, null).andExpect(status().isForbidden());
        chamar(HttpMethod.GET, "/api/demandas/minhas", tokenBibVI, null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admins ativos são avisados só na criação da demanda, não nos incrementos")
    void notificacaoSoNaCriacao() throws Exception {
        rita.setAtivo(false);
        usuarioRepository.save(rita);
        interesse(tokenU1, "Torto Arado", "Itamar Vieira Junior").andExpect(status().isOk());
        interesse(tokenU2, "Torto Arado", "Itamar Vieira Junior").andExpect(status().isOk());
        interesse(tokenU3, "torto arado", "itamar vieira junior").andExpect(status().isOk());

        List<Notificacao> doAdmin = notificacaoRepository.findByUsuarioIdOrderByIdAsc(admin.getId());
        assertThat(doAdmin).extracting(Notificacao::getTipo).containsExactly(TIPO_NOVA_DEMANDA);
        assertThat(doAdmin.get(0).getMensagem()).contains("UsuÃ¡rio 1", "Torto Arado", "Itamar Vieira Junior");
        assertThat(doAdmin.get(0).getLink()).isEqualTo("/admin/demandas");
        assertThat(notificacaoRepository.findByUsuarioIdOrderByIdAsc(rita.getId())).isEmpty();
        assertThat(notificacaoRepository.findByUsuarioIdOrderByIdAsc(u1.getId())).isEmpty();
    }

    // ───────────────────────── Apoio ─────────────────────────

    private ResultActions interesse(String token, String titulo, String autor) throws Exception {
        String corpo = "{\"titulo\":" + (titulo == null ? "null" : "\"" + titulo + "\"")
            + ",\"autor\":" + (autor == null ? "null" : "\"" + autor + "\"") + "}";
        return chamar(HttpMethod.POST, "/api/demandas", token, corpo);
    }

    private ResultActions mudarStatus(long id, String status) throws Exception {
        return mudarStatus(id, status, tokenAdmin);
    }

    private ResultActions mudarStatus(long id, String status, String token) throws Exception {
        return chamar(HttpMethod.PATCH, "/api/demandas/" + id + "/status", token, "{\"status\":\"" + status + "\"}");
    }

    private long demandaId(ResultActions r) throws Exception {
        String json = r.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll(".*\"demanda\":\\{\"id\":(\\d+).*", "$1"));
    }
}
