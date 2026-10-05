# Manual do Usuário — Circula Book

Guia para leitores, bibliotecários e administradores da rede. Explica cada tela e as regras do sistema em linguagem simples.

## Sumário

1. [O que é o Circula Book](#1-o-que-é-o-circula-book)
2. [Funcionalidades por perfil](#2-funcionalidades-por-perfil)
3. [Regras principais](#3-regras-principais)
4. [Fluxos passo a passo](#4-fluxos-passo-a-passo)
5. [Glossário, mensagens e perguntas frequentes](#5-glossário-mensagens-e-perguntas-frequentes)
6. [Diagramas recomendados](#6-diagramas-recomendados)

---

## 1. O que é o Circula Book

O Circula Book liga as bibliotecas comunitárias de uma rede. Você encontra um livro em qualquer unidade, entra na fila de espera quando todos os exemplares de uma biblioteca estão emprestados e pode escolher retirar o livro em outra biblioteca, que recebe o exemplar por transferência.

| Perfil | Para quem | O que faz |
| --- | --- | --- |
| Usuário | Leitores da comunidade | Busca livros, entra em filas, acompanha reservas e empréstimos, pede livros que a rede não tem |
| Bibliotecário | Equipe de uma biblioteca | Empresta, recebe devoluções, cadastra exemplares e recebe transferências, sempre na própria biblioteca |
| Administrador | Coordenação da rede | Decide transferências, cria transferências entre bibliotecas, cuida do catálogo, das bibliotecas, dos bibliotecários e das demandas, consulta histórico e relatórios |

### 1.1 Como acessar

Abra o endereço do sistema no navegador (na instalação local, `http://localhost:5173`). Todas as telas exigem login.

- **Entrar:** informe e-mail e senha e clique em **Entrar**. Você vai para a tela inicial do seu perfil: Usuário em **Buscar Livros**, Bibliotecário em **Empréstimos**, Administrador em **Painel**.
- **Cadastrar-se:** na tela de login, clique em **Cadastre-se**, preencha nome, e-mail, senha (mínimo de 6 caracteres) e a confirmação. A conta criada é sempre de Usuário e você já entra logado. Contas de Bibliotecário são criadas pelo Administrador.
- **Sair:** botão **Sair** no topo da tela.
- A sessão dura 8 horas. Depois disso o sistema pede login de novo.

### 1.2 Contas de demonstração

Senha de todas: `senha123`.

| Perfil | Nome | E-mail |
| --- | --- | --- |
| Administrador | Admin | `admin@circulabook.com` |
| Bibliotecário | Bibliotecário Central | `bibliotecariocentral@circulabook.com` |
| Bibliotecário | Bibliotecário Vila Isabel | `bibliotecariovilaisabel@circulabook.com` |
| Bibliotecário | Bibliotecário Tijuca | `bibliotecariotijuca@circulabook.com` |
| Usuário | Usuário 1 (conta nova, sem histórico) | `usuario1@circulabook.com` |
| Usuário | Usuário 2 (3 livros, um atrasado) | `usuario2@circulabook.com` |
| Usuário | Usuário 3 (com reservas) | `usuario3@circulabook.com` |

As bibliotecas de demonstração são a Biblioteca Central, a Biblioteca Comunitária de Vila Isabel e a Biblioteca Popular da Tijuca (esta começa sem livros).

### 1.3 O sino de notificações

Todos os perfis têm um sino no topo. A bolinha vermelha mostra quantos avisos ainda não foram lidos. Clique no sino para ver a lista (mais recentes primeiro); clicar num aviso marca como lido e leva à tela relacionada. **Marcar todas como lidas** zera o contador. O sino se atualiza sozinho a cada 30 segundos e quando você troca de tela.

---

## 2. Funcionalidades por perfil

### 2.1 Visitante (sem login)

Só vê as telas **Entrar** e **Criar conta** (seção 1.1).

### 2.2 Usuário

Menu: **Buscar Livros**, **Meus Empréstimos**, **Minhas Reservas**, **Meu Histórico**, **Pedir um Livro**.

**Buscar livros**
1. Em **Buscar Livros**, digite título, autor ou ISBN no campo principal. Se quiser, use os filtros (gênero, autor, ano de publicação de/até, ISBN) e clique em **Aplicar filtros**.
2. Os resultados mostram cada livro com a situação na rede: **Disponível**, **Aguardando** (todos os exemplares emprestados) ou **Indisponível: sem exemplares**.
3. Clique em **Ver detalhes** para abrir a ficha.

**Ficha do livro**
- Mostra os dados do livro e uma tabela com as bibliotecas que têm o título: quantos exemplares estão disponíveis de quantos existem. Use a busca da tabela ou marque **Só com exemplar disponível**.
- Biblioteca com exemplar livre: botão **Como retirar**, que explica que a retirada é presencial, com o bibliotecário. Não há reserva nesse caso.
- Biblioteca com todos os exemplares emprestados: botão **Entrar na fila**.
- O link **Resultados** no topo volta para a sua busca.

**Entrar na fila**
1. Na ficha, clique em **Entrar na fila** na biblioteca desejada. A tela mostra em qual posição você ficará.
2. Escolha onde retirar:
   - **Na própria biblioteca**: você retira onde entrou na fila.
   - **Em outra biblioteca**: escolha a biblioteca de retirada. Bibliotecas que não podem receber aparecem bloqueadas com o motivo (seção 3.4).
3. Clique em **Entrar na fila** e confirme no quadro de confirmação. Você vai para **Minhas Reservas**.

**Minhas Reservas**
- Cada reserva mostra o livro, a biblioteca da fila e a de retirada, sua posição, a situação (por exemplo **NA FILA** ou **PRONTA PARA RETIRADA**) e, quando há transferência, a etapa: *Transferência pendente*, *Aprovada, aguardando exemplar*, *Em trânsito*, *Transferência rejeitada* etc.
- Reserva pronta mostra **Retire até dd/mm/aaaa**.
- **Cancelar reserva** pede confirmação; você perde o lugar na fila. Se o livro já estiver a caminho de outra biblioteca, a viagem continua e o exemplar fica disponível lá para qualquer pessoa.

**Meus Empréstimos**
- Lista os livros que estão com você, a biblioteca, a data do empréstimo e a data de devolução. Atrasados aparecem como **Atrasado há N dias**.
- Mostra quantos você tem de 3 permitidos e, se houver, o aviso **Empréstimos bloqueados** com a data final do bloqueio.
- A devolução é feita no balcão da biblioteca, com o bibliotecário.

**Meu Histórico**
- Empréstimos devolvidos e reservas encerradas (retiradas, canceladas ou expiradas). Filtre por tipo (**Todos**, **Empréstimos**, **Reservas**) e por período (**De**, **Até**) e clique em **Filtrar**.

**Pedir um Livro**
1. Quando a busca não encontra nada, use **Registrar interesse neste livro**, ou o menu **Pedir um Livro**.
2. Informe título e autor e confirme.
3. Se outras pessoas já pediram o mesmo livro, o sistema soma o seu pedido ao delas e mostra quantas pessoas pediram. A mesma pessoa não pode pedir o mesmo livro duas vezes. Seus pedidos aparecem em **Meus pedidos**.

### 2.3 Bibliotecário

Menu: **Empréstimos**, **Registrar Empréstimo**, **Registrar Devolução**, **Cadastrar Exemplares**, **Acervo**, **Transferências**, **Reservas**. Tudo vale apenas para a sua biblioteca.

**Empréstimos (painel)**
- Indicadores **Em andamento**, **Atrasados** e **Devolvidos** (clique para filtrar), busca por usuário, título ou número do exemplar, filtro de período e tabela com os empréstimos, atrasados em destaque. Atalhos para novo empréstimo e devolução.

**Registrar Empréstimo**
1. Digite nome ou e-mail do usuário e escolha-o na lista. A tela mostra se ele pode pegar livros (**Nenhuma pendência encontrada**) ou não (**Empréstimo bloqueado** ou **Limite de empréstimos**).
2. Escolha o exemplar na tabela. Aparecem os exemplares disponíveis da biblioteca e os que estão reservados para esse usuário.
3. O prazo é fixo: 14 dias. Clique em **Confirmar Empréstimo** e confirme. A mensagem mostra a data de devolução prevista.

**Registrar Devolução**
1. Busque pelo nome do usuário, título ou número do exemplar e escolha o empréstimo.
2. A tela mostra a data prevista, os dias de atraso e, se houver atraso, até quando o usuário ficará bloqueado.
3. Escolha a condição: **Bom estado** ou **Danificado**. Clique em **Confirmar Devolução** e confirme.
4. A mensagem informa o resultado: exemplar de volta ao acervo, separado para o próximo da fila, enviado direto para transferência (quando há pedido aprovado) ou fora de circulação (danificado).

**Cadastrar Exemplares**
1. Busque o livro no catálogo e clique em **Selecionar**. Só livros já cadastrados pelo Administrador aparecem.
2. Escolha a conservação (Novo, Bom ou Usado) e a quantidade (1 a 50).
3. Clique em **Cadastrar Exemplares** e confirme. A mensagem lista os números dos exemplares criados. Se houver fila do título na sua biblioteca, o primeiro exemplar já fica separado para o 1º da fila.

**Acervo**
- Todos os exemplares da biblioteca, com filtros (**Todos**, **Disponíveis**, **Emprestados**, **Reservados**, **Em transferência**, **Indisponíveis**) e busca.
- **Marcar indisponível** (com motivo) tira um exemplar de circulação. Se ele estava separado para alguém, a reserva volta para a fila na mesma posição e a pessoa é avisada.
- **Reativar** devolve um exemplar indisponível ao acervo (ou ao 1º da fila, se houver).

**Transferências**
- Aba **A receber**: exemplares a caminho da sua biblioteca, com origem e, quando for o caso, para quem estão reservados. Ao receber, clique em **Confirmar chegada**. Se o livro chegou estragado, marque **Chegou danificado** e escreva uma observação.
- Aba **Saindo**: exemplares que saíram da sua biblioteca (somente consulta).

**Reservas**
- **Aguardando retirada**: reservas prontas na sua biblioteca, com usuário, livro, exemplar e prazo. O botão **Novo empréstimo** abre o empréstimo já com o usuário.
- **Filas de espera por título**: quem está na fila de cada livro, em ordem.

### 2.4 Administrador

Menu: **Painel**, **Transferências**, **Catálogo**, **Categorias**, **Bibliotecas**, **Bibliotecários**, **Demandas**, **Histórico**, **Relatórios**. O Administrador não empresta, não recebe devoluções, não cadastra exemplares e não confirma chegada de transferência.

**Painel:** números da rede agora (bibliotecas, exemplares por situação, empréstimos e atrasos, reservas, transferências, demandas), em cartões e tabelas.

**Transferências**
1. **Pedidos aguardando decisão**: cada cartão mostra livro, quem pediu, origem → destino, se já há exemplar separado e se a origem pode ceder o livro. **Aprovar** ou **Rejeitar** (com motivo opcional), sempre com confirmação.
2. **Nova transferência avulsa**: escolha exemplares disponíveis na tabela (busca por título), a biblioteca de destino e, se quiser, uma observação; clique em **Transferir selecionados** e confirme (seção 4.3).
3. **Acompanhamento**: tabela de todas as transferências com filtro por situação.

**Catálogo:** cadastra e edita livros (título, autor, ISBN, editora, ano, categoria, sinopse). Um livro novo aparece na busca como "Indisponível: sem exemplares" até algum bibliotecário cadastrar exemplares.

**Categorias:** cadastra e edita categorias (nome e descrição).

**Bibliotecas:** cadastra, edita, desativa e reativa bibliotecas (nome, endereço, e-mail, telefone). Uma biblioteca só recebe transferências se estiver ativa e tiver bibliotecário ativo.

**Bibliotecários:** cadastra bibliotecários (nome, e-mail, senha inicial com no mínimo 6 caracteres e biblioteca obrigatória) e os desativa ou reativa. Esta tela não cria contas de Usuário.

**Demandas:** livros pedidos pelos leitores, dos mais pedidos para os menos. A demanda segue **Aberta → Em análise → Aprovada ou Rejeitada**, com os botões **Iniciar análise**, **Aprovar compra** e **Rejeitar**. Filtre por situação.

**Histórico:** linha do tempo de um exemplar (digite o número e clique em **Ver linha do tempo**) e eventos recentes da rede, com filtros por evento, biblioteca, período e número do exemplar. As marcas de fila ficam ocultas; marque **Mostrar marcas de fila** para vê-las.

**Relatórios:** seis tabelas com filtro de período: acervo por biblioteca e situação, empréstimos por biblioteca, atrasos, livros mais emprestados, demandas mais pedidas e transferências por situação.

---

## 3. Regras principais

### 3.1 Empréstimos

- Até **3 livros ao mesmo tempo**, de títulos diferentes.
- Prazo **fixo de 14 dias**.
- **Atraso gera bloqueio:** ao devolver com atraso, a pessoa fica sem poder pegar livros por **2 dias para cada dia de atraso** (6 dias de atraso = 12 dias de bloqueio). Ela recebe um aviso com a data final.
- O sistema avisa 2 dias antes do vencimento e quando o empréstimo atrasa.

### 3.2 Fila de espera

- A fila é **por biblioteca**. Só dá para entrar na fila de uma biblioteca que tem o livro e em que **todos** os exemplares estão emprestados. Se houver exemplar livre, a retirada é presencial, sem reserva.
- Não dá para ter duas reservas do mesmo livro, nem reservar um livro que já está com você.

### 3.3 Reserva pronta

Quando um exemplar volta e você é o primeiro da fila, ele fica separado para você e a reserva fica **pronta para retirada por 3 dias**. Passado o prazo, a reserva expira e o exemplar vai para o próximo da fila (ou volta ao acervo).

### 3.4 Retirar em outra biblioteca

A opção fica liberada para uma biblioteca quando:

- ela **não tem nenhum exemplar** do livro (se tivesse, você pegaria ou entraria na fila de lá);
- ela está **ativa** e tem **bibliotecário ativo** para receber o livro;
- a biblioteca da fila **pode ceder** um exemplar: nenhuma biblioteca pode ficar sem o livro. Se ela tem só 1 exemplar, ou se outras transferências do mesmo livro já vão sair de lá, a opção fica bloqueada com a explicação.

O pedido de transferência precisa ser aprovado pelo Administrador. Se for rejeitado, você continua na fila e retira na biblioteca da fila.

### 3.5 Quem confirma o quê

| Ação | Quem faz |
| --- | --- |
| Empréstimo e devolução | Bibliotecário da biblioteca do exemplar |
| Cadastro de exemplares | Bibliotecário, na própria biblioteca |
| Aprovar ou rejeitar transferência | Administrador |
| Confirmar a chegada de uma transferência | Bibliotecário da biblioteca que recebe |
| Cadastro de livros, categorias, bibliotecas e bibliotecários | Administrador |
| Conta de Usuário | A própria pessoa, em **Cadastre-se** |

---

## 4. Fluxos passo a passo

### 4.1 Reserva com transferência até a retirada

Exemplo do seed: o **Usuário 3** está na fila de *O Hobbit* da Biblioteca Central e quer retirar na Biblioteca Comunitária de Vila Isabel.

1. **Usuário 3** entrou na fila da Central escolhendo **Em outra biblioteca** → Vila Isabel. Em **Minhas Reservas** a reserva aparece como *Transferência pendente*. O Administrador recebe o aviso "Novo pedido de transferência".
2. **Administrador**, em **Transferências**, aprova o pedido do Hobbit. Ele passa a *Aprovada, aguardando exemplar* e o Usuário 3 é avisado.
3. **Bibliotecário Central** recebe a devolução do Hobbit do Usuário 2 em bom estado. Como o pedido está aprovado, a tela avisa que o exemplar seguiu direto para transferência. O Usuário 3 é avisado de que o livro está a caminho.
4. **Bibliotecário Vila Isabel**, em **Transferências → A receber**, vê o Hobbit "Reservado para Usuário 3" e clica em **Confirmar chegada**. A reserva fica pronta por 3 dias e o Usuário 3 é avisado.
5. **Bibliotecário Vila Isabel**, em **Reservas**, clica em **Novo empréstimo** na linha do Usuário 3 e confirma. A reserva é concluída.

Se o exemplar voltar antes da decisão do Administrador, ele fica separado na Central e o Administrador recebe um aviso urgente. Ao aprovar, o exemplar segue na hora; ao rejeitar, o Usuário 3 retira na Central.

### 4.2 Devolução com atraso

Exemplo: o **Usuário 2** está com *Duna* da Central, atrasado há 6 dias, e o **Usuário 1** está na fila desse livro.

1. **Bibliotecário Central**, em **Registrar Devolução**, busca "Usuário 2" e escolhe *Duna*.
2. A tela mostra **Devolução com atraso**: 6 dias, bloqueio total de 12 dias, e a data final.
3. Escolhe **Bom estado**, clica em **Confirmar Devolução** e confirma.
4. Resultado: o Usuário 2 fica bloqueado por 12 dias e recebe o aviso; o exemplar fica separado para o Usuário 1, que recebe "Reserva pronta para retirada".
5. Em **Reservas**, o Usuário 1 aparece com *Duna*; **Novo empréstimo** conclui a retirada.

### 4.3 Transferência avulsa em lote ("tudo ou nada")

Exemplo: o **Administrador** quer reforçar o acervo da Biblioteca Popular da Tijuca.

1. Em **Transferências**, clica em **Nova transferência avulsa**.
2. Busca "1984" e marca os **2** exemplares da Central; escolhe a Tijuca como destino e clica em **Transferir selecionados (2)**.
3. O sistema recusa tudo e explica: a Central tem só 2 exemplares de *1984*, e só 1 pode sair. **Nada é criado** e a seleção continua na tela.
4. Desmarca um exemplar de *1984*, busca "Dom Casmurro", marca 2 exemplares da Central e transfere os 3.
5. As 3 transferências aparecem **Em trânsito** no **Acompanhamento**. O Bibliotecário Tijuca confirma cada chegada em **Transferências → A receber**.

Regra: se qualquer item do lote deixaria uma biblioteca sem o livro (ou não puder sair), nenhum item é transferido.

### 4.4 Pedido de livro

Exemplo: o **Usuário 1** procura *Ensaio sobre a Cegueira*, que a rede não tem.

1. A busca mostra "0 livros encontrados" e o botão **Registrar interesse neste livro**.
2. O título vem preenchido; ele informa o autor (José Saramago) e confirma.
3. Como o Usuário 2 e o Usuário 3 já tinham pedido, a mensagem diz que agora 3 pessoas pediram o livro.
4. Se tentar de novo: "Você já registrou interesse neste livro."
5. O **Administrador** vê a demanda em **Demandas**, no topo da lista, e conduz a análise.

---

## 5. Glossário, mensagens e perguntas frequentes

### 5.1 Glossário

| Termo na tela | Significado |
| --- | --- |
| Exemplar nº X | Cada cópia física de um livro, identificada por um número |
| Disponível | Na estante, pode ser emprestado no balcão |
| Emprestado | Está com algum leitor |
| Emprestado (com fila) | Está com um leitor e há gente esperando por ele nesta biblioteca |
| Reservado | Separado para quem está em primeiro na fila |
| Em transferência | Viajando entre bibliotecas |
| Indisponível | Fora de circulação (danificado ou em reparo) |
| NA FILA | Sua reserva está esperando um exemplar |
| PRONTA PARA RETIRADA | O exemplar está separado para você; retire no prazo |
| AGUARDANDO TRANSFERÊNCIA | O exemplar foi separado e vai ou está indo para a biblioteca de retirada |
| Aprovada, aguardando exemplar | O Administrador aprovou; falta um exemplar ser devolvido |
| Em trânsito | O exemplar está a caminho |
| Demanda | Pedido de compra de um livro que a rede não tem |

### 5.2 Mensagens frequentes

| Mensagem | O que significa | O que fazer |
| --- | --- | --- |
| E-mail ou senha inválidos | Dados de login errados | Confira e tente de novo |
| Sessão inválida ou expirada. Faça login novamente. | A sessão de 8 horas acabou | Entre de novo |
| Acesso negado para o seu perfil. | A tela ou ação é de outro perfil | Use a conta certa |
| A ... tem N exemplar(es) disponível(is) ... faça o empréstimo presencialmente. | Há livro na estante; não existe fila | Vá à biblioteca e peça ao bibliotecário |
| A ... não possui exemplares deste título ... | A biblioteca não tem o livro | Escolha outra biblioteca |
| Você já possui uma reserva ativa para este título. | Você já está na fila desse livro | Acompanhe em Minhas Reservas |
| Você já está com um exemplar de "..." emprestado ... | O livro já está com você | Devolva antes de reservar de novo |
| A ... tem só 1 exemplar deste título ... | A biblioteca da fila não pode ceder o livro | Retire na própria biblioteca da fila |
| ... mais uma a deixaria sem o livro. | Outras transferências do livro já vão sair de lá | Retire na própria biblioteca da fila |
| A ... ainda não tem bibliotecário ativo para receber o livro. | Biblioteca de destino sem equipe | Escolha outra biblioteca |
| Usuário bloqueado para novos empréstimos até ... | Bloqueio por atraso | Aguarde a data |
| Limite de 3 empréstimos simultâneos atingido. | Já tem 3 livros | Devolva um antes |
| Este exemplar está reservado para ... | O exemplar está separado para outra pessoa | Escolha outro exemplar |
| Você já registrou interesse neste livro. | Pedido repetido | Nada: seu pedido já conta |
| Nenhuma transferência foi criada: ... | Algum item do lote não pode sair | Ajuste a seleção conforme a lista de motivos |

### 5.3 Perguntas frequentes

**Posso reservar um livro que está na estante?** Não. Se há exemplar disponível, vá até a biblioteca e peça o empréstimo.

**Quanto tempo tenho para buscar uma reserva?** 3 dias depois de ela ficar pronta. A data aparece em **Minhas Reservas** e no aviso do sino.

**Por que não consigo escolher outra biblioteca para retirar?** Ou a biblioteca de destino já tem o livro, ou não tem bibliotecário ativo, ou a biblioteca da fila não pode ceder o exemplar. O motivo aparece ao lado de cada opção.

**O que acontece se eu cancelar uma reserva com transferência?** O pedido é cancelado. Se o livro já estava a caminho, ele chega à outra biblioteca e fica disponível para qualquer pessoa.

**Posso devolver o livro em outra biblioteca?** Não. A devolução é registrada pelo bibliotecário da biblioteca do empréstimo.

**Como mudo meu perfil para Bibliotecário?** Contas de Bibliotecário são criadas pelo Administrador.

**Os dados da demonstração somem?** Sim. Na instalação de demonstração, os dados voltam ao estado inicial sempre que o servidor é reiniciado.

---

## 6. Diagramas recomendados

Dois diagramas ajudam quem usa o sistema. Os detalhes de cada um estão no [Manual do Desenvolvedor, seção 11](MANUAL_DO_DESENVOLVEDOR.md#11-diagramas-recomendados).

| Diagrama | Onde entra neste manual | Prioridade |
| --- | --- | --- |
| Casos de uso por perfil | Início da seção 2 | Essencial |
| Ciclo da reserva com transferência (versão simplificada) | Seção 4.1 | Desejável |
