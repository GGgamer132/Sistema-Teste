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
    private Usuario roberto, rita, adminInativo, fernanda, fernandaInativa, carlos, ana, bruno, camila;
    private Livro hobbit;
    private Exemplar hobbitAna;
    private Emprestimo empHobbitAna;
    private Reserva r1Camila;
    private SolicitacaoTransferencia t1;
    private String tokenAdmin, tokenFernanda, tokenCarlos, tokenAna, tokenCamila;

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    @BeforeEach
    void montar() throws Exception {
        central = biblioteca("Biblioteca Central");
        vilaIsabel = biblioteca("Biblioteca Vila Isabel");
        roberto = usuario("Roberto Dias", "ADMIN", null);
        rita = usuario("Rita Admin", "ADMIN", null);
        adminInativo = inativo(usuario("Ze Inativo", "ADMIN", null));
        fernanda = usuario("Fernanda Reis", "BIBLIOTECARIO", central);
        fernandaInativa = inativo(usuario("Flavia Inativa", "BIBLIOTECARIO", central));
        carlos = usuario("Carlos Lima", "BIBLIOTECARIO", vilaIsabel);
        ana = usuario("Ana Souza", "COMUM", null);
        bruno = usuario("Bruno Alves", "COMUM", null);
        camila = usuario("Camila Duarte", "COMUM", null);
        tokenAdmin = login(roberto);
        tokenFernanda = login(fernanda);
        tokenCarlos = login(carlos);
        tokenAna = login(ana);
        tokenCamila = login(camila);

        // O Hobbit: 2 emprestados na Central; Camila na fila com retirada na VI (gera o pedido T1)
        hobbit = livro("O Hobbit");
        hobbitAna = exemplar(hobbit, central, EMPRESTADO);
        Exemplar hobbitBruno = exemplar(hobbit, central, EMPRESTADO);
        empHobbitAna = emprestimo(hobbitAna, ana, 3);
        emprestimo(hobbitBruno, bruno, 8);
        String json = chamar(HttpMethod.POST, "/api/reservas", tokenCamila,
            "{\"livroId\":" + hobbit.getId() + ",\"bibliotecaFilaId\":" + central.getId()
            + ",\"bibliotecaDestinoId\":" + vilaIsabel.getId() + "}")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        r1Camila = reservaRepository.findById(id(json)).orElseThrow();
        t1 = transferenciaRepository.findAll().get(0);
    }

    // ───────────────────────── Eventos de transferência ─────────────────────────

    @Test
    @DisplayName("A-20: novo pedido avisa só os Admins ativos")
    void a20_novoPedido() {
        assertThat(tipos(roberto)).containsExactly(TIPO_NOVO_PEDIDO);
        assertThat(tipos(rita)).containsExactly(TIPO_NOVO_PEDIDO);
        assertThat(ultima(roberto).getLink()).isEqualTo("/admin/transferencias");
        assertThat(ultima(roberto).getMensagem()).contains("Camila Duarte", "O Hobbit", "Biblioteca Vila Isabel");
        semNotificacao(adminInativo, fernanda, fernandaInativa, carlos, ana, bruno, camila);
    }

    @Test
    @DisplayName("A-20: exemplar retido aguardando decisão avisa os Admins (urgente)")
    void a20_exemplarRetido() throws Exception {
        devolver(empHobbitAna, tokenFernanda);
        assertThat(tipos(roberto)).containsExactly(TIPO_NOVO_PEDIDO, TIPO_EXEMPLAR_RETIDO);
        assertThat(tipos(rita)).containsExactly(TIPO_NOVO_PEDIDO, TIPO_EXEMPLAR_RETIDO);
        assertThat(ultima(roberto).getTitulo()).startsWith("Urgente");
        assertThat(ultima(roberto).getMensagem()).contains("Exemplar nº " + hobbitAna.getId());
        semNotificacao(adminInativo, fernanda, fernandaInativa, carlos, camila);
    }

    @Test
    @DisplayName("Ciclo aprovado: usuário (aprovado, a caminho, pronta); destino (a caminho, retirada); origem (envio)")
    void cicloAprovado() throws Exception {
        aprovar().andExpect(status().isOk());
        assertThat(tipos(camila)).containsExactly(TIPO_PEDIDO_APROVADO);

        devolver(empHobbitAna, tokenFernanda);
        assertThat(tipos(camila)).containsExactly(TIPO_PEDIDO_APROVADO, TIPO_EXEMPLAR_A_CAMINHO);
        assertThat(tipos(carlos)).containsExactly(TIPO_TRANSFERENCIA_A_CAMINHO);
        assertThat(ultima(carlos).getMensagem()).contains("Confirme a chegada", "reservado para Camila Duarte");
        assertThat(ultima(carlos).getLink()).isEqualTo("/biblioteca/transferencias");
        assertThat(tipos(fernanda)).containsExactly(TIPO_SEPARADO_PARA_ENVIO);
        assertThat(tipos(roberto)).containsExactly(TIPO_NOVO_PEDIDO);
        semNotificacao(fernandaInativa, adminInativo, ana);

        chamar(HttpMethod.PATCH, "/api/transferencias/" + t1.getId() + "/confirmar-chegada", tokenCarlos, null)
            .andExpect(status().isOk());
        assertThat(tipos(camila)).endsWith(TIPO_RESERVA_PRONTA);
        String prazo = r(r1Camila).getDataExpiracao().format(DIA_MES);
        assertThat(ultima(camila).getMensagem()).contains("Biblioteca Vila Isabel", "Retire até " + prazo);
        assertThat(tipos(carlos)).containsExactly(TIPO_TRANSFERENCIA_A_CAMINHO, TIPO_RETIRADA_NA_BIBLIOTECA);
        assertThat(ultima(carlos).getMensagem()).contains("Camila Duarte virá buscar");
        assertThat(ultima(carlos).getLink()).isEqualTo("/biblioteca/reservas");
        assertThat(tipos(fernanda)).containsExactly(TIPO_SEPARADO_PARA_ENVIO);
    }

    @Test
    @DisplayName("Rejeitado sem exemplar: usuário avisado de que a retirada voltou para a fila")
    void rejeitadoSemExemplar() throws Exception {
        rejeitar().andExpect(status().isOk());
        assertThat(tipos(camila)).containsExactly(TIPO_PEDIDO_REJEITADO);
        assertThat(ultima(camila).getMensagem()).contains("a retirada voltou para a Biblioteca Central");
        semNotificacao(carlos, fernanda, ana);
    }

    @Test
    @DisplayName("Rejeitado com exemplar retido: usuário (rejeitado + pronta na origem) e bibliotecária da origem")
    void rejeitadoComRetido() throws Exception {
        devolver(empHobbitAna, tokenFernanda);
        rejeitar().andExpect(status().isOk());
        assertThat(tipos(camila)).containsExactly(TIPO_PEDIDO_REJEITADO, TIPO_RESERVA_PRONTA);
        assertThat(ultima(camila).getMensagem()).contains("Biblioteca Central");
        assertThat(tipos(fernanda)).containsExactly(TIPO_RETIRADA_NA_BIBLIOTECA);
        semNotificacao(carlos, fernandaInativa);
    }

    @Test
    @DisplayName("Pedido cancelado no despacho por capacidade da origem: Admins avisados; usuário retira na origem")
    void canceladoPorCapacidade() throws Exception {
        aprovar().andExpect(status().isOk());
        SolicitacaoTransferencia extra = new SolicitacaoTransferencia();
        extra.setLivro(hobbit);
        extra.setBibliotecaOrigem(central);
        extra.setBibliotecaDestino(vilaIsabel);
        extra.setSolicitante(ana);
        extra.setStatus("EM_TRANSITO");
        extra.setDataSolicitacao(LocalDateTime.now().minusDays(2));
        transferenciaRepository.save(extra);

        devolver(empHobbitAna, tokenFernanda);
        assertThat(tipos(roberto)).containsExactly(TIPO_NOVO_PEDIDO, TIPO_PEDIDO_CANCELADO_CAPACIDADE);
        assertThat(tipos(rita)).endsWith(TIPO_PEDIDO_CANCELADO_CAPACIDADE);
        assertThat(ultima(roberto).getMensagem()).contains("ficaria sem nenhum exemplar");
        assertThat(tipos(camila)).containsExactly(TIPO_PEDIDO_APROVADO, TIPO_RESERVA_PRONTA);
        semNotificacao(carlos);
    }

    @Test
    @DisplayName("Chegada danificada: a reserva volta à fila e o usuário é avisado (§4.4)")
    void chegadaDanificada() throws Exception {
        aprovar().andExpect(status().isOk());
        devolver(empHobbitAna, tokenFernanda);
        chamar(HttpMethod.PATCH, "/api/transferencias/" + t1.getId() + "/confirmar-chegada", tokenCarlos,
               "{\"danificado\":true}").andExpect(status().isOk());
        assertThat(tipos(camila)).endsWith(TIPO_RESERVA_VOLTOU_FILA);
        assertThat(ultima(camila).getMensagem()).contains("chegou danificado", "fila da Biblioteca Central",
            "retirada será na Biblioteca Central");
        assertThat(tipos(carlos)).containsExactly(TIPO_TRANSFERENCIA_A_CAMINHO);
    }

    // ───────────────────────── Reserva e empréstimo ─────────────────────────

    @Test
    @DisplayName("ES-13 / B-20 / expiração: pronta (usuário + bibliotecário), indisponível volta à fila, expirada")
    void reservaLocal() throws Exception {
        Livro harry = livro("Harry Potter");
        Exemplar h = exemplar(harry, vilaIsabel, EMPRESTADO);
        Emprestimo emp = emprestimo(h, camila, 4);
        chamar(HttpMethod.POST, "/api/reservas", tokenAna,
               "{\"livroId\":" + harry.getId() + ",\"bibliotecaFilaId\":" + vilaIsabel.getId()
               + ",\"bibliotecaDestinoId\":" + vilaIsabel.getId() + "}").andExpect(status().isOk());

        devolver(emp, tokenCarlos);                                      // B-10: separado para Ana
        assertThat(tipos(ana)).containsExactly(TIPO_RESERVA_PRONTA);
        assertThat(tipos(carlos)).containsExactly(TIPO_RETIRADA_NA_BIBLIOTECA);   // B-20
        assertThat(ultima(carlos).getMensagem()).contains("Ana Souza virá buscar \"Harry Potter\"");
        semNotificacao(fernanda);

        // ES-13: Carlos marca o exemplar separado como indisponível
        chamar(HttpMethod.PATCH, "/api/exemplares/" + h.getId() + "/indisponivel", tokenCarlos,
               "{\"motivo\":\"capa rasgada\"}").andExpect(status().isOk());
        assertThat(tipos(ana)).containsExactly(TIPO_RESERVA_PRONTA, TIPO_RESERVA_VOLTOU_FILA);
        assertThat(ultima(ana).getMensagem()).contains("ficou indisponível", "sem perder a posição");

        // Reativado, volta a ficar pronta; depois expira
        chamar(HttpMethod.PATCH, "/api/exemplares/" + h.getId() + "/reativar", tokenCarlos, null)
            .andExpect(status().isOk());
        Reserva r = reservaRepository.findByUsuario(ana).get(0);
        r.setDataExpiracao(LocalDateTime.now().minusMinutes(1));
        reservaRepository.save(r);
        chamar(HttpMethod.POST, "/api/reservas/expirar-vencidas", tokenAdmin, null).andExpect(status().isOk());
        assertThat(tipos(ana)).containsExactly(TIPO_RESERVA_PRONTA, TIPO_RESERVA_VOLTOU_FILA,
            TIPO_RESERVA_PRONTA, TIPO_RESERVA_EXPIRADA);
    }

    @Test
    @DisplayName("B-07: devolução com 6 dias de atraso avisa o bloqueio de 12 dias com a data final")
    void b07_bloqueio() throws Exception {
        Livro duna = livro("Duna");
        Emprestimo emp = emprestimo(exemplar(duna, central, EMPRESTADO), camila, 20);
        devolver(emp, tokenFernanda);
        assertThat(tipos(camila)).containsExactly(TIPO_BLOQUEIO);
        String fim = LocalDateTime.now().plusDays(12).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertThat(ultima(camila).getMensagem()).contains("6 dia(s) de atraso", "até " + fim);
        assertThat(ultima(camila).getLink()).isEqualTo("/meus-emprestimos");
        semNotificacao(ana, fernanda);
    }

    @Test
    @DisplayName("Rotina de vencimento/atraso: avisa uma vez cada e é idempotente; só o Admin dispara")
    void rotinaIdempotente() throws Exception {
        Livro a = livro("Vence Logo"), b = livro("Atrasado"), c = livro("Em Dia");
        emprestimo(exemplar(a, central, EMPRESTADO), bruno, 13);   // vence em 1 dia
        emprestimo(exemplar(b, central, EMPRESTADO), ana, 17);     // 3 dias de atraso
        emprestimo(exemplar(c, central, EMPRESTADO), ana, 1);      // vence em 13 dias

        chamar(HttpMethod.POST, "/api/notificacoes/verificar-vencimentos", tokenAna, null).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/notificacoes/verificar-vencimentos", tokenFernanda, null).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/notificacoes/verificar-vencimentos", tokenAdmin, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.criadas").value(2));
        chamar(HttpMethod.POST, "/api/notificacoes/verificar-vencimentos", tokenAdmin, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.criadas").value(0));

        assertThat(tipos(bruno)).containsExactly(TIPO_EMPRESTIMO_VENCE);
        assertThat(ultima(bruno).getMensagem()).contains("\"Vence Logo\"");
        assertThat(tipos(ana)).containsExactly(TIPO_EMPRESTIMO_ATRASADO);
        assertThat(ultima(ana).getMensagem()).contains("\"Atrasado\"");
        semNotificacao(camila);
    }

    // ───────────────────────── API: isolamento, contagem, marcar ─────────────────────────

    @Test
    @DisplayName("U-15: lista só as do token (mais recentes primeiro), contagem, marcar lida e todas; de outro -> 404")
    void u15_api() throws Exception {
        aprovar().andExpect(status().isOk());
        devolver(empHobbitAna, tokenFernanda);   // Camila: aprovado + a caminho
        long deRoberto = ultima(roberto).getId();

        chamar(HttpMethod.GET, "/api/notificacoes", null, null).andExpect(status().isUnauthorized());
        chamar(HttpMethod.GET, "/api/notificacoes?tamanho=1", tokenCamila, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItens").value(2))
            .andExpect(jsonPath("$.totalPaginas").value(2))
            .andExpect(jsonPath("$.itens[0].tipo").value(TIPO_EXEMPLAR_A_CAMINHO))
            .andExpect(jsonPath("$.itens[0].lida").value(false))
            .andExpect(jsonPath("$.itens[0].link").value("/minhas-reservas"))
            .andExpect(jsonPath("$.itens[0].usuario").doesNotExist());
        chamar(HttpMethod.GET, "/api/notificacoes?tamanho=99", tokenCamila, null).andExpect(status().isBadRequest());
        chamar(HttpMethod.GET, "/api/notificacoes", tokenAna, null)
            .andExpect(jsonPath("$.totalItens").value(0));
        contagem(tokenCamila, 2);

        // Não marca a de outro usuário
        chamar(HttpMethod.PATCH, "/api/notificacoes/" + deRoberto + "/lida", tokenCamila, null)
            .andExpect(status().isNotFound());
        assertThat(notificacaoRepository.findById(deRoberto).orElseThrow().getLida()).isFalse();
        chamar(HttpMethod.PATCH, "/api/notificacoes/999999/lida", tokenCamila, null).andExpect(status().isNotFound());

        long minha = ultima(camila).getId();
        chamar(HttpMethod.PATCH, "/api/notificacoes/" + minha + "/lida", tokenCamila, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.lida").value(true));
        contagem(tokenCamila, 1);

        chamar(HttpMethod.PATCH, "/api/notificacoes/marcar-todas-lidas", tokenCamila, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.marcadas").value(1));
        contagem(tokenCamila, 0);
        // As do Admin continuam não lidas
        contagem(tokenAdmin, 1);
        chamar(HttpMethod.GET, "/api/notificacoes", tokenFernanda, null)
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
