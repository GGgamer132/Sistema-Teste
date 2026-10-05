package com.circulabook.controller;

import com.circulabook.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static com.circulabook.model.StatusExemplar.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Reserva e fila do usuário COMUM (§5.1, §5.2, §5.3) e dados do próprio usuário,
 * pela API real com JWT. Roteiros U-03..U-13, U-16, ES-01, ES-02, ES-07, ES-08 da §9.
 * A invariante 4.2 é conferida depois de cada operação que mexe em fila ou empréstimo.
 */
class ReservaFilaComumTest extends ApoioApiTest {

    private Biblioteca central, vilaIsabel, tijuca;
    private Usuario u1, u2, u3, u4;
    private Livro duna, grandeSertao, hobbit, harryPotter, livro1984, capitaes, sapiens;
    private Exemplar dunaU3, dunaU2, harryU1, harryU3;
    private Emprestimo empDunaU3;
    private Reserva r2U1, r3U2;
    private String tokenAdmin, tokenBibC, tokenBibVI, tokenU1, tokenU2, tokenU3, tokenU4;

    @BeforeEach
    void montar() throws Exception {
        central = biblioteca("Biblioteca Central");
        vilaIsabel = biblioteca("Biblioteca Vila Isabel");
        tijuca = biblioteca("Biblioteca Tijuca"); // sem bibliotecário (RN22)
        Usuario admin = usuario("Admin", "ADMIN", null);
        Usuario bibC = usuario("BibliotecÃ¡rio Central", "BIBLIOTECARIO", central);
        Usuario bibVI = usuario("BibliotecÃ¡rio Vila Isabel", "BIBLIOTECARIO", vilaIsabel);
        u1 = usuario("UsuÃ¡rio 1", "COMUM", null);
        u2 = usuario("UsuÃ¡rio 2", "COMUM", null);
        u3 = usuario("UsuÃ¡rio 3", "COMUM", null);
        u4 = usuario("UsuÃ¡rio 4", "COMUM", null);

        // Duna: 2 emprestados na Central (U3 atrasada há 6 dias, U2)
        duna = livro("Duna");
        dunaU3 = exemplar(duna, central, EMPRESTADO);
        dunaU2 = exemplar(duna, central, EMPRESTADO);
        empDunaU3 = emprestimo(dunaU3, u3, 20);
        emprestimo(dunaU2, u2, 10);

        // Grande Sertão: 1 só exemplar na Central, emprestado ao U2
        grandeSertao = livro("Grande Sertão: Veredas");
        emprestimo(exemplar(grandeSertao, central, EMPRESTADO), u2, 6);

        // O Hobbit: 2 emprestados na Central (U1, U2)
        hobbit = livro("O Hobbit");
        emprestimo(exemplar(hobbit, central, EMPRESTADO), u1, 3);
        emprestimo(exemplar(hobbit, central, EMPRESTADO), u2, 8);

        // Harry Potter na VI: 1 RESERVADO para U1 (R2 DISPONIVEL) + 1 com U3; U2 na fila (R3)
        harryPotter = livro("Harry Potter e a Pedra Filosofal");
        exemplar(harryPotter, central, DISPONIVEL);
        harryU1 = exemplar(harryPotter, vilaIsabel, RESERVADO);
        harryU3 = exemplar(harryPotter, vilaIsabel, EMPRESTADO_RESERVADO);
        emprestimo(harryU3, u3, 4);
        r2U1 = reservaSalva(harryPotter, u1, vilaIsabel, "DISPONIVEL", LocalDateTime.now().minusDays(1));
        r2U1.setExemplar(harryU1);
        r2U1.setDataExpiracao(LocalDateTime.now().plusDays(2));
        reservaRepository.save(r2U1);
        r3U2 = reservaSalva(harryPotter, u2, vilaIsabel, "PENDENTE", LocalDateTime.now().minusHours(20));

        // 1984: disponível nas duas; Capitães da Areia: só na VI
        livro1984 = livro("1984");
        exemplar(livro1984, central, DISPONIVEL);
        exemplar(livro1984, central, DISPONIVEL);
        exemplar(livro1984, vilaIsabel, DISPONIVEL);
        capitaes = livro("Capitães da Areia");
        exemplar(capitaes, vilaIsabel, DISPONIVEL);

        // Sapiens: todos emprestados na Central, 1 disponível na VI
        sapiens = livro("Sapiens");
        emprestimo(exemplar(sapiens, central, EMPRESTADO), u3, 2);
        emprestimo(exemplar(sapiens, central, EMPRESTADO), u2, 2);
        exemplar(sapiens, vilaIsabel, DISPONIVEL);

        consistente();
        tokenAdmin = login(admin);
        tokenBibC = login(bibC);
        tokenBibVI = login(bibVI);
        tokenU1 = login(u1);
        tokenU2 = login(u2);
        tokenU3 = login(u3);
        tokenU4 = login(u4);
    }

