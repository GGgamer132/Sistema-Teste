package com.circulabook.controller;

import com.circulabook.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static com.circulabook.model.StatusExemplar.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ciclo completo de transferências (§5.2 a §5.4, RN15, RN16, RN22, T8–T11, §4.4)
 * pela API real com JWT. Roteiros A-03..A-11, ES-06, ES-09, ES-10, ES-14, B-10, B-12, B-13
 * e U-12 completa. A invariante 4.2 é conferida após cada operação.
 */
class TransferenciasTest extends ApoioApiTest {

    private Biblioteca central, vilaIsabel, tijuca;
    private Usuario u1, u2, u3, u4;
    private Livro hobbit, livro1984, domCasmurro, fahrenheit, harry;
    private Exemplar hobbitU1, hobbitU2, harryU3;
    private Emprestimo empHobbitU1, empHarryU3;
    private Reserva r1U3;
    private SolicitacaoTransferencia t1;
    private String tokenAdmin, tokenBibC, tokenBibVI, tokenU1, tokenU2, tokenU3, tokenU4;

    @BeforeEach
    void montar() throws Exception {
        central = biblioteca("Biblioteca Central");
        vilaIsabel = biblioteca("Biblioteca Vila Isabel");
        tijuca = biblioteca("Biblioteca Tijuca"); // sem bibliotecário (RN22)
        Usuario admin = usuario("Admin", "ADMIN", null);
        Usuario bibC = usuario("Bibliotecário Central", "BIBLIOTECARIO", central);
        Usuario bibVI = usuario("Bibliotecário Vila Isabel", "BIBLIOTECARIO", vilaIsabel);
        u1 = usuario("Usuário 1", "COMUM", null);
        u2 = usuario("Usuário 2", "COMUM", null);
        u3 = usuario("Usuário 3", "COMUM", null);
        u4 = usuario("Usuário 4", "COMUM", null);
        tokenAdmin = login(admin);
        tokenBibC = login(bibC);
        tokenBibVI = login(bibVI);
        tokenU1 = login(u1);
        tokenU2 = login(u2);
        tokenU3 = login(u3);
        tokenU4 = login(u4);

        // O Hobbit: 2 emprestados na Central (U1, U2); U3 entra na fila com retirada na VI (R1 + T1)
        hobbit = livro("O Hobbit");
        hobbitU1 = exemplar(hobbit, central, EMPRESTADO);
        hobbitU2 = exemplar(hobbit, central, EMPRESTADO);
        empHobbitU1 = emprestimo(hobbitU1, u1, 3);
        emprestimo(hobbitU2, u2, 8);
        r1U3 = reservaPorApi(tokenU3, hobbit, central, vilaIsabel);
        t1 = pedidoDa(r1U3);

        // 1984: 2 disponíveis na Central, 1 na VI; Dom Casmurro: 3 na Central, 2 na VI; Fahrenheit: 2 na Central
        livro1984 = livro("1984");
        exemplar(livro1984, central, DISPONIVEL);
        exemplar(livro1984, central, DISPONIVEL);
        exemplar(livro1984, vilaIsabel, DISPONIVEL);
        domCasmurro = livro("Dom Casmurro");
        for (int i = 0; i < 3; i++) exemplar(domCasmurro, central, DISPONIVEL);
        exemplar(domCasmurro, vilaIsabel, DISPONIVEL);
        exemplar(domCasmurro, vilaIsabel, DISPONIVEL);
        fahrenheit = livro("Fahrenheit 451");
        exemplar(fahrenheit, central, DISPONIVEL);
        exemplar(fahrenheit, central, DISPONIVEL);

        // Harry Potter na VI: emprestado à U3, U2 na fila (retirada na VI)
        harry = livro("Harry Potter e a Pedra Filosofal");
        harryU3 = exemplar(harry, vilaIsabel, EMPRESTADO);
        empHarryU3 = emprestimo(harryU3, u3, 4);
        reservaPorApi(tokenU2, harry, vilaIsabel, vilaIsabel);
        consistente();
    }

