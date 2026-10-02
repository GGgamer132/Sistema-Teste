package com.circulabook.service;

import com.circulabook.model.*;
import com.circulabook.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Especialista de Fila — Knowledge Source da arquitetura Blackboard.
 *
 * O "quadro" é o estado dos exemplares no banco. Sempre que um exemplar fica
 * DISPONIVEL em uma biblioteca (devolução, chegada de transferência, expiração
 * de reserva, cancelamento, cadastro), este especialista olha a fila daquela
 * biblioteca e atende o primeiro, decidindo entre:
 *   - retirada na própria biblioteca  -> reserva DISPONIVEL (3 dias);
 *   - retirada em outra biblioteca    -> vincula o exemplar ao pedido de
 *                                        transferência e, se já aprovado, despacha.
 *
 * É chamado de forma síncrona, na mesma transação de quem liberou o exemplar.
 */
@Service
public class FilaEsperaService {

    public static final int DIAS_PARA_RETIRADA = 3; // RN03

    @Autowired private ReservaRepository reservaRepository;
    @Autowired private SolicitacaoTransferenciaRepository transferenciaRepository;
    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private HistoricoService historicoService;

    /**
     * Chamar sempre que um exemplar ficar DISPONIVEL em sua biblioteca.
     * Se não houver fila para o título nessa biblioteca, não faz nada.
     */
    @Transactional
    public void promoverProximo(Exemplar exemplar) {
        if (!"DISPONIVEL".equals(exemplar.getStatus())) return;

        List<Reserva> fila = reservaRepository
            .findByLivroAndBibliotecaFilaAndStatusOrderByDataReservaAsc(
                exemplar.getLivro(), exemplar.getBiblioteca(), "PENDENTE");
        if (fila.isEmpty()) return;

        Reserva reserva = fila.get(0);

        // RN05 — o exemplar fica separado para o 1º da fila
        exemplar.setStatus("RESERVADO");
        exemplarRepository.save(exemplar);
        reserva.setExemplar(exemplar);

        SolicitacaoTransferencia pedido = transferenciaRepository
            .findFirstByReservaAndStatusIn(reserva, List.of("PENDENTE", "APROVADA"))
            .orElse(null);

        // Sem transferência (ou pedido rejeitado/cancelado): retirada na própria biblioteca
        if (pedido == null) {
            liberarParaRetirada(reserva, exemplar);
            return;
        }

        // Com transferência: vincula o exemplar ao pedido
        pedido.setExemplar(exemplar);
        reserva.setStatus("AGUARDANDO_TRANSFERENCIA");
        reservaRepository.save(reserva);

        if ("APROVADA".equals(pedido.getStatus())) {
            despachar(pedido, null);              // já aprovada: segue viagem agora
        } else {
            transferenciaRepository.save(pedido); // PENDENTE: espera o Admin decidir
            System.out.println("[FILA] Exemplar " + exemplar.getCodigoBarras()
                + " separado para " + reserva.getUsuario().getNome()
                + ", aguardando aprovação da transferência #" + pedido.getId() + ".");
        }
    }

    /** Reserva fica DISPONIVEL e o usuário ganha 3 dias para retirar. */
    @Transactional
    public void liberarParaRetirada(Reserva reserva, Exemplar exemplar) {
        reserva.setExemplar(exemplar);
        reserva.setStatus("DISPONIVEL");
        reserva.setDataExpiracao(LocalDateTime.now().plusDays(DIAS_PARA_RETIRADA));
        reservaRepository.save(reserva);

        historicoService.registrar(exemplar, "RESERVA", reserva.getUsuario(),
            exemplar.getBiblioteca(),
            "Exemplar reservado para " + reserva.getUsuario().getNome()
            + ". Retirada na " + reserva.getBibliotecaDestino().getNome()
            + " em até " + DIAS_PARA_RETIRADA + " dias.");

        System.out.println("[FILA] " + reserva.getUsuario().getNome()
            + " foi notificado: " + exemplar.getLivro().getTitulo()
            + " disponível para retirada na " + reserva.getBibliotecaDestino().getNome() + ".");
    }

    /** Pedido aprovado + exemplar vinculado => EM_TRANSITO e exemplar EM_TRANSFERENCIA. */
    @Transactional
    public void despachar(SolicitacaoTransferencia pedido, Usuario aprovador) {
        Exemplar exemplar = pedido.getExemplar();

        if (aprovador != null) pedido.setAprovador(aprovador);
        pedido.setStatus("EM_TRANSITO");
        transferenciaRepository.save(pedido);

        exemplar.setStatus("EM_TRANSFERENCIA");
        exemplarRepository.save(exemplar);

        historicoService.registrar(exemplar, "TRANSFERENCIA_SAIDA", pedido.getAprovador(),
            pedido.getBibliotecaOrigem(),
            "Saída da " + pedido.getBibliotecaOrigem().getNome()
            + " rumo à " + pedido.getBibliotecaDestino().getNome() + ".");

        System.out.println("[FILA] Transferência #" + pedido.getId() + " em trânsito: "
            + pedido.getBibliotecaOrigem().getNome() + " -> " + pedido.getBibliotecaDestino().getNome());
    }

    /** Marca o exemplar DISPONIVEL e tenta atender o próximo da fila da biblioteca onde ele está. */
    @Transactional
    public void liberarExemplar(Exemplar exemplar) {
        exemplar.setStatus("DISPONIVEL");
        exemplarRepository.save(exemplar);
        promoverProximo(exemplar);
    }

    /**
     * Retirada vencida: a reserva expira e o exemplar FICA onde está
     * (se já chegou na biblioteca de retirada, passa a fazer parte do acervo dela).
     * Roda a cada minuto; também pode ser disparado manualmente pelo controller.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expirarVencidas() {
        List<Reserva> vencidas = reservaRepository
            .findByStatusAndDataExpiracaoBefore("DISPONIVEL", LocalDateTime.now());

        for (Reserva reserva : vencidas) {
            reserva.setStatus("EXPIRADA");
            reservaRepository.save(reserva);

            Exemplar exemplar = reserva.getExemplar();
            if (exemplar != null && "RESERVADO".equals(exemplar.getStatus())) {
                historicoService.registrar(exemplar, "RESERVA", reserva.getUsuario(),
                    exemplar.getBiblioteca(),
                    "Reserva de " + reserva.getUsuario().getNome()
                    + " expirou sem retirada. Exemplar liberado na " + exemplar.getBiblioteca().getNome() + ".");
                liberarExemplar(exemplar);
            }
            System.out.println("[FILA] Reserva #" + reserva.getId() + " expirou.");
        }
    }
}