# Circula Book — Especificação funcional por perfil (v2)

> **Para o Claude Code:** este documento é a **fonte da verdade** do que o sistema deve fazer. Sua tarefa, nesta ordem:
>
> 1. Ler este arquivo e **todo o código** (`backend` e `frontend`) e me ajudar a finalizar de implementar o que está faltando do meu sistema de biblioteca.

> **Regras de trabalho:** as regras de negócio das seções 3 a 6 são decisão do dono do projeto — **não as altere**. Se algo for ambíguo, **pergunte** antes de decidir. Commits pequenos e descritivos. Código, mensagens e UI em **português do Brasil**.

---

## 1. O sistema

**Circula Book**: gestão de uma **rede de bibliotecas comunitárias**. Cada biblioteca tem acervo pequeno e independente. O sistema integra a rede: o usuário **encontra** um livro em qualquer unidade, **entra na fila** quando todos os exemplares da unidade estão emprestados e **escolhe onde retirar** (inclusive em outra biblioteca, via **transferência**). Projeto de Projeto e Construção de Sistemas (CEFET/RJ 2026.2). Repositório: `https://github.com/marceloareas/CirculaBook`.

- **Arquitetura:** Blackboard. O quadro é o banco (a tabela `exemplar` é o estado compartilhado); os **especialistas** são serviços que leem/escrevem esse estado. O especialista de fila (`FilaEsperaService`) é acionado **de forma síncrona, na mesma transação**, quando um exemplar fica livre. **Não existe trigger/`LISTEN`/`NOTIFY`** no banco; qualquer resquício é bug.
- **Stack:** Java 17 + **Spring Boot 4.x** (conferir `pom.xml`; as dependências novas devem ser compatíveis com a versão do parent) + JPA + Lombok + PostgreSQL | React + TypeScript + Vite + Tailwind + `react-router-dom`.
- **Como rodar:**
  - Banco: `circula_book_db`, usuário `circula_user`, senha `circula_senha_123`.
  - Backend: `cd backend/circula-book && mvn spring-boot:run` → `http://localhost:8080` (`ddl-auto=create` recria o schema a cada start; `data.sql` popula).
  - Frontend: `cd frontend && npm install && npm run dev` → `http://localhost:5173` (proxy `/api` → 8080).
- **Fim do seletor de perfil.** Toda a navegação passa a exigir **login** (seção 2).

---

## 2. Perfis e autenticação (login com JWT — **urgente**)

### 2.1 Perfis

| Perfil                | Código          | Resumo                                                                                                                                             |
| --------------------- | --------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| Usuário da comunidade | `COMUM`         | Busca, reserva (fila), acompanha empréstimos/reservas/histórico, registra interesse em livros. **Não** registra empréstimo nem devolução           |
| Bibliotecário         | `BIBLIOTECARIO` | Opera **somente a própria biblioteca**: empréstimos, devoluções, **cadastro de exemplares** (de livros já cadastrados), chegada de transferências  |
| Administrador         | `ADMIN`         | Cadastra **livros, categorias, bibliotecas e bibliotecários**; decide transferências; cria transferências avulsas; demandas; relatórios; histórico |

### 2.2 Requisitos do login

- **Backend:** Spring Security **stateless** + JWT.
  - `POST /api/auth/login` `{email, senha}` → `200 {token, expiraEm, usuario:{id, nome, email, perfil, bibliotecaId, bibliotecaNome}}`. Credencial inválida → `401` ("E-mail ou senha inválidos", sem revelar qual); usuário inativo → `403`.
  - `POST /api/auth/cadastro` `{nome, email, senha}` — **público**, cria **somente `COMUM`** (ignorar qualquer `tipo` enviado), e-mail único, senha ≥ 6 caracteres; retorna o mesmo payload do login (já autentica).
  - `GET /api/auth/me` → usuário do token.
  - Senhas com **BCrypt**; o hash **nunca** é serializado no JSON (`@JsonIgnore` em `senhaHash` — hoje `/api/usuarios` o expõe, corrigir).
  - JWT HS256 com claims `sub` (id), `perfil`, `bibliotecaId`; expiração 8 h; segredo em `JWT_SECRET` (com valor padrão só para desenvolvimento em `application.properties`).
  - CORS deve permitir o header `Authorization` para `http://localhost:5173`.
  - **O ator vem do token**, nunca de parâmetros: remover/ignorar `adminId`, `responsavelId`, `bibliotecarioId` e `usuarioId` (quando for o próprio ator) dos controllers. O `usuarioId` do corpo de `POST /emprestimos/registrar` continua existindo (é o **tomador**, não o ator).
- **Frontend:**
  - Telas **`/login`** (e-mail, senha, link "Cadastre-se") e **`/cadastro`** (nome, e-mail, senha, confirmação). Mensagens de erro claras.
  - `AuthContext` com usuário/token (token em `localStorage`), `api/client.ts` anexa `Authorization: Bearer`, **401 → limpa sessão e vai para `/login`**.
  - **Rotas protegidas por perfil** (`RotaProtegida`): sem sessão → `/login`; perfil errado → redireciona à tela inicial do próprio perfil (ou 403 amigável).
  - Cabeçalho: nome + perfil + **Sair** + **sino de notificações** (seção 6). **Remover** o `<select>` de troca de perfil e todas as constantes fixas (`USUARIO_LOGADO`, `BIBLIOTECA_LOGADA`, `ADMIN_LOGADO`); usar o usuário da sessão.
  - Tela inicial pós-login: `COMUM` → `/`, `BIBLIOTECARIO` → `/biblioteca`, `ADMIN` → `/admin`.

### 2.3 Matriz de autorização (aplicada **no servidor**)

| Recurso                                                                    | COMUM      | BIBLIOTECARIO               | ADMIN                         |
| -------------------------------------------------------------------------- | ---------- | --------------------------- | ----------------------------- |
| `auth/login`, `auth/cadastro`                                              | público    | público                     | público                       |
| Buscar livros, detalhes, bibliotecas, categorias (GET)                     | ✅         | ✅                          | ✅                            |
| Criar/cancelar reserva; ver **as próprias** reservas/empréstimos/histórico | ✅         | —                           | —                             |
| Registrar **empréstimo** e **devolução**                                   | ❌         | ✅ só da própria biblioteca | ❌                            |
| Listar empréstimos                                                         | só os seus | só da própria biblioteca    | leitura (todos)               |
| Buscar usuários para o balcão                                              | ❌         | ✅ (somente `COMUM`)        | —                             |
| **Cadastrar exemplar**, marcar `INDISPONIVEL`/reativar                     | ❌         | ✅ só da própria biblioteca | ❌                            |
| **Cadastrar/editar livro e categoria**                                     | ❌         | ❌                          | ✅                            |
| Cadastrar/editar **biblioteca**                                            | ❌         | ❌                          | ✅                            |
| Cadastrar **usuário bibliotecário** (com biblioteca)                       | ❌         | ❌                          | ✅ (**não** cadastra `COMUM`) |
| Aprovar/rejeitar transferência; criar **avulsa**                           | ❌         | ❌                          | ✅                            |
| **Confirmar chegada** de transferência                                     | ❌         | ✅ só do **destino**        | ❌                            |
| Registrar interesse (demanda)                                              | ✅         | ❌                          | ❌                            |
| Listar/alterar demandas; **relatórios**; **histórico de circulação**       | ❌         | ❌                          | ✅                            |
| Notificações                                                               | só as suas | só as suas                  | só as suas                    |

---

## 3. Regras de negócio

Base: RN01–RN14 do enunciado do professor, com as mudanças abaixo. **Quando houver conflito com o PDF, vale este documento** (e o PDF deve ser atualizado).