    // ───────────────────────── Decisão do Admin ─────────────────────────

    @Test
    @DisplayName("A-02 / A-03: pendente sem exemplar mostra o título e a RN15; aprovar -> APROVADA aguardando exemplar")
    void a03_aprovarSemExemplar() throws Exception {
        chamar(HttpMethod.GET, "/api/transferencias/pedidos-pendentes", tokenAdmin, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].titulo").value("O Hobbit"))
            .andExpect(jsonPath("$[0].exemplarId").value(nullValue()))
            .andExpect(jsonPath("$[0].exemplar").value("VINCULADO_NA_DEVOLUCAO"))
            .andExpect(jsonPath("$[0].solicitante.nome").value("Usuário 3"))
            .andExpect(jsonPath("$[0].origem.nome").value("Biblioteca Central"))
            .andExpect(jsonPath("$[0].destino.nome").value("Biblioteca Vila Isabel"))
            .andExpect(jsonPath("$[0].rn15.permitido").value(true))
            .andExpect(jsonPath("$[0].rn15.total").value(2))
            .andExpect(jsonPath("$[0].rn15.abertas").value(0));

        aprovar(t1, tokenAdmin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APROVADA"));
        assertThat(t(t1).getExemplar()).isNull();
        assertThat(r(r1U3).getStatus()).isEqualTo("PENDENTE");
        chamar(HttpMethod.GET, "/api/transferencias/acompanhamento?status=APROVADA_AGUARDANDO_EXEMPLAR", tokenAdmin, null)
            .andExpect(jsonPath("$.totalItens").value(1))
            .andExpect(jsonPath("$.itens[0].etapa").value("APROVADA_AGUARDANDO_EXEMPLAR"))
            .andExpect(jsonPath("$.itens[0].reservadoPara").value("Usuário 3"));
        consistente();
    }

    @Test
    @DisplayName("A-04: rejeitar sem exemplar -> REJEITADA; reserva segue PENDENTE com retirada na origem")
    void a04_rejeitarSemExemplar() throws Exception {
        chamar(HttpMethod.PATCH, "/api/transferencias/" + t1.getId() + "/rejeitar?motivo=Sem%20malote", tokenAdmin, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJEITADA"));
        Reserva r = r(r1U3);
        assertThat(r.getStatus()).isEqualTo("PENDENTE");
        assertThat(r.getBibliotecaDestino().getId()).isEqualTo(central.getId());
        assertThat(situacao(hobbitU2)).isEqualTo(EMPRESTADO_RESERVADO);
        consistente();
    }

    @Test
    @DisplayName("ES-06 / A-05: devolução com pedido PENDENTE retém o exemplar; aprovar envia direto (T9)")
    void es06_a05_retidoEAprovado() throws Exception {
        devolver(empHobbitU1, tokenBibC);
        assertThat(situacao(hobbitU1)).isEqualTo(RESERVADO);                         // T4, retido
        assertThat(t(t1).getExemplar().getId()).isEqualTo(hobbitU1.getId());
        assertThat(t(t1).getStatus()).isEqualTo("PENDENTE");
        assertThat(r(r1U3).getStatus()).isEqualTo("AGUARDANDO_TRANSFERENCIA");
        assertThat(situacao(hobbitU2)).isEqualTo(EMPRESTADO);                      // fila PENDENTE vazia
        consistente();
        chamar(HttpMethod.GET, "/api/transferencias/pedidos-pendentes", tokenAdmin, null)
            .andExpect(jsonPath("$[0].exemplar").value("RETIDO"))
            .andExpect(jsonPath("$[0].exemplarId").value(hobbitU1.getId()));
        chamar(HttpMethod.GET, "/api/transferencias/biblioteca/saindo", tokenBibC, null)
            .andExpect(jsonPath("$[0].status").value("PENDENTE"))
            .andExpect(jsonPath("$[0].exemplarId").value(hobbitU1.getId()));

        // ES-14: exemplar retido não sai em avulsa
        avulsa(tokenAdmin, List.of(hobbitU1.getId()), vilaIsabel)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Exemplar nº " + hobbitU1.getId())));

        aprovar(t1, tokenAdmin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EM_TRANSITO"));
        assertThat(situacao(hobbitU1)).isEqualTo(EM_TRANSFERENCIA);                  // T9
        assertThat(r(r1U3).getStatus()).isEqualTo("AGUARDANDO_TRANSFERENCIA");
        consistente();
    }

    @Test
    @DisplayName("Rejeitar com exemplar retido: ele atende a reserva na origem por 3 dias")
    void rejeitarComExemplarRetido() throws Exception {
        devolver(empHobbitU1, tokenBibC);
        chamar(HttpMethod.PATCH, "/api/transferencias/" + t1.getId() + "/rejeitar", tokenAdmin, null)
            .andExpect(status().isOk());
        Reserva r = r(r1U3);
        assertThat(r.getStatus()).isEqualTo("DISPONIVEL");
        assertThat(r.getBibliotecaDestino().getId()).isEqualTo(central.getId());
        assertThat(r.getExemplar().getId()).isEqualTo(hobbitU1.getId());
        assertThat(Duration.between(LocalDateTime.now(), r.getDataExpiracao()).toHours()).isBetween(71L, 72L);
        assertThat(situacao(hobbitU1)).isEqualTo(RESERVADO);
        consistente();
    }

    @Test
    @DisplayName("A-06: pedido já processado -> 'já foi processada'; perfis errados -> 403")
    void a06_jaProcessadaEPerfis() throws Exception {
        aprovar(t1, tokenBibVI).andExpect(status().isForbidden());
        aprovar(t1, tokenBibC).andExpect(status().isForbidden());
        aprovar(t1, tokenU3).andExpect(status().isForbidden());
        chamar(HttpMethod.PATCH, "/api/transferencias/" + t1.getId() + "/rejeitar", tokenU1, null)
            .andExpect(status().isForbidden());
        aprovar(t1, tokenAdmin).andExpect(status().isOk());
        aprovar(t1, tokenAdmin).andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("já foi processada")));
        chamar(HttpMethod.PATCH, "/api/transferencias/" + t1.getId() + "/rejeitar", tokenAdmin, null)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("já foi processada")));