    // ───────────────────────── Criar reserva ─────────────────────────

    @Test
    @DisplayName("U-03: biblioteca com exemplar disponível orienta o empréstimo presencial")
    void u03_comDisponivel() throws Exception {
        reservar(tokenU1, livro1984, central, central)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(allOf(containsString("disponível(is)"), containsString("presencialmente"))));
        assertThat(reservaRepository.findByUsuario(u1)).hasSize(1); // só a R2 de antes
    }

    @Test
    @DisplayName("U-04: biblioteca sem o título não tem fila")
    void u04_semTitulo() throws Exception {
        reservar(tokenU1, capitaes, central, central)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("não possui exemplares deste título")));
    }

    @Test
    @DisplayName("U-05 / ES-01 / ES-02: fila local em 1º, T3 em todos os emprestados e T12 ao cancelar")
    void u05_filaLocal_es01_es02() throws Exception {
        String json = reservar(tokenU1, duna, central, central)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDENTE"))
            .andExpect(jsonPath("$.posicaoFila").value(1))
            .andExpect(jsonPath("$.bibliotecaDestino.id").value(central.getId()))
            .andReturn().getResponse().getContentAsString();
        long id = id(json);
        assertThat(transferenciaRepository.findAll()).isEmpty();
        assertThat(situacao(dunaU3)).isEqualTo(EMPRESTADO_RESERVADO);
        assertThat(situacao(dunaU2)).isEqualTo(EMPRESTADO_RESERVADO);
        consistente();

        // U4 entra depois: 2º da fila daquela biblioteca
        reservar(tokenU4, duna, central, central)
            .andExpect(status().isOk()).andExpect(jsonPath("$.posicaoFila").value(2));
        chamar(HttpMethod.GET, "/api/conta/reservas", tokenU4, null)
            .andExpect(jsonPath("$[0].posicao").value(2));

        // ES-02: U1 cancela; com o U4 ainda na fila os emprestados continuam marcados
        chamar(HttpMethod.PATCH, "/api/reservas/" + id + "/cancelar", tokenU1, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELADA"));
        assertThat(situacao(dunaU3)).isEqualTo(EMPRESTADO_RESERVADO);
        chamar(HttpMethod.GET, "/api/conta/reservas", tokenU4, null)
            .andExpect(jsonPath("$[0].posicao").value(1));
        consistente();

        // ...e quando a fila esvazia, voltam a EMPRESTADO (T12)
        long idU4 = reservaRepository.findByUsuario(u4).get(0).getId();
        chamar(HttpMethod.PATCH, "/api/reservas/" + idU4 + "/cancelar", tokenU4, null)
            .andExpect(status().isOk());
        assertThat(situacao(dunaU3)).isEqualTo(EMPRESTADO);
        assertThat(situacao(dunaU2)).isEqualTo(EMPRESTADO);
        consistente();
    }

    @Test
    @DisplayName("U-06: destinos sem a origem; reserva + transferência PENDENTE sem exemplar; capacidade RN15 usada")
    void u06_retiradaEmOutra() throws Exception {
        chamar(HttpMethod.GET, "/api/reservas/destinos?livroId=" + duna.getId()
               + "&bibliotecaFilaId=" + central.getId(), tokenU1, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].bibliotecaId", not(hasItem(central.getId().intValue()))))
            .andExpect(jsonPath("$[?(@.nome == 'Biblioteca Vila Isabel')].permitido").value(contains(true)))
            .andExpect(jsonPath("$[?(@.nome == 'Biblioteca Tijuca')].permitido").value(contains(false)))
            .andExpect(jsonPath("$[?(@.nome == 'Biblioteca Tijuca')].motivo")
                .value(contains(containsString("bibliotecário ativo"))));

        String json = reservar(tokenU1, duna, central, vilaIsabel)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDENTE"))
            .andExpect(jsonPath("$.bibliotecaFila.id").value(central.getId()))
            .andExpect(jsonPath("$.bibliotecaDestino.id").value(vilaIsabel.getId()))
            .andReturn().getResponse().getContentAsString();
        Reserva r = reservaRepository.findById(id(json)).orElseThrow();
        List<SolicitacaoTransferencia> ts = transferenciaRepository.findAll();
        assertThat(ts).hasSize(1);
        assertThat(ts.get(0).getStatus()).isEqualTo("PENDENTE");
        assertThat(ts.get(0).getExemplar()).isNull();
        assertThat(ts.get(0).getReserva().getId()).isEqualTo(r.getId());
        assertThat(ts.get(0).getBibliotecaOrigem().getId()).isEqualTo(central.getId());
        assertThat(ts.get(0).getBibliotecaDestino().getId()).isEqualTo(vilaIsabel.getId());
        consistente();

        // Minhas reservas mostra a etapa da transferência
        chamar(HttpMethod.GET, "/api/conta/reservas", tokenU1, null)
            .andExpect(jsonPath("$[?(@.titulo == 'Duna')].transferencia.etapa").value(contains("PENDENTE")))
            .andExpect(jsonPath("$[?(@.titulo == 'Duna')].bibliotecaRetirada").value(contains("Biblioteca Vila Isabel")));

        // Central tem 2 de Duna e já há 1 transferência aberta: abertas + 1 <= total - 1 deixa de valer
        chamar(HttpMethod.GET, "/api/reservas/destinos?livroId=" + duna.getId()
               + "&bibliotecaFilaId=" + central.getId(), tokenU4, null)
            .andExpect(jsonPath("$[?(@.nome == 'Biblioteca Vila Isabel')].permitido").value(contains(false)))
            .andExpect(jsonPath("$[?(@.nome == 'Biblioteca Vila Isabel')].motivo")
                .value(contains(containsString("transferência(s) deste título em andamento"))));
        reservar(tokenU4, duna, central, vilaIsabel)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("deixaria sem o livro")));
        reservar(tokenU4, duna, central, tijuca)
            .andExpect(status().isBadRequest());
        // na fila local ele ainda entra
        reservar(tokenU4, duna, central, central).andExpect(status().isOk());
        consistente();
    }

    @Test
    @DisplayName("U-07: origem com 1 só exemplar bloqueia retirada em outra biblioteca (tela e API)")
    void u07_origemComUmExemplar() throws Exception {
        chamar(HttpMethod.GET, "/api/reservas/destinos?livroId=" + grandeSertao.getId()
               + "&bibliotecaFilaId=" + central.getId(), tokenU1, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].permitido", everyItem(is(false))))
            .andExpect(jsonPath("$[*].motivo", everyItem(containsString("só 1 exemplar"))));
        reservar(tokenU1, grandeSertao, central, vilaIsabel)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("só 1 exemplar")));
        assertThat(transferenciaRepository.findAll()).isEmpty();
        assertThat(reservaRepository.findByUsuario(u1)).hasSize(1);
        reservar(tokenU1, grandeSertao, central, central).andExpect(status().isOk());
        consistente();
    }

    @Test
    @DisplayName("U-08: destino que já tem o título é recusado")
    void u08_destinoComTitulo() throws Exception {
        chamar(HttpMethod.GET, "/api/reservas/destinos?livroId=" + sapiens.getId()
               + "&bibliotecaFilaId=" + central.getId(), tokenU1, null)
            .andExpect(jsonPath("$[?(@.nome == 'Biblioteca Vila Isabel')].motivo")
                .value(contains(containsString("já possui exemplares deste título"))));
        reservar(tokenU1, sapiens, central, vilaIsabel)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("já possui exemplares deste título")));
        // destinos de uma fila inválida: 400 com a mesma orientação da reserva
        chamar(HttpMethod.GET, "/api/reservas/destinos?livroId=" + livro1984.getId()
               + "&bibliotecaFilaId=" + central.getId(), tokenU1, null)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("presencialmente")));
    }

    @Test
    @DisplayName("U-09: título já emprestado e reserva duplicada são recusados")
    void u09_duplicadaEJaEmprestado() throws Exception {
        reservar(tokenU1, hobbit, central, central)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("já está com um exemplar de \"O Hobbit\"")));
        reservar(tokenU1, duna, central, central).andExpect(status().isOk());
        reservar(tokenU1, duna, central, central)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("já possui uma reserva ativa")));
        // R2 (Harry Potter DISPONIVEL) também conta como ativa
        reservar(tokenU1, harryPotter, vilaIsabel, vilaIsabel)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("já possui uma reserva ativa")));
        consistente();
    }

    // ───────────────────────── Cancelar reserva ─────────────────────────

    @Test
    @DisplayName("U-11: cancelar com transferência PENDENTE ou APROVADA (sem exemplar) cancela o pedido")
    void u11_cancelarPendenteEAprovada() throws Exception {
        long r1 = id(reservar(tokenU1, duna, central, vilaIsabel).andReturn().getResponse().getContentAsString());
        cancelar(r1, tokenU1).andExpect(status().isOk());
        assertThat(transferenciaRepository.findAll()).singleElement()
            .extracting(SolicitacaoTransferencia::getStatus).isEqualTo("CANCELADA");
        consistente();

        long r2 = id(reservar(tokenU1, duna, central, vilaIsabel).andReturn().getResponse().getContentAsString());
        SolicitacaoTransferencia t = pedidoDa(r2);
        chamar(HttpMethod.PATCH, "/api/transferencias/" + t.getId() + "/aprovar", tokenAdmin, null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APROVADA"));
        chamar(HttpMethod.GET, "/api/conta/reservas", tokenU1, null)
            .andExpect(jsonPath("$[?(@.titulo == 'Duna')].transferencia.etapa")
                .value(contains("APROVADA_AGUARDANDO_EXEMPLAR")));
        cancelar(r2, tokenU1).andExpect(status().isOk());
        assertThat(transferenciaRepository.findById(t.getId()).orElseThrow().getStatus()).isEqualTo("CANCELADA");
        assertThat(situacao(dunaU3)).isEqualTo(EMPRESTADO);
        consistente();
    }

    @Test
    @DisplayName("U-11: cancelar com exemplar já separado reatribui ao próximo (T7b) ou libera (T7)")
    void u11_cancelarComExemplarVinculado() throws Exception {
        long rU1 = id(reservar(tokenU1, duna, central, vilaIsabel).andReturn().getResponse().getContentAsString());
        reservar(tokenU4, duna, central, central).andExpect(status().isOk());
        devolver(empDunaU3, tokenBibC);
        // U1 é a 1ª: o exemplar fica RESERVADO, vinculado ao pedido ainda PENDENTE
        assertThat(situacao(dunaU3)).isEqualTo(RESERVADO);
        SolicitacaoTransferencia t = pedidoDa(rU1);
        assertThat(t.getExemplar().getId()).isEqualTo(dunaU3.getId());
        assertThat(reservaRepository.findById(rU1).orElseThrow().getStatus()).isEqualTo("AGUARDANDO_TRANSFERENCIA");
        consistente();

        cancelar(rU1, tokenU1).andExpect(status().isOk());
        assertThat(transferenciaRepository.findById(t.getId()).orElseThrow().getStatus()).isEqualTo("CANCELADA");
        Reserva rU4 = reservaRepository.findByUsuario(u4).get(0);
        assertThat(rU4.getStatus()).isEqualTo("DISPONIVEL");                    // T7b
        assertThat(rU4.getExemplar().getId()).isEqualTo(dunaU3.getId());
        assertThat(situacao(dunaU3)).isEqualTo(RESERVADO);
        assertThat(situacao(dunaU2)).isEqualTo(EMPRESTADO);                       // fila vazia: T12
        consistente();

        cancelar(rU4.getId(), tokenU4).andExpect(status().isOk());
        assertThat(situacao(dunaU3)).isEqualTo(DISPONIVEL);                      // T7
        consistente();
    }

    @Test
    @DisplayName("U-12: cancelar com transferência EM_TRANSITO: viagem continua e chega DISPONIVEL")
    void u12_cancelarEmTransito() throws Exception {
        long rU1 = id(reservar(tokenU1, duna, central, vilaIsabel).andReturn().getResponse().getContentAsString());
        SolicitacaoTransferencia t = pedidoDa(rU1);
        chamar(HttpMethod.PATCH, "/api/transferencias/" + t.getId() + "/aprovar", tokenAdmin, null)
            .andExpect(status().isOk());
        devolver(empDunaU3, tokenBibC);
        assertThat(transferenciaRepository.findById(t.getId()).orElseThrow().getStatus()).isEqualTo("EM_TRANSITO");
        assertThat(situacao(dunaU3)).isEqualTo(EM_TRANSFERENCIA);
        chamar(HttpMethod.GET, "/api/conta/reservas", tokenU1, null)
            .andExpect(jsonPath("$[?(@.titulo == 'Duna')].transferencia.etapa").value(contains("EM_TRANSITO")));

        cancelar(rU1, tokenU1).andExpect(status().isOk());
        assertThat(reservaRepository.findById(rU1).orElseThrow().getStatus()).isEqualTo("CANCELADA");
        assertThat(transferenciaRepository.findById(t.getId()).orElseThrow().getStatus()).isEqualTo("EM_TRANSITO");
        assertThat(situacao(dunaU3)).isEqualTo(EM_TRANSFERENCIA);
        assertThat(situacao(dunaU2)).isEqualTo(EMPRESTADO);
        consistente();

        // Chegada (fluxo da etapa de transferências): entra no acervo da VI como DISPONIVEL (T11)
        chamar(HttpMethod.PATCH, "/api/transferencias/" + t.getId() + "/confirmar-chegada", tokenBibVI, null)
            .andExpect(status().isOk());
        Exemplar chegou = exemplarRepository.findById(dunaU3.getId()).orElseThrow();
        assertThat(chegou.getStatus()).isEqualTo(DISPONIVEL);
        assertThat(chegou.getBiblioteca().getId()).isEqualTo(vilaIsabel.getId());
        consistente();
    }

    @Test
    @DisplayName("Cancelar reserva de outro usuário: 403; reserva já encerrada: 400")
    void cancelarAlheiaOuEncerrada() throws Exception {
        cancelar(r3U2.getId(), tokenU1).andExpect(status().isForbidden());
        cancelar(r3U2.getId(), tokenU2).andExpect(status().isOk());
        cancelar(r3U2.getId(), tokenU2)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("não pode mais ser cancelada")));
        consistente();
    }

    // ───────────────────────── Expiração ─────────────────────────

    @Test
    @DisplayName("ES-07 / ES-08: expiração reatribui ao próximo (T7b) e depois libera (T7); só o Admin dispara")
    void es07_es08_expiracao() throws Exception {
        chamar(HttpMethod.POST, "/api/reservas/expirar-vencidas", tokenU1, null).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/reservas/expirar-vencidas", tokenBibVI, null).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/reservas/expirar-vencidas", null, null).andExpect(status().isUnauthorized());

        r2U1.setDataExpiracao(LocalDateTime.now().minusMinutes(1));
        reservaRepository.save(r2U1);
        chamar(HttpMethod.POST, "/api/reservas/expirar-vencidas", tokenAdmin, null).andExpect(status().isOk());

        assertThat(reservaRepository.findById(r2U1.getId()).orElseThrow().getStatus()).isEqualTo("EXPIRADA");
        Reserva r3 = reservaRepository.findById(r3U2.getId()).orElseThrow();
        assertThat(r3.getStatus()).isEqualTo("DISPONIVEL");
        assertThat(r3.getExemplar().getId()).isEqualTo(harryU1.getId());
        Exemplar ex = exemplarRepository.findById(harryU1.getId()).orElseThrow();
        assertThat(ex.getStatus()).isEqualTo(RESERVADO);                             // T7b
        assertThat(ex.getBiblioteca().getId()).isEqualTo(vilaIsabel.getId());         // fica onde está
        // a fila PENDENTE da VI esvaziou: pela invariante 4.2 o Harry da U3 já volta a EMPRESTADO
        assertThat(situacao(harryU3)).isEqualTo(EMPRESTADO);
        consistente();

        r3.setDataExpiracao(LocalDateTime.now().minusMinutes(1));
        reservaRepository.save(r3);
        chamar(HttpMethod.POST, "/api/reservas/expirar-vencidas", tokenAdmin, null).andExpect(status().isOk());
        assertThat(reservaRepository.findById(r3.getId()).orElseThrow().getStatus()).isEqualTo("EXPIRADA");
        assertThat(situacao(harryU1)).isEqualTo(DISPONIVEL);                          // T7
        assertThat(situacao(harryU3)).isEqualTo(EMPRESTADO);
        consistente();
    }

    // ───────────────────────── Dados do próprio usuário ─────────────────────────

    @Test
    @DisplayName("U-13: Meus empréstimos só do token, x/3, prazo de 14 dias, atraso e bloqueio")
    void u13_meusEmprestimos() throws Exception {
        // BibC empresta 1984 à U1 pelo balcão: prazo fixo de 14 dias
        Exemplar e1984 = exemplarRepository.findByLivroAndBibliotecaAndStatus(livro1984, central, DISPONIVEL).get(0);
        chamar(HttpMethod.POST, "/api/emprestimos/registrar", tokenBibC,
               "{\"exemplarId\":" + e1984.getId() + ",\"usuarioId\":" + u1.getId() + "}")
            .andExpect(status().isOk());
        consistente();

        chamar(HttpMethod.GET, "/api/conta/emprestimos?usuarioId=" + u2.getId(), tokenU1, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.limite").value(3))
            .andExpect(jsonPath("$.emprestimos[*].titulo", containsInAnyOrder("O Hobbit", "1984")))
            .andExpect(jsonPath("$.bloqueado").value(false))
            .andExpect(jsonPath("$.bloqueadoAte").value(nullValue()));
        Emprestimo novo = emprestimoRepository.findByUsuario(u1).stream()
            .filter(e -> e.getExemplar().getId().equals(e1984.getId())).findFirst().orElseThrow();
        assertThat(Duration.between(novo.getDataEmprestimo(), novo.getDataPrevDevolucao()).toDays()).isEqualTo(14);

        // U3: Duna atrasado há 6 dias; bloqueio (RN12) aparece com a data de liberação
        u3.setBloqueadoAte(LocalDateTime.now().plusDays(12));
        usuarioRepository.save(u3);
        chamar(HttpMethod.GET, "/api/conta/emprestimos", tokenU3, null)
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.emprestimos[0].titulo").value("Duna"))
            .andExpect(jsonPath("$.emprestimos[0].atrasado").value(true))
            .andExpect(jsonPath("$.emprestimos[0].diasAtraso").value(6))
            .andExpect(jsonPath("$.bloqueado").value(true))
            .andExpect(jsonPath("$.bloqueadoAte").isNotEmpty());
    }

    @Test
    @DisplayName("U-10 / U-13: Minhas reservas e histórico só do usuário do token, com filtros e paginação")
    void u13_reservasEHistorico() throws Exception {
        chamar(HttpMethod.GET, "/api/conta/reservas", tokenU1, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("DISPONIVEL"))
            .andExpect(jsonPath("$[0].retireAte").isNotEmpty())
            .andExpect(jsonPath("$[0].posicao").value(nullValue()))
            .andExpect(jsonPath("$[0].transferencia").value(nullValue()));
        chamar(HttpMethod.GET, "/api/conta/reservas", tokenU2, null)
            .andExpect(jsonPath("$[0].posicao").value(1))
            .andExpect(jsonPath("$[0].retireAte").value(nullValue()));

        // Histórico da U1: 2 devolvidos + reservas encerradas; o do U2 não aparece
        Emprestimo dev1 = emprestimo(exemplar(capitaes, vilaIsabel, DISPONIVEL), u1, 40);
        dev1.setStatus("DEVOLVIDO");
        dev1.setDataDevolucao(dev1.getDataPrevDevolucao().minusDays(1));
        emprestimoRepository.save(dev1);
        Emprestimo dev2 = emprestimo(exemplar(capitaes, vilaIsabel, DISPONIVEL), u1, 5);
        dev2.setStatus("DEVOLVIDO");
        dev2.setDataDevolucao(LocalDateTime.now());
        emprestimoRepository.save(dev2);
        reservaSalva(livro1984, u1, central, "RETIRADA", LocalDateTime.now().minusDays(30));
        reservaSalva(duna, u1, central, "CANCELADA", LocalDateTime.now().minusDays(3));
        reservaSalva(sapiens, u1, central, "EXPIRADA", LocalDateTime.now().minusDays(60));
        reservaSalva(duna, u2, central, "CANCELADA", LocalDateTime.now().minusDays(2));

        chamar(HttpMethod.GET, "/api/conta/historico?usuarioId=" + u2.getId(), tokenU1, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItens").value(5))
            .andExpect(jsonPath("$.pagina").value(0))
            .andExpect(jsonPath("$.itens[0].titulo").value("Duna"))              // mais recente primeiro
            .andExpect(jsonPath("$.itens[0].status").value("CANCELADA"));
        chamar(HttpMethod.GET, "/api/conta/historico?tipo=emprestimo", tokenU1, null)
            .andExpect(jsonPath("$.totalItens").value(2))
            .andExpect(jsonPath("$.itens[*].status", everyItem(is("DEVOLVIDO"))));
        chamar(HttpMethod.GET, "/api/conta/historico?tipo=RESERVA&de="
               + LocalDateTime.now().minusDays(31).toLocalDate(), tokenU1, null)
            .andExpect(jsonPath("$.totalItens").value(2))
            .andExpect(jsonPath("$.itens[*].status", containsInAnyOrder("RETIRADA", "CANCELADA")));
        chamar(HttpMethod.GET, "/api/conta/historico?tamanho=2&pagina=2", tokenU1, null)
            .andExpect(jsonPath("$.itens.length()").value(1))
            .andExpect(jsonPath("$.totalPaginas").value(3))
            .andExpect(jsonPath("$.itens[0].status").value("EXPIRADA"));
        chamar(HttpMethod.GET, "/api/conta/historico?tipo=OUTRO", tokenU1, null)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Tipo inválido")));
        chamar(HttpMethod.GET, "/api/conta/historico?de=2026-05-10&ate=2026-05-01", tokenU1, null)
            .andExpect(status().isBadRequest());
    }

    // ───────────────────────── Permissões ─────────────────────────

    @Test
    @DisplayName("U-16: COMUM em endpoints de bibliotecário/admin -> 403; /api/conta só COMUM")
    void u16_permissoes() throws Exception {
        chamar(HttpMethod.POST, "/api/emprestimos/registrar", tokenU1, "{}").andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/emprestimos/devolver", tokenU1, "{}").andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/exemplares", tokenU1, "{}").andExpect(status().isForbidden());
        chamar(HttpMethod.PATCH, "/api/transferencias/1/aprovar", tokenU1, null).andExpect(status().isForbidden());
        chamar(HttpMethod.PATCH, "/api/transferencias/1/confirmar-chegada", tokenU1, null).andExpect(status().isForbidden());
        chamar(HttpMethod.POST, "/api/transferencias/avulsa", tokenU1, "{}").andExpect(status().isForbidden());
        chamar(HttpMethod.GET, "/api/reservas", tokenU1, null).andExpect(status().isForbidden());
        chamar(HttpMethod.GET, "/api/historico", tokenU1, null).andExpect(status().isForbidden());
        chamar(HttpMethod.GET, "/api/usuarios", tokenU1, null).andExpect(status().isForbidden());

        for (String url : List.of("/api/conta/reservas", "/api/conta/emprestimos", "/api/conta/historico")) {
            chamar(HttpMethod.GET, url, null, null).andExpect(status().isUnauthorized());
            chamar(HttpMethod.GET, url, tokenBibVI, null).andExpect(status().isForbidden());
            chamar(HttpMethod.GET, url, tokenAdmin, null).andExpect(status().isForbidden());
        }
        reservar(tokenBibVI, duna, central, central).andExpect(status().isForbidden());
        reservar(tokenAdmin, duna, central, central).andExpect(status().isForbidden());
        chamar(HttpMethod.GET, "/api/reservas/destinos?livroId=" + duna.getId()
               + "&bibliotecaFilaId=" + central.getId(), tokenBibVI, null).andExpect(status().isForbidden());
        assertThat(reservaRepository.findAll()).hasSize(2);
    }

    // ───────────────────────── Apoio ─────────────────────────

    private org.springframework.test.web.servlet.ResultActions reservar(String token, Livro livro,
                                                                        Biblioteca fila, Biblioteca destino) throws Exception {
        return chamar(HttpMethod.POST, "/api/reservas", token,
            "{\"livroId\":" + livro.getId() + ",\"bibliotecaFilaId\":" + fila.getId()
            + ",\"bibliotecaDestinoId\":" + destino.getId() + "}");
    }

    private org.springframework.test.web.servlet.ResultActions cancelar(long reservaId, String token) throws Exception {
        return chamar(HttpMethod.PATCH, "/api/reservas/" + reservaId + "/cancelar", token, null);
    }

    private void devolver(Emprestimo emp, String token) throws Exception {
        chamar(HttpMethod.POST, "/api/emprestimos/devolver", token,
               "{\"emprestimoId\":" + emp.getId() + ",\"condicaoExemplar\":\"BOM\"}")
            .andExpect(status().isOk());
    }

    private SolicitacaoTransferencia pedidoDa(long reservaId) {
        return transferenciaRepository.findAll().stream()
            .filter(t -> t.getReserva() != null && t.getReserva().getId().equals(reservaId))
            .findFirst().orElseThrow();
    }

    private String situacao(Exemplar e) {
        return exemplarRepository.findById(e.getId()).orElseThrow().getStatus();
    }

    private Reserva reservaSalva(Livro livro, Usuario usuario, Biblioteca biblioteca, String status,
                                 LocalDateTime data) {
        Reserva r = new Reserva();
        r.setLivro(livro);
        r.setUsuario(usuario);
        r.setBibliotecaFila(biblioteca);
        r.setBibliotecaDestino(biblioteca);
        r.setDataReserva(data);
        r.setDataExpiracao(data.plusDays(3));
        r.setStatus(status);
        return reservaRepository.save(r);
    }
}
