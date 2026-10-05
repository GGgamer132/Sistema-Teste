package com.circulabook.controller;

import com.circulabook.model.*;
import com.circulabook.repository.NotificacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.circulabook.model.StatusExemplar.*;
import static com.circulabook.service.NotificacaoService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Notificações in-app (§6, RN20): cada evento gera aviso para os destinatários certos
 * (e nenhum outro), isolamento por usuário, contagem, marcar lida/todas e a rotina de
 * vencimento/atraso idempotente. U-15, B-07, B-20, A-20 e ES-13 na parte do backend.
 */
class NotificacoesTest extends ApoioApiTest {

    @Autowired private NotificacaoRepository notificacaoRepository;

    private Biblioteca central, vilaIsabel;
    private Usuario admin, rita, adminInativo, bibC, bibCInativo, bibVI, u1, u2, u3;
    private Livro hobbit;
    private Exemplar hobbitU1;
    private Emprestimo empHobbitU1;
    private Reserva r1U3;
    private SolicitacaoTransferencia t1;
    private String tokenAdmin, tokenBibC, tokenBibVI, tokenU1, tokenU3;

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    @BeforeEach
    void montar() throws Exception {
        central = biblioteca("Biblioteca Central");
        vilaIsabel = biblioteca("Biblioteca Vila Isabel");
        admin = usuario("Admin", "ADMIN", null);
        rita = usuario("Rita Admin", "ADMIN", null);
        adminInativo = inativo(usuario("Ze Inativo", "ADMIN", null));
        bibC = usuario("Bibliotecário Central", "BIBLIOTECARIO", central);
        bibCInativo = inativo(usuario("Flavia Inativa", "BIBLIOTECARIO", central));
        bibVI = usuario("Bibliotecário Vila Isabel", "BIBLIOTECARIO", vilaIsabel);
        u1 = usuario("Usuário 1", "COMUM", null);
        u2 = usuario("Usuário 2", "COMUM", null);
        u3 = usuario("Usuário 3", "COMUM", null);
        tokenAdmin = login(admin);
        tokenBibC = login(bibC);
        tokenBibVI = login(bibVI);
        tokenU1 = login(u1);
        tokenU3 = login(u3);

        // O Hobbit: 2 emprestados na Central; U3 na fila com retirada na VI (gera o pedido T1)
        hobbit = livro("O Hobbit");
        hobbitU1 = exemplar(hobbit, central, EMPRESTADO);
        Exemplar hobbitU2 = exemplar(hobbit, central, EMPRESTADO);
        empHobbitU1 = emprestimo(hobbitU1, u1, 3);
        emprestimo(hobbitU2, u2, 8);
        String json = chamar(HttpMethod.POST, "/api/reservas", tokenU3,
            "{\"livroId\":" + hobbit.getId() + ",\"bibliotecaFilaId\":" + central.getId()
            + ",\"bibliotecaDestinoId\":" + vilaIsabel.getId() + "}")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        r1U3 = reservaRepository.findById(id(json)).orElseThrow();
        t1 = transferenciaRepository.findAll().get(0);
    }

    // ───────────────────────── Eventos de transferência ─────────────────────────

    @Test
    @DisplayName("A-20: novo pedido avisa só os Admins ativos")
    void a20_novoPedido() {
        assertThat(tipos(admin)).containsExactly(TIPO_NOVO_PEDIDO);
        assertThat(tipos(rita)).containsExactly(TIPO_NOVO_PEDIDO);
        assertThat(ultima(admin).getLink()).isEqualTo("/admin/transferencias");
        assertThat(ultima(admin).getMensagem()).contains("Usuário 3", "O Hobbit", "Biblioteca Vila Isabel");
        semNotificacao(adminInativo, bibC, bibCInativo, bibVI, u1, u2, u3);
    }

    @Test
    @DisplayName("A-20: exemplar retido aguardando decisão avisa os Admins (urgente)")
    void a20_exemplarRetido() throws Exception {
        devolver(empHobbitU1, tokenBibC);
        assertThat(tipos(admin)).containsExactly(TIPO_NOVO_PEDIDO, TIPO_EXEMPLAR_RETIDO);
        assertThat(tipos(rita)).containsExactly(TIPO_NOVO_PEDIDO, TIPO_EXEMPLAR_RETIDO);
        assertThat(ultima(admin).getTitulo()).startsWith("Urgente");
        assertThat(ultima(admin).getMensagem()).contains("Exemplar nº " + hobbitU1.getId());
        semNotificacao(adminInativo, bibC, bibCInativo, bibVI, u3);
    }

