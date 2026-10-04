package com.circulabook.service;

import com.circulabook.dto.ContaDTOs.Pagina;
import com.circulabook.model.*;
import com.circulabook.repository.EmprestimoRepository;
import com.circulabook.repository.NotificacaoRepository;
import com.circulabook.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Notificações in-app (RN20, §6) — o único lugar que cria notificações.
 *
 * Os especialistas de domínio chamam um método por evento, dentro da mesma transação
 * do evento (se o evento falhar, a notificação some junto). Destinatários:
 *  - usuário: o dono da reserva/empréstimo;
 *  - bibliotecário: todos os bibliotecários ATIVOS da biblioteca;
 *  - Admin: todos os Admins ativos.
 */
@Service
public class NotificacaoService {

    // Usuário
    public static final String TIPO_PEDIDO_APROVADO = "PEDIDO_APROVADO";
    public static final String TIPO_PEDIDO_REJEITADO = "PEDIDO_REJEITADO";
    public static final String TIPO_EXEMPLAR_A_CAMINHO = "EXEMPLAR_A_CAMINHO";
    public static final String TIPO_RESERVA_PRONTA = "RESERVA_PRONTA";
    public static final String TIPO_RESERVA_EXPIRADA = "RESERVA_EXPIRADA";
    public static final String TIPO_RESERVA_VOLTOU_FILA = "RESERVA_VOLTOU_FILA";
    public static final String TIPO_EMPRESTIMO_VENCE = "EMPRESTIMO_VENCE";
    public static final String TIPO_EMPRESTIMO_ATRASADO = "EMPRESTIMO_ATRASADO";
    public static final String TIPO_BLOQUEIO = "BLOQUEIO";
    // Bibliotecário
    public static final String TIPO_TRANSFERENCIA_A_CAMINHO = "TRANSFERENCIA_A_CAMINHO";
    public static final String TIPO_SEPARADO_PARA_ENVIO = "SEPARADO_PARA_ENVIO";
    public static final String TIPO_RETIRADA_NA_BIBLIOTECA = "RETIRADA_NA_BIBLIOTECA";
    // Admin
    public static final String TIPO_NOVO_PEDIDO = "NOVO_PEDIDO";
    public static final String TIPO_EXEMPLAR_RETIDO = "EXEMPLAR_RETIDO";
    public static final String TIPO_PEDIDO_CANCELADO_CAPACIDADE = "PEDIDO_CANCELADO_CAPACIDADE";
    public static final String TIPO_NOVA_DEMANDA = "NOVA_DEMANDA";

    public static final int DIAS_AVISO_VENCIMENTO = 2;

    private static final String LINK_RESERVAS = "/minhas-reservas";
    private static final String LINK_EMPRESTIMOS = "/meus-emprestimos";
    private static final String LINK_BIB_TRANSFERENCIAS = "/biblioteca/transferencias";
    private static final String LINK_BIB_RESERVAS = "/biblioteca/reservas";
    private static final String LINK_ADMIN_TRANSFERENCIAS = "/admin/transferencias";
    private static final String LINK_ADMIN_DEMANDAS = "/admin/demandas";

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private NotificacaoRepository notificacaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EmprestimoRepository emprestimoRepository;

    /** Item devolvido pela API (o dono nunca vai no JSON). */
    public record NotificacaoDTO(Long id, String titulo, String mensagem, String tipo, boolean lida,
                                 LocalDateTime criadaEm, String link) {
        static NotificacaoDTO de(Notificacao n) {
            return new NotificacaoDTO(n.getId(), n.getTitulo(), n.getMensagem(), n.getTipo(),
                Boolean.TRUE.equals(n.getLida()), n.getCriadaEm(), n.getLink());
        }
    }

    // ───────────────────────── Leitura (sempre do usuário do token) ─────────────────────────

    public Pagina<NotificacaoDTO> listar(Long usuarioId, int pagina, int tamanho) {
        if (pagina < 0) throw new RuntimeException("A página começa em 0.");
        if (tamanho < 1 || tamanho > 50) throw new RuntimeException("O tamanho da página deve ser de 1 a 50.");
        Page<Notificacao> p = notificacaoRepository.findByUsuarioIdOrderByCriadaEmDescIdDesc(
            usuarioId, PageRequest.of(pagina, tamanho));
        return new Pagina<>(p.getContent().stream().map(NotificacaoDTO::de).toList(),
            pagina, tamanho, p.getTotalElements(), p.getTotalPages());
    }

