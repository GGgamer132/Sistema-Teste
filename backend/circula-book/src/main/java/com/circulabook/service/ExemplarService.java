package com.circulabook.service;

import com.circulabook.dto.CadastroExemplarDTO;
import com.circulabook.model.*;
import static com.circulabook.model.StatusExemplar.*;
import com.circulabook.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cadastro de exemplares (UC11 / RN10) — Tela 7.
 */
@Service
public class ExemplarService {

    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private LivroRepository livroRepository;
    @Autowired private BibliotecaRepository bibliotecaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
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
     * Cadastra um exemplar, criando o título antes caso seja um livro novo.
     *
     * RN10: exige o ID do bibliotecário responsável e valida que ele pertence
     *       à biblioteca onde o exemplar será cadastrado.
     * T15: nasce DISPONIVEL ou, se a biblioteca tem fila do título, RESERVADO para o 1º.
     */
    @Transactional
    public Exemplar cadastrar(CadastroExemplarDTO dto, Long bibliotecarioId) {

        Usuario bibliotecario = usuarioRepository.findById(bibliotecarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + bibliotecarioId));

        Biblioteca biblioteca = bibliotecaRepository.findById(dto.getBibliotecaId())
            .orElseThrow(() -> new RuntimeException("Biblioteca não encontrada: ID " + dto.getBibliotecaId()));

        // RN10 — só um bibliotecário da própria biblioteca (ou o admin) pode cadastrar
        boolean ehAdmin = "ADMIN".equals(bibliotecario.getTipo());
        boolean ehBibliotecarioDaCasa = "BIBLIOTECARIO".equals(bibliotecario.getTipo())
            && bibliotecario.getBiblioteca() != null
            && bibliotecario.getBiblioteca().getId().equals(biblioteca.getId());

        if (!ehAdmin && !ehBibliotecarioDaCasa) {
            throw new RuntimeException(
                "Apenas um bibliotecário da " + biblioteca.getNome()
                + " pode cadastrar exemplares nesta biblioteca.");
        }

        Livro livro = resolverLivro(dto);

        Exemplar exemplar = new Exemplar();
        exemplar.setLivro(livro);
        exemplar.setBiblioteca(biblioteca);
        exemplar.setEstadoConservacao(
            dto.getEstadoConservacao() != null ? dto.getEstadoConservacao() : "NOVO");

        // T15: a fila define o status inicial (o exemplar é salvo pela máquina de estados)
        filaEsperaService.liberar(exemplar);
        String statusInicial = exemplar.getStatus();

        historicoService.registrar(exemplar, "CADASTRO", bibliotecario, biblioteca,
            "Exemplar cadastrado com status inicial " + statusInicial + ".");

        System.out.println("[CIRCULA BOOK] Exemplar cadastrado: " + livro.getTitulo()
            + " | Biblioteca: " + biblioteca.getNome() + " | Status: " + statusInicial);

        return exemplar;
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

    private Livro resolverLivro(CadastroExemplarDTO dto) {
        if (dto.getLivroId() != null) {
            return livroRepository.findById(dto.getLivroId())
                .orElseThrow(() -> new RuntimeException("Livro não encontrado: ID " + dto.getLivroId()));
        }
        if (dto.getTitulo() == null || dto.getTitulo().isBlank()
            || dto.getAutor() == null || dto.getAutor().isBlank()) {
            throw new RuntimeException("Informe um livro já cadastrado ou preencha título e autor.");
        }
        if (dto.getIsbn() != null && !dto.getIsbn().isBlank()) {
            var existente = livroRepository.findByIsbn(dto.getIsbn());
            if (existente.isPresent()) return existente.get();
        }

        Livro novo = new Livro();
        novo.setTitulo(dto.getTitulo());
        novo.setAutor(dto.getAutor());
        novo.setEditora(dto.getEditora());
        novo.setIsbn(dto.getIsbn());
        novo.setAnoPublicacao(dto.getAnoPublicacao());
        novo.setSinopse(dto.getSinopse());
        if (dto.getCategoriaId() != null) {
            novo.setCategoria(categoriaRepository.findById(dto.getCategoriaId()).orElse(null));
        }
        return livroRepository.save(novo);
    }
}