| RN       | Regra                                                                                                                                                                                                         | Situação                                                                         |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| RN01     | Máx. **3** exemplares emprestados simultaneamente, de **títulos distintos**. Usuário bloqueado/atrasado não empresta. _Esclarecimento:_ quem está com exemplar do título **não pode reservar o mesmo título** | Vale                                                                             |
| RN02     | Prazo de empréstimo **fixo: 14 dias corridos**. O bibliotecário **não** altera o prazo (remover seletor de 7/21/30 e o campo `prazoDias`)                                                                     | **Alterada**                                                                     |
| RN03     | Reserva = fila de espera, **por biblioteca** (seção 5). Expira em **3 dias** após o exemplar ficar pronto para retirada                                                                                       | Refinada                                                                         |
| RN04     | Transferência exige **aprovação do Admin** antes do envio; só exemplar `DISPONIVEL` (ou `RESERVADO` já vinculado a pedido aprovado) sai                                                                       | Vale                                                                             |
| RN05     | _(substituída)_ Estados e transições do exemplar: **seção 4**                                                                                                                                                 | **Substituída**                                                                  |
| RN06     | Todo evento do exemplar vai ao histórico com data e responsável                                                                                                                                               | Vale                                                                             |
| RN07     | Demanda de aquisição: mesmo usuário não repete interesse no mesmo título; outro usuário no mesmo título/autor **incrementa o contador**                                                                       | Vale                                                                             |
| RN08     | Bibliotecário só gerencia **a própria biblioteca**; usuário comum só vê o que é seu. **Aplicada no servidor via JWT**                                                                                         | Vale (reforçada)                                                                 |
| RN09     | Busca percorre **toda a rede**, mostrando unidades e exemplares disponíveis                                                                                                                                   | Vale                                                                             |
| RN10     | Só o **bibliotecário da biblioteca** cadastra exemplar nela, e **apenas de livros previamente cadastrados pelo Admin**                                                                                        | **Alterada**                                                                     |
| ~~RN11~~ | ~~Exemplar único da rede fica indisponível~~                                                                                                                                                                  | **REMOVIDA** — todo exemplar pode ser emprestado. Remover do código, UI e testes |
| RN12     | Cada dia de atraso = **2 dias** de bloqueio para novos empréstimos                                                                                                                                            | Vale                                                                             |
| ~~RN13~~ | ~~Usuário solicita transferência direta~~                                                                                                                                                                     | **SUBSTITUÍDA** — a transferência nasce da reserva (seção 5)                     |
| RN14     | Transferência **avulsa** do Admin nasce `APROVADA` (e já segue `EM_TRANSITO`)                                                                                                                                 | Vale                                                                             |
| **RN15** | **Transferência só é permitida se a biblioteca de origem ficar com ao menos 1 exemplar do título** (nenhuma biblioteca pode ficar sem o livro). Detalhe na seção 5.3                                          | **Nova**                                                                         |
| **RN16** | **Só o bibliotecário do destino** confirma a chegada e dá entrada do exemplar transferido. **O Admin não confirma chegada**                                                                                   | **Nova**                                                                         |
| **RN17** | **Só o bibliotecário** registra empréstimo e devolução (somente da própria biblioteca). Usuário comum e Admin não                                                                                             | **Nova**                                                                         |
| **RN18** | **Admin** cadastra livros, categorias, bibliotecas e **bibliotecários** (não cadastra usuário comum). **Bibliotecário** cadastra **exemplares**. **Usuário comum** se autocadastra na tela de login           | **Nova**                                                                         |
| **RN19** | Autenticação por **JWT**, com perfis `COMUM`, `BIBLIOTECARIO`, `ADMIN` (seção 2)                                                                                                                              | **Nova**                                                                         |
| **RN20** | **Notificações** in-app para eventos importantes, com sino e contador vermelho (seção 6)                                                                                                                      | **Nova**                                                                         |
| **RN21** | Na devolução, a condição do exemplar é **`BOM` ou `DANIFICADO`**. Não existe `PERDIDO`. `DANIFICADO` → exemplar `INDISPONIVEL`                                                                                | **Nova**                                                                         |
| **RN22** | **Destino de transferência válido:** só uma biblioteca **ativa** com **pelo menos um bibliotecário ativo** (quem confirma a chegada). Vale para o retirar-em-outra-biblioteca (5.1) e para a transferência avulsa (5.4) | **Nova** |

**Remoções globais:** **não deve existir nenhuma menção a "código de barras"** (campo `codigoBarras`, leitor, busca por código, endpoint `/exemplares/codigo/{c}`, coluna "Código", textos e placeholders). O exemplar é identificado por um **número sequencial** (`id`), exibido como **"Exemplar nº X"**. Também remover `PERDIDO` de conservação/devolução e jargão "RNxx" das telas.

---

## 4. Estados e transições

### 4.1 Estados do exemplar

`DISPONIVEL` · `EMPRESTADO` · `EMPRESTADO_RESERVADO` (emprestado **e há fila** para o título nesta biblioteca) · `RESERVADO` (separado para o 1º da fila, aguardando retirada) · `EM_TRANSFERENCIA` · `INDISPONIVEL`.
Conservação (campo à parte): `NOVO | BOM | USADO | DANIFICADO`.

### 4.2 Invariante da fila (regra que governa `EMPRESTADO_RESERVADO`)

Para cada par **(livro, biblioteca)**, seja `fila` = nº de reservas `PENDENTE` com `bibliotecaFila` = essa biblioteca:

- se `fila > 0` → **todo** exemplar emprestado do título naquela biblioteca é `EMPRESTADO_RESERVADO`;
- se `fila == 0` → ficam `EMPRESTADO`.

Implementar como rotina única `sincronizarMarcaDeFila(livro, biblioteca)`, chamada após: criar reserva, cancelar/expirar reserva, promover da fila, registrar empréstimo, registrar devolução, cadastrar/reativar exemplar. **Nunca** pode existir `EMPRESTADO_RESERVADO` sem fila, nem `EMPRESTADO` com fila.

### 4.3 Transições válidas (tudo fora disto deve ser recusado)

| #        | De → Para                                 | Gatilho                                                                                               |
| -------- | ----------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| T1       | `DISPONIVEL → EMPRESTADO`                 | Empréstimo                                                                                            |
| T2       | `EMPRESTADO → DISPONIVEL`                 | Devolução `BOM`, fila vazia                                                                           |
| T3       | `EMPRESTADO → EMPRESTADO_RESERVADO`       | Alguém entra na fila (vale para **todos** os emprestados do título na biblioteca)                     |
| T4       | `EMPRESTADO_RESERVADO → RESERVADO`        | Devolução `BOM`: o exemplar é separado para o 1º da fila                                              |
| T5       | `RESERVADO → EMPRESTADO_RESERVADO`        | Retirada pelo reservante e **ainda há** pessoas na fila                                               |
| T6       | `RESERVADO → EMPRESTADO`                  | Retirada pelo reservante e **não há** mais fila                                                       |
| T7       | `RESERVADO → DISPONIVEL`                  | Cancelamento/expiração da reserva e **fila vazia**                                                    |
| **T7b**  | `RESERVADO → RESERVADO`                   | Cancelamento/expiração e **há fila**: reatribui ao próximo _(nova)_                                   |
| T8       | `DISPONIVEL → EM_TRANSFERENCIA`           | Transferência **avulsa** do Admin                                                                     |
| T9       | `RESERVADO → EM_TRANSFERENCIA`            | Transferência **de reserva** aprovada (exemplar já vinculado)                                         |
| T10      | `EM_TRANSFERENCIA → RESERVADO`            | Chegada confirmada e há reserva esperando (a do pedido ou o 1º da fila do destino)                    |
| T11      | `EM_TRANSFERENCIA → DISPONIVEL`           | Chegada de avulsa, ou de reserva já cancelada, sem fila no destino                                    |
| T12      | `EMPRESTADO_RESERVADO → EMPRESTADO`       | A fila **esvaziou**: cancelamento da última reserva **ou** o último da fila foi atendido _(ampliada)_ |
| T13      | `qualquer → INDISPONIVEL`                 | Acidente/dano/devolução `DANIFICADO`/baixa pelo bibliotecário (efeitos em 4.4)                        |
| **T14**  | `INDISPONIVEL → DISPONIVEL`               | Bibliotecário **reativa** o exemplar _(nova)_                                                         |
| **T14b** | `INDISPONIVEL → RESERVADO`                | Reativação numa biblioteca com fila: atende o 1º _(nova)_                                             |
| **T15**  | `(criação) → DISPONIVEL` ou `→ RESERVADO` | Cadastro de exemplar (vai a `RESERVADO` se a biblioteca tem fila do título) _(nova)_                  |

Observações: não existe `DISPONIVEL → RESERVADO` direto como estado estável (a promoção é atômica; o exemplar nunca fica `DISPONIVEL` com fila). `EM_TRANSFERENCIA → INDISPONIVEL` ocorre pela opção **"chegou danificado"** na confirmação de chegada. O **despacho na devolução** (`EMPRESTADO_RESERVADO → EM_TRANSFERENCIA`, pedido `APROVADA`) é a composição de **T4 + T9 na mesma transação**: o exemplar não aparece como `RESERVADO` para quem consulta.

### 4.4 Efeitos colaterais de T13 (por estado de origem)