    @Test
    @DisplayName("Ciclo aprovado: usuário (aprovado, a caminho, pronta); destino (a caminho, retirada); origem (envio)")
    void cicloAprovado() throws Exception {
        aprovar().andExpect(status().isOk());
        assertThat(tipos(u3)).containsExactly(TIPO_PEDIDO_APROVADO);

        devolver(empHobbitU1, tokenBibC);
        assertThat(tipos(u3)).containsExactly(TIPO_PEDIDO_APROVADO, TIPO_EXEMPLAR_A_CAMINHO);
        assertThat(tipos(bibVI)).containsExactly(TIPO_TRANSFERENCIA_A_CAMINHO);
        assertThat(ultima(bibVI).getMensagem()).contains("Confirme a chegada", "reservado para Usuário 3");
        assertThat(ultima(bibVI).getLink()).isEqualTo("/biblioteca/transferencias");
        assertThat(tipos(bibC)).containsExactly(TIPO_SEPARADO_PARA_ENVIO);
        assertThat(tipos(admin)).containsExactly(TIPO_NOVO_PEDIDO);
        semNotificacao(bibCInativo, adminInativo, u1);

        chamar(HttpMethod.PATCH, "/api/transferencias/" + t1.getId() + "/confirmar-chegada", tokenBibVI, null)
            .andExpect(status().isOk());
        assertThat(tipos(u3)).endsWith(TIPO_RESERVA_PRONTA);
        String prazo = r(r1U3).getDataExpiracao().format(DIA_MES);
        assertThat(ultima(u3).getMensagem()).contains("Biblioteca Vila Isabel", "Retire até " + prazo);
        assertThat(tipos(bibVI)).containsExactly(TIPO_TRANSFERENCIA_A_CAMINHO, TIPO_RETIRADA_NA_BIBLIOTECA);
        assertThat(ultima(bibVI).getMensagem()).contains("Usuário 3 virá buscar");
        assertThat(ultima(bibVI).getLink()).isEqualTo("/biblioteca/reservas");
        assertThat(tipos(bibC)).containsExactly(TIPO_SEPARADO_PARA_ENVIO);
    }

    @Test
    @DisplayName("Rejeitado sem exemplar: usuário avisado de que a retirada voltou para a fila")
    void rejeitadoSemExemplar() throws Exception {
        rejeitar().andExpect(status().isOk());
        assertThat(tipos(u3)).containsExactly(TIPO_PEDIDO_REJEITADO);
        assertThat(ultima(u3).getMensagem()).contains("a retirada voltou para a Biblioteca Central");
        semNotificacao(bibVI, bibC, u1);
    }

    @Test
    @DisplayName("Rejeitado com exemplar retido: usuário (rejeitado + pronta na origem) e bibliotecária da origem")
    void rejeitadoComRetido() throws Exception {
        devolver(empHobbitU1, tokenBibC);
        rejeitar().andExpect(status().isOk());
        assertThat(tipos(u3)).containsExactly(TIPO_PEDIDO_REJEITADO, TIPO_RESERVA_PRONTA);
        assertThat(ultima(u3).getMensagem()).contains("Biblioteca Central");
        assertThat(tipos(bibC)).containsExactly(TIPO_RETIRADA_NA_BIBLIOTECA);
        semNotificacao(bibVI, bibCInativo);
    }

    @Test
    @DisplayName("Pedido cancelado no despacho por capacidade da origem: Admins avisados; usuário retira na origem")
    void canceladoPorCapacidade() throws Exception {
        aprovar().andExpect(status().isOk());
        SolicitacaoTransferencia extra = new SolicitacaoTransferencia();
        extra.setLivro(hobbit);
        extra.setBibliotecaOrigem(central);
        extra.setBibliotecaDestino(vilaIsabel);
        extra.setSolicitante(u1);
        extra.setStatus("EM_TRANSITO");
        extra.setDataSolicitacao(LocalDateTime.now().minusDays(2));
        transferenciaRepository.save(extra);

        devolver(empHobbitU1, tokenBibC);
        assertThat(tipos(admin)).containsExactly(TIPO_NOVO_PEDIDO, TIPO_PEDIDO_CANCELADO_CAPACIDADE);
        assertThat(tipos(rita)).endsWith(TIPO_PEDIDO_CANCELADO_CAPACIDADE);
        assertThat(ultima(admin).getMensagem()).contains("ficaria sem nenhum exemplar");
        assertThat(tipos(u3)).containsExactly(TIPO_PEDIDO_APROVADO, TIPO_RESERVA_PRONTA);
        semNotificacao(bibVI);
    }

