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
    private Usuario ana, bruno, camila, diego;
    private Livro hobbit, livro1984, domCasmurro, fahrenheit, harry;
    private Exemplar hobbitAna, hobbitBruno, harryCamila;
    private Emprestimo empHobbitAna, empHarryCamila;
    private Reserva r1Camila;
    private SolicitacaoTransferencia t1;
    private String tokenAdmin, tokenFernanda, tokenCarlos, tokenAna, tokenBruno, tokenCamila, tokenDiego;

    @BeforeEach
    void montar() throws Exception {
        central = biblioteca("Biblioteca Central");
        vilaIsabel = biblioteca("Biblioteca Vila Isabel");
        tijuca = biblioteca("Biblioteca Tijuca"); // sem bibliotecário (RN22)
        Usuario roberto = usuario("Roberto Dias", "ADMIN", null);
        Usuario fernanda = usuario("Fernanda Reis", "BIBLIOTECARIO", central);
        Usuario carlos = usuario("Carlos Lima", "BIBLIOTECARIO", vilaIsabel);
        ana = usuario("Ana Souza", "COMUM", null);
        bruno = usuario("Bruno Alves", "COMUM", null);
        camila = usuario("Camila Duarte", "COMUM", null);
        diego = usuario("Diego Santos", "COMUM", null);
        tokenAdmin = login(roberto);
        tokenFernanda = login(fernanda);
        tokenCarlos = login(carlos);
        tokenAna = login(ana);
        tokenBruno = login(bruno);
        tokenCamila = login(camila);
        tokenDiego = login(diego);

        // O Hobbit: 2 emprestados na Central (Ana, Bruno); Camila entra na fila com retirada na VI (R1 + T1)
        hobbit = livro("O Hobbit");
        hobbitAna = exemplar(hobbit, central, EMPRESTADO);
        hobbitBruno = exemplar(hobbit, central, EMPRESTADO);
        empHobbitAna = emprestimo(hobbitAna, ana, 3);
        emprestimo(hobbitBruno, bruno, 8);
        r1Camila = reservaPorApi(tokenCamila, hobbit, central, vilaIsabel);
        t1 = pedidoDa(r1Camila);

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

        // Harry Potter na VI: emprestado à Camila, Bruno na fila (retirada na VI)
        harry = livro("Harry Potter e a Pedra Filosofal");
        harryCamila = exemplar(harry, vilaIsabel, EMPRESTADO);
        empHarryCamila = emprestimo(harryCamila, camila, 4);
        reservaPorApi(tokenBruno, harry, vilaIsabel, vilaIsabel);
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
            .andExpect(jsonPath("$[0].solicitante.nome").value("Camila Duarte"))
            .andExpect(jsonPath("$[0].origem.nome").value("Biblioteca Central"))
            .andExpect(jsonPath("$[0].destino.nome").value("Biblioteca Vila Isabel"))
            .andExpect(jsonPath("$[0].rn15.permitido").value(true))
            .andExpect(jsonPath("$[0].rn15.total").value(2))
            .andExpect(jsonPath("$[0].rn15.abertas").value(0));

        aprovar(t1, tokenAdmin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APROVADA"));
        assertThat(t(t1).getExemplar()).isNull();
        assertThat(r(r1Camila).getStatus()).isEqualTo("PENDENTE");
        chamar(HttpMethod.GET, "/api/transferencias/acompanhamento?status=APROVADA_AGUARDANDO_EXEMPLAR", tokenAdmin, null)
            .andExpect(jsonPath("$.totalItens").value(1))
            .andExpect(jsonPath("$.itens[0].etapa").value("APROVADA_AGUARDANDO_EXEMPLAR"))
            .andExpect(jsonPath("$.itens[0].reservadoPara").value("Camila Duarte"));
        consistente();
    }

    @Test
    @DisplayName("A-04: rejeitar sem exemplar -> REJEITADA; reserva segue PENDENTE com retirada na origem")
    void a04_rejeitarSemExemplar() throws Exception {
        chamar(HttpMethod.PATCH, "/api/transferencias/" + t1.getId() + "/rejeitar?motivo=Sem%20malote", tokenAdmin, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJEITADA"));
        Reserva r = r(r1Camila);
        assertThat(r.getStatus()).isEqualTo("PENDENTE");
        assertThat(r.getBibliotecaDestino().getId()).isEqualTo(central.getId());
        assertThat(situacao(hobbitBruno)).isEqualTo(EMPRESTADO_RESERVADO);
        consistente();
    }

    @Test
    @DisplayName("ES-06 / A-05: devolução com pedido PENDENTE retém o exemplar; aprovar envia direto (T9)")
    void es06_a05_retidoEAprovado() throws Exception {
        devolver(empHobbitAna, tokenFernanda);
        assertThat(situacao(hobbitAna)).isEqualTo(RESERVADO);                         // T4, retido
        assertThat(t(t1).getExemplar().getId()).isEqualTo(hobbitAna.getId());
        assertThat(t(t1).getStatus()).isEqualTo("PENDENTE");
        assertThat(r(r1Camila).getStatus()).isEqualTo("AGUARDANDO_TRANSFERENCIA");
        assertThat(situacao(hobbitBruno)).isEqualTo(EMPRESTADO);                      // fila PENDENTE vazia
        consistente();
        chamar(HttpMethod.GET, "/api/transferencias/pedidos-pendentes", tokenAdmin, null)
            .andExpect(jsonPath("$[0].exemplar").value("RETIDO"))
            .andExpect(jsonPath("$[0].exemplarId").value(hobbitAna.getId()));
        chamar(HttpMethod.GET, "/api/transferencias/biblioteca/saindo", tokenFernanda, null)
            .andExpect(jsonPath("$[0].status").value("PENDENTE"))
            .andExpect(jsonPath("$[0].exemplarId").value(hobbitAna.getId()));

        // ES-14: exemplar retido não sai em avulsa
        avulsa(tokenAdmin, List.of(hobbitAna.getId()), vilaIsabel)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Exemplar nº " + hobbitAna.getId())));

        aprovar(t1, tokenAdmin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EM_TRANSITO"));
        assertThat(situacao(hobbitAna)).isEqualTo(EM_TRANSFERENCIA);                  // T9
        assertThat(r(r1Camila).getStatus()).isEqualTo("AGUARDANDO_TRANSFERENCIA");
        consistente();
    }

    @Test
    @DisplayName("Rejeitar com exemplar retido: ele atende a reserva na origem por 3 dias")
    void rejeitarComExemplarRetido() throws Exception {
        devolver(empHobbitAna, tokenFernanda);
        chamar(HttpMethod.PATCH, "/api/transferencias/" + t1.getId() + "/rejeitar", tokenAdmin, null)
            .andExpect(status().isOk());
        Reserva r = r(r1Camila);
        assertThat(r.getStatus()).isEqualTo("DISPONIVEL");
        assertThat(r.getBibliotecaDestino().getId()).isEqualTo(central.getId());
        assertThat(r.getExemplar().getId()).isEqualTo(hobbitAna.getId());
        assertThat(Duration.between(LocalDateTime.now(), r.getDataExpiracao()).toHours()).isBetween(71L, 72L);
        assertThat(situacao(hobbitAna)).isEqualTo(RESERVADO);
        consistente();
    }

    @Test
    @DisplayName("A-06: pedido já processado -> 'já foi processada'; perfis errados -> 403")
    void a06_jaProcessadaEPerfis() throws Exception {
        aprovar(t1, tokenCarlos).andExpect(status().isForbidden());
        aprovar(t1, tokenFernanda).andExpect(status().isForbidden());
        aprovar(t1, tokenCamila).andExpect(status().isForbidden());
        chamar(HttpMethod.PATCH, "/api/transferencias/" + t1.getId() + "/rejeitar", tokenAna, null)
            .andExpect(status().isForbidden());
        aprovar(t1, tokenAdmin).andExpect(status().isOk());
        aprovar(t1, tokenAdmin).andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("já foi processada")));
        chamar(HttpMethod.PATCH, "/api/transferencias/" + t1.getId() + "/rejeitar", tokenAdmin, null)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("já foi processada")));

        for (String url : List.of("/api/transferencias/pedidos-pendentes", "/api/transferencias/acompanhamento")) {
            chamar(HttpMethod.GET, url, tokenCarlos, null).andExpect(status().isForbidden());
            chamar(HttpMethod.GET, url, tokenAna, null).andExpect(status().isForbidden());
            chamar(HttpMethod.GET, url, null, null).andExpect(status().isUnauthorized());
        }
        for (String url : List.of("/api/transferencias/biblioteca/a-receber", "/api/transferencias/biblioteca/saindo",
                                  "/api/reservas/biblioteca")) {
            chamar(HttpMethod.GET, url, tokenAdmin, null).andExpect(status().isForbidden());
            chamar(HttpMethod.GET, url, tokenAna, null).andExpect(status().isForbidden());
        }
        avulsa(tokenFernanda, List.of(1L), vilaIsabel).andExpect(status().isForbidden());
        avulsa(tokenAna, List.of(1L), vilaIsabel).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A-07: RN15 recusa segundo pedido na reserva e na aprovação acima da capacidade")
    void a07_rn15() throws Exception {
        // Na reserva: Central tem 2 Hobbit e já há 1 pedido aberto
        reservar(tokenDiego, hobbit, central, vilaIsabel)
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
        Usuario carlos = usuarioRepository.findByEmailIgnoreCase("carlos.lima@teste.com").orElseThrow();
        carlos.setAtivo(false);
        usuarioRepository.save(carlos);
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
        devolver(empHobbitAna, tokenFernanda);

        SolicitacaoTransferencia t = t(t1);
        assertThat(t.getStatus()).isEqualTo("CANCELADA");
        assertThat(t.getObservacoes()).contains("Cancelada no despacho").contains("retirada na Biblioteca Central");
        Reserva r = r(r1Camila);
        assertThat(r.getStatus()).isEqualTo("DISPONIVEL");
        assertThat(r.getBibliotecaDestino().getId()).isEqualTo(central.getId());
        assertThat(situacao(hobbitAna)).isEqualTo(RESERVADO);
        consistente();
    }

    // ───────────────────────── Ciclo completo e chegada ─────────────────────────

    @Test
    @DisplayName("ES-10 / B-12 / B-13: ciclo do Hobbit — aprova, devolução despacha, só o destino confirma, empréstimo")
    void es10_b12_cicloHobbit() throws Exception {
        aprovar(t1, tokenAdmin).andExpect(status().isOk());
        devolver(empHobbitAna, tokenFernanda);
        assertThat(situacao(hobbitAna)).isEqualTo(EM_TRANSFERENCIA);                  // direto, sem parar em RESERVADO
        assertThat(t(t1).getStatus()).isEqualTo("EM_TRANSITO");
        assertThat(r(r1Camila).getStatus()).isEqualTo("AGUARDANDO_TRANSFERENCIA");
        assertThat(situacao(hobbitBruno)).isEqualTo(EMPRESTADO);
        consistente();

        chamar(HttpMethod.GET, "/api/transferencias/biblioteca/a-receber", tokenCarlos, null)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].titulo").value("O Hobbit"))
            .andExpect(jsonPath("$[0].exemplarId").value(hobbitAna.getId()))
            .andExpect(jsonPath("$[0].origem.nome").value("Biblioteca Central"))
            .andExpect(jsonPath("$[0].reservadoPara").value("Camila Duarte"));
        chamar(HttpMethod.GET, "/api/transferencias/biblioteca/a-receber", tokenFernanda, null)
            .andExpect(jsonPath("$.length()").value(0));

        // B-13 / RN16: só o bibliotecário do destino confirma
        confirmar(t1, tokenFernanda, null).andExpect(status().isForbidden());
        confirmar(t1, tokenAdmin, null).andExpect(status().isForbidden());
        confirmar(t1, tokenCamila, null).andExpect(status().isForbidden());

        confirmar(t1, tokenCarlos, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONCLUIDA"));
        Exemplar ex = exemplarRepository.findById(hobbitAna.getId()).orElseThrow();
        assertThat(ex.getStatus()).isEqualTo(RESERVADO);                               // T10
        assertThat(ex.getBiblioteca().getId()).isEqualTo(vilaIsabel.getId());
        assertThat(r(r1Camila).getStatus()).isEqualTo("DISPONIVEL");
        consistente();
        confirmar(t1, tokenCarlos, null).andExpect(status().isBadRequest());

        // B6: a reserva aparece no painel da VI
        chamar(HttpMethod.GET, "/api/reservas/biblioteca", tokenCarlos, null)
            .andExpect(jsonPath("$.aguardandoRetirada[0].usuario").value("Camila Duarte"))
            .andExpect(jsonPath("$.aguardandoRetirada[0].titulo").value("O Hobbit"))
            .andExpect(jsonPath("$.aguardandoRetirada[0].exemplarId").value(hobbitAna.getId()))
            .andExpect(jsonPath("$.aguardandoRetirada[0].retireAte").isNotEmpty());

        chamar(HttpMethod.POST, "/api/emprestimos/registrar", tokenCarlos,
               "{\"exemplarId\":" + hobbitAna.getId() + ",\"usuarioId\":" + camila.getId() + "}")
            .andExpect(status().isOk());
        assertThat(situacao(hobbitAna)).isEqualTo(EMPRESTADO);
        assertThat(r(r1Camila).getStatus()).isEqualTo("RETIRADA");
        consistente();
    }

    @Test
    @DisplayName("Chegada danificada: INDISPONIVEL no destino, CONCLUIDA, reserva volta à fila da origem (§4.4)")
    void chegadaDanificada() throws Exception {
        LocalDateTime dataOriginal = r(r1Camila).getDataReserva();
        aprovar(t1, tokenAdmin).andExpect(status().isOk());
        devolver(empHobbitAna, tokenFernanda);
        confirmar(t1, tokenCarlos, "{\"danificado\":true,\"observacao\":\"capa rasgada\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONCLUIDA"));

        Exemplar ex = exemplarRepository.findById(hobbitAna.getId()).orElseThrow();
        assertThat(ex.getStatus()).isEqualTo(INDISPONIVEL);
        assertThat(ex.getBiblioteca().getId()).isEqualTo(vilaIsabel.getId());
        Reserva r = r(r1Camila);
        assertThat(r.getStatus()).isEqualTo("PENDENTE");
        assertThat(r.getBibliotecaFila().getId()).isEqualTo(central.getId());
        assertThat(r.getBibliotecaDestino().getId()).isEqualTo(central.getId());
        assertThat(r.getExemplar()).isNull();
        assertThat(r.getDataReserva()).isEqualTo(dataOriginal);
        assertThat(situacao(hobbitBruno)).isEqualTo(EMPRESTADO_RESERVADO);           // fila da origem voltou
        consistente();
    }

    @Test
    @DisplayName("U-12 completa: reserva cancelada durante a viagem; a chegada deixa o exemplar DISPONIVEL")
    void u12_canceladaEmTransito() throws Exception {
        aprovar(t1, tokenAdmin).andExpect(status().isOk());
        devolver(empHobbitAna, tokenFernanda);
        chamar(HttpMethod.PATCH, "/api/reservas/" + r1Camila.getId() + "/cancelar", tokenCamila, null)
            .andExpect(status().isOk());
        assertThat(t(t1).getStatus()).isEqualTo("EM_TRANSITO");
        chamar(HttpMethod.GET, "/api/transferencias/biblioteca/a-receber", tokenCarlos, null)
            .andExpect(jsonPath("$[0].reservadoPara").value(nullValue()));
        consistente();

        confirmar(t1, tokenCarlos, null).andExpect(status().isOk());
        Exemplar ex = exemplarRepository.findById(hobbitAna.getId()).orElseThrow();
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
        emprestimo(exemplar(cortico, vilaIsabel, EMPRESTADO), bruno, 2);
        reservaPorApi(tokenDiego, cortico, vilaIsabel, vilaIsabel);
        consistente();

        long tId = idDoArray(avulsa(tokenAdmin, List.of(viajante.getId()), vilaIsabel)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        confirmar(transferenciaRepository.findById(tId).orElseThrow(), tokenCarlos, null).andExpect(status().isOk());
        assertThat(situacao(viajante)).isEqualTo(RESERVADO);
        Reserva rDiego = reservaRepository.findByUsuario(diego).get(0);
        assertThat(rDiego.getStatus()).isEqualTo("DISPONIVEL");
        assertThat(rDiego.getExemplar().getId()).isEqualTo(viajante.getId());
        consistente();
    }

    // ───────────────────────── Avulsa em lote ─────────────────────────

    @Test
    @DisplayName("ES-09: avulsa de Fahrenheit em trânsito; Carlos confirma -> DISPONIVEL na VI (T8, T11)")
    void es09_avulsaChegada() throws Exception {
        Exemplar f = exemplarRepository.findByLivroAndBiblioteca(fahrenheit, central).get(0);
        String json = avulsa(tokenAdmin, List.of(f.getId()), vilaIsabel)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("EM_TRANSITO"))
            .andReturn().getResponse().getContentAsString();
        assertThat(situacao(f)).isEqualTo(EM_TRANSFERENCIA);                          // T8
        SolicitacaoTransferencia t = transferenciaRepository.findById(idDoArray(json)).orElseThrow();
        chamar(HttpMethod.GET, "/api/transferencias/biblioteca/a-receber", tokenCarlos, null)
            .andExpect(jsonPath("$[?(@.titulo == 'Fahrenheit 451')].tipo").value(contains("AVULSA")));
        confirmar(t, tokenCarlos, null).andExpect(status().isOk());
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
        avulsa(tokenAdmin, List.of(valido, hobbitBruno.getId()), vilaIsabel)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Exemplar nº " + hobbitBruno.getId() + " (O Hobbit) está emprestado")));
        assertThat(transferenciaRepository.count()).isEqualTo(antes);
        assertThat(exemplarRepository.findById(valido).orElseThrow().getStatus()).isEqualTo(DISPONIVEL);

        // destino sem bibliotecário (RN22) e destino igual à origem
        avulsa(tokenAdmin, List.of(valido), tijuca)
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("bibliotecário ativo")));
        avulsa(tokenAdmin, List.of(valido), central)
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("já está na Biblioteca Central")));
        assertThat(transferenciaRepository.count()).isEqualTo(antes);
    }

    // ───────────────────────── Bibliotecário: reservas ─────────────────────────

    @Test
    @DisplayName("B-10 / B6: devolução com fila sem transferência -> RESERVADO para o 1º; painel mostra prazo e filas")
    void b10_painelReservas() throws Exception {
        chamar(HttpMethod.GET, "/api/reservas/biblioteca", tokenCarlos, null)
            .andExpect(jsonPath("$.aguardandoRetirada.length()").value(0))
            .andExpect(jsonPath("$.filas[0].titulo").value("Harry Potter e a Pedra Filosofal"))
            .andExpect(jsonPath("$.filas[0].fila[0].usuario").value("Bruno Alves"))
            .andExpect(jsonPath("$.filas[0].fila[0].posicao").value(1));
        chamar(HttpMethod.GET, "/api/reservas/biblioteca", tokenFernanda, null)
            .andExpect(jsonPath("$.filas[*].titulo", not(hasItem("Harry Potter e a Pedra Filosofal"))));

        devolver(empHarryCamila, tokenCarlos);
        assertThat(situacao(harryCamila)).isEqualTo(RESERVADO);                       // T4
        Reserva rBruno = reservaRepository.findByUsuario(bruno).get(0);
        assertThat(rBruno.getStatus()).isEqualTo("DISPONIVEL");
        assertThat(Duration.between(LocalDateTime.now(), rBruno.getDataExpiracao()).toHours()).isBetween(71L, 72L);
        chamar(HttpMethod.GET, "/api/reservas/biblioteca", tokenCarlos, null)
            .andExpect(jsonPath("$.aguardandoRetirada[0].usuario").value("Bruno Alves"))
            .andExpect(jsonPath("$.aguardandoRetirada[0].exemplarId").value(harryCamila.getId()))
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
        s.setSolicitante(ana);
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
