# Manual do Usuário — Circula Book (versão reduzida)

O Circula Book conecta bibliotecas comunitárias: o usuário encontra um livro em qualquer
biblioteca da rede e, se todos os exemplares estiverem emprestados, entra na fila de espera
daquela biblioteca. O bibliotecário registra empréstimos e devoluções no balcão e dá entrada
nos exemplares da sua biblioteca. O administrador cuida dos cadastros da rede.

## Sumário

1. [Acesso](#1-acesso)
2. [Funcionalidades por perfil](#2-funcionalidades-por-perfil)
3. [Regras principais](#3-regras-principais)
4. [Passo a passo com o seed](#4-passo-a-passo-com-o-seed)
5. [Mensagens de erro frequentes](#5-mensagens-de-erro-frequentes)

---

## 1. Acesso

- **Entrar:** abra `http://localhost:5173`, informe e-mail e senha e clique em **Entrar**.
  O Usuário comum vai para **Buscar livros**; o Bibliotecário, para **Empréstimo**; o
  Administrador, para **Livros**.
- **Criar conta:** na tela de login, clique em **Cadastre-se**. Informe nome, e-mail, senha
  (mínimo de 6 caracteres) e a confirmação da senha e clique em **Cadastrar**. A conta criada é
  sempre de **Usuário comum** e você já entra no sistema. Bibliotecários são cadastrados pelo
  Administrador.
- **Sair:** botão **Sair** no topo da tela.
- A sessão dura 8 horas. Depois disso o sistema pede login de novo.
- Um bibliotecário desativado pelo Administrador não consegue entrar, e a sessão aberta dele
  deixa de valer na hora.

### Contas de demonstração

Senha de todas: `senha123`.

| Perfil | Nome | E-mail | Situação inicial |
| --- | --- | --- | --- |
| Administrador | Admin | `admin@circulabook.com` | Administração da rede |
| Bibliotecário | Bibliotecário Central | `bibliotecariocentral@circulabook.com` | Biblioteca Central |
| Bibliotecário | Bibliotecário Vila Isabel | `bibliotecariovilaisabel@circulabook.com` | Biblioteca Comunitária de Vila Isabel |
| Usuário | Usuário 1 | `usuario1@circulabook.com` | Nenhum empréstimo nem reserva |
| Usuário | Usuário 2 | `usuario2@circulabook.com` | 3 empréstimos na Central (Duna atrasado há 6 dias, O Hobbit, Grande Sertão: Veredas) |
| Usuário | Usuário 3 | `usuario3@circulabook.com` | Duna emprestado; Harry Potter pronto para retirada na Vila Isabel; na fila de O Hobbit na Central |

O sistema é reiniciado com estes dados sempre que o servidor sobe.

---

## 2. Funcionalidades por perfil

### 2.1 Usuário comum (menu: **Buscar livros** e **Minhas reservas**)

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
  biblioteca e a sua posição na fila, com o link **Ver minhas reservas**. A retirada é sempre
  nessa mesma biblioteca.
- **Minhas reservas:** lista as suas reservas em andamento. Cada uma mostra o livro, a
  biblioteca e a situação:
  - **NA FILA**, com a sua posição atual (ela sobe quando alguém à frente é atendido ou cancela);
  - **PRONTA PARA RETIRADA**, com a data limite ("Retire na … até dd/mm/aaaa").

  Para desistir, clique em **Cancelar reserva** e confirme (ou **Manter reserva** para voltar).
  Ao cancelar uma reserva pronta, o exemplar separado passa para o próximo da fila ou volta a
  ficar disponível. Reservas já retiradas, expiradas ou canceladas não aparecem na lista.

### 2.2 Bibliotecário (menu: **Empréstimo**, **Devolução** e **Cadastrar Exemplares**)

- **Empréstimo:** (1) digite ao menos 2 letras do nome ou e-mail do usuário e selecione-o;
  a coluna da direita mostra se ele está apto ou bloqueado; (2) escolha o exemplar na lista
  do acervo da sua biblioteca (disponíveis e reservados); (3) confira o prazo (14 dias) e
  clique em **Confirmar Empréstimo**.
- **Devolução:** escolha um dos empréstimos em aberto da sua biblioteca (atrasados aparecem
  com selo vermelho), confira os dias de atraso e o bloqueio calculado e clique em
  **Confirmar Devolução**. A mensagem final informa se o exemplar voltou ao acervo ou se ficou
  reservado para o 1º da fila.
- **Cadastrar Exemplares:** (1) busque o livro no catálogo e clique em **Selecionar** (só
  aparecem livros já cadastrados pelo Administrador); (2) escolha a conservação (**Novo**,
  **Bom** ou **Usado**) e a quantidade (pelo menos 1, sem limite máximo); (3) clique em
  **Cadastrar Exemplares** e confirme. Os exemplares entram sempre na sua biblioteca e a
  mensagem lista os números criados. Se houver fila do título na sua biblioteca, os primeiros
  exemplares já ficam reservados para quem está na fila.

### 2.3 Administrador (menu: **Livros**, **Categorias**, **Bibliotecas** e **Bibliotecários**)

- **Livros:** cadastra livros (título e autor obrigatórios; ISBN, editora, ano, categoria e
  sinopse opcionais) e edita os existentes pelo botão **Editar** da lista. O ISBN não pode se
  repetir. Um livro novo aparece na busca como "Indisponível: sem exemplares" até algum
  bibliotecário cadastrar exemplares dele.
- **Categorias:** cadastra e edita categorias (nome obrigatório e único, descrição opcional).
- **Bibliotecas:** cadastra e edita bibliotecas (nome obrigatório; endereço, e-mail e telefone
  opcionais). **Desativar** só marca a biblioteca como inativa: o acervo e os registros não
  mudam, e ela deixa de aparecer na escolha de biblioteca do cadastro de bibliotecário.
  **Reativar** desfaz.
- **Bibliotecários:** cadastra bibliotecários com nome, e-mail, senha inicial (mínimo de 6
  caracteres) e biblioteca, que precisa estar ativa. **Desativar** impede o login dele;
  **Reativar** devolve o acesso. O Administrador não cadastra usuários comuns: eles se cadastram
  na tela de login.

Cada perfil só abre as próprias telas: se tentar abrir uma tela de outro perfil, o sistema
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
- **Cancelamento:** você pode cancelar a sua reserva a qualquer momento enquanto ela estiver na
  fila ou pronta para retirada; quem estava atrás na fila sobe uma posição.
- **Exemplar reservado** só pode ser emprestado para quem o reservou; o empréstimo encerra a
  reserva.
- O bibliotecário só registra empréstimos, devoluções e novos exemplares da **própria
  biblioteca**.
- **Cadastros da rede:** só o Administrador cadastra livros, categorias, bibliotecas e
  bibliotecários.

---

## 4. Passo a passo com o seed

### 4.1 Usuário 1 reserva Duna

1. Entre como `usuario1@circulabook.com`.
2. Busque **Duna** e clique em **Ver detalhes**: a Biblioteca Central tem 0 de 2 disponíveis
   (os dois exemplares estão com o Usuário 2 e o Usuário 3).
3. Clique em **Entrar na fila**. A tela de reserva já vem com a Biblioteca Central e mostra
   "1º lugar".
4. Clique em **Entrar na fila** e confirme. A tela mostra: Duna, Biblioteca Central, 1º lugar.
5. Clique em **Ver minhas reservas** (ou no menu **Minhas reservas**): Duna aparece como
   **NA FILA**, 1º na fila, na Biblioteca Central.

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
- **Acompanhar e cancelar reservas:** entre como `usuario3@circulabook.com` e abra **Minhas
  reservas**: Harry Potter aparece **PRONTA PARA RETIRADA** na Vila Isabel e O Hobbit **NA FILA**
  (1º) na Central. Cancele o Harry Potter: o exemplar volta a ficar disponível na Vila Isabel.
- **Devolução sem fila:** devolva **Grande Sertão: Veredas — Usuário 2**: o exemplar volta a ficar
  disponível.
- **Novo livro até a estante:** entre como `admin@circulabook.com`, cadastre um livro em **Livros**;
  depois entre como `bibliotecariovilaisabel@circulabook.com` e, em **Cadastrar Exemplares**,
  selecione esse livro e cadastre 2 exemplares. Na busca do usuário comum o livro passa a aparecer
  disponível na Vila Isabel.
- **Cadastro de exemplar que atende a fila:** entre como `bibliotecariocentral@circulabook.com` e
  cadastre 1 exemplar de **O Hobbit**: ele já fica reservado para o Usuário 3, que estava na fila.
- **Criar conta:** na tela de login, clique em **Cadastre-se** e crie um usuário novo.

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
| Esta reserva não pode mais ser cancelada. | A reserva já foi retirada, expirou ou foi cancelada. |
| Só é possível registrar empréstimos/devoluções … da sua biblioteca. | O exemplar ou empréstimo é de outra biblioteca. |
| Já existe uma conta com este e-mail. | O e-mail informado no cadastro já está em uso. |
| A confirmação não confere com a senha. | A senha e a confirmação digitadas no cadastro são diferentes. |
| Usuário inativo. Procure a administração da rede. | O bibliotecário foi desativado pelo Administrador. |
| Já existe um livro com o ISBN … | O ISBN informado já pertence a outro livro. |
| Já existe a categoria "…". | Já há uma categoria com esse nome. |
| A … está inativa. | Não é possível cadastrar bibliotecário em biblioteca desativada. |
| A quantidade deve ser de pelo menos 1 exemplar. | A quantidade de exemplares precisa ser 1 ou mais. |
