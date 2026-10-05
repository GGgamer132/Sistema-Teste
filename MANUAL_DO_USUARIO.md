# Manual do Usuário — Circula Book (versão reduzida)

O Circula Book conecta bibliotecas comunitárias: o usuário encontra um livro em qualquer
biblioteca da rede e, se todos os exemplares estiverem emprestados, entra na fila de espera
daquela biblioteca. O bibliotecário registra empréstimos e devoluções no balcão.

## Sumário

1. [Acesso](#1-acesso)
2. [Funcionalidades por perfil](#2-funcionalidades-por-perfil)
3. [Regras principais](#3-regras-principais)
4. [Passo a passo com o seed](#4-passo-a-passo-com-o-seed)
5. [Mensagens de erro frequentes](#5-mensagens-de-erro-frequentes)

---

## 1. Acesso

- **Entrar:** abra `http://localhost:5173`, informe e-mail e senha e clique em **Entrar**.
  O Usuário comum vai para **Buscar livros**; o Bibliotecário, para **Empréstimo**.
- **Sair:** botão **Sair** no topo da tela.
- A sessão dura 8 horas. Depois disso o sistema pede login de novo.
- Não é possível criar conta pela tela: use as contas de demonstração abaixo.

### Contas de demonstração

Senha de todas: `senha123`.

| Perfil | Nome | E-mail | Situação inicial |
| --- | --- | --- | --- |
| Bibliotecário | Bibliotecário Central | `bibliotecariocentral@circulabook.com` | Biblioteca Central |
| Bibliotecário | Bibliotecário Vila Isabel | `bibliotecariovilaisabel@circulabook.com` | Biblioteca Comunitária de Vila Isabel |
| Usuário | Usuário 1 | `usuario1@circulabook.com` | Nenhum empréstimo nem reserva |
| Usuário | Usuário 2 | `usuario2@circulabook.com` | 3 empréstimos na Central (Duna atrasado há 6 dias, O Hobbit, Grande Sertão: Veredas) |
| Usuário | Usuário 3 | `usuario3@circulabook.com` | Duna emprestado; Harry Potter pronto para retirada na Vila Isabel; na fila de O Hobbit na Central |

O sistema é reiniciado com estes dados sempre que o servidor sobe.

---

## 2. Funcionalidades por perfil

### 2.1 Usuário comum (menu: **Buscar livros**)

- **Buscar livros:** digite título, autor ou ISBN, ou use os filtros (categoria, autor, ano, ISBN)
  e os atalhos de categoria. O resultado mostra cada título com a situação na rede
  (disponível, aguardando ou sem exemplares).
- **Ver detalhes:** ficha do livro (editora, ano, ISBN, categoria, sinopse) e a tabela
  **Disponibilidade na rede**, com quantos exemplares estão livres em cada biblioteca.
  - Biblioteca com exemplar livre: botão **Como retirar**, que explica que o empréstimo é
    presencial, com o bibliotecário (não é preciso reservar).
  - Biblioteca sem exemplar livre: botão **Entrar na fila**, que leva à reserva.
- **Reservar:** escolha a biblioteca (só aparecem as que têm o título e estão com todos os
  exemplares emprestados), confira a posição na fila e clique em **Entrar na fila** e depois
  em **Entrar na fila** na janela de confirmação. A tela mostra a confirmação com o livro, a
  biblioteca e a sua posição na fila. A retirada é sempre nessa mesma biblioteca.

### 2.2 Bibliotecário (menu: **Empréstimo** e **Devolução**)

- **Empréstimo:** (1) digite ao menos 2 letras do nome ou e-mail do usuário e selecione-o;
  a coluna da direita mostra se ele está apto ou bloqueado; (2) escolha o exemplar na lista
  do acervo da sua biblioteca (disponíveis e reservados); (3) confira o prazo (14 dias) e
  clique em **Confirmar Empréstimo**.
- **Devolução:** escolha um dos empréstimos em aberto da sua biblioteca (atrasados aparecem
  com selo vermelho), confira os dias de atraso e o bloqueio calculado e clique em
  **Confirmar Devolução**. A mensagem final informa se o exemplar voltou ao acervo ou se ficou
  reservado para o 1º da fila.

Cada perfil só abre as próprias telas: se tentar abrir uma tela do outro perfil, o sistema
volta para a sua tela inicial.

---

## 3. Regras principais

- **Limite:** cada usuário pode ter no máximo **3 empréstimos** ao mesmo tempo, e nunca dois
  exemplares do mesmo título.
- **Prazo:** todo empréstimo vale **14 dias corridos**.
- **Bloqueio por atraso:** cada dia de atraso na devolução gera **2 dias** sem poder pegar
  livros emprestados (ex.: 6 dias de atraso = 12 dias de bloqueio).
- **Só reserva quando não há exemplar disponível:** a reserva vale para uma biblioteca que tem
  o título e onde todos os exemplares estão emprestados. Havendo exemplar livre, o empréstimo é
  feito direto no balcão.
- **Fila por biblioteca:** cada biblioteca tem a sua fila de cada título, atendida por ordem de
  chegada. Não é possível reservar duas vezes o mesmo título nem reservar um título que você
  já está com emprestado.
- **Reserva pronta:** quando um exemplar é devolvido e há fila, ele fica separado para o 1º da
  fila, que tem **3 dias** para retirá-lo na mesma biblioteca. Se não retirar, a reserva expira
  automaticamente e o exemplar passa para o próximo da fila (ou volta a ficar disponível).
- **Exemplar reservado** só pode ser emprestado para quem o reservou; o empréstimo encerra a
  reserva.
- O bibliotecário só registra empréstimos e devoluções da **própria biblioteca**.

---

## 4. Passo a passo com o seed

### 4.1 Usuário 1 reserva Duna

1. Entre como `usuario1@circulabook.com`.
2. Busque **Duna** e clique em **Ver detalhes**: a Biblioteca Central tem 0 de 2 disponíveis
   (os dois exemplares estão com o Usuário 2 e o Usuário 3).
3. Clique em **Entrar na fila**. A tela de reserva já vem com a Biblioteca Central e mostra
   "1º lugar".
4. Clique em **Entrar na fila** e confirme. A tela mostra: Duna, Biblioteca Central, 1º lugar.

### 4.2 Bibliotecário devolve Duna com atraso

1. Entre como `bibliotecariocentral@circulabook.com` e abra **Devolução**.
2. Escolha **Duna — Usuário 2** (selo "Atrasado"). A tela mostra 6 dias de atraso e o bloqueio
   até daqui a 12 dias.
3. Clique em **Confirmar Devolução** e confirme. A mensagem informa o bloqueio do Usuário 2 e
   que o exemplar ficou reservado para o 1º da fila (o Usuário 1).

### 4.3 Bibliotecário empresta ao 1º da fila

1. Ainda como Bibliotecário Central, abra **Empréstimo**.
2. Busque **Usuário 1** e selecione-o (aparece "Nenhuma pendência encontrada").
3. Filtre o acervo por **Duna**: o exemplar aparece como **Reservado**. Clique em **Selecionar**.
4. Clique em **Confirmar Empréstimo** e confirme. A reserva do Usuário 1 é encerrada e o
   empréstimo vale por 14 dias.
5. Para ver os bloqueios: selecione o **Usuário 2** — o empréstimo aparece bloqueado (limite ou
   atraso).

### 4.4 Outros exemplos prontos

- **Retirada de reserva pronta:** entre como `bibliotecariovilaisabel@circulabook.com`, abra
  **Empréstimo**, selecione o **Usuário 3** e o exemplar reservado de **Harry Potter e a Pedra
  Filosofal**.
- **Devolução que atende a fila:** como Bibliotecário Central, devolva **O Hobbit — Usuário 2**:
  o exemplar fica reservado para o Usuário 3, que estava na fila.
- **Devolução sem fila:** devolva **Grande Sertão: Veredas — Usuário 2**: o exemplar volta a ficar
  disponível.

> Sugestão de diagrama: um **diagrama de sequência** do ciclo "reserva → devolução com fila →
> empréstimo ao 1º da fila" (Usuário, Bibliotecário, Sistema) ajuda a entender os fluxos 4.1 a 4.3.

---

## 5. Mensagens de erro frequentes

| Mensagem | O que significa |
| --- | --- |
| E-mail ou senha inválidos | E-mail ou senha errados. |
| Sessão inválida ou expirada. Faça login novamente. | A sessão venceu (8 h) ou o servidor foi reiniciado. Entre de novo. |
| Acesso negado para o seu perfil. | A ação não pertence ao seu perfil. |
| A … tem N exemplar(es) disponível(is). A reserva só vale quando todos estão emprestados… | Há exemplar livre: faça o empréstimo no balcão. |
| A … não possui exemplares deste título, então não há fila para entrar. | Aquela biblioteca não tem o livro. |
| Você já possui uma reserva ativa para este título. | Você já está na fila (ou tem a reserva pronta) desse livro. |
| Você já está com um exemplar de "…" emprestado e não pode reservar o mesmo título. | Devolva o livro antes de reservá-lo de novo. |
| Limite de 3 empréstimos simultâneos atingido. | O usuário precisa devolver algum livro. |
| Usuário bloqueado para novos empréstimos até dd/mm/aaaa. | Bloqueio por devolução em atraso. |
| O usuário já possui um exemplar de "…" emprestado. | Não é permitido ter dois exemplares do mesmo título. |
| Este exemplar está reservado para … | O exemplar está separado para outra pessoa da fila. |
| Só é possível registrar empréstimos/devoluções … da sua biblioteca. | O exemplar ou empréstimo é de outra biblioteca. |
