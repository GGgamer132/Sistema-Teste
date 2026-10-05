-- ============================================================================
-- CIRCULA BOOK — seed de demonstração (spec §8)
-- Executado pelo Spring Boot depois que o Hibernate cria as tabelas
-- (spring.jpa.defer-datasource-initialization=true). Pensado para o roteiro de
-- gravação da §9.0: U1 começa zerado; U2 e U3 têm histórico e pendências.
-- Todas as datas são relativas ao momento do boot.
-- ============================================================================

-- ─── Categorias (§8.2b) ───
INSERT INTO categoria (id, nome, descricao) VALUES
(1, 'Romance',              'Narrativas centradas em relações e conflitos pessoais'),
(2, 'Ficção Científica',    'Futuros possíveis, tecnologia e sociedades imaginadas'),
(3, 'Fantasia',             'Mundos imaginários, magia e aventura'),
(4, 'Suspense e Policial',  'Mistérios, investigações e crimes'),
(5, 'Biografia',            'Relatos da vida de pessoas reais'),
(6, 'Infantil e Juvenil',   'Obras para crianças e jovens leitores'),
(7, 'História e Sociedade', 'Ensaios sobre história, cultura e sociedade'),
(8, 'Poesia e Crônicas',    'Poemas, crônicas e textos curtos'),
(9, 'Tecnologia',           'Computação e desenvolvimento de software');

-- ─── Bibliotecas (§8.2) — a Tijuca começa sem acervo ───
INSERT INTO biblioteca (id, nome, endereco, email, telefone, ativa, criada_em) VALUES
(1, 'Biblioteca Central',                    'Praça da Biblioteca, 50 - Centro',      'central@circulabook.com',     '(21) 3000-0001', TRUE, CURRENT_TIMESTAMP - INTERVAL '500 days'),
(2, 'Biblioteca Comunitária de Vila Isabel', 'Rua das Letras, 100 - Vila Isabel',     'vilaisabel@circulabook.com',  '(21) 3000-0002', TRUE, CURRENT_TIMESTAMP - INTERVAL '500 days'),
(3, 'Biblioteca Popular da Tijuca',          'Rua dos Leitores, 200 - Tijuca',        'tijuca@circulabook.com',      '(21) 3000-0003', TRUE, CURRENT_TIMESTAMP - INTERVAL '30 days');

-- ─── Usuários (§8.1) — senha de todos: senha123 (hash BCrypt real) ───
INSERT INTO usuario (id, nome, email, senha_hash, tipo, biblioteca_id, ativo, criado_em, bloqueado_ate) VALUES
(1, 'Admin',                     'admin@circulabook.com',                   '$2a$10$65hIwrxXMxThdo9bISpK1uMNzOEm5D/8Ox2cHJRHMnBxgpi.vYExu', 'ADMIN',         NULL, TRUE, CURRENT_TIMESTAMP - INTERVAL '500 days', NULL),
(2, 'Bibliotecário Central',     'bibliotecariocentral@circulabook.com',    '$2a$10$65hIwrxXMxThdo9bISpK1uMNzOEm5D/8Ox2cHJRHMnBxgpi.vYExu', 'BIBLIOTECARIO', 1,    TRUE, CURRENT_TIMESTAMP - INTERVAL '500 days', NULL),
(3, 'Bibliotecário Vila Isabel', 'bibliotecariovilaisabel@circulabook.com', '$2a$10$65hIwrxXMxThdo9bISpK1uMNzOEm5D/8Ox2cHJRHMnBxgpi.vYExu', 'BIBLIOTECARIO', 2,    TRUE, CURRENT_TIMESTAMP - INTERVAL '500 days', NULL),
(4, 'Bibliotecário Tijuca',      'bibliotecariotijuca@circulabook.com',     '$2a$10$65hIwrxXMxThdo9bISpK1uMNzOEm5D/8Ox2cHJRHMnBxgpi.vYExu', 'BIBLIOTECARIO', 3,    TRUE, CURRENT_TIMESTAMP - INTERVAL '30 days',  NULL),
(5, 'Usuário 1',                 'usuario1@circulabook.com',                '$2a$10$65hIwrxXMxThdo9bISpK1uMNzOEm5D/8Ox2cHJRHMnBxgpi.vYExu', 'COMUM',         NULL, TRUE, CURRENT_TIMESTAMP - INTERVAL '2 days',   NULL),
(6, 'Usuário 2',                 'usuario2@circulabook.com',                '$2a$10$65hIwrxXMxThdo9bISpK1uMNzOEm5D/8Ox2cHJRHMnBxgpi.vYExu', 'COMUM',         NULL, TRUE, CURRENT_TIMESTAMP - INTERVAL '120 days', NULL),
(7, 'Usuário 3',                 'usuario3@circulabook.com',                '$2a$10$65hIwrxXMxThdo9bISpK1uMNzOEm5D/8Ox2cHJRHMnBxgpi.vYExu', 'COMUM',         NULL, TRUE, CURRENT_TIMESTAMP - INTERVAL '120 days', NULL);