    public long contarNaoLidas(Long usuarioId) {
        return notificacaoRepository.countByUsuarioIdAndLidaFalse(usuarioId);
    }

    /** Marca uma notificação do próprio usuário; de outro usuário é tratada como inexistente. */
    @Transactional
    public NotificacaoDTO marcarLida(Long id, Long usuarioId) {
        Notificacao n = notificacaoRepository.findByIdAndUsuarioId(id, usuarioId)
            .orElseThrow(() -> new NaoEncontradaException("Notificação não encontrada."));
        n.setLida(true);
        return NotificacaoDTO.de(notificacaoRepository.save(n));
    }

    @Transactional
    public int marcarTodasLidas(Long usuarioId) {
        List<Notificacao> naoLidas = notificacaoRepository.findByUsuarioIdAndLidaFalse(usuarioId);
        naoLidas.forEach(n -> n.setLida(true));
        notificacaoRepository.saveAll(naoLidas);
        return naoLidas.size();
    }

    public static class NaoEncontradaException extends RuntimeException {
        public NaoEncontradaException(String msg) { super(msg); }
    }

    // ───────────────────────── Eventos: usuário ─────────────────────────

    public void pedidoAprovado(SolicitacaoTransferencia s) {
        Reserva r = s.getReserva();
        if (r == null) return;
        criar(r.getUsuario(), TIPO_PEDIDO_APROVADO, "Transferência aprovada",
            "Sua retirada de \"" + s.getLivro().getTitulo() + "\" na " + s.getBibliotecaDestino().getNome()
            + " foi aprovada. O livro segue para lá assim que um exemplar estiver separado para você.",
            LINK_RESERVAS, r.getId());
    }

    public void pedidoRejeitado(SolicitacaoTransferencia s) {
        Reserva r = s.getReserva();
        if (r == null) return;
        criar(r.getUsuario(), TIPO_PEDIDO_REJEITADO, "Transferência não aprovada",
            "O pedido para retirar \"" + s.getLivro().getTitulo() + "\" na " + s.getBibliotecaDestino().getNome()
            + " não foi aprovado. Sua reserva continua na fila e a retirada voltou para a "
            + s.getBibliotecaOrigem().getNome() + ".",
            LINK_RESERVAS, r.getId());
    }

    /** Reserva pronta: avisa o leitor e os bibliotecários da biblioteca de retirada. */
    public void reservaPronta(Reserva r) {
        Biblioteca retirada = r.getBibliotecaDestino();
        String titulo = r.getLivro().getTitulo();
        criar(r.getUsuario(), TIPO_RESERVA_PRONTA, "Reserva pronta para retirada",
            "\"" + titulo + "\" está separado para você na " + retirada.getNome()
            + ". Retire até " + r.getDataExpiracao().format(DIA_MES) + ".",
            LINK_RESERVAS, r.getId());
        for (Usuario b : bibliotecarios(retirada)) {
            criar(b, TIPO_RETIRADA_NA_BIBLIOTECA, "Reserva aguardando retirada",
                r.getUsuario().getNome() + " virá buscar \"" + titulo + "\" até "
                + r.getDataExpiracao().format(DIA_MES) + ".",
                LINK_BIB_RESERVAS, r.getId());
        }
    }

    public void reservaExpirada(Reserva r) {
        criar(r.getUsuario(), TIPO_RESERVA_EXPIRADA, "Reserva expirada",
            "O prazo para retirar \"" + r.getLivro().getTitulo() + "\" na " + r.getBibliotecaDestino().getNome()
            + " terminou e a reserva foi encerrada. O exemplar passou para o próximo da fila.",
            LINK_RESERVAS, r.getId());
    }

