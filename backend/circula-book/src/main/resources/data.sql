-- ============================================================================
-- CIRCULA BOOK — dados mockados para visualização
-- Executado automaticamente pelo Spring Boot depois que o Hibernate cria
-- as tabelas (spring.jpa.defer-datasource-initialization=true).
-- ============================================================================

-- ─── Categorias ───
INSERT INTO categoria (id, nome, descricao) VALUES
(1, 'Romance',   'Narrativas de ficcao centradas em relacoes e conflitos pessoais'),
(2, 'Ficcao',    'Obras de ficcao em geral, incluindo contos e narrativas curtas'),
(3, 'Biografia', 'Relatos da vida de pessoas reais'),
(4, 'Infantil',  'Obras voltadas para o publico infantil');

-- ─── Bibliotecas da rede ───
INSERT INTO biblioteca (id, nome, endereco, email, telefone, ativa, criada_em) VALUES
(1, 'Biblioteca Vila Isabel', 'Rua A, 100 - Zona Norte',  'vilaisabel@circulabook.org.br', '(21) 3000-0001', TRUE, CURRENT_TIMESTAMP),
(2, 'Biblioteca Tijuca',      'Avenida B, 200 - Zona Sul', 'tijuca@circulabook.org.br',     '(21) 3000-0002', TRUE, CURRENT_TIMESTAMP),
(3, 'Biblioteca Meier',       'Rua C, 300 - Zona Oeste',   'meier@circulabook.org.br',      '(21) 3000-0003', TRUE, CURRENT_TIMESTAMP),
(4, 'Biblioteca Centro',      'Praca D, 50 - Centro',      'centro@circulabook.org.br',     '(21) 3000-0004', TRUE, CURRENT_TIMESTAMP),
(5, 'Biblioteca Norte',       'Rua E, 400 - Zona Norte',   'norte@circulabook.org.br',      '(21) 3000-0005', TRUE, CURRENT_TIMESTAMP),
(6, 'Biblioteca Sul',         'Rua F, 500 - Zona Sul',     'sul@circulabook.org.br',        '(21) 3000-0006', TRUE, CURRENT_TIMESTAMP);

-- ─── Usuarios (senha_hash e apenas um valor mock) ───
INSERT INTO usuario (id, nome, email, senha_hash, tipo, biblioteca_id, ativo, criado_em, bloqueado_ate) VALUES
(1,  'Roberto Dias',      'roberto.dias@circulabook.org.br',   'MOCK_HASH_ADMIN_01', 'ADMIN',         NULL, TRUE, CURRENT_TIMESTAMP, NULL),
(2,  'Carlos Lima',       'carlos.lima@circulabook.org.br',    'MOCK_HASH_BIB_01',   'BIBLIOTECARIO', 1,    TRUE, CURRENT_TIMESTAMP, NULL),
(3,  'Fernanda Reis',     'fernanda.reis@circulabook.org.br',  'MOCK_HASH_BIB_02',   'BIBLIOTECARIO', 4,    TRUE, CURRENT_TIMESTAMP, NULL),
(4,  'Joao Pedro Nunes',  'joao.pedro@circulabook.org.br',     'MOCK_HASH_BIB_03',   'BIBLIOTECARIO', 2,    TRUE, CURRENT_TIMESTAMP, NULL),
(5,  'Marcos Silva',      'marcos.silva@circulabook.org.br',   'MOCK_HASH_BIB_04',   'BIBLIOTECARIO', 3,    TRUE, CURRENT_TIMESTAMP, NULL),
(6,  'Ana Souza',         'ana.souza@email.com',               'MOCK_HASH_USR_01',   'COMUM',         NULL, TRUE, CURRENT_TIMESTAMP, NULL),
(7,  'Bruno Alves',       'bruno.alves@email.com',             'MOCK_HASH_USR_02',   'COMUM',         NULL, TRUE, CURRENT_TIMESTAMP, NULL),
(8,  'Camila Duarte',     'camila.duarte@email.com',           'MOCK_HASH_USR_03',   'COMUM',         NULL, TRUE, CURRENT_TIMESTAMP, NULL),
(9,  'Diego Santos',      'diego.santos@email.com',            'MOCK_HASH_USR_04',   'COMUM',         NULL, TRUE, CURRENT_TIMESTAMP, NULL),
(10, 'Elaine Costa',      'elaine.costa@email.com',            'MOCK_HASH_USR_05',   'COMUM',         NULL, FALSE, CURRENT_TIMESTAMP, NULL);