    @Test
    @DisplayName("Chegada danificada: a reserva volta à fila e o usuário é avisado (§4.4)")
    void chegadaDanificada() throws Exception {
        aprovar().andExpect(status().isOk());
        devolver(empHobbitU1, tokenBibC);
        chamar(HttpMethod.PATCH, "/api/transferencias/" + t1.getId() + "/confirmar-chegada", tokenBibVI,
               "{\"danificado\":true}").andExpect(status().isOk());
        assertThat(tipos(u3)).endsWith(TIPO_RESERVA_VOLTOU_FILA);
        assertThat(ultima(u3).getMensagem()).contains("chegou danificado", "fila da Biblioteca Central",
            "retirada será na Biblioteca Central");
        assertThat(tipos(bibVI)).containsExactly(TIPO_TRANSFERENCIA_A_CAMINHO);
    }

    // ───────────────────────── Reserva e empréstimo ─────────────────────────

    @Test
    @DisplayName("ES-13 / B-20 / expiração: pronta (usuário + bibliotecário), indisponível volta à fila, expirada")
    void reservaLocal() throws Exception {
        Livro harry = livro("Harry Potter");
        Exemplar h = exemplar(harry, vilaIsabel, EMPRESTADO);
        Emprestimo emp = emprestimo(h, u3, 4);
        chamar(HttpMethod.POST, "/api/reservas", tokenU1,
               "{\"livroId\":" + harry.getId() + ",\"bibliotecaFilaId\":" + vilaIsabel.getId()
               + ",\"bibliotecaDestinoId\":" + vilaIsabel.getId() + "}").andExpect(status().isOk());

        devolver(emp, tokenBibVI);                                      // B-10: separado para U1
        assertThat(tipos(u1)).containsExactly(TIPO_RESERVA_PRONTA);
        assertThat(tipos(bibVI)).containsExactly(TIPO_RETIRADA_NA_BIBLIOTECA);   // B-20
        assertThat(ultima(bibVI).getMensagem()).contains("Usuário 1 virá buscar \"Harry Potter\"");
        semNotificacao(bibC);

        // ES-13: BibVI marca o exemplar separado como indisponível
        chamar(HttpMethod.PATCH, "/api/exemplares/" + h.getId() + "/indisponivel", tokenBibVI,
               "{\"motivo\":\"capa rasgada\"}").andExpect(status().isOk());
        assertThat(tipos(u1)).containsExactly(TIPO_RESERVA_PRONTA, TIPO_RESERVA_VOLTOU_FILA);
        assertThat(ultima(u1).getMensagem()).contains("ficou indisponível", "sem perder a posição");

        // Reativado, volta a ficar pronta; depois expira
        chamar(HttpMethod.PATCH, "/api/exemplares/" + h.getId() + "/reativar", tokenBibVI, null)
            .andExpect(status().isOk());
        Reserva r = reservaRepository.findByUsuario(u1).get(0);
        r.setDataExpiracao(LocalDateTime.now().minusMinutes(1));
        reservaRepository.save(r);
        chamar(HttpMethod.POST, "/api/reservas/expirar-vencidas", tokenAdmin, null).andExpect(status().isOk());
        assertThat(tipos(u1)).containsExactly(TIPO_RESERVA_PRONTA, TIPO_RESERVA_VOLTOU_FILA,
            TIPO_RESERVA_PRONTA, TIPO_RESERVA_EXPIRADA);
    }

    @Test
    @DisplayName("B-07: devolução com 6 dias de atraso avisa o bloqueio de 12 dias com a data final")
    void b07_bloqueio() throws Exception {
        Livro duna = livro("Duna");
        Emprestimo emp = emprestimo(exemplar(duna, central, EMPRESTADO), u3, 20);
        devolver(emp, tokenBibC);
        assertThat(tipos(u3)).containsExactly(TIPO_BLOQUEIO);
        String fim = LocalDateTime.now().plusDays(12).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertThat(ultima(u3).getMensagem()).contains("6 dia(s) de atraso", "até " + fim);
        assertThat(ultima(u3).getLink()).isEqualTo("/meus-emprestimos");
        semNotificacao(u1, bibC);
    }

