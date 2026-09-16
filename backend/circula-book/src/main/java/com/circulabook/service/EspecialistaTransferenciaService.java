package com.circulabook.service;

import com.circulabook.model.*;
import com.circulabook.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Knowledge Source da arquitetura Blackboard.
 *
 * Equivale ao EspecialistaReposicaoService do sistema-estoque-blackboard:
 * lá, quando o estoque caía abaixo do limiar, ele sugeria transferência ou compra.
 * Aqui, quando uma RESERVA é criada e não há exemplar disponível na biblioteca
 * de destino, ele procura um exemplar livre em outra unidade da rede e cria
 * automaticamente uma SOLICITACAO_TRANSFERENCIA pendente para o Admin avaliar.
 *
 * É acordado pelo PgNotificationListener, que por sua vez escuta o trigger
 * criado pelo BlackboardTriggerInicializador.
 */
@Service
public class EspecialistaTransferenciaService {

    @Autowired private ReservaRepository reservaRepository;
    @Autowired private ExemplarRepository exemplarRepository;
    @Autowired private SolicitacaoTransferenciaRepository transferenciaRepository;

    @Transactional
    public void analisarReserva(Long reservaId) {

        Reserva reserva = reservaRepository.findById(reservaId).orElse(null);
        if (reserva == null) {
            System.err.println("[BLACKBOARD] Reserva " + reservaId + " não encontrada.");
            return;
        }

        Livro livro = reserva.getLivro();
        Biblioteca destino = reserva.getBibliotecaDestino();

        // Já existe exemplar livre no destino? Então nada a fazer.
        List<Exemplar> noDestino = exemplarRepository
            .findByLivroAndBibliotecaAndStatus(livro, destino, "DISPONIVEL");
        if (!noDestino.isEmpty()) {
            System.out.println("[BLACKBOARD] Reserva " + reservaId
                + ": já há exemplar disponível em " + destino.getNome() + ". Nada a sugerir.");
            return;
        }

        // Procura um exemplar disponível em qualquer outra biblioteca da rede
        List<Exemplar> naRede = exemplarRepository.findByLivroAndStatus(livro, "DISPONIVEL");
        Exemplar candidato = naRede.stream()
            .filter(e -> !e.getBiblioteca().getId().equals(destino.getId()))
            .findFirst()
            .orElse(null);

        if (candidato == null) {
            System.out.println("[BLACKBOARD] Reserva " + reservaId + ": nenhum exemplar de \""
                + livro.getTitulo() + "\" disponível na rede. Usuário permanece na fila de espera.");
            return;
        }

        // Não duplica sugestão para o mesmo exemplar
        List<SolicitacaoTransferencia> abertas = transferenciaRepository
            .findByExemplarAndStatusIn(candidato, List.of("PENDENTE", "APROVADA", "EM_TRANSITO"));
        if (!abertas.isEmpty()) {
            System.out.println("[BLACKBOARD] Reserva " + reservaId
                + ": já existe transferência em andamento para este exemplar.");
            return;
        }

        SolicitacaoTransferencia s = new SolicitacaoTransferencia();
        s.setExemplar(candidato);
        s.setBibliotecaOrigem(candidato.getBiblioteca());
        s.setBibliotecaDestino(destino);
        s.setSolicitante(reserva.getUsuario());
        s.setReserva(reserva);
        s.setStatus("PENDENTE");
        s.setDataSolicitacao(LocalDateTime.now());
        s.setObservacoes("Sugestão automática do Especialista de Transferência: "
            + "\"" + livro.getTitulo() + "\" não tem exemplar disponível em "
            + destino.getNome() + ", mas há um exemplar livre em "
            + candidato.getBiblioteca().getNome() + ". Aguardando aprovação do Admin (RN04).");
        transferenciaRepository.save(s);

        // RN05 — segura o exemplar até o Admin decidir
        candidato.setStatus("RESERVADO");
        exemplarRepository.save(candidato);

        System.out.println("[BLACKBOARD] Sugestão de transferência criada automaticamente: "
            + livro.getTitulo() + " | " + candidato.getBiblioteca().getNome()
            + " -> " + destino.getNome());
    }
}
