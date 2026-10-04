package com.circulabook.service;

import com.circulabook.dto.CadastroExemplarDTO;
import com.circulabook.model.*;
import static com.circulabook.model.StatusExemplar.*;
import com.circulabook.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Exemplares: entrada no acervo (RN10) e gerenciamento (T13/T14).
 */
@Service
public class ExemplarService {

    public static final int QUANTIDADE_MAXIMA = 50;
    private static final Set<String> CONSERVACOES_NO_CADASTRO = Set.of("NOVO", "BOM", "USADO");

    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private LivroRepository livroRepository;
    @Autowired private BibliotecaRepository bibliotecaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private HistoricoService historicoService;
    @Autowired private FilaEsperaService filaEsperaService;
    @Autowired private EstadoExemplarService estadoExemplar;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private SolicitacaoTransferenciaRepository transferenciaRepository;

    public List<Exemplar> obterTodos() {
        return exemplarRepository.findAll();
    }

    public List<Exemplar> obterPorBiblioteca(Long bibliotecaId) {
        Biblioteca b = bibliotecaRepository.findById(bibliotecaId)
            .orElseThrow(() -> new RuntimeException("Biblioteca não encontrada: ID " + bibliotecaId));
        return exemplarRepository.findByBiblioteca(b);
    }

    public List<Exemplar> obterPorLivro(Long livroId) {
        Livro l = livroRepository.findById(livroId)
            .orElseThrow(() -> new RuntimeException("Livro não encontrado: ID " + livroId));
        return exemplarRepository.findByLivro(l);
    }

    /**
     * B4 — entrada de exemplares no acervo (RN10).
     * Só o bibliotecário cadastra, sempre na própria biblioteca, e apenas de livro
     * já cadastrado pelo Admin. Cria {@code quantidade} exemplares (1 a 50).
     * T15: cada um nasce DISPONIVEL ou, se a biblioteca tem fila do título,
     * RESERVADO atendendo o próximo da fila.
     */
    @Transactional
    public List<Exemplar> cadastrar(CadastroExemplarDTO dto, Long bibliotecarioId) {

        Usuario bibliotecario = usuarioRepository.findById(bibliotecarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + bibliotecarioId));
        if (!"BIBLIOTECARIO".equals(bibliotecario.getTipo()) || bibliotecario.getBiblioteca() == null) {
            throw new RuntimeException("Apenas bibliotecários cadastram exemplares, na própria biblioteca.");
        }
        Biblioteca biblioteca = bibliotecario.getBiblioteca();
        if (dto.getBibliotecaId() != null && !dto.getBibliotecaId().equals(biblioteca.getId())) {
            throw new RuntimeException("Só é possível cadastrar exemplares na sua biblioteca.");
        }

        if (dto.getLivroId() == null) {
            throw new RuntimeException("Escolha um livro já cadastrado no catálogo.");
        }
        Livro livro = livroRepository.findById(dto.getLivroId())
            .orElseThrow(() -> new RuntimeException("Livro não encontrado: ID " + dto.getLivroId()));

        int quantidade = dto.getQuantidade() != null ? dto.getQuantidade() : 1;
        if (quantidade < 1 || quantidade > QUANTIDADE_MAXIMA) {
            throw new RuntimeException("A quantidade deve ser de 1 a " + QUANTIDADE_MAXIMA + " exemplares.");
        }
        String conservacao = dto.getConservacao() != null ? dto.getConservacao().trim().toUpperCase() : "NOVO";
        if (!CONSERVACOES_NO_CADASTRO.contains(conservacao)) {
            throw new RuntimeException("A conservação deve ser Novo, Bom ou Usado.");
        }

        List<Exemplar> criados = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            Exemplar exemplar = new Exemplar();
            exemplar.setLivro(livro);
            exemplar.setBiblioteca(biblioteca);
            exemplar.setEstadoConservacao(conservacao);