    /** ES-13 e chegada danificada: a reserva volta à fila (mesma posição). */
    public void reservaVoltouParaFila(Reserva r, String motivo) {
        criar(r.getUsuario(), TIPO_RESERVA_VOLTOU_FILA, "Sua reserva voltou para a fila",
            motivo + " Sua reserva de \"" + r.getLivro().getTitulo() + "\" voltou para a fila da "
            + r.getBibliotecaFila().getNome() + ", sem perder a posição. A retirada será na "
            + r.getBibliotecaDestino().getNome() + ".",
            LINK_RESERVAS, r.getId());
    }

    public void bloqueioAplicado(Usuario u, long diasAtraso, LocalDateTime ate) {
        criar(u, TIPO_BLOQUEIO, "Empréstimos bloqueados",
            "Você devolveu um livro com " + diasAtraso + " dia(s) de atraso e não poderá fazer novos "
            + "empréstimos até " + ate.format(DATA) + ".",
            LINK_EMPRESTIMOS, null);
    }

    // ───────────────────────── Eventos: transferência ─────────────────────────

    /** Admins: novo pedido de transferência aguardando decisão. */
    public void pedidoCriado(SolicitacaoTransferencia s) {
        for (Usuario a : admins()) {
            criar(a, TIPO_NOVO_PEDIDO, "Novo pedido de transferência",
                s.getSolicitante().getNome() + " pediu para retirar \"" + s.getLivro().getTitulo()
                + "\" na " + s.getBibliotecaDestino().getNome() + " (fila na "
                + s.getBibliotecaOrigem().getNome() + ").",
                LINK_ADMIN_TRANSFERENCIAS, s.getId());
        }
    }

    /** Admins (urgente): exemplar separado na origem esperando a decisão do pedido. */
    public void exemplarRetido(SolicitacaoTransferencia s) {
        for (Usuario a : admins()) {
            criar(a, TIPO_EXEMPLAR_RETIDO, "Urgente: exemplar retido aguardando decisão",
                "O Exemplar nº " + s.getExemplar().getId() + " de \"" + s.getLivro().getTitulo()
                + "\" foi separado na " + s.getBibliotecaOrigem().getNome() + " para "
                + s.getSolicitante().getNome() + " e só segue para a " + s.getBibliotecaDestino().getNome()
                + " depois que você aprovar ou rejeitar o pedido.",
                LINK_ADMIN_TRANSFERENCIAS, s.getId());
        }
    }

    /** Admins: pedido aprovado cancelado no despacho porque a origem ficaria sem o livro. */
    public void pedidoCanceladoPorCapacidade(SolicitacaoTransferencia s) {
        for (Usuario a : admins()) {
            criar(a, TIPO_PEDIDO_CANCELADO_CAPACIDADE, "Transferência cancelada no envio",
                "O pedido de \"" + s.getLivro().getTitulo() + "\" para a " + s.getBibliotecaDestino().getNome()
                + " foi cancelado: a " + s.getBibliotecaOrigem().getNome()
                + " ficaria sem nenhum exemplar do título. " + s.getSolicitante().getNome()
                + " vai retirar na própria " + s.getBibliotecaOrigem().getNome() + ".",
                LINK_ADMIN_TRANSFERENCIAS, s.getId());
        }
    }

    /**
     * Exemplar saiu em trânsito: o leitor (se for de reserva) sabe que está a caminho,
     * o destino precisa confirmar a chegada e a origem sabe que ele foi separado para envio.
     */
    public void transferenciaDespachada(SolicitacaoTransferencia s) {
        Exemplar e = s.getExemplar();
        String titulo = s.getLivro().getTitulo();
        Reserva r = s.getReserva();
        boolean reservaAtiva = r != null && "AGUARDANDO_TRANSFERENCIA".equals(r.getStatus());
        if (reservaAtiva) {
            criar(r.getUsuario(), TIPO_EXEMPLAR_A_CAMINHO, "Seu livro está a caminho",
                "Um exemplar de \"" + titulo + "\" foi separado para você e está a caminho da "
                + s.getBibliotecaDestino().getNome() + ". Avisaremos quando ele chegar.",
                LINK_RESERVAS, r.getId());
        }
        for (Usuario b : bibliotecarios(s.getBibliotecaDestino())) {
            criar(b, TIPO_TRANSFERENCIA_A_CAMINHO, "Transferência a caminho",
                "O Exemplar nº " + e.getId() + " de \"" + titulo + "\" está vindo da "
                + s.getBibliotecaOrigem().getNome() + ". Confirme a chegada quando recebê-lo"
                + (reservaAtiva ? " (reservado para " + r.getUsuario().getNome() + ")." : "."),
                LINK_BIB_TRANSFERENCIAS, s.getId());
        }
        for (Usuario b : bibliotecarios(s.getBibliotecaOrigem())) {
            criar(b, TIPO_SEPARADO_PARA_ENVIO, "Exemplar separado para envio",
                "O Exemplar nº " + e.getId() + " de \"" + titulo + "\" deve ser enviado para a "
                + s.getBibliotecaDestino().getNome() + ".",
                LINK_BIB_TRANSFERENCIAS, s.getId());
        }
    }