-- ─── Livros ───
INSERT INTO livro (id, titulo, autor, isbn, editora, ano_publicacao, sinopse, categoria_id) VALUES
(1, 'Dom Casmurro', 'Machado de Assis', '978-85-08-12345-6', 'Editora Atica', 1899,
    'Romance narrado por Bento Santiago, que revisita a propria juventude e o casamento com Capitu, questionando ao longo da obra se foi ou nao traido por ela e pelo melhor amigo, Escobar.', 1),
(2, 'Memorias Postumas de Bras Cubas', 'Machado de Assis', '978-85-08-22345-1', 'Editora Atica', 1881,
    'Narrado por um defunto-autor, o romance ironiza os costumes e vaidades da sociedade brasileira do seculo XIX.', 2),
(3, 'Quincas Borba', 'Machado de Assis', '978-85-08-32345-2', 'Editora Atica', 1891,
    'Continuacao indireta de Memorias Postumas, acompanha Rubiao e sua relacao com a filosofia do Humanitismo.', 1),
(4, 'O Alienista', 'Machado de Assis', '978-85-08-42345-3', 'Editora Atica', 1882,
    'Conto que satiriza a ciencia e o poder atraves da historia do medico Simao Bacamarte.', 2),
(5, 'Iracema', 'Jose de Alencar', '978-85-08-52345-4', 'Editora Melhoramentos', 1865,
    'Romance indianista que narra o amor entre a india Iracema e o colonizador portugues Martim.', 1),
(6, 'A Moreninha', 'Joaquim Manuel de Macedo', '978-85-08-62345-5', 'Editora Melhoramentos', 1844,
    'Considerado o primeiro romance urbano brasileiro, narra o namoro entre Augusto e Carolina.', 1);

-- ─── Exemplares (o "Blackboard" do sistema) ───
INSERT INTO exemplar (id, livro_id, biblioteca_id, codigo_barras, status, estado_conservacao, adicionado_em) VALUES
-- Dom Casmurro: disponivel em 3 bibliotecas (alimenta a Tela 3)
(1,  1, 1, 'EX-00231', 'DISPONIVEL',       'BOM',  CURRENT_TIMESTAMP),
(2,  1, 1, 'EX-00232', 'DISPONIVEL',       'BOM',  CURRENT_TIMESTAMP),
(3,  1, 2, 'EX-00233', 'DISPONIVEL',       'BOM',  CURRENT_TIMESTAMP),
(4,  1, 4, 'EX-00234', 'DISPONIVEL',       'NOVO', CURRENT_TIMESTAMP),
(5,  1, 3, 'EX-00235', 'EMPRESTADO',       'BOM',  CURRENT_TIMESTAMP),
-- Memorias Postumas: TODOS emprestados -> habilita a fila de espera da Tela 5
(6,  2, 4, 'EX-00589', 'EMPRESTADO',       'BOM',  CURRENT_TIMESTAMP),
(7,  2, 4, 'EX-00590', 'EMPRESTADO',       'BOM',  CURRENT_TIMESTAMP),
(8,  2, 3, 'EX-00591', 'EMPRESTADO',       'USADO',CURRENT_TIMESTAMP),
-- Quincas Borba
(9,  3, 6, 'EX-00812', 'DISPONIVEL',       'BOM',  CURRENT_TIMESTAMP),
(10, 3, 5, 'EX-00813', 'DISPONIVEL',       'BOM',  CURRENT_TIMESTAMP),
-- O Alienista
(11, 4, 5, 'EX-00733', 'DISPONIVEL',       'BOM',  CURRENT_TIMESTAMP),
(12, 4, 4, 'EX-00734', 'EM_TRANSFERENCIA', 'BOM',  CURRENT_TIMESTAMP),
-- Iracema: exemplar unico na rede -> RN11 deixa INDISPONIVEL
(13, 5, 1, 'EX-01045', 'INDISPONIVEL',     'NOVO', CURRENT_TIMESTAMP),
-- A Moreninha
(14, 6, 3, 'EX-00456', 'DISPONIVEL',       'USADO',CURRENT_TIMESTAMP),
(15, 6, 2, 'EX-00457', 'EMPRESTADO',       'BOM',  CURRENT_TIMESTAMP);

