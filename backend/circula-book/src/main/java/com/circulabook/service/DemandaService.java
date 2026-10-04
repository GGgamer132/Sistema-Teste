package com.circulabook.service;

import com.circulabook.dto.ContaDTOs.Pagina;
import com.circulabook.model.DemandaAquisicao;
import com.circulabook.model.DemandaSolicitante;
import com.circulabook.model.Usuario;
import com.circulabook.repository.DemandaAquisicaoRepository;
import com.circulabook.repository.DemandaSolicitanteRepository;
import com.circulabook.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Demandas de aquisição (RN07, U8, A9).
 *
 * O usuário comum registra interesse num livro que a rede não tem. Pedidos do mesmo
 * título + autor (sem diferenciar caixa, acentos e espaços extras) viram UMA demanda:
 * outro usuário incrementa o contador; o mesmo usuário é barrado. O incremento vale em
 * qualquer status e não muda o status. O Admin conduz ABERTA -> EM_ANALISE -> APROVADA|REJEITADA.
 */
@Service
public class DemandaService {

    public static final String ABERTA = "ABERTA";
    public static final String EM_ANALISE = "EM_ANALISE";
    public static final String APROVADA = "APROVADA";
    public static final String REJEITADA = "REJEITADA";

    private static final Map<String, List<String>> PROXIMOS = Map.of(
        ABERTA, List.of(EM_ANALISE),
        EM_ANALISE, List.of(APROVADA, REJEITADA),
        APROVADA, List.of(),
        REJEITADA, List.of());

    private static final Map<String, String> ROTULO = Map.of(
        ABERTA, "Aberta", EM_ANALISE, "Em análise", APROVADA, "Aprovada", REJEITADA, "Rejeitada");

    @Autowired private DemandaAquisicaoRepository demandaRepository;
    @Autowired private DemandaSolicitanteRepository solicitanteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private NotificacaoService notificacoes;

    public record DemandaDTO(Long id, String titulo, String autor, int totalSolicitacoes, String status,
                             String statusRotulo, List<String> proximosStatus,
                             LocalDateTime criadaEm, LocalDateTime atualizadaEm) {
        static DemandaDTO de(DemandaAquisicao d) {
            String st = d.getStatus();
            return new DemandaDTO(d.getId(), d.getTitulo(), d.getAutor(),
                d.getTotalSolicitacoes() == null ? 0 : d.getTotalSolicitacoes(),
                st, ROTULO.getOrDefault(st, st), PROXIMOS.getOrDefault(st, List.of()),
                d.getCriadaEm(), d.getAtualizadaEm());
        }
    }

    /** Resposta do registro de interesse: {@code nova} = criou a demanda (senão, incrementou). */
    public record InteresseDTO(DemandaDTO demanda, boolean nova, String mensagem) {}

    /** Item de "meus interesses" do usuário comum. */
    public record MeuInteresseDTO(Long demandaId, String titulo, String autor, String status,
                                  String statusRotulo, int totalSolicitacoes, LocalDateTime registradoEm) {}

    // ───────────────────────── Usuário comum ─────────────────────────