        for (String url : List.of("/api/transferencias/pedidos-pendentes", "/api/transferencias/acompanhamento")) {
            chamar(HttpMethod.GET, url, tokenBibVI, null).andExpect(status().isForbidden());
            chamar(HttpMethod.GET, url, tokenU1, null).andExpect(status().isForbidden());
            chamar(HttpMethod.GET, url, null, null).andExpect(status().isUnauthorized());
        }
        for (String url : List.of("/api/transferencias/biblioteca/a-receber", "/api/transferencias/biblioteca/saindo",
                                  "/api/reservas/biblioteca")) {
            chamar(HttpMethod.GET, url, tokenAdmin, null).andExpect(status().isForbidden());
            chamar(HttpMethod.GET, url, tokenU1, null).andExpect(status().isForbidden());
        }
        avulsa(tokenBibC, List.of(1L), vilaIsabel).andExpect(status().isForbidden());
        avulsa(tokenU1, List.of(1L), vilaIsabel).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A-07: RN15 recusa segundo pedido na reserva e na aprovação acima da capacidade")
    void a07_rn15() throws Exception {
        // Na reserva: Central tem 2 Hobbit e já há 1 pedido aberto
        reservar(tokenU4, hobbit, central, vilaIsabel)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("deixaria sem o livro")));

        // Na aprovação: um pedido legado a mais da mesma origem × título estoura a capacidade
        transferenciaAberta(hobbit, central, vilaIsabel, "PENDENTE", null);
        chamar(HttpMethod.GET, "/api/transferencias/pedidos-pendentes", tokenAdmin, null)
            .andExpect(jsonPath("$[0].rn15.permitido").value(false));
        aprovar(t1, tokenAdmin)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(allOf(containsString("Não é possível aprovar"),
                containsString("deixaria sem o livro"))));
        assertThat(t(t1).getStatus()).isEqualTo("PENDENTE");
        consistente();
    }

    @Test
    @DisplayName("RN22 na aprovação: destino sem bibliotecário ativo é recusado")
    void rn22NaAprovacao() throws Exception {
        Usuario bibVI = usuarioRepository.findByEmailIgnoreCase("bibliotecário.vila.isabel@teste.com").orElseThrow();
        bibVI.setAtivo(false);
        usuarioRepository.save(bibVI);
        aprovar(t1, tokenAdmin)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("bibliotecário ativo")));
    }

    // ───────────────────────── Despacho ─────────────────────────

    @Test
    @DisplayName("Despacho com RN15 que deixou de valer: pedido CANCELADA, retirada na origem por 3 dias")
    void despachoComRn15Violada() throws Exception {
        aprovar(t1, tokenAdmin).andExpect(status().isOk());
        transferenciaAberta(hobbit, central, vilaIsabel, "EM_TRANSITO", null); // capacidade estourada
        devolver(empHobbitU1, tokenBibC);

        SolicitacaoTransferencia t = t(t1);
        assertThat(t.getStatus()).isEqualTo("CANCELADA");
        assertThat(t.getObservacoes()).contains("Cancelada no despacho").contains("retirada na Biblioteca Central");
        Reserva r = r(r1U3);
        assertThat(r.getStatus()).isEqualTo("DISPONIVEL");
        assertThat(r.getBibliotecaDestino().getId()).isEqualTo(central.getId());
        assertThat(situacao(hobbitU1)).isEqualTo(RESERVADO);
        consistente();
    }

    // ───────────────────────── Ciclo completo e chegada ─────────────────────────

    @Test
    @DisplayName("ES-10 / B-12 / B-13: ciclo do Hobbit — aprova, devolução despacha, só o destino confirma, empréstimo")
    void es10_b12_cicloHobbit() throws Exception {
        aprovar(t1, tokenAdmin).andExpect(status().isOk());
        devolver(empHobbitU1, tokenBibC);
        assertThat(situacao(hobbitU1)).isEqualTo(EM_TRANSFERENCIA);                  // direto, sem parar em RESERVADO
        assertThat(t(t1).getStatus()).isEqualTo("EM_TRANSITO");
        assertThat(r(r1U3).getStatus()).isEqualTo("AGUARDANDO_TRANSFERENCIA");
        assertThat(situacao(hobbitU2)).isEqualTo(EMPRESTADO);
        consistente();

        chamar(HttpMethod.GET, "/api/transferencias/biblioteca/a-receber", tokenBibVI, null)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].titulo").value("O Hobbit"))
            .andExpect(jsonPath("$[0].exemplarId").value(hobbitU1.getId()))
            .andExpect(jsonPath("$[0].origem.nome").value("Biblioteca Central"))
            .andExpect(jsonPath("$[0].reservadoPara").value("Usuário 3"));
        chamar(HttpMethod.GET, "/api/transferencias/biblioteca/a-receber", tokenBibC, null)
            .andExpect(jsonPath("$.length()").value(0));

        // B-13 / RN16: só o bibliotecário do destino confirma
        confirmar(t1, tokenBibC, null).andExpect(status().isForbidden());
        confirmar(t1, tokenAdmin, null).andExpect(status().isForbidden());
        confirmar(t1, tokenU3, null).andExpect(status().isForbidden());

        confirmar(t1, tokenBibVI, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONCLUIDA"));
        Exemplar ex = exemplarRepository.findById(hobbitU1.getId()).orElseThrow();
        assertThat(ex.getStatus()).isEqualTo(RESERVADO);                               // T10
        assertThat(ex.getBiblioteca().getId()).isEqualTo(vilaIsabel.getId());
        assertThat(r(r1U3).getStatus()).isEqualTo("DISPONIVEL");
        consistente();
        confirmar(t1, tokenBibVI, null).andExpect(status().isBadRequest());

        // B6: a reserva aparece no painel da VI
        chamar(HttpMethod.GET, "/api/reservas/biblioteca", tokenBibVI, null)
            .andExpect(jsonPath("$.aguardandoRetirada[0].usuario").value("Usuário 3"))
            .andExpect(jsonPath("$.aguardandoRetirada[0].titulo").value("O Hobbit"))
            .andExpect(jsonPath("$.aguardandoRetirada[0].exemplarId").value(hobbitU1.getId()))
            .andExpect(jsonPath("$.aguardandoRetirada[0].retireAte").isNotEmpty());

        chamar(HttpMethod.POST, "/api/emprestimos/registrar", tokenBibVI,
               "{\"exemplarId\":" + hobbitU1.getId() + ",\"usuarioId\":" + u3.getId() + "}")
            .andExpect(status().isOk());
        assertThat(situacao(hobbitU1)).isEqualTo(EMPRESTADO);
        assertThat(r(r1U3).getStatus()).isEqualTo("RETIRADA");
        consistente();
    }

    @Test
    @DisplayName("Chegada danificada: INDISPONIVEL no destino, CONCLUIDA, reserva volta à fila da origem (§4.4)")
    void chegadaDanificada() throws Exception {
        LocalDateTime dataOriginal = r(r1U3).getDataReserva();
        aprovar(t1, tokenAdmin).andExpect(status().isOk());
        devolver(empHobbitU1, tokenBibC);
        confirmar(t1, tokenBibVI, "{\"danificado\":true,\"observacao\":\"capa rasgada\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONCLUIDA"));

        Exemplar ex = exemplarRepository.findById(hobbitU1.getId()).orElseThrow();
        assertThat(ex.getStatus()).isEqualTo(INDISPONIVEL);
        assertThat(ex.getBiblioteca().getId()).isEqualTo(vilaIsabel.getId());
        Reserva r = r(r1U3);
        assertThat(r.getStatus()).isEqualTo("PENDENTE");
        assertThat(r.getBibliotecaFila().getId()).isEqualTo(central.getId());
        assertThat(r.getBibliotecaDestino().getId()).isEqualTo(central.getId());
        assertThat(r.getExemplar()).isNull();
        assertThat(r.getDataReserva()).isEqualTo(dataOriginal);
        assertThat(situacao(hobbitU2)).isEqualTo(EMPRESTADO_RESERVADO);           // fila da origem voltou
        consistente();
    }

    @Test
    @DisplayName("U-12 completa: reserva cancelada durante a viagem; a chegada deixa o exemplar DISPONIVEL")
    void u12_canceladaEmTransito() throws Exception {
        aprovar(t1, tokenAdmin).andExpect(status().isOk());
        devolver(empHobbitU1, tokenBibC);
        chamar(HttpMethod.PATCH, "/api/reservas/" + r1U3.getId() + "/cancelar", tokenU3, null)
            .andExpect(status().isOk());
        assertThat(t(t1).getStatus()).isEqualTo("EM_TRANSITO");
        chamar(HttpMethod.GET, "/api/transferencias/biblioteca/a-receber", tokenBibVI, null)
            .andExpect(jsonPath("$[0].reservadoPara").value(nullValue()));
        consistente();

        confirmar(t1, tokenBibVI, null).andExpect(status().isOk());
        Exemplar ex = exemplarRepository.findById(hobbitU1.getId()).orElseThrow();
        assertThat(ex.getStatus()).isEqualTo(DISPONIVEL);                             // T11
        assertThat(ex.getBiblioteca().getId()).isEqualTo(vilaIsabel.getId());
        consistente();
    }

    @Test
    @DisplayName("Chegada de avulsa em biblioteca com fila do título: RESERVADO para o 1º da fila do destino (T10)")
    void chegadaComFilaNoDestino() throws Exception {
        Livro cortico = livro("O Cortiço");
        exemplar(cortico, central, DISPONIVEL);
        Exemplar viajante = exemplar(cortico, central, DISPONIVEL);
        emprestimo(exemplar(cortico, vilaIsabel, EMPRESTADO), u2, 2);
        reservaPorApi(tokenU4, cortico, vilaIsabel, vilaIsabel);
        consistente();

        long tId = idDoArray(avulsa(tokenAdmin, List.of(viajante.getId()), vilaIsabel)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        confirmar(transferenciaRepository.findById(tId).orElseThrow(), tokenBibVI, null).andExpect(status().isOk());
        assertThat(situacao(viajante)).isEqualTo(RESERVADO);
        Reserva rU4 = reservaRepository.findByUsuario(u4).get(0);
        assertThat(rU4.getStatus()).isEqualTo("DISPONIVEL");
        assertThat(rU4.getExemplar().getId()).isEqualTo(viajante.getId());
        consistente();
    }

    // ───────────────────────── Avulsa em lote ─────────────────────────

    @Test
    @DisplayName("ES-09: avulsa de Fahrenheit em trânsito; BibVI confirma -> DISPONIVEL na VI (T8, T11)")
    void es09_avulsaChegada() throws Exception {
        Exemplar f = exemplarRepository.findByLivroAndBiblioteca(fahrenheit, central).get(0);
        String json = avulsa(tokenAdmin, List.of(f.getId()), vilaIsabel)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("EM_TRANSITO"))
            .andReturn().getResponse().getContentAsString();
        assertThat(situacao(f)).isEqualTo(EM_TRANSFERENCIA);                          // T8
        SolicitacaoTransferencia t = transferenciaRepository.findById(idDoArray(json)).orElseThrow();
        chamar(HttpMethod.GET, "/api/transferencias/biblioteca/a-receber", tokenBibVI, null)
            .andExpect(jsonPath("$[?(@.titulo == 'Fahrenheit 451')].tipo").value(contains("AVULSA")));
        confirmar(t, tokenBibVI, null).andExpect(status().isOk());
        assertThat(t(t).getStatus()).isEqualTo("CONCLUIDA");
        assertThat(situacao(f)).isEqualTo(DISPONIVEL);                                // T11
        assertThat(exemplarRepository.findById(f.getId()).orElseThrow().getBiblioteca().getId())
            .isEqualTo(vilaIsabel.getId());
        consistente();
    }

    @Test
    @DisplayName("A-08: 2 de 1984 com a Central tendo 2 -> recusada, nada criado")
    void a08_loteViolaRn15() throws Exception {
        long antes = transferenciaRepository.count();
        List<Long> ids = exemplarRepository.findByLivroAndBiblioteca(livro1984, central).stream().map(Exemplar::getId).toList();
        avulsa(tokenAdmin, ids, vilaIsabel)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(allOf(containsString("Nenhuma transferência foi criada"),
                containsString("\"1984\" na Biblioteca Central: 2 selecionado(s), mas só 1 pode(m) sair"))));
        assertThat(transferenciaRepository.count()).isEqualTo(antes);
        assertThat(exemplarRepository.findByLivroAndBiblioteca(livro1984, central))
            .allMatch(e -> DISPONIVEL.equals(e.getStatus()));
    }

    @Test
    @DisplayName("A-09 / A-11: 1 de 1984 + 2 de Dom Casmurro (Central tem 3) -> criadas, todas EM_TRANSITO, destino com o título")
    void a09_a11_loteValido() throws Exception {
        long id1984 = exemplarRepository.findByLivroAndBiblioteca(livro1984, central).get(0).getId();
        List<Exemplar> dc = exemplarRepository.findByLivroAndBiblioteca(domCasmurro, central);
        avulsa(tokenAdmin, List.of(id1984, dc.get(0).getId(), dc.get(1).getId()), vilaIsabel)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[*].status", everyItem(is("EM_TRANSITO"))));
        assertThat(situacao(dc.get(0))).isEqualTo(EM_TRANSFERENCIA);
        assertThat(situacao(dc.get(2))).isEqualTo(DISPONIVEL);
        chamar(HttpMethod.GET, "/api/transferencias/acompanhamento?status=EM_TRANSITO&tamanho=2", tokenAdmin, null)
            .andExpect(jsonPath("$.totalItens").value(3))
            .andExpect(jsonPath("$.totalPaginas").value(2))
            .andExpect(jsonPath("$.itens.length()").value(2));
        consistente();
    }

    @Test
    @DisplayName("A-10 / ES-14: lote com um item inválido -> tudo ou nada, mensagem lista o violador")
    void a10_es14_loteComInvalido() throws Exception {
        long antes = transferenciaRepository.count();
        long valido = exemplarRepository.findByLivroAndBiblioteca(domCasmurro, central).get(0).getId();
        avulsa(tokenAdmin, List.of(valido, hobbitU2.getId()), vilaIsabel)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Exemplar nº " + hobbitU2.getId() + " (O Hobbit) está emprestado")));
        assertThat(transferenciaRepository.count()).isEqualTo(antes);
        assertThat(exemplarRepository.findById(valido).orElseThrow().getStatus()).isEqualTo(DISPONIVEL);

        // destino sem bibliotecário (RN22) e destino igual à origem
        avulsa(tokenAdmin, List.of(valido), tijuca)
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("bibliotecário ativo")));
        avulsa(tokenAdmin, List.of(valido), central)
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("já está na Biblioteca Central")));
        assertThat(transferenciaRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("A3: destinos da avulsa listam todas as ativas, com motivo nas que não podem receber (RN22)")
    void destinosAvulsa() throws Exception {
        chamar(HttpMethod.GET, "/api/transferencias/destinos-avulsa", tokenAdmin, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[?(@.nome == 'Biblioteca Vila Isabel')].permitido").value(contains(true)))
            .andExpect(jsonPath("$[?(@.nome == 'Biblioteca Central')].permitido").value(contains(true)))
            .andExpect(jsonPath("$[?(@.nome == 'Biblioteca Tijuca')].permitido").value(contains(false)))
            .andExpect(jsonPath("$[?(@.nome == 'Biblioteca Tijuca')].motivo")
                .value(contains(containsString("bibliotecário ativo"))));
        chamar(HttpMethod.GET, "/api/transferencias/destinos-avulsa", tokenBibVI, null).andExpect(status().isForbidden());
        chamar(HttpMethod.GET, "/api/transferencias/destinos-avulsa", tokenU1, null).andExpect(status().isForbidden());
    }

    // ───────────────────────── Bibliotecário: reservas ─────────────────────────

    @Test
    @DisplayName("B-10 / B6: devolução com fila sem transferência -> RESERVADO para o 1º; painel mostra prazo e filas")
    void b10_painelReservas() throws Exception {
        chamar(HttpMethod.GET, "/api/reservas/biblioteca", tokenBibVI, null)
            .andExpect(jsonPath("$.aguardandoRetirada.length()").value(0))
            .andExpect(jsonPath("$.filas[0].titulo").value("Harry Potter e a Pedra Filosofal"))
            .andExpect(jsonPath("$.filas[0].fila[0].usuario").value("Usuário 2"))
            .andExpect(jsonPath("$.filas[0].fila[0].posicao").value(1));
        chamar(HttpMethod.GET, "/api/reservas/biblioteca", tokenBibC, null)
            .andExpect(jsonPath("$.filas[*].titulo", not(hasItem("Harry Potter e a Pedra Filosofal"))));

        devolver(empHarryU3, tokenBibVI);
        assertThat(situacao(harryU3)).isEqualTo(RESERVADO);                       // T4
        Reserva rU2 = reservaRepository.findByUsuario(u2).get(0);
        assertThat(rU2.getStatus()).isEqualTo("DISPONIVEL");
        assertThat(Duration.between(LocalDateTime.now(), rU2.getDataExpiracao()).toHours()).isBetween(71L, 72L);
        chamar(HttpMethod.GET, "/api/reservas/biblioteca", tokenBibVI, null)
            .andExpect(jsonPath("$.aguardandoRetirada[0].usuario").value("Usuário 2"))
            .andExpect(jsonPath("$.aguardandoRetirada[0].exemplarId").value(harryU3.getId()))
            .andExpect(jsonPath("$.filas.length()").value(0));
        consistente();
    }

    // ───────────────────────── Apoio ─────────────────────────

    private ResultActions reservar(String token, Livro livro, Biblioteca fila, Biblioteca destino) throws Exception {
        return chamar(HttpMethod.POST, "/api/reservas", token,
            "{\"livroId\":" + livro.getId() + ",\"bibliotecaFilaId\":" + fila.getId()
            + ",\"bibliotecaDestinoId\":" + destino.getId() + "}");
    }

    private Reserva reservaPorApi(String token, Livro livro, Biblioteca fila, Biblioteca destino) throws Exception {
        String json = reservar(token, livro, fila, destino).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return reservaRepository.findById(id(json)).orElseThrow();
    }

    private ResultActions aprovar(SolicitacaoTransferencia t, String token) throws Exception {
        return chamar(HttpMethod.PATCH, "/api/transferencias/" + t.getId() + "/aprovar", token, null);
    }

    private ResultActions confirmar(SolicitacaoTransferencia t, String token, String corpo) throws Exception {
        return chamar(HttpMethod.PATCH, "/api/transferencias/" + t.getId() + "/confirmar-chegada", token, corpo);
    }

    private ResultActions avulsa(String token, List<Long> ids, Biblioteca destino) throws Exception {
        return chamar(HttpMethod.POST, "/api/transferencias/avulsa", token,
            "{\"exemplarIds\":" + ids + ",\"bibliotecaDestinoId\":" + destino.getId() + "}");
    }

    private void devolver(Emprestimo emp, String token) throws Exception {
        chamar(HttpMethod.POST, "/api/emprestimos/devolver", token,
               "{\"emprestimoId\":" + emp.getId() + ",\"condicaoExemplar\":\"BOM\"}")
            .andExpect(status().isOk());
    }

    /** Pedido legado aberto, gravado direto no banco, para forçar a capacidade da RN15. */
    private void transferenciaAberta(Livro livro, Biblioteca origem, Biblioteca destino, String status, Exemplar ex) {
        SolicitacaoTransferencia s = new SolicitacaoTransferencia();
        s.setLivro(livro);
        s.setExemplar(ex);
        s.setBibliotecaOrigem(origem);
        s.setBibliotecaDestino(destino);
        s.setSolicitante(u1);
        s.setStatus(status);
        s.setDataSolicitacao(LocalDateTime.now().minusDays(5));
        transferenciaRepository.save(s);
    }

    private SolicitacaoTransferencia pedidoDa(Reserva r) {
        return transferenciaRepository.findAll().stream()
            .filter(t -> t.getReserva() != null && t.getReserva().getId().equals(r.getId()))
            .findFirst().orElseThrow();
    }

    private SolicitacaoTransferencia t(SolicitacaoTransferencia t) {
        return transferenciaRepository.findById(t.getId()).orElseThrow();
    }

    private Reserva r(Reserva r) {
        return reservaRepository.findById(r.getId()).orElseThrow();
    }

    private String situacao(Exemplar e) {
        return exemplarRepository.findById(e.getId()).orElseThrow().getStatus();
    }

    private static long idDoArray(String json) {
        return Long.parseLong(json.replaceAll("^\\[\\{\"id\":(\\d+).*", "$1"));
    }
}
