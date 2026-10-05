package com.circulabook.service;

import com.circulabook.dto.CadastroExemplarDTO;
import com.circulabook.model.*;
import com.circulabook.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.circulabook.model.StatusExemplar.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Máquina de estados do exemplar (CONTEXTO §4) e roteiros ES da §9.2.
 * Cada teste monta os próprios dados (o data.sql não roda nos testes) e
 * confere a invariante da fila depois de cada operação (ES-15).
 */
@SpringBootTest
@Transactional
@Import(VerificadorConsistencia.class)
class MaquinaEstadosExemplarTest {

    @Autowired private BibliotecaRepository bibliotecaRepository;
    @Autowired private LivroRepository livroRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private EmprestimoRepository emprestimoRepository;
    @Autowired private ReservaRepository reservaRepository;

    @Autowired private EstadoExemplarService estadoExemplar;
    @Autowired private EmprestimoService emprestimoService;
    @Autowired private ReservaService reservaService;
    @Autowired private FilaEsperaService filaEsperaService;
    @Autowired private ExemplarService exemplarService;
    @Autowired private VerificadorConsistencia verificador;

    private Biblioteca central, vilaIsabel;
    private Usuario u1, u2, u3, u4, bibVI;

    @BeforeEach
    void montarRede() {
        central = biblioteca("Biblioteca Central");
        vilaIsabel = biblioteca("Biblioteca Vila Isabel");
        u1 = usuario("UsuÃ¡rio 1", "COMUM", null);
        u2 = usuario("UsuÃ¡rio 2", "COMUM", null);
        u3 = usuario("UsuÃ¡rio 3", "COMUM", null);
        u4 = usuario("UsuÃ¡rio 4", "COMUM", null);
        bibVI = usuario("BibliotecÃ¡rio Vila Isabel", "BIBLIOTECARIO", vilaIsabel);
    }

    // ───────────────────────── Tabela de transições (§4.3) ─────────────────────────