    /** Admins: alguém pediu um livro que a rede não tem (só na criação da demanda, não nos incrementos). */
    public void novaDemanda(DemandaAquisicao d, Usuario solicitante) {
        for (Usuario a : admins()) {
            criar(a, TIPO_NOVA_DEMANDA, "Nova demanda de aquisição",
                solicitante.getNome() + " registrou interesse em \"" + d.getTitulo() + "\", de "
                + d.getAutor() + ", que a rede ainda não tem.",
                LINK_ADMIN_DEMANDAS, d.getId());
        }
    }

    // ───────────────────────── Rotina de vencimento/atraso ─────────────────────────

    /**
     * Avisa "vence em 2 dias" e "está atrasado" para os empréstimos em aberto.
     * Idempotente: no máximo um aviso de cada tipo por empréstimo (referenciaId).
     * Devolve quantas notificações criou.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public int verificarVencimentos() {
        LocalDateTime agora = LocalDateTime.now();
        int criadas = 0;
        for (Emprestimo e : emprestimoRepository.findByStatusInOrderByDataPrevDevolucaoAsc(List.of("ATIVO", "ATRASADO"))) {
            LocalDateTime prev = e.getDataPrevDevolucao();
            String titulo = e.getExemplar().getLivro().getTitulo();
            if (prev.isBefore(agora)) {
                if (!notificacaoRepository.existsByTipoAndReferenciaId(TIPO_EMPRESTIMO_ATRASADO, e.getId())) {
                    criar(e.getUsuario(), TIPO_EMPRESTIMO_ATRASADO, "Empréstimo atrasado",
                        "A devolução de \"" + titulo + "\" na " + e.getBiblioteca().getNome() + " venceu em "
                        + prev.format(DATA) + ". Devolva o quanto antes: cada dia de atraso gera "
                        + EmprestimoService.DIAS_BLOQUEIO_POR_ATRASO + " dias sem poder pegar livros.",
                        LINK_EMPRESTIMOS, e.getId());
                    criadas++;
                }
            } else if (!prev.isAfter(agora.plusDays(DIAS_AVISO_VENCIMENTO))) {
                if (!notificacaoRepository.existsByTipoAndReferenciaId(TIPO_EMPRESTIMO_VENCE, e.getId())) {
                    criar(e.getUsuario(), TIPO_EMPRESTIMO_VENCE, "Devolução em breve",
                        "\"" + titulo + "\" deve ser devolvido na " + e.getBiblioteca().getNome()
                        + " até " + prev.format(DATA) + ".",
                        LINK_EMPRESTIMOS, e.getId());
                    criadas++;
                }
            }
        }
        return criadas;
    }

    // ───────────────────────── Apoio ─────────────────────────

    private void criar(Usuario destinatario, String tipo, String titulo, String mensagem, String link, Long ref) {
        Notificacao n = new Notificacao();
        n.setUsuario(destinatario);
        n.setTipo(tipo);
        n.setTitulo(titulo);
        n.setMensagem(mensagem);
        n.setLink(link);
        n.setReferenciaId(ref);
        n.setLida(false);
        n.setCriadaEm(LocalDateTime.now());
        notificacaoRepository.save(n);
    }

    private List<Usuario> bibliotecarios(Biblioteca b) {
        return usuarioRepository.findByTipoAndBibliotecaAndAtivoTrue("BIBLIOTECARIO", b);
    }

    private List<Usuario> admins() {
        return usuarioRepository.findByTipoAndAtivoTrue("ADMIN");
    }
}