-- ─── Livros (§8.3) — ISBNs fictícios ───
INSERT INTO livro (id, titulo, autor, isbn, editora, ano_publicacao, sinopse, categoria_id) VALUES
(1,  'Dom Casmurro', 'Machado de Assis', '978-65-90000-01-0', 'Editora Rede Literária', 1899,
     'Bento Santiago revisita a juventude e o casamento com Capitu, sem nunca ter certeza se foi traído.', 1),
(2,  'Memórias Póstumas de Brás Cubas', 'Machado de Assis', '978-65-90000-02-0', 'Editora Rede Literária', 1881,
     'Um defunto-autor narra a própria vida e ironiza as vaidades da sociedade do século XIX.', 1),
(3,  'O Cortiço', 'Aluísio Azevedo', '978-65-90000-03-0', 'Editora Rede Literária', 1890,
     'A vida coletiva de um cortiço carioca e a ambição de seu dono, João Romão.', 1),
(4,  'Capitães da Areia', 'Jorge Amado', '978-65-90000-04-0', 'Editora Rede Literária', 1937,
     'Um grupo de meninos de rua sobrevive nas ruas e trapiches de Salvador.', 1),
(5,  'Grande Sertão: Veredas', 'Guimarães Rosa', '978-65-90000-05-0', 'Editora Rede Literária', 1956,
     'Riobaldo, ex-jagunço, conta suas travessias pelo sertão e sua relação com Diadorim.', 1),
(6,  '1984', 'George Orwell', '978-65-90000-06-0', 'Editora Horizonte', 1949,
     'Winston Smith vive sob a vigilância permanente do Grande Irmão.', 2),
(7,  'Fahrenheit 451', 'Ray Bradbury', '978-65-90000-07-0', 'Editora Horizonte', 1953,
     'Num futuro em que livros são proibidos, um bombeiro encarregado de queimá-los começa a lê-los.', 2),
(8,  'Duna', 'Frank Herbert', '978-65-90000-08-0', 'Editora Horizonte', 1965,
     'Paul Atreides chega ao planeta desértico Arrakis, fonte da especiaria mais valiosa do universo.', 2),
(9,  'O Hobbit', 'J. R. R. Tolkien', '978-65-90000-09-0', 'Editora Horizonte', 1937,
     'Bilbo Bolseiro parte numa aventura com anões para recuperar um tesouro guardado por um dragão.', 3),
(10, 'Harry Potter e a Pedra Filosofal', 'J. K. Rowling', '978-65-90000-10-0', 'Editora Horizonte', 1997,
     'Um menino descobre que é bruxo e começa seus estudos em Hogwarts.', 3),
(11, 'Assassinato no Expresso do Oriente', 'Agatha Christie', '978-65-90000-11-0', 'Editora Mistério', 1934,
     'Hercule Poirot investiga um crime a bordo de um trem preso na neve.', 4),
(12, 'O Cão dos Baskerville', 'Arthur Conan Doyle', '978-65-90000-12-0', 'Editora Mistério', 1902,
     'Sherlock Holmes investiga a lenda de um cão sobrenatural que assombra uma família.', 4),
(13, 'O Diário de Anne Frank', 'Anne Frank', '978-65-90000-13-0', 'Editora Memória', 1947,
     'O diário de uma jovem judia escondida com a família durante a ocupação nazista.', 5),
(14, 'Steve Jobs', 'Walter Isaacson', '978-65-90000-14-0', 'Editora Memória', 2011,
     'Biografia do cofundador da Apple, baseada em entrevistas com ele e com quem o conheceu.', 5),
