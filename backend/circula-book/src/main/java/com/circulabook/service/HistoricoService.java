package com.circulabook.service;

import com.circulabook.dto.ContaDTOs.Pagina;
import com.circulabook.model.*;
import com.circulabook.repository.ExemplarRepository;
import com.circulabook.repository.HistoricoCirculacaoRepository;
import com.circulabook.repository.UsuarioRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Histórico de circulação (RN06, A4).
 *
 * As mudanças de status do exemplar são registradas pela própria máquina de estados
 * (EstadoExemplarService.mudarStatus), então nenhuma transição fica de fora. Eventos que
 * não mudam o status (ex.: o exemplar separado para o 1º da fila) são registrados aqui
 * diretamente pelos especialistas.
 *
 * Responsável = o usuário autenticado na requisição; sem requisição (rotina agendada) = "Sistema".
 */
@Service
public class HistoricoService {

    public static final String SISTEMA = "Sistema";

    /** Eventos e como aparecem na tela. */
    public static final Map<String, String> ROTULO_EVENTO = Map.of(
        "CADASTRO", "Cadastro no acervo",
        "EMPRESTIMO", "Empréstimo",
        "DEVOLUCAO", "Devolução",
        "RESERVA", "Reserva",
        "FILA", "Fila de espera",
        "BAIXA", "Marcado como indisponível",
        "REATIVACAO", "Reativação",
        "TRANSFERENCIA_SAIDA", "Saída para transferência",
        "TRANSFERENCIA_CHEGADA", "Chegada de transferência");

    @Autowired private HistoricoCirculacaoRepository historicoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ExemplarRepository exemplarRepository;

    public record EventoDTO(Long id, LocalDateTime data, String evento, String eventoRotulo,
                            Long exemplarId, Long livroId, String titulo,
                            Long bibliotecaId, String biblioteca, String responsavel, String observacoes) {}

    public record LinhaDoTempoDTO(Long exemplarId, String titulo, String autor, String bibliotecaAtual,
                                  String status, String statusRotulo, List<EventoDTO> eventos) {}

    // ───────────────────────── Registro ─────────────────────────

    public void registrar(Exemplar exemplar, String evento, Biblioteca biblioteca, String observacoes) {
        Usuario responsavel = responsavelAtual();
        HistoricoCirculacao h = new HistoricoCirculacao();
        h.setExemplar(exemplar);
        h.setEvento(evento);
        h.setUsuario(responsavel);
        h.setResponsavel(responsavel != null ? responsavel.getNome() : SISTEMA);
        h.setBiblioteca(biblioteca);
        h.setObservacoes(observacoes);
        h.setDataEvento(LocalDateTime.now());
        historicoRepository.save(h);
    }

    /** Usuário do token da requisição em andamento; null fora de requisição (rotinas). */
    private Usuario responsavelAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            try {
                return usuarioRepository.findById(Long.valueOf(jwt.getName())).orElse(null);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // ───────────────────────── Consulta (Admin) ─────────────────────────

    /**
     * Eventos da rede, mais recentes primeiro. Filtros opcionais: evento, biblioteca,
     * exemplar e período (datas inclusive).
     */
    public Pagina<EventoDTO> listar(String evento, Long bibliotecaId, Long exemplarId,
                                    LocalDate de, LocalDate ate, int pagina, int tamanho) {
        return listar(evento, bibliotecaId, exemplarId, de, ate, false, pagina, tamanho);
    }

    /**
     * Igual ao anterior; com ocultarFila, deixa de fora as marcas de fila (evento FILA, T3/T12),
     * a não ser que o filtro de evento peça justamente FILA.
     */
    public Pagina<EventoDTO> listar(String evento, Long bibliotecaId, Long exemplarId,
                                    LocalDate de, LocalDate ate, boolean ocultarFila, int pagina, int tamanho) {
        if (pagina < 0) throw new RuntimeException("A página começa em 0.");
        if (tamanho < 1 || tamanho > 50) throw new RuntimeException("O tamanho da página deve ser de 1 a 50.");
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new RuntimeException("A data inicial não pode ser depois da data final.");
        }
        String ev = evento == null || evento.isBlank() ? null : evento.trim().toUpperCase();
        if (ev != null && !ROTULO_EVENTO.containsKey(ev)) {
            throw new RuntimeException("Evento inválido.");
        }

        Specification<HistoricoCirculacao> filtro = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (ev != null) p.add(cb.equal(root.get("evento"), ev));
            else if (ocultarFila) p.add(cb.notEqual(root.get("evento"), "FILA"));
            if (bibliotecaId != null) p.add(cb.equal(root.get("biblioteca").get("id"), bibliotecaId));
            if (exemplarId != null) p.add(cb.equal(root.get("exemplar").get("id"), exemplarId));
            if (de != null) p.add(cb.greaterThanOrEqualTo(root.get("dataEvento"), de.atStartOfDay()));
            if (ate != null) p.add(cb.lessThan(root.get("dataEvento"), ate.plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<HistoricoCirculacao> page = historicoRepository.findAll(filtro,
            PageRequest.of(pagina, tamanho, Sort.by(Sort.Order.desc("dataEvento"), Sort.Order.desc("id"))));
        return new Pagina<>(page.getContent().stream().map(HistoricoService::paraDTO).toList(),
            pagina, tamanho, page.getTotalElements(), page.getTotalPages());
    }

    /** Linha do tempo de um exemplar, do mais antigo para o mais recente. */
    public LinhaDoTempoDTO linhaDoTempo(Long exemplarId) {
        Exemplar e = exemplarRepository.findById(exemplarId)
            .orElseThrow(() -> new RuntimeException("Exemplar nº " + exemplarId + " não encontrado."));
        List<EventoDTO> eventos = historicoRepository.findByExemplarOrderByDataEventoAscIdAsc(e).stream()
            .map(HistoricoService::paraDTO).toList();
        return new LinhaDoTempoDTO(e.getId(), e.getLivro().getTitulo(), e.getLivro().getAutor(),
            e.getBiblioteca().getNome(), e.getStatus(), StatusExemplar.rotulo(e.getStatus()), eventos);
    }

    public List<HistoricoCirculacao> obterPorExemplar(Exemplar exemplar) {
        return historicoRepository.findByExemplarOrderByDataEventoDesc(exemplar);
    }

    private static EventoDTO paraDTO(HistoricoCirculacao h) {
        String responsavel = h.getResponsavel() != null ? h.getResponsavel()
            : h.getUsuario() != null ? h.getUsuario().getNome() : SISTEMA;
        Exemplar e = h.getExemplar();
        return new EventoDTO(h.getId(), h.getDataEvento(), h.getEvento(),
            ROTULO_EVENTO.getOrDefault(h.getEvento(), h.getEvento()),
            e.getId(), e.getLivro().getId(), e.getLivro().getTitulo(),
            h.getBiblioteca() != null ? h.getBiblioteca().getId() : null,
            h.getBiblioteca() != null ? h.getBiblioteca().getNome() : null,
            responsavel, h.getObservacoes());
    }
}
