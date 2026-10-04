package com.circulabook.controller;

import com.circulabook.model.*;
import com.circulabook.repository.HistoricoCirculacaoRepository;
import com.circulabook.service.FilaEsperaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Histórico de circulação (RN06, A4): uma linha para cada operação, com o responsável
 * certo (usuário do token; "Sistema" na rotina agendada), filtros e linha do tempo.
 */
class HistoricoCirculacaoTest extends ApoioApiTest {

    @Autowired private HistoricoCirculacaoRepository historicoRepository;
    @Autowired private FilaEsperaService filaEsperaService;

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
    }

    @Test
    @DisplayName("A-18: cada operação gera uma linha com o responsável certo; linha do tempo completa")
    void cicloCompleto() throws Exception {
        Livro livro = livro("O Hobbit");
        List<Long> ex = cadastrar(livro, 2);                                   // CADASTRO x2 (Fernanda)
        long ex1 = ex.get(0), ex2 = ex.get(1);
        long empAna = emprestar(ex1, ana);                                     // EMPRESTIMO (Fernanda)
        emprestar(ex2, bruno);                                                 // EMPRESTIMO (Fernanda)
        reservar(tokenCamila, livro, central, central);                        // FILA x2 (Camila)
        devolver(empAna);                                                      // DEVOLUCAO + RESERVA + FILA ex2 (Fernanda)
        chamar(HttpMethod.PATCH, "/api/exemplares/" + ex1 + "/indisponivel", tokenFernanda,
               "{\"motivo\":\"capa rasgada\"}").andExpect(status().isOk());    // BAIXA + FILA ex2
        chamar(HttpMethod.PATCH, "/api/exemplares/" + ex1 + "/reativar", tokenFernanda, null)
            .andExpect(status().isOk());                                       // REATIVACAO + RESERVA + FILA ex2
        vencerReservaDe(camila);
        chamar(HttpMethod.POST, "/api/reservas/expirar-vencidas", tokenAdmin, null)
            .andExpect(status().isOk());                                       // RESERVA (T7, Roberto)
        String avulsa = chamar(HttpMethod.POST, "/api/transferencias/avulsa", tokenAdmin,
            "{\"exemplarIds\":[" + ex1 + "],\"bibliotecaDestinoId\":" + vilaIsabel.getId() + "}")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();   // SAIDA (Roberto)
        long tAvulsa = Long.parseLong(avulsa.replaceAll("^\\[\\{\"id\":(\\d+).*", "$1"));
        chamar(HttpMethod.PATCH, "/api/transferencias/" + tAvulsa + "/confirmar-chegada", tokenCarlos, null)
            .andExpect(status().isOk());                                       // CHEGADA (Carlos)

        List<HistoricoCirculacao> linhas = historicoRepository.findByExemplarOrderByDataEventoAscIdAsc(
            exemplarRepository.findById(ex1).orElseThrow());
        assertThat(linhas).extracting(HistoricoCirculacao::getEvento, HistoricoCirculacao::getResponsavel)
            .containsExactly(
                tuple("CADASTRO", "Fernanda Reis"),
                tuple("EMPRESTIMO", "Fernanda Reis"),
                tuple("FILA", "Camila Duarte"),
                tuple("DEVOLUCAO", "Fernanda Reis"),
                tuple("RESERVA", "Fernanda Reis"),
                tuple("BAIXA", "Fernanda Reis"),
                tuple("REATIVACAO", "Fernanda Reis"),
                tuple("RESERVA", "Fernanda Reis"),
                tuple("RESERVA", "Roberto Dias"),
                tuple("TRANSFERENCIA_SAIDA", "Roberto Dias"),
                tuple("TRANSFERENCIA_CHEGADA", "Carlos Lima"));
        assertThat(linhas.get(1).getObservacoes()).contains("Emprestado a Ana Souza por 14 dias");
        assertThat(linhas.get(3).getObservacoes()).contains("Devolvido por Ana Souza dentro do prazo");
        assertThat(linhas.get(4).getObservacoes()).contains("Separado para Camila Duarte");
        assertThat(linhas.get(5).getObservacoes()).contains("capa rasgada", "voltou para a fila");
        assertThat(linhas.get(8).getObservacoes()).contains("expirou");
        assertThat(linhas.get(9).getObservacoes()).contains("transferência avulsa");
        assertThat(linhas.get(9).getBiblioteca().getId()).isEqualTo(central.getId());
        assertThat(linhas.get(10).getBiblioteca().getId()).isEqualTo(vilaIsabel.getId());

        // ex2 só recebeu as marcas de fila (T3/T12) além do cadastro e do empréstimo
        assertThat(historicoRepository.findByExemplarOrderByDataEventoAscIdAsc(
                exemplarRepository.findById(ex2).orElseThrow()))
            .extracting(HistoricoCirculacao::getEvento)
            .containsExactly("CADASTRO", "EMPRESTIMO", "FILA", "FILA", "FILA", "FILA"); // formou, esvaziou, voltou (baixa), esvaziou (reativação)

        // Linha do tempo pela API
        chamar(HttpMethod.GET, "/api/historico/exemplar/" + ex1, tokenAdmin, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titulo").value("O Hobbit"))
            .andExpect(jsonPath("$.bibliotecaAtual").value("Biblioteca Vila Isabel"))
            .andExpect(jsonPath("$.statusRotulo").value("Disponível"))
            .andExpect(jsonPath("$.eventos.length()").value(11))
            .andExpect(jsonPath("$.eventos[0].eventoRotulo").value("Cadastro no acervo"))
            .andExpect(jsonPath("$.eventos[10].eventoRotulo").value("Chegada de transferência"))
            .andExpect(jsonPath("$.eventos[10].responsavel").value("Carlos Lima"))
            .andExpect(jsonPath("$.eventos[10].biblioteca").value("Biblioteca Vila Isabel"));
    }

    @Test
    @DisplayName("Transferência de reserva: devolução despacha, chegada separa para o leitor")
    void transferenciaDeReserva() throws Exception {
        Livro livro = livro("Duna");
        List<Long> ex = cadastrar(livro, 2);
        long emp = emprestar(ex.get(0), ana);
        emprestar(ex.get(1), bruno);
        long reserva = reservar(tokenCamila, livro, central, vilaIsabel);
        long t = transferenciaRepository.findAll().stream()
            .filter(s -> s.getReserva() != null && s.getReserva().getId().equals(reserva))
            .findFirst().orElseThrow().getId();
        chamar(HttpMethod.PATCH, "/api/transferencias/" + t + "/aprovar", tokenAdmin, null).andExpect(status().isOk());
        devolver(emp);
        chamar(HttpMethod.PATCH, "/api/transferencias/" + t + "/confirmar-chegada", tokenCarlos, null)
            .andExpect(status().isOk());

        assertThat(historicoRepository.findByExemplarOrderByDataEventoAscIdAsc(
                exemplarRepository.findById(ex.get(0)).orElseThrow()))
            .extracting(HistoricoCirculacao::getEvento, HistoricoCirculacao::getResponsavel)
            .containsExactly(
                tuple("CADASTRO", "Fernanda Reis"),
                tuple("EMPRESTIMO", "Fernanda Reis"),
                tuple("FILA", "Camila Duarte"),
                tuple("DEVOLUCAO", "Fernanda Reis"),
                tuple("TRANSFERENCIA_SAIDA", "Fernanda Reis"),
                tuple("TRANSFERENCIA_CHEGADA", "Carlos Lima"),
                tuple("RESERVA", "Carlos Lima"));
    }

    @Test
    @DisplayName("Rotina agendada registra o responsável como \"Sistema\"")
    void rotinaComoSistema() throws Exception {
        Livro livro = livro("1984");
        long ex = cadastrar(livro, 1).get(0);
        long emp = emprestar(ex, ana);
        reservar(tokenCamila, livro, central, central);
        devolver(emp);
        vencerReservaDe(camila);
        filaEsperaService.expirarVencidas(); // como o agendador: sem requisição, sem token

        List<HistoricoCirculacao> l = historicoRepository.findByExemplarOrderByDataEventoAscIdAsc(
            exemplarRepository.findById(ex).orElseThrow());
        HistoricoCirculacao ultima = l.get(l.size() - 1);
        assertThat(ultima.getEvento()).isEqualTo("RESERVA");
        assertThat(ultima.getResponsavel()).isEqualTo("Sistema");
        assertThat(ultima.getUsuario()).isNull();
        chamar(HttpMethod.GET, "/api/historico?exemplarId=" + ex + "&tamanho=1", tokenAdmin, null)
            .andExpect(jsonPath("$.itens[0].responsavel").value("Sistema"));
    }

    @Test
    @DisplayName("Filtros do histórico (evento, biblioteca, exemplar, período) e permissões")
    void filtros() throws Exception {
        Livro livro = livro("Sapiens");
        List<Long> ex = cadastrar(livro, 3);
        emprestar(ex.get(0), ana);
        chamar(HttpMethod.POST, "/api/transferencias/avulsa", tokenAdmin,
            "{\"exemplarIds\":[" + ex.get(1) + "],\"bibliotecaDestinoId\":" + vilaIsabel.getId() + "}")
            .andExpect(status().isOk());

        chamar(HttpMethod.GET, "/api/historico", tokenAdmin, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItens").value(5))
            .andExpect(jsonPath("$.itens[0].evento").value("TRANSFERENCIA_SAIDA"));   // mais recente primeiro
        chamar(HttpMethod.GET, "/api/historico?evento=cadastro", tokenAdmin, null)
            .andExpect(jsonPath("$.totalItens").value(3))
            .andExpect(jsonPath("$.itens[*].eventoRotulo", everyItem(is("Cadastro no acervo"))));
        chamar(HttpMethod.GET, "/api/historico?exemplarId=" + ex.get(0), tokenAdmin, null)
            .andExpect(jsonPath("$.totalItens").value(2));
        chamar(HttpMethod.GET, "/api/historico?bibliotecaId=" + vilaIsabel.getId(), tokenAdmin, null)
            .andExpect(jsonPath("$.totalItens").value(0));
        chamar(HttpMethod.GET, "/api/historico?bibliotecaId=" + central.getId() + "&evento=EMPRESTIMO", tokenAdmin, null)
            .andExpect(jsonPath("$.totalItens").value(1))
            .andExpect(jsonPath("$.itens[0].exemplarId").value(ex.get(0)));
        String hoje = LocalDate.now().toString(), amanha = LocalDate.now().plusDays(1).toString();
        chamar(HttpMethod.GET, "/api/historico?de=" + hoje + "&ate=" + hoje, tokenAdmin, null)
            .andExpect(jsonPath("$.totalItens").value(5));
        chamar(HttpMethod.GET, "/api/historico?de=" + amanha, tokenAdmin, null)
            .andExpect(jsonPath("$.totalItens").value(0));
        chamar(HttpMethod.GET, "/api/historico?tamanho=2&pagina=2", tokenAdmin, null)
            .andExpect(jsonPath("$.itens.length()").value(1)).andExpect(jsonPath("$.totalPaginas").value(3));

        chamar(HttpMethod.GET, "/api/historico?evento=XYZ", tokenAdmin, null).andExpect(status().isBadRequest());
        chamar(HttpMethod.GET, "/api/historico?de=" + amanha + "&ate=" + hoje, tokenAdmin, null)
            .andExpect(status().isBadRequest());
        chamar(HttpMethod.GET, "/api/historico/exemplar/999999", tokenAdmin, null)
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("não encontrado")));
        chamar(HttpMethod.GET, "/api/historico/eventos", tokenAdmin, null)
            .andExpect(jsonPath("$.length()").value(9))
            .andExpect(jsonPath("$[*].rotulo", hasItem("Saída para transferência")));

        for (String token : new String[]{tokenFernanda, tokenAna}) {
            chamar(HttpMethod.GET, "/api/historico", token, null).andExpect(status().isForbidden());
            chamar(HttpMethod.GET, "/api/historico/exemplar/" + ex.get(0), token, null).andExpect(status().isForbidden());
            chamar(HttpMethod.GET, "/api/historico/eventos", token, null).andExpect(status().isForbidden());
        }
        chamar(HttpMethod.GET, "/api/historico", null, null).andExpect(status().isUnauthorized());
    }

    // ───────────────────────── Apoio ─────────────────────────

    private List<Long> cadastrar(Livro livro, int quantidade) throws Exception {
        String json = chamar(HttpMethod.POST, "/api/exemplares", tokenFernanda,
            "{\"livroId\":" + livro.getId() + ",\"conservacao\":\"BOM\",\"quantidade\":" + quantidade + "}")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return java.util.Arrays.stream(json.split("\\{\"id\":"))
            .skip(1).map(p -> Long.parseLong(p.replaceAll("^(\\d+).*", "$1")))
            .filter(id -> exemplarRepository.existsById(id) && exemplarRepository.findById(id).orElseThrow()
                .getLivro().getId().equals(livro.getId()))
            .distinct().toList();
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

    private long reservar(String token, Livro livro, Biblioteca fila, Biblioteca destino) throws Exception {
        return id(chamar(HttpMethod.POST, "/api/reservas", token,
            "{\"livroId\":" + livro.getId() + ",\"bibliotecaFilaId\":" + fila.getId()
            + ",\"bibliotecaDestinoId\":" + destino.getId() + "}")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void vencerReservaDe(Usuario u) {
        Reserva r = reservaRepository.findByUsuario(u).stream()
            .filter(x -> "DISPONIVEL".equals(x.getStatus())).findFirst().orElseThrow();
        r.setDataExpiracao(LocalDateTime.now().minusMinutes(1));
        reservaRepository.save(r);
    }
}