-- ─── Emprestimos ───
INSERT INTO emprestimo (id, exemplar_id, usuario_id, biblioteca_id, data_emprestimo, data_prev_devolucao, data_devolucao, status) VALUES
(1, 5,  6, 3, CURRENT_TIMESTAMP - INTERVAL '5 days',  CURRENT_TIMESTAMP + INTERVAL '9 days',  NULL, 'ATIVO'),
-- Emprestimo atrasado: e este que a Tela 6 usa para demonstrar a RN12
(2, 6,  6, 4, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '3 days',  NULL, 'ATRASADO'),
(3, 7,  8, 4, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '4 days',  NULL, 'ATRASADO'),
(4, 8,  9, 3, CURRENT_TIMESTAMP - INTERVAL '2 days',  CURRENT_TIMESTAMP + INTERVAL '12 days', NULL, 'ATIVO'),
(5, 15, 7, 2, CURRENT_TIMESTAMP - INTERVAL '3 days',  CURRENT_TIMESTAMP + INTERVAL '11 days', NULL, 'ATIVO'),
(6, 1,  7, 1, CURRENT_TIMESTAMP - INTERVAL '60 days', CURRENT_TIMESTAMP - INTERVAL '46 days', CURRENT_TIMESTAMP - INTERVAL '47 days', 'DEVOLVIDO'),
(7, 3,  8, 2, CURRENT_TIMESTAMP - INTERVAL '90 days', CURRENT_TIMESTAMP - INTERVAL '76 days', CURRENT_TIMESTAMP - INTERVAL '80 days', 'DEVOLVIDO');

-- ─── Reservas (fila de espera de Memorias Postumas) ───
INSERT INTO reserva (id, livro_id, usuario_id, biblioteca_destino_id, data_reserva, data_expiracao, status) VALUES
(1, 2, 7,  1, CURRENT_TIMESTAMP - INTERVAL '4 days',  CURRENT_TIMESTAMP + INTERVAL '3 days',  'PENDENTE'),
(2, 2, 8,  1, CURRENT_TIMESTAMP - INTERVAL '3 days',  CURRENT_TIMESTAMP + INTERVAL '3 days',  'PENDENTE'),
(3, 2, 9,  1, CURRENT_TIMESTAMP - INTERVAL '1 days',  CURRENT_TIMESTAMP + INTERVAL '3 days',  'PENDENTE'),
(4, 1, 8,  2, CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '25 days', 'RETIRADA'),
(5, 6, 9,  3, CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP - INTERVAL '10 days', 'CANCELADA');

-- ─── Solicitacoes de transferencia (Tela 8) ───
INSERT INTO solicitacao_transferencia
 (id, exemplar_id, biblioteca_origem_id, biblioteca_destino_id, solicitante_id, aprovador_id, reserva_id, status, data_solicitacao, data_conclusao, observacoes) VALUES
(1, 3,  2, 1, 2, NULL, NULL, 'PENDENTE', CURRENT_TIMESTAMP - INTERVAL '2 days', NULL,
   'Solicitado pelo bibliotecario da Vila Isabel para atender reserva ativa.'),
(2, 4,  4, 3, 6, NULL, NULL, 'PENDENTE', CURRENT_TIMESTAMP - INTERVAL '1 days', NULL,
   'Solicitado pela propria usuaria Ana Souza (RN13) — exemplar disponivel na Centro.'),
(3, 14, 3, 2, 4, NULL, NULL, 'PENDENTE', CURRENT_TIMESTAMP - INTERVAL '6 hours', NULL,
   'Solicitado pelo bibliotecario da Tijuca para reposicao do acervo local.'),
(4, 9,  6, 5, 5, 1,    NULL, 'EM_TRANSITO', CURRENT_TIMESTAMP - INTERVAL '3 days', NULL,
   'Aprovada pelo Admin; exemplar ja foi despachado da Biblioteca Sul.'),
