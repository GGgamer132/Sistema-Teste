# Manual do Desenvolvedor — Circula Book (versão reduzida)

Esta versão contém só seis fluxos: login, busca de livros, detalhes do livro, reserva,
empréstimo e devolução, com dois perfis (COMUM e BIBLIOTECARIO).

## Sumário

1. [Visão geral e arquitetura](#1-visão-geral-e-arquitetura)
2. [Pré-requisitos](#2-pré-requisitos)
3. [Instalação do ambiente de desenvolvimento](#3-instalação-do-ambiente-de-desenvolvimento)
4. [Estrutura de pastas](#4-estrutura-de-pastas)
5. [Modelo de dados](#5-modelo-de-dados)
6. [Referência da API](#6-referência-da-api)
7. [Estados e regras](#7-estados-e-regras)
8. [Segurança](#8-segurança)
9. [Diagramas recomendados](#9-diagramas-recomendados)

---

## 1. Visão geral e arquitetura

| Componente | Tecnologia | Porta | Papel |
| --- | --- | --- | --- |
| Frontend | React 19 + TypeScript + Vite 8, Tailwind CSS 4, React Router 7 | 5173 | Telas; repassa `/api` ao backend pelo proxy do Vite |
| Backend | Java 17 + Spring Boot 4.1 (Web MVC, Data JPA, Security + OAuth2 Resource Server) | 8080 | API REST, regras de negócio, emissão e validação do JWT |
| Banco | PostgreSQL | 5432 | Tabelas recriadas a cada início e preenchidas pelo `data.sql` |

**Padrão Blackboard.** O banco é o "quadro" compartilhado e a coluna `exemplar.status` é o estado
central. Os services são especialistas que leem e escrevem esse estado:

- `EstadoExemplarService` é o único que muda `exemplar.status`, validando a transição na tabela de
  `StatusExemplar`, e mantém a marca de fila (`sincronizarMarcaDeFila`).
- `FilaEsperaService` é chamado de forma síncrona, na mesma transação, sempre que um exemplar fica
  livre (devolução ou expiração de reserva): sem fila o exemplar fica `DISPONIVEL`; com fila fica
  `RESERVADO` e a reserva do 1º passa a `DISPONIVEL` com 3 dias para retirada. Um `@Scheduled`
  (a cada minuto) expira as reservas prontas vencidas.
- `ReservaService` (entrar na fila), `EmprestimoService` (empréstimo, devolução, atraso e bloqueio) e
  `LivroService` (busca e disponibilidade por biblioteca).

Convenções: código, mensagens e telas em português; status são `String` comparadas por literal;
erros de regra voltam como `400` com texto puro, que o frontend mostra direto.

---

## 2. Pré-requisitos

| Ferramenta | Versão |
| --- | --- |
| Java (JDK) | 17 ou superior (`java.version` = 17 no `pom.xml`) |
| Maven | 3.6.3 ou superior (não há Maven Wrapper) |
| Spring Boot | 4.1.1 (parent do `pom.xml`) |
| Node.js | 20.19+ ou 22.12+ (exigência do Vite 8), com npm |
| PostgreSQL | 15 ou superior |

---

## 3. Instalação do ambiente de desenvolvimento

1. **Clonar** o repositório e entrar na branch desejada.
2. **Banco** (no `psql` como superusuário, ou pelo pgAdmin):

   ```sql
   CREATE USER circula_user WITH PASSWORD 'circula_senha_123';
   CREATE DATABASE circula_book_db OWNER circula_user;
   ```

   O usuário precisa ser dono do banco, pois o backend cria e apaga as tabelas a cada início.
3. **Configuração** em `backend/circula-book/src/main/resources/application.properties`:

   | Propriedade | Padrão | Variável de ambiente |
   | --- | --- | --- |
   | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/circula_book_db` | `DB_URL` |
   | `spring.datasource.username` | `circula_user` | `DB_USER` |
   | `spring.datasource.password` | `circula_senha_123` | `DB_PASSWORD` |
   | `circulabook.jwt.secret` | segredo só de desenvolvimento | `JWT_SECRET` (mínimo de 32 caracteres) |
   | `circulabook.jwt.expiracao-horas` | `8` | — |

   Para usar outro segredo: `$env:JWT_SECRET="..."` (PowerShell) ou `export JWT_SECRET=...` (Bash)
   antes de subir o backend.
4. **Backend:**

   ```
   cd backend/circula-book
   mvn spring-boot:run          # http://localhost:8080
   ```

   Com `spring.jpa.hibernate.ddl-auto=create`, o Hibernate recria as tabelas a cada início e, como
   `spring.jpa.defer-datasource-initialization=true` e `spring.sql.init.mode=always`, o `data.sql`
   é executado em seguida. Tudo o que foi criado em tempo de execução se perde ao reiniciar.
5. **Frontend:**

   ```
   cd frontend
   npm install
   npm run dev                  # http://localhost:5173 (proxy /api -> localhost:8080)
   npm run build                # checagem de tipos (tsc -b) + build
   npm run lint
   ```

O `CorsConfig` libera a origem `http://localhost:5173` para `/api/**`.

---

## 4. Estrutura de pastas

```
backend/circula-book/src/main/
  java/com/circulabook/
    config/       SecurityConfig (JWT e perfis), CorsConfig, Ator (quem chama, lido do token)
    controller/   Auth, Livro, Categoria, Reserva, Emprestimo, Exemplar, Usuario
    service/      AuthService, LivroService, ReservaService, EmprestimoService,
                  FilaEsperaService, EstadoExemplarService
    repository/   Spring Data JPA (consultas derivadas)
    model/        Entidades JPA (Lombok) e StatusExemplar
    dto/          Payloads de requisição e resposta
  resources/      application.properties, data.sql (seed)
frontend/src/
  api/client.ts   cliente HTTP único (token, 401 -> login, qs(), datas)
  auth/           AuthContext, RotaProtegida, TELA_INICIAL por perfil
  components/     Layout, Header (menu por perfil), TabelaPaginada, ModalConfirmacao, ui.tsx
  pages/          Login, BuscaLivros, ResultadosBusca, DetalhesLivro, ReservarLivro,
                  RegistrarEmprestimo, RegistrarDevolucao
  App.tsx         rotas agrupadas por perfil
  types.ts        espelho do JSON do backend
```

---

## 5. Modelo de dados

| Tabela | Colunas principais | Relacionamentos |
| --- | --- | --- |
| `categoria` | `id`, `nome`, `descricao` | 1 categoria → N livros |
| `livro` | `id`, `titulo`, `autor`, `isbn` (único), `editora`, `ano_publicacao`, `sinopse`, `categoria_id` | N livros → 1 categoria (opcional) |
| `biblioteca` | `id`, `nome`, `endereco`, `email`, `telefone`, `criada_em` | — |
| `usuario` | `id`, `nome`, `email` (único), `senha_hash` (BCrypt), `tipo` (`COMUM`/`BIBLIOTECARIO`), `biblioteca_id`, `bloqueado_ate`, `criado_em` | N bibliotecários → 1 biblioteca (nulo para COMUM) |
| `exemplar` | `id`, `livro_id`, `biblioteca_id`, `status`, `adicionado_em` | N exemplares → 1 livro; N → 1 biblioteca |
| `emprestimo` | `id`, `exemplar_id`, `usuario_id`, `biblioteca_id`, `data_emprestimo`, `data_prev_devolucao`, `data_devolucao`, `status` | N → 1 exemplar; N → 1 usuário; N → 1 biblioteca |
| `reserva` | `id`, `livro_id`, `usuario_id`, `biblioteca_fila_id`, `exemplar_id`, `data_reserva`, `data_expiracao`, `status` | N → 1 livro; N → 1 usuário; N → 1 biblioteca (fila e retirada); N → 0..1 exemplar (preenchido quando a reserva fica pronta) |

Não há Flyway: mudanças de schema são feitas nas entidades e no `data.sql`. O seed usa IDs
explícitos, datas relativas a `CURRENT_TIMESTAMP` e termina com `setval` das sequences.

---

## 6. Referência da API

Base `/api`. Todas as rotas, menos o login, exigem `Authorization: Bearer <token>`.
Qualquer rota fora desta tabela é negada (`401` sem token, `403` com token).

| Método e rota | Perfil | Descrição |
| --- | --- | --- |
| `POST /auth/login` | público | `{email, senha}` → `{token, expiraEm, usuario}`; `401` se inválido |
| `GET /auth/me` | autenticado | Dados da sessão do token |
| `GET /livros/busca?termo&autor&isbn&categoriaId&anoDe&anoAte` | COMUM | Busca com filtros opcionais |
| `GET /livros/{id}` | COMUM | Detalhes + `disponibilidade` por biblioteca |
| `GET /categorias` | COMUM | Categorias (filtro e atalhos da busca) |
| `GET /reservas/posicao/{livroId}?bibliotecaId` | COMUM | Posição que o usuário ocuparia na fila |
| `POST /reservas` | COMUM | `{livroId, bibliotecaId}`; o usuário vem do token; devolve a reserva com `posicaoFila` |
| `GET /usuarios/comuns` | BIBLIOTECARIO | Usuários COMUM, para localizar o leitor |
| `GET /exemplares/biblioteca/{id}` | BIBLIOTECARIO | Acervo da própria biblioteca (`403` para outra) |
| `GET /emprestimos/situacao/{usuarioId}` | BIBLIOTECARIO | `{apto, motivo, emprestimosAtivos, limite, bloqueado, bloqueadoAte}` |
| `POST /emprestimos/registrar` | BIBLIOTECARIO | `{exemplarId, usuarioId}`; só exemplar da própria biblioteca (`403`) |
| `GET /emprestimos/ativos` | BIBLIOTECARIO | Empréstimos em aberto da própria biblioteca |
| `POST /emprestimos/devolver` | BIBLIOTECARIO | `{emprestimoId}`; só da própria biblioteca (`403`) |

---

## 7. Estados e regras

### 7.1 Exemplar (`StatusExemplar`)

| De | Para | Quando |
| --- | --- | --- |
| `DISPONIVEL` | `EMPRESTADO` | Empréstimo |
| `EMPRESTADO` | `DISPONIVEL` | Devolução sem fila |
| `EMPRESTADO` | `EMPRESTADO_RESERVADO` | Alguém entrou na fila do título na biblioteca |
| `EMPRESTADO_RESERVADO` | `RESERVADO` | Devolução com fila: separado para o 1º |
| `EMPRESTADO_RESERVADO` | `EMPRESTADO` | A fila esvaziou |
| `RESERVADO` | `EMPRESTADO` / `EMPRESTADO_RESERVADO` | Retirada pelo reservante (sem / com fila restante) |
| `RESERVADO` | `DISPONIVEL` / `RESERVADO` | Reserva pronta expirou (fila vazia / passa ao próximo) |

**Invariante da fila:** para cada (livro, biblioteca), todo exemplar emprestado fica
`EMPRESTADO_RESERVADO` se e somente se há reserva `PENDENTE` naquela fila; é ressincronizada ao
fim de toda operação que mexe em fila ou empréstimo. Todo `RESERVADO` está ligado a uma reserva
`DISPONIVEL`.

### 7.2 Reserva

`PENDENTE` (na fila) → `DISPONIVEL` (exemplar separado, 3 dias para retirar) → `RETIRADA`
(virou empréstimo) ou `EXPIRADA` (prazo vencido). Regras ao criar: só COMUM; biblioteca precisa
ter o título e nenhum exemplar `DISPONIVEL`; sem reserva ativa do mesmo título; sem o título
emprestado. A retirada é sempre na biblioteca da fila.

### 7.3 Empréstimo

`ATIVO`/`ATRASADO` → `DEVOLVIDO`. Prazo fixo de 14 dias; máximo de 3 simultâneos; nunca dois
exemplares do mesmo título; usuário com `bloqueado_ate` futuro não pega livros; exemplar
`RESERVADO` só sai para o dono da reserva pronta. Na devolução, cada dia de atraso soma 2 dias
de bloqueio (somados a um bloqueio ainda vigente).

---

## 8. Segurança

- **JWT HS256** (Spring Security stateless + OAuth2 Resource Server). O login confere a senha com
  BCrypt e emite um token de 8 horas com `sub` = id do usuário, `perfil` e, para bibliotecários,
  `bibliotecaId`.
- O token só é aceito se a assinatura e a validade conferem e o usuário ainda existe na base.
- O claim `perfil` vira a role (`ROLE_COMUM`, `ROLE_BIBLIOTECARIO`), aplicada por rota no
  `SecurityConfig` (seção 6). O ator vem sempre do token (`Ator.de(jwt)`), nunca de parâmetros:
  a reserva usa o id do token e o bibliotecário fica restrito à sua biblioteca nos controllers.
- Respostas: `401` "Sessão inválida ou expirada. Faça login novamente." e `403` "Acesso negado
  para o seu perfil.". O frontend limpa a sessão e volta ao `/login` ao receber `401`.
- `JWT_SECRET` vem do ambiente; o valor padrão do `application.properties` é só para desenvolvimento.

---

## 9. Diagramas recomendados

Indicações do que desenhar (os diagramas não fazem parte deste repositório):

1. **Diagrama de implantação** — três nós: navegador com o frontend React (servido pelo Vite na
   porta 5173), backend Spring Boot (porta 8080) e PostgreSQL (porta 5432). Deve mostrar o proxy
   `/api` do Vite para o backend, o protocolo HTTP/JSON com JWT no header `Authorization` e a
   conexão JDBC do backend com o banco.
2. **Modelo lógico do banco** — as sete tabelas da seção 5 com chaves primárias, colunas-chave e
   chaves estrangeiras, e as cardinalidades: categoria 1–N livro; livro 1–N exemplar;
   biblioteca 1–N exemplar; biblioteca 1–N usuário (só bibliotecários, 0..1 do lado do usuário);
   usuário 1–N empréstimo; exemplar 1–N empréstimo; biblioteca 1–N empréstimo; usuário 1–N
   reserva; livro 1–N reserva; biblioteca 1–N reserva; exemplar 0..1–N reserva.
3. **Diagrama de sequência do ciclo reserva → devolução com fila → empréstimo** — participantes:
   Usuário, Bibliotecário, Frontend, `ReservaController`/`ReservaService`,
   `EmprestimoController`/`EmprestimoService`, `FilaEsperaService`, `EstadoExemplarService` e
   banco. Deve mostrar a reserva `PENDENTE` marcando os emprestados como `EMPRESTADO_RESERVADO`,
   a devolução chamando `FilaEsperaService.liberar` na mesma transação (exemplar `RESERVADO`,
   reserva `DISPONIVEL` com 3 dias) e o empréstimo ao reservante encerrando a reserva como
   `RETIRADA`.