    @Test
    @DisplayName("Rotina de vencimento/atraso: avisa uma vez cada e é idempotente; só o Admin dispara")
    void rotinaIdempotente() throws Exception {
        Livro a = livro("Vence Logo"), b = livro("Atrasado"), c = livro("Em Dia");
        emprestimo(exemplar(a, central, EMPRESTADO), u2, 13);   // vence em 1 dia
        emprestimo(exemplar(b, central, EMPRESTADO), u1, 17);     // 3 dias de atraso
        emprestimo(exemplar(c, central, EMPRESTADO), u1, 1);      // vence em 13 dias

        chamar(HttpMethod.POST, "/api/notificacoes/verificar-vencimentos", tokenU1, null).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/notificacoes/verificar-vencimentos", tokenBibC, null).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/notificacoes/verificar-vencimentos", tokenAdmin, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.criadas").value(2));
        chamar(HttpMethod.POST, "/api/notificacoes/verificar-vencimentos", tokenAdmin, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.criadas").value(0));

        assertThat(tipos(u2)).containsExactly(TIPO_EMPRESTIMO_VENCE);
        assertThat(ultima(u2).getMensagem()).contains("\"Vence Logo\"");
        assertThat(tipos(u1)).containsExactly(TIPO_EMPRESTIMO_ATRASADO);
        assertThat(ultima(u1).getMensagem()).contains("\"Atrasado\"");
        semNotificacao(u3);
    }

    // ───────────────────────── API: isolamento, contagem, marcar ─────────────────────────

    @Test
    @DisplayName("U-15: lista só as do token (mais recentes primeiro), contagem, marcar lida e todas; de outro -> 404")
    void u15_api() throws Exception {
        aprovar().andExpect(status().isOk());
        devolver(empHobbitU1, tokenBibC);   // U3: aprovado + a caminho
        long deAdmin = ultima(admin).getId();

        chamar(HttpMethod.GET, "/api/notificacoes", null, null).andExpect(status().isUnauthorized());
        chamar(HttpMethod.GET, "/api/notificacoes?tamanho=1", tokenU3, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItens").value(2))
            .andExpect(jsonPath("$.totalPaginas").value(2))
            .andExpect(jsonPath("$.itens[0].tipo").value(TIPO_EXEMPLAR_A_CAMINHO))
            .andExpect(jsonPath("$.itens[0].lida").value(false))
            .andExpect(jsonPath("$.itens[0].link").value("/minhas-reservas"))
            .andExpect(jsonPath("$.itens[0].usuario").doesNotExist());
        chamar(HttpMethod.GET, "/api/notificacoes?tamanho=99", tokenU3, null).andExpect(status().isBadRequest());
        chamar(HttpMethod.GET, "/api/notificacoes", tokenU1, null)
            .andExpect(jsonPath("$.totalItens").value(0));
        contagem(tokenU3, 2);

        // Não marca a de outro usuário
        chamar(HttpMethod.PATCH, "/api/notificacoes/" + deAdmin + "/lida", tokenU3, null)
            .andExpect(status().isNotFound());
        assertThat(notificacaoRepository.findById(deAdmin).orElseThrow().getLida()).isFalse();
        chamar(HttpMethod.PATCH, "/api/notificacoes/999999/lida", tokenU3, null).andExpect(status().isNotFound());

        long minha = ultima(u3).getId();
        chamar(HttpMethod.PATCH, "/api/notificacoes/" + minha + "/lida", tokenU3, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.lida").value(true));
        contagem(tokenU3, 1);

        chamar(HttpMethod.PATCH, "/api/notificacoes/marcar-todas-lidas", tokenU3, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.marcadas").value(1));
        contagem(tokenU3, 0);
        // As do Admin continuam não lidas
        contagem(tokenAdmin, 1);
        chamar(HttpMethod.GET, "/api/notificacoes", tokenBibC, null)
            .andExpect(jsonPath("$.itens[*].tipo", contains(TIPO_SEPARADO_PARA_ENVIO)));
    }

    // ───────────────────────── Apoio ─────────────────────────

    private ResultActions aprovar() throws Exception {
        return chamar(HttpMethod.PATCH, "/api/transferencias/" + t1.getId() + "/aprovar", tokenAdmin, null);
    }

    private ResultActions rejeitar() throws Exception {
        return chamar(HttpMethod.PATCH, "/api/transferencias/" + t1.getId() + "/rejeitar", tokenAdmin, null);
    }

    private void devolver(Emprestimo emp, String token) throws Exception {
        chamar(HttpMethod.POST, "/api/emprestimos/devolver", token,
               "{\"emprestimoId\":" + emp.getId() + ",\"condicaoExemplar\":\"BOM\"}")
            .andExpect(status().isOk());
    }

    private void contagem(String token, long esperado) throws Exception {
        chamar(HttpMethod.GET, "/api/notificacoes/nao-lidas/contagem", token, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.naoLidas").value((int) esperado));
    }

    private List<String> tipos(Usuario u) {
        return notificacaoRepository.findByUsuarioIdOrderByIdAsc(u.getId()).stream().map(Notificacao::getTipo).toList();
    }

    private Notificacao ultima(Usuario u) {
        List<Notificacao> l = notificacaoRepository.findByUsuarioIdOrderByIdAsc(u.getId());
        assertThat(l).isNotEmpty();
        return l.get(l.size() - 1);
    }

    private void semNotificacao(Usuario... usuarios) {
        for (Usuario u : usuarios) {
            assertThat(tipos(u)).as("notificações de " + u.getNome()).isEmpty();
        }
    }

    private Usuario inativo(Usuario u) {
        u.setAtivo(false);
        return usuarioRepository.save(u);
    }

    private Reserva r(Reserva r) {
        return reservaRepository.findById(r.getId()).orElseThrow();
    }
}