(15, 'Quarto de Despejo', 'Carolina Maria de Jesus', '978-65-90000-15-0', 'Editora Memória', 1960,
     'O diário de uma catadora de papel na favela do Canindé, em São Paulo.', 5),
(16, 'O Pequeno Príncipe', 'Antoine de Saint-Exupéry', '978-65-90000-16-0', 'Editora Ciranda', 1943,
     'Um aviador que cai no deserto conhece um pequeno príncipe vindo de outro planeta.', 6),
(17, 'Sapiens', 'Yuval Noah Harari', '978-65-90000-17-0', 'Editora Ágora', 2011,
     'Uma breve história da humanidade, da pré-história aos dias de hoje.', 7),
(18, 'Casa-Grande & Senzala', 'Gilberto Freyre', '978-65-90000-18-0', 'Editora Ágora', 1933,
     'Ensaio sobre a formação da sociedade brasileira no regime patriarcal.', 7),
(19, 'Antologia Poética', 'Carlos Drummond de Andrade', '978-65-90000-19-0', 'Editora Rede Literária', 1962,
     'Seleção de poemas organizada pelo próprio autor.', 8),
(20, 'Código Limpo (Clean Code)', 'Robert C. Martin', '978-65-90000-20-0', 'Editora Bits', 2008,
     'Boas práticas para escrever código legível e fácil de manter.', 9);