    @ParameterizedTest(name = "{0} -> {1} é válida")
    @CsvSource({
        "DISPONIVEL,EMPRESTADO",                 // T1
        "EMPRESTADO,DISPONIVEL",                 // T2
        "EMPRESTADO,EMPRESTADO_RESERVADO",       // T3
        "EMPRESTADO_RESERVADO,RESERVADO",        // T4
        "RESERVADO,EMPRESTADO_RESERVADO",        // T5
        "RESERVADO,EMPRESTADO",                  // T6
        "RESERVADO,DISPONIVEL",                  // T7
        "RESERVADO,RESERVADO",                   // T7b
        "DISPONIVEL,EM_TRANSFERENCIA",           // T8 (prevista)
        "RESERVADO,EM_TRANSFERENCIA",            // T9 (prevista)
        "EM_TRANSFERENCIA,RESERVADO",            // T10 (prevista)
        "EM_TRANSFERENCIA,DISPONIVEL",           // T11 (prevista)
        "EMPRESTADO_RESERVADO,EMPRESTADO",       // T12
        "DISPONIVEL,INDISPONIVEL",               // T13
        "EMPRESTADO,INDISPONIVEL",               // T13 (devolução DANIFICADO)
        "EMPRESTADO_RESERVADO,INDISPONIVEL",     // T13 (devolução DANIFICADO)
        "RESERVADO,INDISPONIVEL",                // T13
        "EM_TRANSFERENCIA,INDISPONIVEL",         // T13 (chegou danificado)
        "INDISPONIVEL,DISPONIVEL",               // T14
        "INDISPONIVEL,RESERVADO",                // T14b
    })
    void transicoesValidas(String de, String para) {
        assertThat(StatusExemplar.transicaoValida(de, para)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} é recusada")
    @CsvSource({
        "DISPONIVEL,RESERVADO",
        "DISPONIVEL,EMPRESTADO_RESERVADO",
        "DISPONIVEL,DISPONIVEL",
        "EMPRESTADO,RESERVADO",
        "EMPRESTADO,EM_TRANSFERENCIA",
        "EMPRESTADO_RESERVADO,DISPONIVEL",
        "EM_TRANSFERENCIA,EMPRESTADO",
        "INDISPONIVEL,EMPRESTADO",
        "INDISPONIVEL,EM_TRANSFERENCIA",
        "RESERVADO,PERDIDO",
    })
    void transicoesInvalidas(String de, String para) {
        assertThat(StatusExemplar.transicaoValida(de, para)).isFalse();
    }

    @Test
    @DisplayName("T15: exemplar novo só nasce DISPONIVEL ou RESERVADO")
    void criacao() {
        assertThat(StatusExemplar.transicaoValida(null, DISPONIVEL)).isTrue();
        assertThat(StatusExemplar.transicaoValida(null, RESERVADO)).isTrue();
        assertThat(StatusExemplar.transicaoValida(null, EMPRESTADO)).isFalse();
        assertThat(StatusExemplar.transicaoValida(null, INDISPONIVEL)).isFalse();
    }

    @Test
    @DisplayName("mudarStatus recusa transição inválida com mensagem em português e não altera o exemplar")
    void mudarStatusRecusaInvalida() {
        Exemplar e = exemplar(livro("Dom Casmurro"), central, DISPONIVEL);
        assertThatThrownBy(() -> estadoExemplar.mudarStatus(e, RESERVADO))
            .hasMessageContaining("não permitida")
            .hasMessageContaining("de Disponível para Reservado");
        assertThat(status(e)).isEqualTo(DISPONIVEL);
    }

    // ───────────────────────── Empréstimo e devolução ─────────────────────────

    @Test
    @DisplayName("T1 empréstimo e T2 devolução BOM sem fila")
    void t1T2() {
        Exemplar e = exemplar(livro("Dom Casmurro"), central, DISPONIVEL);
        Emprestimo emp = emprestimoService.registrar(e.getId(), u1.getId());
        assertThat(status(e)).isEqualTo(EMPRESTADO);
        consistente();

        emprestimoService.devolver(emp.getId(), "BOM");
        assertThat(status(e)).isEqualTo(DISPONIVEL);
        consistente();
    }

    @Test
    @DisplayName("T4: devolução BOM com fila separa o exemplar para o 1º (e a marca da fila é reavaliada)")
    void t4DevolucaoBomComFila() {
        Livro livro = livro("O Hobbit");
        Exemplar x1 = exemplar(livro, vilaIsabel, EMPRESTADO_RESERVADO);
        Exemplar x2 = exemplar(livro, vilaIsabel, EMPRESTADO_RESERVADO);
        Emprestimo emp1 = emprestimoAtivo(x1, u3);
        emprestimoAtivo(x2, u4);
        Reserva r = reserva(livro, u1, vilaIsabel, "PENDENTE", null, dias(-2));
        consistente();

        emprestimoService.devolver(emp1.getId(), "BOM");

        assertThat(status(x1)).isEqualTo(RESERVADO);
        assertThat(r.getStatus()).isEqualTo("DISPONIVEL");
        assertThat(r.getExemplar().getId()).isEqualTo(x1.getId());
        assertThat(status(x2)).isEqualTo(EMPRESTADO); // T12: a fila esvaziou
        consistente();
    }

    @Test
    @DisplayName("Devolução DANIFICADO (RN21/§4.4): INDISPONIVEL, empréstimo DEVOLVIDO, fila não é promovida")
    void devolucaoDanificado() {
        Livro livro = livro("O Hobbit");
        Exemplar x1 = exemplar(livro, vilaIsabel, EMPRESTADO_RESERVADO);
        Exemplar x2 = exemplar(livro, vilaIsabel, EMPRESTADO_RESERVADO);
        Emprestimo emp1 = emprestimoAtivo(x1, u3);
        emprestimoAtivo(x2, u4);
        Reserva r = reserva(livro, u1, vilaIsabel, "PENDENTE", null, dias(-2));

        emprestimoService.devolver(emp1.getId(), "DANIFICADO");

        Exemplar x1Atual = exemplarRepository.findById(x1.getId()).orElseThrow();
        assertThat(x1Atual.getStatus()).isEqualTo(INDISPONIVEL);
        assertThat(x1Atual.getEstadoConservacao()).isEqualTo("DANIFICADO");
        assertThat(emprestimoRepository.findById(emp1.getId()).orElseThrow().getStatus()).isEqualTo("DEVOLVIDO");
        assertThat(r.getStatus()).isEqualTo("PENDENTE");
        assertThat(r.getExemplar()).isNull();
        assertThat(status(x2)).isEqualTo(EMPRESTADO_RESERVADO); // a fila continua
        consistente();
    }

    @Test
    @DisplayName("Devolução só aceita BOM ou DANIFICADO")
    void devolucaoCondicaoInvalida() {
        Exemplar e = exemplar(livro("Dom Casmurro"), central, EMPRESTADO);
        Emprestimo emp = emprestimoAtivo(e, u1);
        assertThatThrownBy(() -> emprestimoService.devolver(emp.getId(), "PERDIDO"))
            .hasMessageContaining("Bom ou Danificado");
        assertThat(status(e)).isEqualTo(EMPRESTADO);
    }

    // ───────────────────────── Roteiros ES (§9.2) ─────────────────────────

    @Test
    @DisplayName("ES-01 (T3) e ES-02 (T12): entrar e sair da fila de Duna na Central")
    void es01Es02() {
        Livro duna = livro("Duna");
        Exemplar d1 = exemplar(duna, central, EMPRESTADO);
        Exemplar d2 = exemplar(duna, central, EMPRESTADO);
        emprestimoAtivo(d1, u2);
        emprestimoAtivo(d2, u3);
        consistente();

        Reserva r = reservaService.criar(duna.getId(), u1.getId(), central.getId(), central.getId());
        assertThat(status(d1)).isEqualTo(EMPRESTADO_RESERVADO);
        assertThat(status(d2)).isEqualTo(EMPRESTADO_RESERVADO);
        consistente();

        reservaService.cancelar(r.getId());
        assertThat(status(d1)).isEqualTo(EMPRESTADO);
        assertThat(status(d2)).isEqualTo(EMPRESTADO);
        consistente();
    }

    @Test
    @DisplayName("ES-03 (T5) e ES-04 (T12): U1 retira o Harry com U2 na fila; depois U2 cancela")
    void es03Es04() {
        CenarioHarry h = cenarioHarry();

        emprestimoService.registrar(h.h1.getId(), u1.getId());
        assertThat(status(h.h1)).isEqualTo(EMPRESTADO_RESERVADO);
        assertThat(h.r2.getStatus()).isEqualTo("RETIRADA");
        assertThat(status(h.h2)).isEqualTo(EMPRESTADO_RESERVADO);
        consistente();

        reservaService.cancelar(h.r3.getId());
        assertThat(status(h.h1)).isEqualTo(EMPRESTADO);
        assertThat(status(h.h2)).isEqualTo(EMPRESTADO);
        consistente();
    }

    @Test
    @DisplayName("ES-05 (T6): U2 cancela antes e U1 retira com a fila vazia")
    void es05() {
        CenarioHarry h = cenarioHarry();

        reservaService.cancelar(h.r3.getId());
        assertThat(status(h.h2)).isEqualTo(EMPRESTADO);
        assertThat(status(h.h1)).isEqualTo(RESERVADO);
        consistente();

        emprestimoService.registrar(h.h1.getId(), u1.getId());
        assertThat(status(h.h1)).isEqualTo(EMPRESTADO);
        consistente();
    }

    @Test
    @DisplayName("ES-07 (T7b) e ES-08 (T7): expirações sucessivas da retirada")
    void es07Es08() {
        CenarioHarry h = cenarioHarry();

        h.r2.setDataExpiracao(LocalDateTime.now().minusHours(1));
        filaEsperaService.expirarVencidas();
        assertThat(h.r2.getStatus()).isEqualTo("EXPIRADA");
        assertThat(status(h.h1)).isEqualTo(RESERVADO);
        assertThat(h.r3.getStatus()).isEqualTo("DISPONIVEL");
        assertThat(h.r3.getExemplar().getId()).isEqualTo(h.h1.getId());
        assertThat(status(h.h2)).isEqualTo(EMPRESTADO); // ninguém mais PENDENTE
        consistente();

        h.r3.setDataExpiracao(LocalDateTime.now().minusHours(1));
        filaEsperaService.expirarVencidas();
        assertThat(h.r3.getStatus()).isEqualTo("EXPIRADA");
        assertThat(status(h.h1)).isEqualTo(DISPONIVEL);
        assertThat(status(h.h2)).isEqualTo(EMPRESTADO);
        consistente();
    }

    @Test
    @DisplayName("ES-11 (T14): reativar sem fila deixa DISPONIVEL")
    void es11T14() {
        Exemplar s1 = exemplar(livro("Steve Jobs"), vilaIsabel, INDISPONIVEL);
        exemplarService.reativar(s1.getId(), bibVI.getId());
        assertThat(status(s1)).isEqualTo(DISPONIVEL);
        consistente();
    }

    @Test
    @DisplayName("ES-11 (T14b): reativar com fila vai a RESERVADO e atende o 1º")
    void es11T14b() {
        Livro jobs = livro("Steve Jobs");
        Exemplar s1 = exemplar(jobs, vilaIsabel, INDISPONIVEL);
        Exemplar s2 = exemplar(jobs, vilaIsabel, EMPRESTADO_RESERVADO);
        emprestimoAtivo(s2, u3);
        Reserva r = reserva(jobs, u1, vilaIsabel, "PENDENTE", null, dias(-1));
        consistente();

        exemplarService.reativar(s1.getId(), bibVI.getId());
        assertThat(status(s1)).isEqualTo(RESERVADO);
        assertThat(r.getStatus()).isEqualTo("DISPONIVEL");
        assertThat(r.getExemplar().getId()).isEqualTo(s1.getId());
        assertThat(status(s2)).isEqualTo(EMPRESTADO);
        consistente();
    }

    @Test
    @DisplayName("ES-12 (T13): DISPONIVEL marcado indisponível sai da lista de empréstimo")
    void es12() {
        Exemplar e = exemplar(livro("Dom Casmurro"), vilaIsabel, DISPONIVEL);
        exemplarService.marcarIndisponivel(e.getId(), bibVI.getId(), "Capa rasgada");
        assertThat(status(e)).isEqualTo(INDISPONIVEL);
        assertThatThrownBy(() -> emprestimoService.registrar(e.getId(), u1.getId()))
            .hasMessageContaining("não pode ser emprestado");
        consistente();
    }

    @Test
    @DisplayName("ES-13 (T13 de RESERVADO): a reserva volta a PENDENTE mantendo a posição")
    void es13() {
        CenarioHarry h = cenarioHarry();
        LocalDateTime dataOriginal = h.r2.getDataReserva();

        exemplarService.marcarIndisponivel(h.h1.getId(), bibVI.getId(), null);

        assertThat(status(h.h1)).isEqualTo(INDISPONIVEL);
        assertThat(h.r2.getStatus()).isEqualTo("PENDENTE");
        assertThat(h.r2.getExemplar()).isNull();
        assertThat(h.r2.getDataReserva()).isEqualTo(dataOriginal);
        List<Reserva> fila = reservaRepository.findByLivroAndBibliotecaFilaAndStatusOrderByDataReservaAsc(
            h.harry, vilaIsabel, "PENDENTE");
        assertThat(fila).extracting(Reserva::getId).containsExactly(h.r2.getId(), h.r3.getId());
        assertThat(status(h.h2)).isEqualTo(EMPRESTADO_RESERVADO);
        consistente();
    }

    @Test
    @DisplayName("T15: cadastro com fila nasce RESERVADO e atende o 1º; sem fila nasce DISPONIVEL")
    void t15Cadastro() {
        Livro livro = livro("Fahrenheit 451");
        Exemplar x = exemplar(livro, vilaIsabel, EMPRESTADO_RESERVADO);
        emprestimoAtivo(x, u3);
        Reserva r = reserva(livro, u1, vilaIsabel, "PENDENTE", null, dias(-1));

        Exemplar novo = exemplarService.cadastrar(dto(livro, vilaIsabel), bibVI.getId()).get(0);
        assertThat(status(novo)).isEqualTo(RESERVADO);
        assertThat(r.getStatus()).isEqualTo("DISPONIVEL");
        assertThat(r.getExemplar().getId()).isEqualTo(novo.getId());
        assertThat(status(x)).isEqualTo(EMPRESTADO);
        consistente();

        Exemplar outro = exemplarService.cadastrar(dto(livro, vilaIsabel), bibVI.getId()).get(0);
        assertThat(status(outro)).isEqualTo(DISPONIVEL);
        consistente();
    }

    @Test
    @DisplayName("Operações fora da tabela são recusadas")
    void operacoesInvalidas() {
        Livro livro = livro("Memórias Póstumas");
        Exemplar emprestado = exemplar(livro, vilaIsabel, EMPRESTADO);
        emprestimoAtivo(emprestado, u3);
        Exemplar disponivel = exemplar(livro, vilaIsabel, DISPONIVEL);
        Exemplar indisponivel = exemplar(livro, vilaIsabel, INDISPONIVEL);

        assertThatThrownBy(() -> exemplarService.marcarIndisponivel(emprestado.getId(), bibVI.getId(), null))
            .hasMessageContaining("disponível ou reservado");
        assertThatThrownBy(() -> exemplarService.reativar(disponivel.getId(), bibVI.getId()))
            .hasMessageContaining("Só um exemplar indisponível");
        assertThatThrownBy(() -> emprestimoService.registrar(indisponivel.getId(), u1.getId()))
            .hasMessageContaining("não pode ser emprestado");
        assertThat(status(emprestado)).isEqualTo(EMPRESTADO);
        assertThat(status(disponivel)).isEqualTo(DISPONIVEL);
        assertThat(status(indisponivel)).isEqualTo(INDISPONIVEL);
        consistente();
    }

    @Test
    @DisplayName("ES-15: o verificador acusa violações da invariante")
    void verificadorDetectaViolacoes() {
        Livro livro = livro("Livro Inconsistente");
        exemplar(livro, central, EMPRESTADO_RESERVADO);   // sem fila
        exemplar(livro, vilaIsabel, RESERVADO);           // sem reserva
        assertThat(verificador.violacoes()).hasSize(2);
    }

    // ───────────────────────── Montagem dos cenários ─────────────────────────

    /** Harry Potter na VI: H1 separado para U1 (R2), H2 emprestado à U3, U2 (R3) na fila. */
    private record CenarioHarry(Livro harry, Exemplar h1, Exemplar h2, Reserva r2, Reserva r3) {}

    private CenarioHarry cenarioHarry() {
        Livro harry = livro("Harry Potter e a Pedra Filosofal");
        Exemplar h1 = exemplar(harry, vilaIsabel, RESERVADO);
        Exemplar h2 = exemplar(harry, vilaIsabel, EMPRESTADO_RESERVADO);
        emprestimoAtivo(h2, u3);
        Reserva r2 = reserva(harry, u1, vilaIsabel, "DISPONIVEL", h1, dias(-4));
        r2.setDataExpiracao(LocalDateTime.now().plusDays(2));
        Reserva r3 = reserva(harry, u2, vilaIsabel, "PENDENTE", null, dias(-1));
        consistente();
        return new CenarioHarry(harry, h1, h2, r2, r3);
    }

    private void consistente() {
        assertThat(verificador.violacoes()).isEmpty();
    }

    private String status(Exemplar e) {
        return exemplarRepository.findById(e.getId()).orElseThrow().getStatus();
    }

    private static LocalDateTime dias(int n) {
        return LocalDateTime.now().plusDays(n);
    }

    private Biblioteca biblioteca(String nome) {
        Biblioteca b = new Biblioteca();
        b.setNome(nome);
        return bibliotecaRepository.save(b);
    }

    private Usuario usuario(String nome, String tipo, Biblioteca biblioteca) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(nome.toLowerCase().replace(' ', '.') + "@teste.com");
        u.setSenhaHash("x");
        u.setTipo(tipo);
        u.setBiblioteca(biblioteca);
        return usuarioRepository.save(u);
    }

