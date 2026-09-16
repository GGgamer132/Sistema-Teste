package com.circulabook.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Cria o trigger PostgreSQL que funciona como "sensor" do Blackboard.
 *
 * Mesma estratégia do sistema-estoque-blackboard: roda em
 * ApplicationReadyEvent para garantir que o Hibernate já criou as tabelas.
 * A diferença é o gatilho — lá era o estoque caindo abaixo do limiar,
 * aqui é a criação de uma reserva pendente.
 */
@Component
public class BlackboardTriggerInicializador {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void criarTrigger() {
        try {
            System.out.println("[BLACKBOARD] Inicializando trigger de monitoramento de reservas...");

            jdbcTemplate.execute(
                "DROP TRIGGER IF EXISTS trigger_reserva_pendente ON reserva");
            jdbcTemplate.execute(
                "DROP FUNCTION IF EXISTS verificar_reserva_pendente()");

            jdbcTemplate.execute(
                "CREATE OR REPLACE FUNCTION verificar_reserva_pendente() " +
                "RETURNS TRIGGER AS $$ " +
                "BEGIN " +
                "    IF NEW.status = 'PENDENTE' THEN " +
                "        PERFORM pg_notify( " +
                "            'reserva_sem_exemplar', " +
                "            json_build_object( " +
                "                'reserva_id', NEW.id, " +
                "                'livro_id', NEW.livro_id, " +
                "                'biblioteca_destino_id', NEW.biblioteca_destino_id " +
                "            )::text " +
                "        ); " +
                "    END IF; " +
                "    RETURN NEW; " +
                "END; " +
                "$$ LANGUAGE plpgsql");

            jdbcTemplate.execute(
                "CREATE TRIGGER trigger_reserva_pendente " +
                "AFTER INSERT ON reserva " +
                "FOR EACH ROW " +
                "EXECUTE FUNCTION verificar_reserva_pendente()");

            System.out.println("[BLACKBOARD] Trigger criado! "
                + "O Especialista de Transferência será acordado a cada nova reserva pendente.");

        } catch (Exception e) {
            System.err.println("[BLACKBOARD] Erro ao criar trigger: " + e.getMessage());
        }
    }
}