-- ─── Exemplares (o "quadro" do Blackboard) — estados do instante inicial (§8.3) ───
-- Biblioteca: 1 = Central, 2 = Vila Isabel (a Tijuca não tem exemplares).
INSERT INTO exemplar (id, livro_id, biblioteca_id, status, estado_conservacao, adicionado_em) VALUES
-- 1 Dom Casmurro: C 3 + VI 2, todos disponíveis
(1,  1,  1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(2,  1,  1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(3,  1,  1, 'DISPONIVEL',           'NOVO',       CURRENT_TIMESTAMP - INTERVAL '400 days'),
(4,  1,  2, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(5,  1,  2, 'DISPONIVEL',           'USADO',      CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 3 O Cortiço: C 2 + VI 1
(6,  3,  1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(7,  3,  1, 'DISPONIVEL',           'USADO',      CURRENT_TIMESTAMP - INTERVAL '400 days'),
(8,  3,  2, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 4 Capitães da Areia: VI 2
(9,  4,  2, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(10, 4,  2, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 5 Grande Sertão: C 1, emprestado a U2 (origem com 1 só exemplar: RN15 bloqueia transferência)
(11, 5,  1, 'EMPRESTADO',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 6 1984: C 2 + VI 1
(12, 6,  1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(13, 6,  1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(14, 6,  2, 'DISPONIVEL',           'USADO',      CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 7 Fahrenheit 451: C 2 + 1 em trânsito para a VI (T2)
(15, 7,  1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(16, 7,  1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(17, 7,  1, 'EM_TRANSFERENCIA',     'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 8 Duna: C 2, ambos emprestados (U2 atrasado, U3), nenhum disponível
(18, 8,  1, 'EMPRESTADO',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(19, 8,  1, 'EMPRESTADO',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 9 O Hobbit: C 1 emprestado a U2 com fila (R1) + 1 em reparo
(20, 9,  1, 'EMPRESTADO_RESERVADO', 'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(21, 9,  1, 'INDISPONIVEL',         'DANIFICADO', CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 10 Harry Potter: C 2 + VI 1 reservado para U3 (R2) + VI 1 disponível
(22, 10, 1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(23, 10, 1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(24, 10, 2, 'RESERVADO',            'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(25, 10, 2, 'DISPONIVEL',           'NOVO',       CURRENT_TIMESTAMP - INTERVAL '60 days'),
-- 11 Assassinato no Expresso do Oriente: C 1 + VI 2
(26, 11, 1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(27, 11, 2, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(28, 11, 2, 'DISPONIVEL',           'USADO',      CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 12 O Cão dos Baskerville: VI 2
(29, 12, 2, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(30, 12, 2, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 13 O Diário de Anne Frank: C 2
(31, 13, 1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(32, 13, 1, 'DISPONIVEL',           'USADO',      CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 14 Steve Jobs: C 1 + VI 1 danificado (BibVI reativa na demonstração)
(33, 14, 1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(34, 14, 2, 'INDISPONIVEL',         'DANIFICADO', CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 15 Quarto de Despejo: VI 2
(35, 15, 2, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(36, 15, 2, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 16 O Pequeno Príncipe: C 2 + VI 3
(37, 16, 1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(38, 16, 1, 'DISPONIVEL',           'NOVO',       CURRENT_TIMESTAMP - INTERVAL '400 days'),
(39, 16, 2, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(40, 16, 2, 'DISPONIVEL',           'USADO',      CURRENT_TIMESTAMP - INTERVAL '400 days'),
(41, 16, 2, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 17 Sapiens: C 2 + VI 1 (o 44 veio da Central pela avulsa concluída T3)
(42, 17, 1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(43, 17, 1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(44, 17, 2, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 18 Casa-Grande & Senzala: C 1
(45, 18, 1, 'DISPONIVEL',           'USADO',      CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 19 Antologia Poética: VI 2
(46, 19, 2, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(47, 19, 2, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
-- 20 Código Limpo: C 2 + VI 1
(48, 20, 1, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days'),
(49, 20, 1, 'DISPONIVEL',           'NOVO',       CURRENT_TIMESTAMP - INTERVAL '400 days'),
(50, 20, 2, 'DISPONIVEL',           'BOM',        CURRENT_TIMESTAMP - INTERVAL '400 days');

-- ─── Empréstimos (§8.4) — prazo fixo de 14 dias ───
-- Ativos: U2 3/3 (Duna atrasado há 6 dias), U3 1/3. U1 nenhum.
-- Histórico: U2 devolveu Sapiens e O Pequeno Príncipe; U3 devolveu Código Limpo e Anne Frank.
INSERT INTO emprestimo (id, exemplar_id, usuario_id, biblioteca_id, data_emprestimo, data_prev_devolucao, data_devolucao, status) VALUES
(1, 20, 6, 1, CURRENT_TIMESTAMP - INTERVAL '3 days',  CURRENT_TIMESTAMP + INTERVAL '11 days', NULL, 'ATIVO'),
(2, 11, 6, 1, CURRENT_TIMESTAMP - INTERVAL '6 days',  CURRENT_TIMESTAMP + INTERVAL '8 days',  NULL, 'ATIVO'),
(3, 18, 6, 1, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '6 days',  NULL, 'ATRASADO'),
(4, 19, 7, 1, CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP + INTERVAL '4 days',  NULL, 'ATIVO'),
(5, 42, 6, 1, CURRENT_TIMESTAMP - INTERVAL '60 days', CURRENT_TIMESTAMP - INTERVAL '46 days', CURRENT_TIMESTAMP - INTERVAL '50 days', 'DEVOLVIDO'),
(6, 39, 6, 2, CURRENT_TIMESTAMP - INTERVAL '45 days', CURRENT_TIMESTAMP - INTERVAL '31 days', CURRENT_TIMESTAMP - INTERVAL '35 days', 'DEVOLVIDO'),
(7, 48, 7, 1, CURRENT_TIMESTAMP - INTERVAL '41 days', CURRENT_TIMESTAMP - INTERVAL '27 days', CURRENT_TIMESTAMP - INTERVAL '28 days', 'DEVOLVIDO'),
(8, 31, 7, 1, CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '16 days', CURRENT_TIMESTAMP - INTERVAL '18 days', 'DEVOLVIDO');

-- ─── Reservas (§8.4) ───
-- R1: U3, O Hobbit, fila na Central com retirada na Vila Isabel (gera o pedido T1).
-- R2: U3, Harry Potter, fila e retirada na Vila Isabel, pronta (exemplar 24), expira em 2 dias.
-- 3 a 5: histórico (U2 cancelou Dom Casmurro; U3 retirou Código Limpo; U3 deixou 1984 expirar).
INSERT INTO reserva (id, livro_id, usuario_id, biblioteca_fila_id, biblioteca_destino_id, exemplar_id, data_reserva, data_expiracao, status) VALUES
(1, 9,  7, 1, 2, NULL, CURRENT_TIMESTAMP - INTERVAL '1 days',  CURRENT_TIMESTAMP + INTERVAL '2 days',  'PENDENTE'),
(2, 10, 7, 2, 2, 24,   CURRENT_TIMESTAMP - INTERVAL '6 days',  CURRENT_TIMESTAMP + INTERVAL '2 days',  'DISPONIVEL'),
(3, 1,  6, 1, 1, NULL, CURRENT_TIMESTAMP - INTERVAL '70 days', CURRENT_TIMESTAMP - INTERVAL '67 days', 'CANCELADA'),
(4, 20, 7, 1, 1, 48,   CURRENT_TIMESTAMP - INTERVAL '45 days', CURRENT_TIMESTAMP - INTERVAL '40 days', 'RETIRADA'),
(5, 6,  7, 1, 1, 12,   CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '22 days', 'EXPIRADA');

-- ─── Transferências (§8.4) ───
-- T1: pedido da R1, sem exemplar, aguardando o Admin. T2: avulsa em trânsito. T3: avulsa concluída.
INSERT INTO solicitacao_transferencia
 (id, livro_id, exemplar_id, biblioteca_origem_id, biblioteca_destino_id, solicitante_id, aprovador_id, reserva_id, status, data_solicitacao, data_conclusao, observacoes) VALUES
(1, 9,  NULL, 1, 2, 7, NULL, 1,    'PENDENTE',    CURRENT_TIMESTAMP - INTERVAL '1 days',  NULL,
   'Pedido gerado pela reserva de Usuário 3: fila na Biblioteca Central, retirada na Biblioteca Comunitária de Vila Isabel.'),
(2, 7,  17,   1, 2, 1, 1,    NULL, 'EM_TRANSITO', CURRENT_TIMESTAMP - INTERVAL '3 days',  NULL,
   'Transferência avulsa para reforçar o acervo de ficção científica da Vila Isabel.'),
(3, 17, 44,   1, 2, 1, 1,    NULL, 'CONCLUIDA',   CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '10 days',
   'Transferência avulsa concluída: exemplar recebido na Vila Isabel.');

-- ─── Histórico de circulação ───
-- usuario_id/responsavel = quem executou a ação (bibliotecário, Admin, leitor que entrou na fila
-- ou NULL/"Sistema" para rotinas automáticas), nunca o leitor que recebeu o livro.
-- 1 a 50: cadastro de cada exemplar no acervo de origem (o 44 e o 17 nasceram na Central).
INSERT INTO historico_circulacao (id, exemplar_id, evento, usuario_id, biblioteca_id, data_evento, observacoes, responsavel)
SELECT e.id, e.id, 'CADASTRO',
       CASE WHEN e.id IN (17, 44) OR e.biblioteca_id = 1 THEN 2 ELSE 3 END,
       CASE WHEN e.id IN (17, 44) THEN 1 ELSE e.biblioteca_id END,
       e.adicionado_em, 'Situação: novo → disponível.',
       CASE WHEN e.id IN (17, 44) OR e.biblioteca_id = 1 THEN 'Bibliotecário Central' ELSE 'Bibliotecário Vila Isabel' END
FROM exemplar e;

INSERT INTO historico_circulacao (id, exemplar_id, evento, usuario_id, biblioteca_id, data_evento, observacoes, responsavel) VALUES
-- Empréstimos ativos (registrados pelo Bibliotecário Central)
(51, 20, 'EMPRESTIMO', 2, 1, CURRENT_TIMESTAMP - INTERVAL '3 days',  'Empréstimo para Usuário 2. Devolução prevista em 14 dias.', 'Bibliotecário Central'),
(52, 20, 'FILA',       7, 1, CURRENT_TIMESTAMP - INTERVAL '1 days',  'Formou-se fila de espera para o título na Biblioteca Central: quando este exemplar voltar, ele fica com o próximo da fila.', 'Usuário 3'),
(53, 11, 'EMPRESTIMO', 2, 1, CURRENT_TIMESTAMP - INTERVAL '6 days',  'Empréstimo para Usuário 2. Devolução prevista em 14 dias.', 'Bibliotecário Central'),
(54, 18, 'EMPRESTIMO', 2, 1, CURRENT_TIMESTAMP - INTERVAL '20 days', 'Empréstimo para Usuário 2. Devolução prevista em 14 dias.', 'Bibliotecário Central'),
(55, 19, 'EMPRESTIMO', 2, 1, CURRENT_TIMESTAMP - INTERVAL '10 days', 'Empréstimo para Usuário 3. Devolução prevista em 14 dias.', 'Bibliotecário Central'),
-- Baixas (em reparo)
(56, 21, 'BAIXA',      2, 1, CURRENT_TIMESTAMP - INTERVAL '15 days', 'Em reparo: lombada solta.', 'Bibliotecário Central'),
(57, 34, 'BAIXA',      3, 2, CURRENT_TIMESTAMP - INTERVAL '8 days',  'Capa rasgada e páginas soltas.', 'Bibliotecário Vila Isabel'),
-- Harry Potter separado para U3 (R2)
(58, 24, 'RESERVA',    3, 2, CURRENT_TIMESTAMP - INTERVAL '1 days',  'Separado para Usuário 3. Retirada na Biblioteca Comunitária de Vila Isabel em até 3 dias.', 'Bibliotecário Vila Isabel'),
-- Transferências avulsas (Admin) e chegada confirmada na Vila Isabel
(59, 17, 'TRANSFERENCIA_SAIDA',   1, 1, CURRENT_TIMESTAMP - INTERVAL '3 days',  'Saída para a Biblioteca Comunitária de Vila Isabel (transferência avulsa).', 'Admin'),
(60, 44, 'TRANSFERENCIA_SAIDA',   1, 1, CURRENT_TIMESTAMP - INTERVAL '12 days', 'Saída para a Biblioteca Comunitária de Vila Isabel (transferência avulsa).', 'Admin'),
(61, 44, 'TRANSFERENCIA_CHEGADA', 3, 2, CURRENT_TIMESTAMP - INTERVAL '10 days', 'Chegada confirmada na Biblioteca Comunitária de Vila Isabel.', 'Bibliotecário Vila Isabel'),
-- Histórico de U2
(62, 42, 'EMPRESTIMO', 2, 1, CURRENT_TIMESTAMP - INTERVAL '60 days', 'Empréstimo para Usuário 2. Devolução prevista em 14 dias.', 'Bibliotecário Central'),
(63, 42, 'DEVOLUCAO',  2, 1, CURRENT_TIMESTAMP - INTERVAL '50 days', 'Devolução de Usuário 2 em bom estado, no prazo.', 'Bibliotecário Central'),
(64, 39, 'EMPRESTIMO', 3, 2, CURRENT_TIMESTAMP - INTERVAL '45 days', 'Empréstimo para Usuário 2. Devolução prevista em 14 dias.', 'Bibliotecário Vila Isabel'),
(65, 39, 'DEVOLUCAO',  3, 2, CURRENT_TIMESTAMP - INTERVAL '35 days', 'Devolução de Usuário 2 em bom estado, no prazo.', 'Bibliotecário Vila Isabel'),
-- Histórico de U3
(66, 48, 'RESERVA',    2, 1, CURRENT_TIMESTAMP - INTERVAL '43 days', 'Separado para Usuário 3. Retirada na Biblioteca Central em até 3 dias.', 'Bibliotecário Central'),
(67, 48, 'EMPRESTIMO', 2, 1, CURRENT_TIMESTAMP - INTERVAL '41 days', 'Empréstimo para Usuário 3 (reserva retirada). Devolução prevista em 14 dias.', 'Bibliotecário Central'),
(68, 48, 'DEVOLUCAO',  2, 1, CURRENT_TIMESTAMP - INTERVAL '28 days', 'Devolução de Usuário 3 em bom estado, no prazo.', 'Bibliotecário Central'),
(69, 31, 'EMPRESTIMO', 2, 1, CURRENT_TIMESTAMP - INTERVAL '30 days', 'Empréstimo para Usuário 3. Devolução prevista em 14 dias.', 'Bibliotecário Central'),
(70, 31, 'DEVOLUCAO',  2, 1, CURRENT_TIMESTAMP - INTERVAL '18 days', 'Devolução de Usuário 3 em bom estado, no prazo.', 'Bibliotecário Central'),
(71, 12, 'RESERVA',    2, 1, CURRENT_TIMESTAMP - INTERVAL '25 days', 'Separado para Usuário 3. Retirada na Biblioteca Central em até 3 dias.', 'Bibliotecário Central'),
(72, 12, 'RESERVA',    NULL, 1, CURRENT_TIMESTAMP - INTERVAL '22 days', 'Reserva de Usuário 3 expirou; exemplar liberado (situação: reservado → disponível).', 'Sistema');

-- ─── Demandas de aquisição (§8.4) — U1 não pediu nada ───
INSERT INTO demanda_aquisicao (id, titulo, autor, isbn, total_solicitacoes, status, criada_em, atualizada_em) VALUES
(1, 'Ensaio sobre a Cegueira', 'José Saramago',        NULL, 2, 'ABERTA',     CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '9 days'),
(2, 'Torto Arado',             'Itamar Vieira Junior', NULL, 1, 'EM_ANALISE', CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP - INTERVAL '5 days'),
(3, 'A Hora da Estrela',       'Clarice Lispector',    NULL, 1, 'APROVADA',   CURRENT_TIMESTAMP - INTERVAL '40 days', CURRENT_TIMESTAMP - INTERVAL '30 days');

INSERT INTO demanda_solicitante (id, demanda_id, usuario_id, criado_em) VALUES
(1, 1, 6, CURRENT_TIMESTAMP - INTERVAL '20 days'),
(2, 1, 7, CURRENT_TIMESTAMP - INTERVAL '9 days'),
(3, 2, 7, CURRENT_TIMESTAMP - INTERVAL '15 days'),
(4, 3, 6, CURRENT_TIMESTAMP - INTERVAL '40 days');

-- ─── Notificações iniciais, não lidas (§8.4) — U1 e BibT: nenhuma ───
-- tipo/referencia_id iguais aos que o sistema gera (a rotina de atraso não duplica o aviso de Duna).
INSERT INTO notificacao (id, usuario_id, titulo, mensagem, tipo, lida, criada_em, link, referencia_id) VALUES
(1, 7, 'Reserva pronta para retirada',
    '"Harry Potter e a Pedra Filosofal" está separado para você na Biblioteca Comunitária de Vila Isabel. Retire até '
      || TO_CHAR(CURRENT_TIMESTAMP + INTERVAL '2 days', 'DD/MM') || '.',
    'RESERVA_PRONTA', FALSE, CURRENT_TIMESTAMP - INTERVAL '1 days', '/minhas-reservas', 2),
(2, 6, 'Empréstimo atrasado',
    'A devolução de "Duna" na Biblioteca Central venceu em ' || TO_CHAR(CURRENT_TIMESTAMP - INTERVAL '6 days', 'DD/MM/YYYY')
      || '. Devolva o quanto antes: cada dia de atraso gera 2 dias sem poder pegar livros.',
    'EMPRESTIMO_ATRASADO', FALSE, CURRENT_TIMESTAMP - INTERVAL '6 days', '/meus-emprestimos', 3),
(3, 3, 'Transferência a caminho',
    'O Exemplar nº 17 de "Fahrenheit 451" está vindo da Biblioteca Central. Confirme a chegada quando recebê-lo.',
    'TRANSFERENCIA_A_CAMINHO', FALSE, CURRENT_TIMESTAMP - INTERVAL '3 days', '/biblioteca/transferencias', 2),
(4, 3, 'Reserva aguardando retirada',
    'Usuário 3 virá buscar "Harry Potter e a Pedra Filosofal" até ' || TO_CHAR(CURRENT_TIMESTAMP + INTERVAL '2 days', 'DD/MM') || '.',
    'RETIRADA_NA_BIBLIOTECA', FALSE, CURRENT_TIMESTAMP - INTERVAL '1 days', '/biblioteca/reservas', 2),
(5, 1, 'Novo pedido de transferência',
    'Usuário 3 pediu para retirar "O Hobbit" na Biblioteca Comunitária de Vila Isabel (fila na Biblioteca Central).',
    'NOVO_PEDIDO', FALSE, CURRENT_TIMESTAMP - INTERVAL '1 days', '/admin/transferencias', 1);

-- ============================================================================
-- Sincroniza as sequences: com IDs explícitos acima, sem isto o próximo INSERT
-- feito pela aplicação tentaria reusar o ID 1.
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
SELECT setval(pg_get_serial_sequence('demanda_solicitante','id'),       (SELECT MAX(id) FROM demanda_solicitante));
SELECT setval(pg_get_serial_sequence('notificacao','id'),               (SELECT MAX(id) FROM notificacao));