    @Transactional
    public InteresseDTO registrarInteresse(Long usuarioId, String titulo, String autor) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + usuarioId));
        String t = limpar(titulo);
        String a = limpar(autor);
        if (t.isEmpty()) throw new RuntimeException("Informe o título do livro.");
        if (a.isEmpty()) throw new RuntimeException("Informe o autor do livro.");
        if (t.length() > 255 || a.length() > 255) throw new RuntimeException("Título e autor têm no máximo 255 caracteres.");

        Optional<DemandaAquisicao> existente = buscarIgual(t, a);
        if (existente.isPresent()) {
            DemandaAquisicao d = existente.get();
            if (solicitanteRepository.existsByDemandaAndUsuario(d, usuario)) {
                throw new RuntimeException("Você já registrou interesse neste livro.");
            }
            d.setTotalSolicitacoes((d.getTotalSolicitacoes() == null ? 0 : d.getTotalSolicitacoes()) + 1);
            d.setAtualizadaEm(LocalDateTime.now());
            demandaRepository.save(d);
            registrarSolicitante(d, usuario);
            return new InteresseDTO(DemandaDTO.de(d), false,
                "Interesse registrado. Agora " + d.getTotalSolicitacoes() + " pessoas pediram \"" + d.getTitulo() + "\".");
        }

        DemandaAquisicao d = new DemandaAquisicao();
        d.setTitulo(t);
        d.setAutor(a);
        d.setTotalSolicitacoes(1);
        d.setStatus(ABERTA);
        d.setCriadaEm(LocalDateTime.now());
        d.setAtualizadaEm(d.getCriadaEm());
        demandaRepository.save(d);
        registrarSolicitante(d, usuario);
        notificacoes.novaDemanda(d, usuario);
        return new InteresseDTO(DemandaDTO.de(d), true,
            "Interesse registrado. A administração da rede vai avaliar a compra de \"" + d.getTitulo() + "\".");
    }

    public List<MeuInteresseDTO> meusInteresses(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: ID " + usuarioId));
        return solicitanteRepository.findByUsuarioOrderByCriadoEmDesc(usuario).stream()
            .map(s -> {
                DemandaAquisicao d = s.getDemanda();
                return new MeuInteresseDTO(d.getId(), d.getTitulo(), d.getAutor(), d.getStatus(),
                    ROTULO.getOrDefault(d.getStatus(), d.getStatus()),
                    d.getTotalSolicitacoes() == null ? 0 : d.getTotalSolicitacoes(), s.getCriadoEm());
            }).toList();
    }

    // ───────────────────────── Admin ─────────────────────────

    /** Mais pedidas primeiro; empate pela atualização mais recente. */
    public Pagina<DemandaDTO> listar(String status, int pagina, int tamanho) {
        if (pagina < 0) throw new RuntimeException("A página começa em 0.");
        if (tamanho < 1 || tamanho > 50) throw new RuntimeException("O tamanho da página deve ser de 1 a 50.");
        String st = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (st != null && !PROXIMOS.containsKey(st)) {
            throw new RuntimeException("Situação inválida: use ABERTA, EM_ANALISE, APROVADA ou REJEITADA.");
        }
        List<DemandaDTO> todas = demandaRepository.findAll().stream()
            .filter(d -> st == null || st.equals(d.getStatus()))
            .sorted(Comparator.comparing((DemandaAquisicao d) -> d.getTotalSolicitacoes() == null ? 0 : d.getTotalSolicitacoes())
                .reversed()
                .thenComparing(DemandaAquisicao::getAtualizadaEm, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(DemandaAquisicao::getId))
            .map(DemandaDTO::de)
            .toList();
        int total = todas.size();
        int inicio = Math.min(pagina * tamanho, total);
        int fim = Math.min(inicio + tamanho, total);
        return new Pagina<>(todas.subList(inicio, fim), pagina, tamanho, total,
            (int) Math.ceil(total / (double) tamanho));
    }

    @Transactional
    public DemandaDTO alterarStatus(Long id, String novo) {
        DemandaAquisicao d = demandaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Demanda não encontrada: ID " + id));
        String alvo = novo == null ? "" : novo.trim().toUpperCase();
        List<String> permitidos = PROXIMOS.getOrDefault(d.getStatus(), List.of());
        if (!permitidos.contains(alvo)) {
            String de = ROTULO.getOrDefault(d.getStatus(), d.getStatus());
            String para = ROTULO.getOrDefault(alvo, alvo.isEmpty() ? "(vazio)" : alvo);
            throw new RuntimeException(permitidos.isEmpty()
                ? "A demanda já está " + de.toLowerCase() + " e não muda mais de situação."
                : "Não é possível passar de " + de + " para " + para + ". Próxima situação possível: "
                  + String.join(" ou ", permitidos.stream().map(ROTULO::get).toList()) + ".");
        }
        d.setStatus(alvo);
        d.setAtualizadaEm(LocalDateTime.now());
        return DemandaDTO.de(demandaRepository.save(d));
    }

    public long contarPorStatus(String status) {
        return demandaRepository.countByStatus(status);
    }

    // ───────────────────────── Apoio ─────────────────────────

    /** Mesma demanda = mesmo título e autor normalizados (as demandas são poucas). */
    private Optional<DemandaAquisicao> buscarIgual(String titulo, String autor) {
        String chave = normalizar(titulo) + "|" + normalizar(autor);
        return demandaRepository.findAll().stream()
            .filter(d -> chave.equals(normalizar(d.getTitulo()) + "|" + normalizar(d.getAutor())))
            .min(Comparator.comparing(DemandaAquisicao::getId));
    }

    private void registrarSolicitante(DemandaAquisicao d, Usuario u) {
        DemandaSolicitante s = new DemandaSolicitante();
        s.setDemanda(d);
        s.setUsuario(u);
        solicitanteRepository.save(s);
    }

    /** Remove espaços extras (para gravar como o usuário digitou, só que limpo). */
    private static String limpar(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    /** Sem caixa, sem acentos e sem espaços extras (para comparar). */
    static String normalizar(String s) {
        String semAcento = Normalizer.normalize(limpar(s), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase();
    }
}