            // T15: a fila define o status inicial (o exemplar é salvo pela máquina de estados)
            filaEsperaService.liberar(exemplar);
            historicoService.registrar(exemplar, "CADASTRO", bibliotecario, biblioteca,
                "Exemplar cadastrado com status inicial " + exemplar.getStatus() + ".");
            criados.add(exemplar);
        }

        System.out.println("[CIRCULA BOOK] " + quantidade + " exemplar(es) de " + livro.getTitulo()
            + " cadastrado(s) na " + biblioteca.getNome() + ".");
        return criados;
    }

    /**
     * T13 pelo bibliotecário (§4.4): só a partir de DISPONIVEL ou RESERVADO.
     * Emprestado sai de circulação pela devolução DANIFICADO; em transferência, na chegada.
     * RESERVADO: a reserva volta a PENDENTE com a data original (mesma posição na fila)
     * e o pedido de transferência vinculado volta a ficar sem exemplar.
     */
    @Transactional
    public Exemplar marcarIndisponivel(Long exemplarId, Long responsavelId, String motivo) {
        Exemplar exemplar = buscar(exemplarId);
        Usuario responsavel = usuarioRepository.findById(responsavelId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + responsavelId));

        String origem = exemplar.getStatus();
        if (!DISPONIVEL.equals(origem) && !RESERVADO.equals(origem)) {
            throw new RuntimeException("Só é possível marcar como indisponível um exemplar disponível "
                + "ou reservado. O Exemplar nº " + exemplar.getId() + " está "
                + StatusExemplar.rotulo(origem).toLowerCase() + ".");
        }

        Reserva afetada = RESERVADO.equals(origem)
            ? reservaRepository.findFirstByExemplarAndStatusIn(exemplar,
                  List.of("DISPONIVEL", "AGUARDANDO_TRANSFERENCIA")).orElse(null)
            : null;

        estadoExemplar.mudarStatus(exemplar, INDISPONIVEL);

        String obs = "Marcado como indisponível"
            + (motivo != null && !motivo.isBlank() ? ": " + motivo.trim() : ".");
        if (afetada != null) {
            afetada.setStatus("PENDENTE"); // dataReserva intacta: mantém a posição
            afetada.setExemplar(null);
            reservaRepository.save(afetada);
            for (SolicitacaoTransferencia p : transferenciaRepository
                    .findByReservaAndStatusIn(afetada, List.of("PENDENTE", "APROVADA"))) {
                p.setExemplar(null);
                transferenciaRepository.save(p);
            }
            obs += " A reserva de " + afetada.getUsuario().getNome() + " voltou para a fila.";
            estadoExemplar.sincronizarMarcaDeFila(afetada.getLivro(), afetada.getBibliotecaFila());
        }
        estadoExemplar.sincronizarMarcaDeFila(exemplar.getLivro(), exemplar.getBiblioteca());

        historicoService.registrar(exemplar, "BAIXA", responsavel, exemplar.getBiblioteca(), obs);
        return exemplar;
    }

    /** T14/T14b: reativa um exemplar INDISPONIVEL; havendo fila do título, já atende o 1º. */
    @Transactional
    public Exemplar reativar(Long exemplarId, Long responsavelId) {
        Exemplar exemplar = buscar(exemplarId);
        Usuario responsavel = usuarioRepository.findById(responsavelId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + responsavelId));

        if (!INDISPONIVEL.equals(exemplar.getStatus())) {
            throw new RuntimeException("Só um exemplar indisponível pode ser reativado. O Exemplar nº "
                + exemplar.getId() + " está " + StatusExemplar.rotulo(exemplar.getStatus()).toLowerCase() + ".");
        }

        filaEsperaService.liberar(exemplar);
        historicoService.registrar(exemplar, "REATIVACAO", responsavel, exemplar.getBiblioteca(),
            "Exemplar reativado: agora " + StatusExemplar.rotulo(exemplar.getStatus()).toLowerCase() + ".");
        return exemplar;
    }

    public Exemplar buscar(Long exemplarId) {
        return exemplarRepository.findById(exemplarId)
            .orElseThrow(() -> new RuntimeException("Exemplar não encontrado: nº " + exemplarId));
    }

}
