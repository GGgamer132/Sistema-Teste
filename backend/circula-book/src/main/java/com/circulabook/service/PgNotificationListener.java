package com.circulabook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Controlador da arquitetura Blackboard.
 * Não decide nada: fica escutando o canal de notificações do PostgreSQL e,
 * quando o trigger dispara, acorda o EspecialistaTransferenciaService.
 */
@Component
public class PgNotificationListener implements ApplicationRunner {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EspecialistaTransferenciaService especialistaTransferencia;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void run(ApplicationArguments args) {
        Thread listenerThread = new Thread(this::escutarNotificacoes);
        listenerThread.setDaemon(true);
        listenerThread.setName("pg-notification-listener");
        listenerThread.start();
    }

    private void escutarNotificacoes() {
        try (Connection conn = dataSource.getConnection()) {

            PGConnection pgConn = conn.unwrap(PGConnection.class);
            conn.createStatement().execute("LISTEN reserva_sem_exemplar");

            System.out.println("[BLACKBOARD] Listener ativo — aguardando reservas pendentes...");

            while (!Thread.currentThread().isInterrupted()) {
                PGNotification[] notifications = pgConn.getNotifications(500);
                if (notifications != null) {
                    for (PGNotification n : notifications) {
                        processarNotificacao(n.getParameter());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[BLACKBOARD] Erro no listener: " + e.getMessage());
        }
    }

    private void processarNotificacao(String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            Long reservaId = json.get("reserva_id").asLong();

            System.out.println("[BLACKBOARD] Notificação recebida! Reserva ID: " + reservaId);
            System.out.println("[BLACKBOARD] Acordando Especialista de Transferência...");

            especialistaTransferencia.analisarReserva(reservaId);

        } catch (Exception e) {
            System.err.println("[BLACKBOARD] Erro ao processar notificação: " + e.getMessage());
        }
    }
}