(5, 11, 5, 4, 3, 1,    NULL, 'CONCLUIDA', CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '7 days',
   'Transferencia concluida — exemplar confirmado na chegada a Biblioteca Centro.'),
(6, 12, 4, 2, 6, 1,    NULL, 'REJEITADA', CURRENT_TIMESTAMP - INTERVAL '9 days', CURRENT_TIMESTAMP - INTERVAL '8 days',
   'Rejeitada pelo Admin: exemplar com reserva ativa na origem.');

-- ─── Historico de circulacao ───
INSERT INTO historico_circulacao (id, exemplar_id, evento, usuario_id, biblioteca_id, data_evento, observacoes) VALUES
(1, 1,  'CADASTRO',              2, 1, CURRENT_TIMESTAMP - INTERVAL '400 days', 'Exemplar cadastrado no acervo inicial.'),
(2, 1,  'EMPRESTIMO',            7, 1, CURRENT_TIMESTAMP - INTERVAL '60 days',  'Emprestimo para Bruno Alves.'),
(3, 1,  'DEVOLUCAO',             7, 1, CURRENT_TIMESTAMP - INTERVAL '47 days',  'Devolucao em bom estado.'),
(4, 6,  'EMPRESTIMO',            6, 4, CURRENT_TIMESTAMP - INTERVAL '20 days',  'Emprestimo para Ana Souza.'),
(5, 12, 'TRANSFERENCIA_SAIDA',   1, 4, CURRENT_TIMESTAMP - INTERVAL '8 days',   'Saida da Biblioteca Centro rumo a Tijuca.'),
(6, 11, 'TRANSFERENCIA_CHEGADA', 1, 4, CURRENT_TIMESTAMP - INTERVAL '7 days',   'Chegada confirmada na Biblioteca Centro.'),
(7, 13, 'CADASTRO',              2, 1, CURRENT_TIMESTAMP - INTERVAL '5 days',   'Unico exemplar da rede — indisponivel para emprestimo (RN11).');

-- ─── Demandas de aquisicao ───
INSERT INTO demanda_aquisicao (id, titulo, autor, isbn, total_solicitacoes, status, criada_em, atualizada_em) VALUES
(1, 'Grande Sertao: Veredas', 'Guimaraes Rosa',   NULL, 2, 'ABERTA',     CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '2 days'),
(2, 'Capitaes da Areia',      'Jorge Amado',      NULL, 1, 'EM_ANALISE', CURRENT_TIMESTAMP - INTERVAL '8 days',  CURRENT_TIMESTAMP - INTERVAL '1 days'),
(3, 'Vidas Secas',            'Graciliano Ramos', NULL, 1, 'APROVADA',   CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '20 days');

-- ============================================================================
-- Sincroniza as sequences: como inserimos IDs explicitos acima, sem isto o
-- proximo INSERT feito pela aplicacao tentaria reusar o ID 1 e quebraria.
-- ============================================================================
SELECT setval(pg_get_serial_sequence('categoria','id'),                 (SELECT MAX(id) FROM categoria));
SELECT setval(pg_get_serial_sequence('biblioteca','id'),                (SELECT MAX(id) FROM biblioteca));
SELECT setval(pg_get_serial_sequence('usuario','id'),                   (SELECT MAX(id) FROM usuario));
SELECT setval(pg_get_serial_sequence('livro','id'),                     (SELECT MAX(id) FROM livro));
SELECT setval(pg_get_serial_sequence('exemplar','id'),                  (SELECT MAX(id) FROM exemplar));
SELECT setval(pg_get_serial_sequence('emprestimo','id'),                (SELECT MAX(id) FROM emprestimo));
SELECT setval(pg_get_serial_sequence('reserva','id'),                   (SELECT MAX(id) FROM reserva));
SELECT setval(pg_get_serial_sequence('solicitacao_transferencia','id'), (SELECT MAX(id) FROM solicitacao_transferencia));
SELECT setval(pg_get_serial_sequence('historico_circulacao','id'),      (SELECT MAX(id) FROM historico_circulacao));
SELECT setval(pg_get_serial_sequence('demanda_aquisicao','id'),         (SELECT MAX(id) FROM demanda_aquisicao));