| Origem                                | Efeito                                                                                                                                                               |
| ------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DISPONIVEL`                          | Apenas muda de estado                                                                                                                                                |
| `EMPRESTADO` / `EMPRESTADO_RESERVADO` | Só via devolução `DANIFICADO`: empréstimo `DEVOLVIDO`, exemplar `INDISPONIVEL`, **não** promove a fila; reavaliar a invariante 4.2                                   |
| `RESERVADO`                           | A reserva volta a `PENDENTE` **mantendo a posição** (data original); pedido de transferência vinculado volta a "sem exemplar"; notificar o usuário                   |
| `EM_TRANSFERENCIA`                    | Na chegada danificada: transferência `CONCLUIDA`, exemplar `INDISPONIVEL` no destino; reserva volta a `PENDENTE` na fila de origem com retirada na origem; notificar |

### 4.5 Outros estados

- **Empréstimo:** `ATIVO | ATRASADO | DEVOLVIDO` (o atraso também é derivado da data prevista).
- **Reserva:** `PENDENTE` (na fila) · `AGUARDANDO_TRANSFERENCIA` (exemplar separado, ainda não retirável) · `DISPONIVEL` (pronta, **3 dias**) · `RETIRADA` · `CANCELADA` · `EXPIRADA`.
- **Transferência:** `PENDENTE` · `APROVADA` · `EM_TRANSITO` · `CONCLUIDA` · `REJEITADA` · `CANCELADA`. **`APROVADA` com `exemplar == null` = "aprovada, aguardando exemplar".**

---

## 5. Reserva e transferência

### 5.1 Reserva (fila por biblioteca)

1. Só entra na fila de uma biblioteca que **tem o título** e em que **não há exemplar `DISPONIVEL`**. Havendo ao menos 1 disponível, **não reserva**: o usuário vai presencialmente e o bibliotecário empresta.
2. A posição conta apenas as reservas `PENDENTE` do título **naquela biblioteca**.
3. Uma reserva guarda `bibliotecaFila` (origem, de onde o exemplar sai) e `bibliotecaDestino` (retirada). Iguais → sem transferência.
4. Para **retirar em outra biblioteca**: o destino **não pode ter nenhum exemplar do título**, a **RN15** precisa permitir a saída (5.3) e o destino precisa cumprir a **RN22** (ativo e com bibliotecário ativo).
5. O usuário não pode ter duas reservas ativas do mesmo título, nem reservar título que já tem emprestado.

### 5.2 Ciclo da transferência de reserva

| Passo | Quem                   | Ação                                                                        | Resultado                                                                                                                                                                                                                                                          |
| ----- | ---------------------- | --------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1     | Usuário                | Entra na fila de A com retirada em B                                        | Reserva `PENDENTE` + transferência `PENDENTE` **sem exemplar**; Admin notificado                                                                                                                                                                                   |
| 2     | Admin                  | **Aprova** o pedido                                                         | `APROVADA` ("aguardando exemplar")                                                                                                                                                                                                                                 |
| 2'    | Admin                  | **Rejeita**                                                                 | `REJEITADA`; a reserva **continua na fila com retirada em A**; usuário notificado                                                                                                                                                                                  |
| 3     | Bibliotecário de A     | Registra a **devolução** de um exemplar do título; o usuário é o 1º da fila | Exemplar `RESERVADO` vinculado ao pedido. Se `APROVADA` → vai **direto** a `EM_TRANSFERENCIA`/`EM_TRANSITO` (reserva `AGUARDANDO_TRANSFERENCIA`). Se ainda `PENDENTE` → fica retido aguardando o Admin (aprovar → `EM_TRANSITO`; rejeitar → retirada em A, 3 dias) |
| 4     | **Bibliotecário de B** | **Confirma a chegada** e dá entrada no acervo de B                          | Transferência `CONCLUIDA`; exemplar passa a pertencer a B, `RESERVADO`; reserva `DISPONIVEL` com **3 dias**; usuário notificado                                                                                                                                    |
| 5     | Bibliotecário de B     | Registra o **empréstimo** ao usuário                                        | Exemplar → `EMPRESTADO` (ou `EMPRESTADO_RESERVADO` se B tem fila); reserva `RETIRADA`. Para **outro** usuário: erro "reservado para <nome>"                                                                                                                        |

Sem transferência (retirada em A): no passo 3 a reserva vai direto a `DISPONIVEL` (3 dias em A).

**Casos de borda (decididos):**

- **Retirada vencida:** reserva `EXPIRADA`; o exemplar **fica onde está** (se já chegou em B, fica em B) e vai ao próximo da fila (T7b) ou a `DISPONIVEL` (T7). Rotina agendada a cada 60 s + `POST /api/reservas/expirar-vencidas` para demonstração.
- **Cancelar reserva:** transferência `PENDENTE`/`APROVADA` → `CANCELADA` (exemplar vinculado é reatribuído/liberado). Se já `EM_TRANSITO`, a viagem **continua** e o exemplar chega a B como `DISPONIVEL`.
- Exemplar novo/reativado/chegado numa biblioteca **com fila** atende o 1º da fila.
- **Por que o destino não pode ter o título:** se tivesse exemplar livre, o usuário pegaria lá; se tivesse todos emprestados, entraria na fila de lá. Só destino sem o título cria acesso novo.

### 5.3 RN15 — a origem nunca fica sem o livro

Definições (por par origem × título): `total` = exemplares do título pertencentes à origem (**qualquer status**, inclusive `INDISPONIVEL`); `abertas` = transferências com essa origem e título em `PENDENTE`, `APROVADA` ou `EM_TRANSITO`.
**Invariante: `abertas ≤ total − 1`.** Uma nova transferência só é aceita se `abertas + 1 ≤ total − 1`.
Validar em **todos** estes pontos:

1. **Criar reserva com retirada em outra biblioteca** (a UI desabilita a opção, com explicação, quando a origem tem só 1 exemplar ou a capacidade já foi usada; o back valida de novo).
2. **Aprovação** pelo Admin.
3. **Despacho** (quando o exemplar volta e o pedido `APROVADA` vai sair): se a regra deixou de valer, o pedido vira `CANCELADA` com observação, a reserva segue com **retirada na origem** e usuário/Admin são notificados.
4. **Transferência avulsa** (inclusive em lote — 5.4).

### 5.4 Transferência avulsa (Admin), inclusive em lote

- Seleciona **um ou mais** exemplares `DISPONIVEL` (tabela paginada com busca, toda a rede) e **uma** biblioteca de destino (**qualquer** que cumpra a **RN22**, ≠ origem de cada exemplar; sem a restrição de "destino sem o título").
- **Lote:** para cada grupo (origem, título), `abertas + selecionados ≤ total − 1`. É **tudo ou nada**: se algum grupo violar, **nada é criado** e a mensagem lista o que violou.
- Cada exemplar gera uma transferência `APROVADA` que vai a `EM_TRANSITO` imediatamente (T8). Sem reserva. Endpoint sugerido: `POST /api/transferencias/avulsa` com `{exemplarIds: [], bibliotecaDestinoId, observacoes}`.

---

## 6. Notificações (RN20)

- **Sino** no cabeçalho de **todos** os perfis; **bolinha vermelha com a contagem** de não lidas; clique abre painel/lista (mais recentes primeiro) com **marcar como lida** e **marcar todas**; clicar leva à tela relacionada.
- **Backend:** entidade `Notificacao` (usuário, título, mensagem, tipo, `lida`, `criadaEm`, link opcional). Endpoints sugeridos: `GET /api/notificacoes`, `GET /api/notificacoes/nao-lidas/contagem`, `PATCH /api/notificacoes/{id}/lida`, `PATCH /api/notificacoes/marcar-todas-lidas`. Atualização por **polling** (ex.: a cada 30 s); WebSocket é desnecessário.
- **Eventos mínimos:**

| Para          | Evento                                                                                                   |
| ------------- | -------------------------------------------------------------------------------------------------------- |
| Usuário       | Pedido de transferência **aprovado** / **rejeitado** (e que a retirada voltou para a biblioteca da fila) |
| Usuário       | Exemplar separado e **a caminho** da biblioteca de retirada                                              |
| Usuário       | Reserva **pronta para retirada** em <biblioteca> **até dd/mm**                                           |
| Usuário       | Reserva **expirou**; reserva voltou à fila por exemplar indisponível                                     |
| Usuário       | Empréstimo vence em 2 dias / **está atrasado** (rotina agendada)                                         |
| Usuário       | **Bloqueio** aplicado por atraso, com data final                                                         |
| Bibliotecário | Transferência **a caminho** da sua biblioteca (confirmar chegada)                                        |
| Bibliotecário | Exemplar da sua biblioteca **separado para envio** (transferência aprovada saindo)                       |
| Bibliotecário | Reserva **pronta para retirada** na sua biblioteca (usuário X virá buscar)                               |
| Admin         | **Novo pedido de transferência** pendente                                                                |
| Admin         | Exemplar **retido aguardando decisão** (urgente); pedido cancelado por RN15                              |
| Admin         | Nova demanda de aquisição                                                                                |

---

## 7. Funcionalidades por perfil

Legenda: ✅ esperado implementado (testar) · 🟡 parcial/ajustar · ❌ a construir · ❓ verificar.

### 7.1 Visitante (sem sessão)

| ID  | Funcionalidade                                  | Rota        | Comportamento                                                   | Estado |
| --- | ----------------------------------------------- | ----------- | --------------------------------------------------------------- | ------ |
| V1  | **Login**                                       | `/login`    | E-mail/senha → JWT → tela inicial do perfil. Link "Cadastre-se" | ❌     |
| V2  | **Cadastre-se** (autocadastro de usuário comum) | `/cadastro` | Nome, e-mail, senha, confirmação; cria `COMUM`; já autentica    | ❌     |

### 7.2 Usuário da comunidade (`COMUM`)

| ID  | Funcionalidade                   | Rota                                     | Comportamento                                                                                                                                                                                                                        | Estado            |
| --- | -------------------------------- | ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------- |
| U1  | **Buscar livros**                | `/` → `/resultados?...`                  | Filtros: título/autor/ISBN (campo único), gênero, autor, ano de/até, ISBN. Toda a rede. Badge Disponível / Aguardando / Indisponível; filtros na URL                                                                                 | ✅                |
| U2  | **Detalhes do livro**            | `/livro/:id`                             | Ficha + **tabela paginada de disponibilidade por biblioteca** (5/pág.): `disponíveis/total`, barra, badge, busca, filtro "só com exemplar disponível". Breadcrumb "Resultados" preserva a busca                                      | ✅                |
| U3  | **Como retirar**                 | modal em `/livro/:id`                    | Há exemplar livre: modal informativo (retirada **presencial** com o bibliotecário). **Sem reserva**                                                                                                                                  | ✅                |
| U4  | **Entrar na fila**               | `/livro/:id/reservar?biblioteca=ID`      | Seção 5.1. Escolhe retirada na própria biblioteca ou em outra (só sem o título **e** com RN15 satisfeita; opção bloqueada com explicação). Posição na fila **da biblioteca**. Modal de confirmação                                   | 🟡 (aplicar RN15) |
| U5  | **Minhas Reservas**              | `/minhas-reservas`                       | Livro, fila → retirada, **posição**, status legível, "retire até dd/mm", situação da transferência (pendente / aprovada aguardando exemplar / em trânsito / rejeitada → retirada na origem). **Cancelar** com modal (`tom="perigo"`) | ❌                |
| U6  | **Meus Empréstimos**             | `/meus-emprestimos`                      | Ativos: título, biblioteca, data, devolução prevista (14 dias), "há N dias" se atrasado; limite x/3; aviso de bloqueio com data. Somente leitura                                                                                     | ❌                |
| U7  | **Meu Histórico**                | `/historico`                             | Tabela paginada de empréstimos devolvidos e reservas concluídas/canceladas/expiradas; filtros por tipo e período                                                                                                                     | ❌                |
| U8  | **Registrar interesse em livro** | botão em `/resultados` (vazio) e/ou tela | Título + autor. RN07                                                                                                                                                                                                                 | ❌                |
| U9  | **Notificações**                 | sino no cabeçalho                        | Seção 6                                                                                                                                                                                                                              | ❌                |

### 7.3 Bibliotecário (`BIBLIOTECARIO`) — só a própria biblioteca

| ID  | Funcionalidade                               | Rota                                             | Comportamento                                                                                                                                                                                                                                                                                                                                                                                                             | Estado                              |
| --- | -------------------------------------------- | ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| B1  | **Empréstimos da biblioteca** (painel)       | `/biblioteca`                                    | 3 indicadores clicáveis; chips de status; busca (usuário/título/nº do exemplar); filtro de período; **tabela paginada** (10/pág.) por urgência, linha vermelha para atrasados; botões **Novo empréstimo** e **Registrar devolução**                                                                                                                                                                                       | 🟡 (usar sessão; sem "código")      |
| B2  | **Novo empréstimo** (balcão)                 | `/biblioteca/emprestimo`                         | 1) identificar usuário (busca; mostra x/3 e apto/impedido); 2) escolher **exemplar** por **tabela paginada com busca** (título/autor/categoria/nº) — **só exemplares `DISPONIVEL` da própria biblioteca e os `RESERVADO` para o usuário escolhido**; 3) **prazo fixo 14 dias** (campo somente leitura); modal. Bloqueia RN01 e RN12. Empréstimo de `RESERVADO` marca a reserva `RETIRADA`. **Sem campo/leitor de código** | 🟡                                  |
| B3  | **Registrar devolução**                      | modal em `/biblioteca` e `/biblioteca/devolucao` | Mostra atraso e bloqueio (2 dias por dia). Condição: **Bom / Danificado** (sem Perdido). Bom → T2/T4 (promove fila); Danificado → `INDISPONIVEL` (T13). Modal                                                                                                                                                                                                                                                             | 🟡                                  |
| B4  | **Cadastrar exemplares** (entrada no acervo) | `/biblioteca/exemplares`                         | Escolhe **livro já cadastrado** (busca em tabela; **não existe criar livro novo aqui**), conservação (Novo/Bom/Usado) e **quantidade** (1–50, padrão 1). Estado inicial `DISPONIVEL` ou `RESERVADO` (T15)                                                                                                                                                                                                                 | 🟡 (remover "novo título" e código) |
| B5  | **Transferências a receber**                 | `/biblioteca/transferencias`                     | Aba **A receber** (`EM_TRANSITO` com destino na sua biblioteca: livro, exemplar nº, origem, reservado para quem) com **Confirmar chegada** (modal; opção "chegou danificado"); aba **Saindo** (somente leitura). Não confirma as de outra biblioteca                                                                                                                                                                      | ❌                                  |
| B6  | **Reservas aguardando retirada**             | `/biblioteca/reservas`                           | Reservas `DISPONIVEL` com retirada na sua biblioteca (usuário, livro, exemplar, prazo), atalho para Novo empréstimo; visão da fila por título                                                                                                                                                                                                                                                                             | ❌                                  |
| B7  | **Gerenciar exemplares**                     | `/biblioteca/acervo`                             | Tabela paginada dos exemplares da biblioteca (título, nº, status, conservação). Ações: **Marcar indisponível** (T13, com motivo) e **Reativar** (T14/T14b), com modal                                                                                                                                                                                                                                                     | ❌                                  |
| B8  | **Buscar livros na rede**                    | `/`                                              | Mesma busca (consulta; não reserva)                                                                                                                                                                                                                                                                                                                                                                                       | ❓                                  |

### 7.4 Administrador (`ADMIN`)

| ID  | Funcionalidade                  | Rota                             | Comportamento                                                                                                                                                                                                                                                                                                                                                                                              | Estado |
| --- | ------------------------------- | -------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ |
| A1  | **Dashboard**                   | `/admin`                         | KPIs em **cards/tabelas**: bibliotecas, exemplares por status, empréstimos ativos/atrasados, reservas em fila, transferências pendentes/em trânsito, demandas abertas                                                                                                                                                                                                                                      | ❌     |
| A2  | **Transferências — decisão**    | `/admin/transferencias`          | **Pendentes** (cards): livro (`s.livro`), solicitante, origem → destino, se há exemplar já separado ou "será vinculado na devolução", RN15. **Aprovar / Rejeitar** com modal explicando o efeito. **Acompanhamento** em tabela paginada (aprovada–aguardando exemplar, em trânsito, concluídas, rejeitadas, canceladas). **Sem botão de confirmar chegada** (RN16). Não pode quebrar com `exemplar = null` | 🟡     |
| A3  | **Transferência avulsa** (lote) | botão em `/admin/transferencias` | Seção 5.4: tabela com seleção múltipla + destino + observação + modal; mostra erro por grupo violando RN15                                                                                                                                                                                                                                                                                                 | ❌     |
| A4  | **Histórico de circulação**     | `/admin/historico`               | Eventos recentes da rede e linha do tempo por exemplar: data, evento, biblioteca, responsável, observação; filtros e busca por exemplar                                                                                                                                                                                                                                                                    | ❌     |
| A5  | **Bibliotecas**                 | `/admin/bibliotecas`             | Listar/criar/editar/desativar (nome, endereço, e-mail, telefone)                                                                                                                                                                                                                                                                                                                                           | ❌     |
| A6  | **Bibliotecários**              | `/admin/bibliotecarios`          | Listar/criar/ativar-desativar usuários `BIBLIOTECARIO` com **biblioteca obrigatória** e senha inicial. **Não cadastra `COMUM`**                                                                                                                                                                                                                                                                            | ❌     |
| A7  | **Categorias**                  | `/admin/categorias`              | Listar/criar/editar                                                                                                                                                                                                                                                                                                                                                                                        | ❌     |
| A8  | **Catálogo de livros**          | `/admin/catalogo`                | Tabela paginada com busca; criar/editar livro (título, autor, ISBN, editora, ano, categoria, sinopse). **Só o Admin**                                                                                                                                                                                                                                                                                      | ❌     |
| A9  | **Demandas de aquisição**       | `/admin/demandas`                | Tabela (título, autor, nº de solicitações, status, datas) por mais pedidas; Admin altera status `ABERTA → EM_ANALISE → APROVADA/REJEITADA`                                                                                                                                                                                                                                                                 | ❌     |
| A10 | **Relatórios gerenciais**       | `/admin/relatorios`              | **Somente tabelas — sem gráficos** (nenhuma lib de gráfico, canvas ou SVG de chart). Mínimo: acervo por biblioteca e status; empréstimos por período; atrasos; livros mais emprestados; demandas mais pedidas; transferências por status. Filtro de período                                                                                                                                                | ❌     |
| A11 | **Buscar livros na rede**       | `/`                              | Mesma busca                                                                                                                                                                                                                                                                                                                                                                                                | ❓     |

O Admin **não** cadastra exemplares, **não** registra empréstimo/devolução e **não** confirma chegada.

---

## 8. Dados de demonstração (seed novo — `data.sql`)

**Reescrever o seed inteiro** (substitui o antigo). Regras: dados realistas e coerentes com **todas** as regras acima; datas relativas (`CURRENT_TIMESTAMP - INTERVAL`); IDs explícitos + `setval` das sequences ao final; **sem código de barras**; histórico de circulação e notificações coerentes com cada estado; ISBNs **fictícios** (formato `978-65-XXXXX-XX-X`) ou nulos — **não inventar ISBN real**. Gerar hash **BCrypt real** para as senhas.

**Objetivo do seed:** servir de base para o **vídeo de demonstração de ~7 min** (roteiro na seção 9.0). Por isso: (a) um usuário comum **zerado** (U1) para mostrar os fluxos do zero; (b) dois usuários com histórico e pendências (U2 e U3) para mostrar atrasos, limite, fila e retirada; (c) transferências **prontas para o Admin decidir ao vivo**; (d) **todas** as unidades com bibliotecário.

### 8.1 Usuários (senha de demonstração de todos: `senha123`)

**Padrão obrigatório:** cada biblioteca tem **exatamente um** bibliotecário (nenhuma unidade fica sem). Para a unidade de nome curto `<Unidade>`: nome `Bibliotecário <Unidade>` e e-mail `bibliotecario<unidade>@circulabook.com` (minúsculas, sem espaços nem acentos). Existe um único Admin. Os usuários comuns são `Usuário 1/2/3`. **Esse mesmo padrão vale para qualquer biblioteca/bibliotecário criado depois pela interface** (ex.: A-14).

| Apelido | Perfil        | Nome                      | E-mail                                      | Biblioteca  |
| ------- | ------------- | ------------------------- | ------------------------------------------- | ----------- |
| Admin   | ADMIN         | Admin                     | `admin@circulabook.com`                     | —           |
| BibC    | BIBLIOTECARIO | Bibliotecário Central     | `bibliotecariocentral@circulabook.com`      | Central     |
| BibVI   | BIBLIOTECARIO | Bibliotecário Vila Isabel | `bibliotecariovilaisabel@circulabook.com`   | Vila Isabel |
| BibT    | BIBLIOTECARIO | Bibliotecário Tijuca      | `bibliotecariotijuca@circulabook.com`       | Tijuca      |
| U1      | COMUM         | Usuário 1                 | `usuario1@circulabook.com`                  | —           |
| U2      | COMUM         | Usuário 2                 | `usuario2@circulabook.com`                  | —           |
| U3      | COMUM         | Usuário 3                 | `usuario3@circulabook.com`                  | —           |

**Papel de cada usuário comum no seed:** **U1 = zerado** (0 empréstimos, 0 reservas, 0 histórico, 0 notificações, nenhuma demanda); **U2 = "no limite e atrasado"** (3/3 emprestados, um atrasado); **U3 = "com reservas"** (uma reserva pronta para retirada e um pedido de transferência pendente).

### 8.2 Bibliotecas

1. **Biblioteca Central** (Centro) — nome curto `Central` — BibC.
2. **Biblioteca Comunitária de Vila Isabel** — nome curto `Vila Isabel` (VI) — BibVI.
3. **Biblioteca Popular da Tijuca** — nome curto `Tijuca` (T) — BibT. **Sem acervo** no início: serve de destino de transferência (tem bibliotecário ativo, RN22), de local para o bibliotecário cadastrar os primeiros exemplares (ex.: _Memórias Póstumas_) e de biblioteca "sem o título" para retirada em outra unidade.

### 8.2b Categorias

Romance · Ficção Científica · Fantasia · Suspense e Policial · Biografia · Infantil e Juvenil · História e Sociedade · Poesia e Crônicas · Tecnologia.

### 8.3 Livros e exemplares (C = Central, VI = Vila Isabel, T = Tijuca: vazia)

Livros, autores e categorias são **dados fixos do catálogo** (mantidos). Os estados dos exemplares abaixo são os do instante inicial.

| #   | Livro — Autor (ano)                                         | Categoria            | C                                                    | VI                                                          | Cenário                                                                                          |
| --- | ----------------------------------------------------------- | -------------------- | ---------------------------------------------------- | ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| 1   | Dom Casmurro — Machado de Assis (1899)                      | Romance              | 3 `DISP`                                             | 2 `DISP`                                                    | Tudo disponível ("Como retirar"); usado na avulsa em lote                                        |
| 2   | Memórias Póstumas de Brás Cubas — Machado de Assis (1881)   | Romance              | —                                                    | —                                                           | **Sem exemplares** (BibT cadastra o 1º; mostra "Indisponível: sem exemplares")                   |
| 3   | O Cortiço — Aluísio Azevedo (1890)                          | Romance              | 2 `DISP`                                             | 1 `DISP`                                                    |                                                                                                  |
| 4   | Capitães da Areia — Jorge Amado (1937)                      | Romance              | —                                                    | 2 `DISP`                                                    |                                                                                                  |
| 5   | Grande Sertão: Veredas — Guimarães Rosa (1956)             | Romance              | **1 `EMPR`** (U2)                                    | —                                                           | **RN15:** origem com 1 só exemplar → "retirar em outra biblioteca" bloqueado                     |
| 6   | 1984 — George Orwell (1949)                                 | Ficção Científica    | 2 `DISP`                                             | 1 `DISP`                                                    | Avulsa em lote: 2 de 2 viola RN15                                                                |
| 7   | Fahrenheit 451 — Ray Bradbury (1953)                        | Ficção Científica    | 2 `DISP` + 1 **`EM_TRANSFERENCIA`**                  | —                                                           | Avulsa **em trânsito** C → VI (BibVI confirma a chegada)                                         |
| 8   | Duna — Frank Herbert (1965)                                 | Ficção Científica    | **2 `EMPR`** (U2 **atrasado**, U3)                   | —                                                           | **Demo ao vivo:** U1 entra na fila na Central; devolução do atrasado promove U1                  |
| 9   | O Hobbit — J. R. R. Tolkien (1937)                          | Fantasia             | **1 `EMPRESTADO_RESERVADO`** (U2) + **1 `INDISPONIVEL`** | —                                                       | Fila: U3 (retirada VI) + transferência **PENDENTE** (Admin aprova ao vivo)                       |
| 10  | Harry Potter e a Pedra Filosofal — J. K. Rowling (1997)     | Fantasia             | 2 `DISP`                                             | **1 `RESERVADO`** (para U3) + 1 `DISP`                      | Reserva de U3 `DISPONIVEL`, expira em 2 dias (BibVI empresta ao vivo)                            |
| 11  | Assassinato no Expresso do Oriente — Agatha Christie (1934) | Suspense e Policial  | 1 `DISP`                                             | 2 `DISP`                                                    |                                                                                                  |
| 12  | O Cão dos Baskerville — Arthur Conan Doyle (1902)           | Suspense e Policial  | —                                                    | 2 `DISP`                                                    |                                                                                                  |
| 13  | O Diário de Anne Frank — Anne Frank (1947)                  | Biografia            | 2 `DISP`                                             | —                                                           |                                                                                                  |
| 14  | Steve Jobs — Walter Isaacson (2011)                         | Biografia            | 1 `DISP`                                             | **1 `INDISPONIVEL`** (danificado)                           | BibVI **reativa**                                                                                |
| 15  | Quarto de Despejo — Carolina Maria de Jesus (1960)          | Biografia            | —                                                    | 2 `DISP`                                                    |                                                                                                  |
| 16  | O Pequeno Príncipe — Antoine de Saint-Exupéry (1943)        | Infantil e Juvenil   | 2 `DISP`                                             | 3 `DISP`                                                    |                                                                                                  |
| 17  | Sapiens — Yuval Noah Harari (2011)                          | História e Sociedade | 2 `DISP`                                             | 1 `DISP` (veio por avulsa concluída)                        |                                                                                                  |
| 18  | Casa-Grande & Senzala — Gilberto Freyre (1933)              | História e Sociedade | 1 `DISP`                                             | —                                                           |                                                                                                  |
| 19  | Antologia Poética — Carlos Drummond de Andrade (1962)       | Poesia e Crônicas    | —                                                    | 2 `DISP`                                                    |                                                                                                  |
| 20  | Código Limpo (Clean Code) — Robert C. Martin (2008)         | Tecnologia           | 2 `DISP`                                             | 1 `DISP`                                                    |                                                                                                  |

**Por que o Hobbit tem 1 `INDISPONIVEL`:** com apenas U2 e U3 em movimento, o Hobbit precisa ter **2 exemplares na origem** (para a RN15 permitir a transferência de U3) e **só 1 emprestado** (de U2), já que U3 é quem está na fila. O outro exemplar fica `INDISPONIVEL` (em reparo).

### 8.4 Empréstimos, reservas, transferências, demandas, notificações

- **Empréstimos ativos (14 dias):**
  - **U2 → 3/3 (no limite):** O Hobbit (C, há 3 dias, `EMPRESTADO_RESERVADO`), Grande Sertão (C, há 6 dias), Duna (C, há 20 dias, **ATRASADO há 6**).
  - **U3 → 1/3:** Duna (C, há 10 dias).
  - **U1 → nenhum.**
  - Ninguém bloqueado no início (U2 será bloqueado ao devolver Duna: 2 × 6 = 12 dias).
- **Histórico (só U2 e U3; U1 vazio):**
  - U2: empréstimos devolvidos _Sapiens_ (no prazo) e _O Pequeno Príncipe_; reserva antiga `CANCELADA` (_Dom Casmurro_).
  - U3: empréstimos devolvidos _Código Limpo_ e _O Diário de Anne Frank_; reservas antigas `RETIRADA` (_Código Limpo_) e `EXPIRADA` (_1984_).
- **Reservas ativas:**
  - **R1** U3 — O Hobbit — fila C, retirada VI — `PENDENTE`.
  - **R2** U3 — Harry Potter — fila VI, retirada VI — `DISPONIVEL` (exemplar `RESERVADO`, expira em +2 dias).
- **Transferências:**
  - **T1** `PENDENTE` (R1, sem exemplar, C → VI, criada há 1 dia).
  - **T2** `EM_TRANSITO` avulsa (Fahrenheit 451, C → VI, Admin, há 3 dias).
  - **T3** `CONCLUIDA` avulsa (Sapiens, C → VI, há 10 dias).
- **Demandas:** _Ensaio sobre a Cegueira_ (Saramago) — 2 solicitações (U2, U3) `ABERTA`; _Torto Arado_ (Itamar Vieira Junior) — 1 (U3) `EM_ANALISE`; _A Hora da Estrela_ (Clarice Lispector) — 1 (U2) `APROVADA`. U1 não pediu nada (assim ele pode registrar interesse ao vivo).
- **Notificações iniciais (não lidas):** U3 — Harry Potter pronto para retirada em VI; U2 — Duna atrasado; BibVI — Fahrenheit 451 a caminho e "U3 virá buscar o Harry Potter"; Admin — novo pedido de transferência (O Hobbit); **U1 e BibT: nenhuma.**
- **Invariantes que o seed deve satisfazer (testar no boot):** 4.2 (Hobbit emprestado = `EMPRESTADO_RESERVADO` porque R1 está `PENDENTE` na Central; Duna e Grande Sertão = `EMPRESTADO`); nenhum `RESERVADO` sem reserva (o do Harry tem R2); RN15 (Hobbit: total 2, abertas 1; Grande Sertão: total 1, abertas 0); RN22 (as 3 bibliotecas têm bibliotecário ativo).

---

## 9. Roteiros de teste e de gravação

Parta do **seed 8**. Credenciais na 8.1 (todas com `senha123`). Roteiros marcados **(T)** são cobertos por teste automatizado com dados próprios (não dependem do seed); os marcados **(\*)** montam dados adicionais pela API antes de executar. **Reinicie o backend antes de gravar o vídeo e entre grupos que alteram estado** (`ddl-auto=create` recria tudo).

### 9.0 Roteiro de gravação do vídeo de demonstração (≈ 7 min)

**Pré-condições:** backend recém-iniciado (seed limpo); frontend em `localhost:5173`; um navegador com 5 abas (ou janela anônima por perfil) para trocar de usuário rápido, ou uso de "Sair" + login. Siga **esta ordem** (as cenas dependem umas das outras). Os tempos são alvos; se passar de 7 min, corte as etapas marcadas _(opcional)_.

| Cena | Tempo | Quem | Passo a passo (o que clicar) | O que mostrar / resultado esperado |
| ---- | ----- | ---- | ---------------------------- | ---------------------------------- |
| **1. Usuário "do zero"** | 0:00–1:30 | **U1** (`usuario1@`) | 1) Fazer login. 2) Mostrar o sino (sem notificações), **Meus Empréstimos** (0/3), **Minhas Reservas** (vazio). 3) Buscar **"Dom Casmurro"**, abrir a ficha: tabela por biblioteca; clicar **Como retirar** (modal informativo, **sem** botão de reservar). 4) Buscar **"Duna"**: Central com 0 disponíveis. Clicar em entrar na fila da **Central**, retirada **"Nesta biblioteca"**, confirmar no modal. 5) Em **Minhas Reservas**: posição **1ª**, status pendente. 6) Buscar **"Grande Sertão"**, iniciar reserva, escolher **"Em outra biblioteca"**: as opções aparecem **bloqueadas com o motivo** (a Central só tem 1 exemplar); não confirmar. 7) Buscar **"Ensaio sobre a Cegueira"** (não há exemplares nem catálogo): **Registrar interesse** → sucesso (contador sobe de 2 para 3). Tentar de novo → **barrado** ("você já registrou interesse"). | Fluxo de busca, fila, regra de capacidade da origem (RN15) e demanda de aquisição. |
| **2. Usuários com histórico** | 1:30–2:15 | **U3** (`usuario3@`), depois **U2** (`usuario2@`) | **U3:** sino com 1 notificação (Harry Potter pronto na Vila Isabel); **Minhas Reservas**: Harry (`retire até` em 2 dias) e **O Hobbit** (transferência **aguardando decisão do Admin**); **Meus Empréstimos** 1/3. **U2:** **Meus Empréstimos** 3/3 com **Duna atrasado há 6 dias**; sino com aviso de atraso; **Histórico** com itens antigos. | Telas de acompanhamento, contador x/3, atraso e histórico. |
| **3. Admin decide ao vivo** | 2:15–4:00 | **Admin** (`admin@`) | 1) Login: **Dashboard** (KPIs). 2) Sino: **novo pedido de transferência (O Hobbit)**. 3) **Transferências** → o pedido pendente do Hobbit (aparece o título mesmo sem exemplar separado): clicar **Aprovar** → modal → confirmar → fica **"aprovada, aguardando exemplar"**. 4) **Avulsa em lote**: selecionar **2 exemplares de _1984_** da Central → destino Tijuca → confirmar → **recusada, nada criado** (a Central ficaria sem o livro). 5) Refazer: **1 de _1984_ + 2 de _Dom Casmurro_** → destino **Tijuca** → confirmar → criadas, todas **em trânsito**. 6) Mostrar **Acompanhamento** (status das transferências). 7) _(opcional)_ **Demandas**: mudar _Torto Arado_ de "Em análise" para "Aprovada". | Aprovação de transferência, regra de capacidade em lote (tudo ou nada) e avulsa concluída. |
| **4. Bibliotecário da Central** | 4:00–5:15 | **BibC** (`bibliotecariocentral@`) | 1) Login: painel da Central. 2) **Devolução** do **O Hobbit** (Usuário 2), condição **Bom**: como o pedido foi aprovado, o exemplar **vai direto para transferência** (avisar na tela). 3) **Devolução** de **Duna** (Usuário 2, atrasado): o **modal avisa o atraso**; confirmar: Usuário 2 fica **bloqueado por 12 dias** e é notificado; o exemplar fica **reservado para o Usuário 1** (1º da fila). 4) **Reservas aguardando retirada**: aparece o Usuário 1 com Duna. **Novo empréstimo** de Duna ao **Usuário 1** (atalho) → sucesso, devolução em +14 dias. 5) Tentar **emprestar** qualquer livro ao **Usuário 2** → **bloqueado** (atraso/bloqueio). | Devolução com fila, despacho automático de transferência, bloqueio por atraso e promoção da fila. |
| **5. Bibliotecário de Vila Isabel** | 5:15–6:30 | **BibVI** (`bibliotecariovilaisabel@`) | 1) Login. 2) **Transferências → A receber**: aparecem **O Hobbit** (para o Usuário 3) e **Fahrenheit 451**. **Confirmar chegada** de ambos: o Fahrenheit fica disponível; o Hobbit fica reservado para o Usuário 3. 3) **Reservas aguardando retirada**: Harry Potter e Hobbit do Usuário 3. **Novo empréstimo** (atalho) de cada um ao **Usuário 3** → 3/3. 4) _(opcional)_ **Gerenciar exemplares**: **reativar** o _Steve Jobs_ indisponível. | Chegada de transferência (reserva e avulsa), retirada de reserva e reativação. |
| **6. Bibliotecário da Tijuca** | 6:30–6:50 | **BibT** (`bibliotecariotijuca@`) | 1) Login; acervo vazio. 2) **Cadastrar exemplares**: _Memórias Póstumas de Brás Cubas_, **quantidade 2** → disponíveis. 3) **A receber**: os 3 exemplares da avulsa → **confirmar chegada**. | Biblioteca nova começando do zero e recebendo a avulsa. |
| **7. Fechamento** | 6:50–7:30 | **U1** e **Admin** | **U1:** sino com "Duna pronto"/empréstimo; **Meus Empréstimos** 1/3. **Admin:** **Histórico de circulação** (linha do tempo do _Hobbit_: devolução → transferência → chegada → empréstimo) e **Relatórios** (somente tabelas). | Notificações, rastreabilidade e relatórios. |

**Atalho para gravar em 5 min:** cortar 3.7, 3.6, 5.4, a cena 6 e o item 2 da cena 2.

**Verificações que o seed precisa garantir para este roteiro** (testar no boot): U1 sem nenhum registro; Hobbit `EMPRESTADO_RESERVADO` (U2) com o pedido T1 `PENDENTE`; Central com **exatamente 2 exemplares de _1984_** e **3 de _Dom Casmurro_**; Duna com 2 emprestados (U2 atrasado há 6 dias, U3) e **nenhum disponível**; Grande Sertão com **1 só exemplar** na Central; Tijuca sem exemplares e com bibliotecário; _Ensaio sobre a Cegueira_ com 2 solicitações (U2, U3) e **sem** U1.

### 9.1 Autenticação (AU)

| #     | Cenário                                                                   | Esperado                                                                               |
| ----- | ------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| AU-01 | Login válido de cada usuário da 8.1 (Admin, 3 bibliotecários, U1–U3)      | Token + usuário/perfil; redireciona à tela inicial do perfil; sem `<select>` de perfil |
| AU-02 | Senha errada / e-mail inexistente                                         | `401` com mensagem genérica                                                            |
| AU-03 | Chamar a API sem token / com token adulterado ou expirado                 | `401`; no front, volta ao `/login`                                                     |
| AU-04 | Autocadastro com dados válidos                                            | Cria **`COMUM`** (ignora `tipo` enviado), já entra logado                              |
| AU-05 | Autocadastro com e-mail repetido / senha < 6 / confirmação diferente      | Erros claros                                                                           |
| AU-06 | `COMUM` chama endpoints de bibliotecário/admin e abre suas rotas no front | `403` / redirecionado                                                                  |
| AU-07 | Enviar `adminId`/`usuarioId` falso em parâmetros                          | **Ignorado**: o ator é o do token                                                      |
| AU-08 | Resposta de `/usuarios` ou `/auth/me`                                     | **Nunca** contém `senhaHash`                                                           |
| AU-09 | Sair                                                                      | Sessão limpa; rotas protegidas voltam ao login                                         |

### 9.2 Estados e invariantes (ES)

| #     | Cenário                                                                  | Esperado                                                                                                           |
| ----- | ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------ |
| ES-01 | U1 entra na fila de **Duna** na Central (retirada Central)               | Os **2** exemplares de Duna: `EMPRESTADO → EMPRESTADO_RESERVADO` (T3)                                              |
| ES-02 | U1 cancela essa reserva                                                  | Ambos voltam a `EMPRESTADO` (T12)                                                                                  |
| ES-03 | BibVI empresta o Harry Potter reservado a **U3**                         | Exemplar `RESERVADO → EMPRESTADO` (T6); R2 `RETIRADA`                                                              |
| ES-04 | (T) Empréstimo do exemplar reservado com outras reservas na fila         | `RESERVADO → EMPRESTADO_RESERVADO` (T5); cancelando a fila, `→ EMPRESTADO` (T12)                                   |
| ES-05 | (T) Cancelamento da última reserva pendente de uma fila                  | Todos os emprestados do título na biblioteca voltam a `EMPRESTADO` (T12)                                           |
| ES-06 | BibC devolve o **Hobbit** de U2 (Bom) **sem** T1 aprovada                | `EMPRESTADO_RESERVADO → RESERVADO` para U3 (T4); a reserva fica retida aguardando o Admin                          |
| ES-07 | Forçar expiração de R2 (U3)                                              | `RESERVADO → DISPONIVEL` (T7), pois não há outra pessoa na fila; R2 `EXPIRADA`; U3 notificado                      |
| ES-08 | (T) Expiração de reserva com outra pendente na fila                      | `RESERVADO → RESERVADO` reatribuído ao próximo (T7b)                                                               |
| ES-09 | BibVI confirma a chegada de **Fahrenheit 451**                           | `EM_TRANSFERENCIA → DISPONIVEL` na VI (T11); transferência `CONCLUIDA`                                             |
| ES-10 | Ciclo completo do Hobbit (ver B-12)                                      | `…→ RESERVADO → EM_TRANSFERENCIA → RESERVADO` (VI) `→ EMPRESTADO`                                                  |
| ES-11 | BibVI reativa o **Steve Jobs** `INDISPONIVEL` da VI                      | `INDISPONIVEL → DISPONIVEL` (T14); (T) com fila → `RESERVADO` (T14b)                                               |
| ES-12 | BibVI marca um exemplar `DISPONIVEL` como indisponível                   | T13; sai da lista de empréstimo                                                                                    |
| ES-13 | (T) Marcar `INDISPONIVEL` um exemplar `RESERVADO`                        | Reserva volta a `PENDENTE` mantendo a posição; usuário notificado (ver limitação 11.7)                             |
| ES-14 | Avulsa de exemplar `EMPRESTADO`/`RESERVADO` sem pedido aprovado          | **Recusada** (só `DISPONIVEL`)                                                                                     |
| ES-15 | Invariante 4.2 em qualquer momento                                       | Nunca `EMPRESTADO_RESERVADO` sem fila nem `EMPRESTADO` com fila; nenhum exemplar `RESERVADO` sem reserva associada |

### 9.3 Usuário comum (U)

| #    | Cenário                                                                 | Esperado                                                                                                   |
| ---- | ----------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| U-01 | U1 busca "orwell" e abre _1984_                                         | Resultado com badge/nº de bibliotecas; detalhe com tabela por biblioteca; breadcrumb preserva a busca      |
| U-02 | Linha com disponíveis > 0                                               | "Como retirar" abre modal informativo; **sem** botão de reservar                                           |
| U-03 | Reservar (API) em biblioteca com exemplar livre                         | Erro "…disponível(is)… presencialmente"                                                                    |
| U-04 | Reservar em biblioteca sem o título (ex.: Tijuca)                       | Erro "não possui exemplares deste título"                                                                  |
| U-05 | U1: _Duna_ → fila na Central, retirada na Central                       | Reserva `PENDENTE`, posição 1ª, sem transferência                                                          |
| U-06 | U1: _Duna_ → fila na Central, "Em outra biblioteca"                     | Destinos liberados: **Vila Isabel** e **Tijuca** (nunca a Central); cria reserva + transferência `PENDENTE` sem exemplar |
| U-07 | U1: _Grande Sertão_ → "Em outra biblioteca"                             | **Bloqueado** (Central só tem 1 exemplar, RN15); API também recusa                                         |
| U-08 | (T) Destino que já tem o título                                         | Erro explicativo                                                                                           |
| U-09 | U2 tenta reservar **O Hobbit** (já tem emprestado) / U3 tenta reservar o Hobbit de novo (já tem R1) | Erro                                                                           |
| U-10 | Minhas Reservas de U3                                                   | Mostra posição, status e etapa da transferência (Hobbit); "retire até" no Harry (`DISPONIVEL`)            |
| U-11 | Cancelar reserva com transferência `PENDENTE`/`APROVADA` (U3 cancela R1) | Modal de confirmação; transferência `CANCELADA`; exemplar vinculado reatribuído/liberado                  |
| U-12 | (\*) Cancelar com transferência `EM_TRANSITO` (após BibC devolver o Hobbit) | Viagem continua; exemplar chega à VI como `DISPONIVEL`                                                  |
| U-13 | Meus Empréstimos / Histórico de U2                                      | Só dados de U2; 3/3; Duna atrasado há 6 dias; prazo de 14 dias                                             |
| U-14 | Interesse: U1 em _Ensaio sobre a Cegueira_; U1 de novo; (\*) novo título pedido por U1 e depois por U2 | 1º incrementa de 2 para 3; **mesmo usuário barrado**; outro usuário incrementa o título novo (RN07) |
| U-15 | Sino                                                                    | U3 vê contador vermelho (1); marcar lida/todas zera; **U1 sem notificações**                               |
| U-16 | U1 abre `/biblioteca` e `/admin`                                        | Bloqueada                                                                                                  |

### 9.4 Bibliotecário (B) — BibVI, BibC e BibT

| #    | Cenário                                                                                                                                       | Esperado                                                                                                                              |
| ---- | --------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| B-01 | Login de BibVI                                                                                                                                | Menu do bibliotecário; painel `/biblioteca` com dados **da VI**                                                                       |
| B-02 | Novo empréstimo                                                                                                                               | Exemplares **só da biblioteca do logado**; **sem campo de código**; prazo fixo 14 dias, não editável                                  |
| B-03 | BibC empresta a **U2** (3/3)                                                                                                                  | Bloqueado (RN01)                                                                                                                      |
| B-04 | BibC empresta _Dom Casmurro_ a **U1** e tenta um 2º exemplar do mesmo título ao mesmo usuário                                                  | 1º ok; 2º bloqueado (RN01)                                                                                                            |
| B-05 | BibVI tenta emprestar a **U1** o Harry reservado a **U3**                                                                                     | Erro "reservado para Usuário 3"                                                                                                       |
| B-06 | BibVI empresta o Harry reservado a **U3**                                                                                                     | Sucesso; reserva `RETIRADA`; devolução prevista +14 dias                                                                              |
| B-07 | BibC devolve **Duna** de U2 (Bom, 6 dias de atraso)                                                                                           | Modal avisa antes; U2 **bloqueado 12 dias** e notificado                                                                              |
| B-08 | Devolução: opções de condição                                                                                                                 | Só **Bom** e **Danificado**                                                                                                           |
| B-09 | BibC devolve **Grande Sertão** (U2) como **Danificado**                                                                                       | Exemplar `INDISPONIVEL`; fila **não** promovida                                                                                       |
| B-10 | (\*) U1 na fila de Duna (retirada Central) e BibC devolve Duna (Bom)                                                                           | Vai a `RESERVADO` para U1 (T4); reserva `DISPONIVEL` com +3 dias                                                                      |
| B-11 | BibVI tenta devolver/emprestar exemplar da **Central** (API)                                                                                  | `403` (RN08/RN17)                                                                                                                     |
| B-12 | **Ciclo Hobbit:** Admin aprova T1 → BibC devolve o Hobbit de U2 → BibVI vê em "A receber" → confirma chegada → empresta a U3                  | Exemplar vai direto a `EM_TRANSFERENCIA`; chegada → `RESERVADO` na VI, reserva `DISPONIVEL`; empréstimo → `EMPRESTADO`; R1 `RETIRADA` |
| B-13 | BibC tenta confirmar chegada de transferência para a VI                                                                                       | Recusado (só o bibliotecário do destino)                                                                                              |
| B-14 | BibT cadastra exemplares: lista de livros é **só de livros existentes**; quantidade 3                                                         | Cria 3 exemplares; **não existe** opção de livro novo nem campo de código                                                             |
| B-15 | BibT cadastra o 1º exemplar de _Memórias Póstumas_                                                                                            | Fica `DISPONIVEL` e **emprestável** (RN11 removida)                                                                                   |
| B-16 | Cadastrar exemplar na Central estando logado como BibT (API)                                                                                  | `403`                                                                                                                                 |
| B-17 | (\*) Com U1 na fila de Duna, BibC cadastra 1 exemplar de Duna                                                                                 | Novo exemplar nasce `RESERVADO` e atende U1 (T15)                                                                                     |
| B-18 | Gerenciar exemplares: marcar indisponível / reativar com modal                                                                                | T13/T14                                                                                                                               |
| B-19 | Bibliotecário tenta criar livro/categoria                                                                                                     | `403`; sem tela para isso                                                                                                             |
| B-20 | Reservas aguardando retirada (BibVI)                                                                                                          | Mostra U3/Harry com prazo; sino do bibliotecário notifica                                                                             |

### 9.5 Administrador (A) — Admin

| #    | Cenário                                                                              | Esperado                                                                                            |
| ---- | ------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| A-01 | Login e dashboard                                                                    | KPIs corretos; **só tabelas/cards**                                                                 |
| A-02 | Transferências                                                                       | Pendente T1 mostra o título do Hobbit mesmo **sem exemplar**; **não há** botão de confirmar chegada |
| A-03 | Aprovar T1                                                                           | `APROVADA` "aguardando exemplar" (Central tem 2 Hobbit; RN15 ok)                                    |
| A-04 | Rejeitar T1                                                                          | `REJEITADA`; R1 segue `PENDENTE` com retirada na Central; U3 notificado                             |
| A-05 | (\*) BibC devolve o Hobbit antes da aprovação; depois o Admin aprova                 | Exemplar já retido: vai direto a `EM_TRANSITO`                                                      |
| A-06 | Pedido já processado / não-Admin aprovando (API)                                     | Erro "já foi processada" / `403`                                                                    |
| A-07 | U1 tenta reservar o Hobbit com retirada na VI (já há T1 aberta na origem)            | Recusado: capacidade da origem esgotada (RN15), mensagem clara                                      |
| A-08 | Avulsa em lote: 2 de _1984_ (Central tem 2)                                          | **Recusada**, nada criado                                                                           |
| A-09 | Avulsa em lote: 1 de _1984_ + 2 de _Dom Casmurro_ (Central tem 3) para a **Tijuca**  | Criadas; todas `EM_TRANSITO`                                                                        |
| A-10 | Lote com um item inválido e outros válidos                                           | **Tudo ou nada**: nada criado, mensagem lista o violador                                            |
| A-11 | Avulsa para destino que já tem o título (_Dom Casmurro_ C → VI)                      | Permitida                                                                                           |
| A-12 | Cadastrar categoria e livro                                                          | Livro aparece na busca como "Indisponível: sem exemplares"                                          |
| A-13 | Editar livro                                                                         | Persiste; bibliotecário não consegue (403)                                                          |
| A-14 | Cadastrar **uma biblioteca nova** e seu bibliotecário **no padrão da 8.1** (`Bibliotecário <Unidade>` / `bibliotecario<unidade>@circulabook.com`); logar com ele | Usuário `BIBLIOTECARIO` vinculado; acessa só a nova biblioteca; a UI não permite criar biblioteca sem bibliotecário depois de concluído o cadastro |
| A-15 | Tentar cadastrar usuário `COMUM`, exemplar, empréstimo ou devolução como Admin       | Recusado (`403` / sem opção na UI)                                                                  |
| A-16 | Cadastrar/editar biblioteca                                                          | Funciona                                                                                            |
| A-17 | Demandas                                                                             | Lista por mais pedidas (_Ensaio_ com 2 primeiro); troca de status                                   |
| A-18 | Histórico de circulação                                                              | Eventos e linha do tempo por exemplar completos                                                     |
| A-19 | Relatórios                                                                           | **Somente tabelas**; nenhuma biblioteca de gráficos no `package.json` e nenhum `<canvas>`/chart     |
| A-20 | Sino do Admin                                                                        | Notifica novo pedido de transferência e retenção urgente                                            |

### 9.6 Consistência geral

- Nenhum exemplar com dois "donos"; `EM_TRANSFERENCIA` sempre com transferência `EM_TRANSITO`.
- Toda mudança relevante de estado gera linha em `historico_circulacao`.
- **Nenhuma** ocorrência de "código de barras"/`codigoBarras`, `PERDIDO` ou `RN11`: `grep -ri` no repositório deve vir vazio (exceto este documento e o histórico do Git).
- Nenhuma classe de trigger/listener (`BlackboardTriggerInicializador`, `PgNotificationListener`, `EspecialistaTransferenciaService`); a aplicação sobe sem erros.
- Sequences do `data.sql` ressincronizadas.
- **Padrão de usuários do seed (8.1):** exatamente 1 Admin, exatamente 1 bibliotecário por biblioteca (nenhuma sem), 3 usuários comuns; e-mails e nomes exatamente como na 8.1.

---

## 10. Backlog de ajustes no código (ordem recomendada)

1. **Login JWT** (seção 2): dependências (`spring-boot-starter-security` + biblioteca JWT compatível com o Boot do `pom.xml`), `SecurityConfig`, filtro, `AuthController`, BCrypt, `@JsonIgnore` no hash, autorização por perfil (2.3), ator vindo do token em **todos** os controllers/serviços; no front: `/login`, `/cadastro`, `AuthContext`, `RotaProtegida`, cliente HTTP com Bearer, header sem seletor de perfil.
2. **Domínio:** novo status `EMPRESTADO_RESERVADO` + `sincronizarMarcaDeFila`; transições T1–T15 (4.3) com validação de transição inválida; reativar/marcar indisponível; remover **RN11**, **`PERDIDO`** e **prazo variável** (14 fixos).
3. **Remover código de barras** por completo (entidade, DTOs, repositório `findByCodigoBarras`, endpoint `/exemplares/codigo/{c}`, UI, seed, textos). Exibir "Exemplar nº X".
4. **Transferência:** RN15 (criação de reserva, aprovação, despacho, avulsa), **avulsa em lote** (`exemplarIds[]`, tudo ou nada), **chegada só pelo bibliotecário do destino** (remover permissão do Admin), `GET` de transferências "a receber"/"saindo" por biblioteca.
5. **Permissões de cadastro:** livro/categoria/biblioteca/bibliotecário só Admin; exemplar só bibliotecário (de livro existente; remover "novo título" do cadastro de exemplar, aceitar `quantidade`); empréstimo/devolução só bibliotecário.
6. **Notificações** (seção 6): entidade, endpoints, geração nos eventos, polling e sino.
7. **Demandas** (RN07) e **relatórios em tabelas**; **histórico** para o Admin.
8. **Front — telas pendentes:** Minhas Reservas, Meus Empréstimos, Meu Histórico, Interesse, B5, B6, B7, A1–A10; ajustar `TransferenciasAdmin` (usar `s.livro`, tratar `exemplar == null`, sem confirmar chegada, modais), `RegistrarEmprestimo`, `RegistrarDevolucao`, `CadastrarExemplar`, `EmprestimosBiblioteca`, `ReservarLivro` (RN15), `types.ts`.
9. **Seed novo** (seção 8).
10. **Documentação:** README, RN e casos de uso com o modelo desta versão.

---

## 11. Pontos de atenção (reportar, não decidir sozinho)

1. O **PDF do professor** ainda traz RN11, RN13, prazo variável, usuário registrando devolução e bibliotecário cadastrando livros; este documento muda tudo isso. Os artefatos do PDF (RN, casos de uso, Figma) precisam ser atualizados.
2. **Token em `localStorage`** é aceitável para o escopo da disciplina; documentar o risco de XSS.
3. **Todas as unidades do seed têm bibliotecário** (Tijuca inclusive; padrão da 8.1). **Decidido:** a **RN22** continua valendo (destino só se ativo e com bibliotecário ativo) para qualquer biblioteca futura ou desativada.
4. **Fila e `EMPRESTADO_RESERVADO`:** a marca vale para todos os emprestados do título na biblioteca (4.2). **Decidido:** mantém-se a marca em todos os emprestados (mais simples; a invariante é função só de a fila ter ou não pendentes).
5. **Notificações** são in-app (sem e-mail/push).
6. O histórico de circulação **do bibliotecário** não está previsto (só Admin consulta).
7. **Limitação conhecida (cenário raro, não tratado):** se uma reserva volta a `PENDENTE` (exemplar `RESERVADO` marcado `INDISPONIVEL`, ou chegada danificada) e a mesma biblioteca já tem outro exemplar `DISPONIVEL` do título, esse exemplar não é promovido a `RESERVADO`, porque a tabela 4.3 não permite `DISPONIVEL → RESERVADO` como estado estável.

---

## 12. Definition of Done

- [ ] Login/cadastro JWT funcionando; autorização por perfil **no servidor** (matriz 2.3); AU-01…AU-09 em PASS.
- [ ] **Usuário:** U1–U9 e U-01…U-16 em PASS.
- [ ] **Bibliotecário:** B1–B8 e B-01…B-20 em PASS.
- [ ] **Admin:** A1–A11 e A-01…A-20 em PASS.
- [ ] Máquina de estados 4.3 completa, com invariante 4.2 e ES-01…ES-15 em PASS.
- [ ] RN11, `PERDIDO`, prazo variável e **código de barras** erradicados (9.6).
- [ ] RN15 e avulsa em lote funcionando e testadas.
- [ ] Todas as transações com **modal de confirmação**; listagens grandes com **tabela paginada**; relatórios **sem gráficos**.
- [ ] Nenhum menu leva a "Em construção"; sino de notificações em todos os perfis.
- [ ] Seed novo (8) carregando sem erro, no padrão de usuários da 8.1; sem trigger/listener; aplicação sobe limpa.
- [ ] Roteiro de gravação 9.0 executa de ponta a ponta, na ordem, sem erro e em ≈ 7 min.
- [ ] README, RN e casos de uso atualizados.

---

## 13. Referências no repositório

- **Backend** `backend/circula-book/src/main/java/com/circulabook/`: `controller/`, `service/` (`EmprestimoService`, `ReservaService`, `TransferenciaService`, `FilaEsperaService`, `ExemplarService`, `LivroService`, `HistoricoService`), `model/`, `repository/`, `dto/`, `config/`; `resources/data.sql`, `application.properties`.
- **Frontend** `frontend/src/`: `App.tsx` (rotas), `components/` (`Header`, `Layout`, `ui`, `ModalConfirmacao`, `TabelaPaginada`), `pages/`, `api/client.ts`, `types.ts`.
- Convenções existentes: erros do back como `RuntimeException("texto em português")` → `badRequest().body(msg)`; o front exibe `e.message`; componentes `ModalConfirmacao` (props `aberto, titulo, children, confirmarRotulo, cancelarRotulo, tom, carregando, onConfirmar, onCancelar` + `ResumoModal`) e `TabelaPaginada<T>` (props `dados, colunas, chave, porPagina, classeLinha, textoVazio`).