    private Livro livro(String titulo) {
        Livro l = new Livro();
        l.setTitulo(titulo);
        l.setAutor("Autor de " + titulo);
        return livroRepository.save(l);
    }

    /** Montagem direta do cenário (como faria o seed); as operações testadas usam a máquina de estados. */
    private Exemplar exemplar(Livro livro, Biblioteca biblioteca, String status) {
        Exemplar e = new Exemplar();
        e.setLivro(livro);
        e.setBiblioteca(biblioteca);
        e.setStatus(status);
        e.setEstadoConservacao("BOM");
        return exemplarRepository.save(e);
    }

    private Emprestimo emprestimoAtivo(Exemplar exemplar, Usuario usuario) {
        Emprestimo emp = new Emprestimo();
        emp.setExemplar(exemplar);
        emp.setUsuario(usuario);
        emp.setBiblioteca(exemplar.getBiblioteca());
        emp.setDataEmprestimo(LocalDateTime.now().minusDays(3));
        emp.setDataPrevDevolucao(LocalDateTime.now().plusDays(11));
        emp.setStatus("ATIVO");
        return emprestimoRepository.save(emp);
    }

    private Reserva reserva(Livro livro, Usuario usuario, Biblioteca biblioteca, String status,
                            Exemplar exemplar, LocalDateTime data) {
        Reserva r = new Reserva();
        r.setLivro(livro);
        r.setUsuario(usuario);
        r.setBibliotecaFila(biblioteca);
        r.setBibliotecaDestino(biblioteca);
        r.setExemplar(exemplar);
        r.setDataReserva(data);
        r.setDataExpiracao(data.plusDays(3));
        r.setStatus(status);
        return reservaRepository.save(r);
    }

    private static CadastroExemplarDTO dto(Livro livro, Biblioteca biblioteca) {
        CadastroExemplarDTO d = new CadastroExemplarDTO();
        d.setLivroId(livro.getId());
        d.setBibliotecaId(biblioteca.getId());
        d.setConservacao("NOVO");
        return d;
    }
}
