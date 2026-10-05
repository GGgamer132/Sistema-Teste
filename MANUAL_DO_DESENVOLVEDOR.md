# Manual do Desenvolvedor — Circula Book

Guia para instalar o ambiente, entender a arquitetura, manter o código e publicar o sistema. Descreve o sistema como ele está no código. Para a intenção funcional completa, consulte `CONTEXTO_SISTEMA_CIRCULA_BOOK_v2.md`.

## Sumário

1. [Visão geral e arquitetura](#1-visão-geral-e-arquitetura)
2. [Pré-requisitos](#2-pré-requisitos)
3. [Instalação do ambiente de desenvolvimento](#3-instalação-do-ambiente-de-desenvolvimento)
4. [Deploy](#4-deploy)
5. [Segurança](#5-segurança)
6. [Modelo de dados](#6-modelo-de-dados)
7. [Referência da API REST](#7-referência-da-api-rest)
8. [Máquinas de estado e regras](#8-máquinas-de-estado-e-regras)
9. [Testes](#9-testes)
10. [Problemas comuns e contribuição](#10-problemas-comuns-e-contribuição)
11. [Diagramas recomendados](#11-diagramas-recomendados)

---

## 1. Visão geral e arquitetura

### 1.1 Componentes

| Componente | Tecnologia | Pasta | Porta em desenvolvimento |
| --- | --- | --- | --- |
| Frontend (SPA) | React 19 + TypeScript + Vite 8, Tailwind CSS 4, React Router 7 | `frontend/` | 5173 |
| Backend (API REST) | Java 17 + Spring Boot 4.1 (Web MVC, Data JPA, Security, OAuth2 Resource Server), Lombok | `backend/circula-book/` | 8080 |
| Banco de dados | PostgreSQL | — | 5432 |

O navegador carrega a SPA do Vite; todas as chamadas vão para `/api/...`, que o Vite repassa ao backend (proxy em `frontend/vite.config.ts`). O backend fala com o PostgreSQL via JPA/Hibernate.

### 1.2 Padrão Blackboard

O banco é o "quadro" compartilhado. A coluna `exemplar.status` é o estado central da rede. Os serviços do pacote `service/` são os "especialistas": cada um lê o quadro, aplica sua regra e escreve o novo estado. O especialista da fila (`FilaEsperaService`) é chamado de forma **síncrona, na mesma transação**, sempre que um exemplar fica livre (devolução, chegada de transferência, expiração/cancelamento de reserva, cadastro ou reativação de exemplar). Não existem triggers, `LISTEN` ou `NOTIFY` no banco.

### 1.3 Pacotes do backend (`com.circulabook`)

| Pacote | Conteúdo |
| --- | --- |
| `config/` | `SecurityConfig` (regras de acesso, JWT), `CorsConfig`, `Ator` (perfil, id e biblioteca extraídos do token) |
| `controller/` | 14 controllers REST sob `/api/...` |
| `service/` | Regras de negócio e transições de estado |
| `repository/` | Spring Data JPA (consultas derivadas do nome do método) |
| `model/` | Entidades JPA (Lombok) e `StatusExemplar` |
| `dto/` | Corpos de requisição e respostas |

### 1.4 Classes-chave

| Classe | Papel |
| --- | --- |
| `model/StatusExemplar` | Constantes dos status do exemplar e a única tabela de transições válidas (`transicaoValida`) |
| `service/EstadoExemplarService` | Rotina única de mudança de status (`mudarStatus`): valida a transição, salva e registra no histórico. Também mantém a marca de fila (`sincronizarMarcaDeFila`) |
| `service/FilaEsperaService` | Especialista da fila: `liberar` (exemplar livre vai ao 1º da fila ou fica disponível), `liberarParaRetirada` (reserva pronta por 3 dias), `despachar` (exemplar segue em trânsito), `expirarVencidas` (agendada a cada 60 s) |
| `service/RegrasTransferenciaService` | Capacidade da origem (`capacidade`, `motivoBloqueioOrigem`) e destino válido (`motivoBloqueioDestino`) |
| `service/TransferenciaService` | Pedidos, aprovação, rejeição, avulsa em lote, chegada |
| `service/ReservaService` | Criação e cancelamento de reservas, destinos possíveis, painel de reservas do bibliotecário |
| `service/EmprestimoService` | Empréstimo, devolução, bloqueio por atraso, situação do usuário no balcão |
| `service/ExemplarService` | Cadastro de exemplares, baixa e reativação |
| `service/NotificacaoService` | Criação das notificações por evento e rotina de vencimento/atraso (agendada a cada 60 s) |
| `service/HistoricoService` | Registro de cada evento do exemplar com o responsável (usuário do token ou "Sistema") e consultas |
| `service/AuthService` | Login, autocadastro e emissão do JWT |
| `service/PainelService`, `RelatorioService` | Dashboard e relatórios do Admin |
| `service/ContaService` | Dados do próprio usuário (reservas, empréstimos, histórico) |
| `service/DemandaService` | Pedidos de aquisição |
| `service/RedeService` | Bibliotecas e bibliotecários |
| `service/CatalogoService`, `LivroService` | Livros, categorias, busca e ficha do título |

### 1.5 Pastas do frontend (`frontend/src`)

| Caminho | Conteúdo |
| --- | --- |
| `App.tsx` | Todas as rotas, agrupadas por perfil com `RotaProtegida` |
| `api/client.ts` | Cliente HTTP único (`api.get/post/put/patch`), `qs()` para querystring, formatação de datas, anexa o token `Bearer` e trata 401 |
| `auth/` | `AuthContext` (sessão em `localStorage`), `RotaProtegida`, `contexto.ts` (`useAuth`, `useUsuarioLogado`) |
| `components/` | `Layout`, `Header` (menu por perfil, sair), `Sino` (notificações), `TabelaPaginada`, `ModalConfirmacao`, `ui.tsx` (primitivas: botões, badges, campos, avisos) |
| `pages/` | Uma página por tela |
| `types.ts` | Tipos que espelham o JSON do backend |
| `e2e/` (fora de `src`) | Testes Playwright |

### 1.6 Convenções

- Status (exemplar, empréstimo, reserva, transferência, demanda) são `String` comparadas por literal, não enums.
- Mudança de status de exemplar só por `EstadoExemplarService.mudarStatus`.
- Erros de regra: o serviço lança `RuntimeException("mensagem em português")` e o controller responde `ResponseEntity.badRequest().body(msg)` em texto simples; o frontend mostra a mensagem como está.
- `401` = sem sessão ou token inválido; `403` = perfil sem permissão (texto simples, gerado no `SecurityConfig`).
- O ator vem sempre do token (`Ator.de(jwt)`), nunca de parâmetros.
- Toda ação que altera dados passa por `ModalConfirmacao`; listas grandes usam `TabelaPaginada`.
- Código, mensagens e telas em português do Brasil.

---

## 2. Pré-requisitos

| Ferramenta | Versão | Onde está definido |
| --- | --- | --- |
| Java (JDK) | 17 ou superior | `pom.xml` (`java.version` = 17) |
| Maven | 3.6.3 ou superior (não há Maven Wrapper) | — |
| Spring Boot | 4.1.1 (vem do Maven) | `pom.xml` (parent) |
| Node.js | 20.19+ ou 22.12+ | `engines` do Vite 8 |
| npm | o que acompanha o Node | — |
| PostgreSQL | 15 ou superior | — |
| Git | qualquer versão recente | — |

---

## 3. Instalação do ambiente de desenvolvimento

### 3.1 Clonar

```
git clone https://github.com/marceloareas/CirculaBook.git
cd CirculaBook
```

### 3.2 Banco de dados

Com o `psql` (como superusuário `postgres`):

```sql
CREATE USER circula_user WITH PASSWORD 'circula_senha_123';
CREATE DATABASE circula_book_db OWNER circula_user;
```

Pelo pgAdmin: em *Login/Group Roles*, crie o usuário `circula_user` com a senha `circula_senha_123` e a permissão *Can login*; em *Databases*, crie `circula_book_db` com *Owner* = `circula_user`.

O usuário precisa ser dono do banco: o backend cria e apaga as tabelas a cada início, e os testes criam o schema `seed_teste`.

### 3.3 Configuração

Arquivo: `backend/circula-book/src/main/resources/application.properties`.

| Propriedade | Valor padrão | Variável de ambiente |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/circula_book_db` | `DB_URL` |
| `spring.datasource.username` | `circula_user` | `DB_USER` |
| `spring.datasource.password` | `circula_senha_123` | `DB_PASSWORD` |
| `circulabook.jwt.secret` | segredo de desenvolvimento | `JWT_SECRET` (mínimo de 32 caracteres) |
| `circulabook.jwt.expiracao-horas` | `8` | — |
| `spring.jpa.hibernate.ddl-auto` | `create` | — |
| `spring.sql.init.mode` / `encoding` | `always` / `UTF-8` | — |
| `server.port` | `8080` | `SERVER_PORT` (convenção do Spring Boot) |

Os valores padrão servem apenas para desenvolvimento. Em PowerShell, defina variáveis assim: `$env:JWT_SECRET="..."`; em Bash: `export JWT_SECRET=...`.

**CORS e proxy:** em desenvolvimento o navegador fala só com o Vite (`localhost:5173`), que repassa `/api` para `http://localhost:8080` (`frontend/vite.config.ts`). O `CorsConfig` libera a origem `http://localhost:5173` com os headers `Authorization` e `Content-Type` para `/api/**`.

### 3.4 Rodar

```
cd backend/circula-book
mvn spring-boot:run          # http://localhost:8080

cd frontend
npm install
npm run dev                  # http://localhost:5173
```

### 3.5 Dados de demonstração

Com `ddl-auto=create`, o Hibernate apaga e recria todas as tabelas a cada início, e o `data.sql` é executado em seguida (`spring.jpa.defer-datasource-initialization=true`). **Tudo o que for feito pela interface se perde ao reiniciar.** O seed traz 3 bibliotecas (a Tijuca sem acervo), 20 livros, 50 exemplares, empréstimos, reservas, transferências, demandas, histórico e notificações.

Senha de todos: `senha123`.

| Perfil | E-mail |
| --- | --- |
| ADMIN | `admin@circulabook.com` |
| BIBLIOTECARIO (Biblioteca Central) | `bibliotecariocentral@circulabook.com` |
| BIBLIOTECARIO (Biblioteca Comunitária de Vila Isabel) | `bibliotecariovilaisabel@circulabook.com` |
| BIBLIOTECARIO (Biblioteca Popular da Tijuca) | `bibliotecariotijuca@circulabook.com` |
| COMUM (sem histórico) | `usuario1@circulabook.com` |
| COMUM (3/3 empréstimos, um atrasado) | `usuario2@circulabook.com` |
| COMUM (com reservas) | `usuario3@circulabook.com` |

### 3.6 Scripts do frontend (`frontend/package.json`)

| Script | O que faz |
| --- | --- |
| `npm run dev` | Servidor de desenvolvimento do Vite |
| `npm run build` | Verificação de tipos (`tsc -b`) e build de produção em `dist/` |
| `npm run lint` | ESLint |
| `npm run preview` | Serve o `dist/` localmente |
| `npm run test:e2e` | Todas as specs Playwright, cada uma num backend recém-reiniciado |
| `npm run test:e2e:demo` | Só o roteiro de gravação (`e2e/demo-gravacao.spec.ts`) |
| `npm run backend:reiniciar` | Para o processo na porta 8080 e sobe o backend de novo (seed limpo) |

---

## 4. Deploy

Não há Docker nem pipeline no repositório. O procedimento abaixo foi executado de verdade nesta versão: o `.jar` subiu e respondeu ao login, e o `dist/` servido com proxy respondeu à página, a uma rota da SPA e à API.

### 4.1 Backend

```
cd backend/circula-book
mvn -DskipTests package
```

Gera `target/circula-book-0.0.1-SNAPSHOT.jar`. Para executar (Bash):

```
export JWT_SECRET="um-segredo-forte-com-pelo-menos-32-caracteres"
export DB_URL="jdbc:postgresql://servidor:5432/circula_book_db"
export DB_USER="circula_user"
export DB_PASSWORD="senha-do-banco"
java -jar target/circula-book-0.0.1-SNAPSHOT.jar
```

Em PowerShell, use `$env:JWT_SECRET="..."` etc. antes do `java -jar`. Se `JWT_SECRET` tiver menos de 32 caracteres, a aplicação não sobe.

### 4.2 Frontend

```
cd frontend
npm install
npm run build
```

O resultado fica em `frontend/dist/`. O frontend chama sempre `/api/...` no mesmo endereço em que foi carregado, então o servidor web precisa (1) servir os arquivos do `dist/`, (2) devolver o `index.html` para qualquer rota da SPA e (3) repassar `/api` ao backend. Para conferir localmente, `npm run preview` serve o `dist/` na porta 4173 já com o proxy de `/api` do `vite.config.ts`.

**Sugestão de configuração (Nginx, exemplo não versionado no repositório):**

```nginx
server {
    listen 80;
    server_name circulabook.exemplo.org;
    root /var/www/circulabook/dist;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri /index.html;
    }
}
```

Servindo frontend e API pela mesma origem, o navegador não faz requisições entre origens e o CORS não entra em jogo.

### 4.3 O que muda para produção

| Item | Situação atual | Para produção |
| --- | --- | --- |
| `spring.jpa.hibernate.ddl-auto` | `create` (apaga tudo a cada início) | `validate` ou `none`, com o schema criado uma vez (ou uma ferramenta de migração) |
| Seed de demonstração | `spring.sql.init.mode=always` roda o `data.sql` | `spring.sql.init.mode=never`; não usar as contas de demonstração |
| Segredo do JWT | padrão de desenvolvimento | `JWT_SECRET` forte, só no ambiente |
| Credenciais do banco | padrão de desenvolvimento | `DB_URL`, `DB_USER`, `DB_PASSWORD` |
| HTTPS | não configurado | terminar TLS no servidor web (o token trafega no header `Authorization`) |
| CORS | `http://localhost:5173` fixo em `CorsConfig` | ajustar a origem em `CorsConfig` se o frontend estiver em outra origem |
| Token no navegador | `localStorage` | risco de XSS conhecido e aceito no escopo da disciplina |

### 4.4 Docker

Evolução prevista. Ainda não existem `Dockerfile` nem `docker-compose` no repositório.

---

## 5. Segurança

### 5.1 Fluxo do JWT

1. `POST /api/auth/login` com `{email, senha}`. O `AuthService` confere a senha com BCrypt. Credencial errada: `401` "E-mail ou senha inválidos". Usuário inativo: `403`.
2. O servidor devolve `{token, expiraEm, usuario}`. O token é HS256, emissor `circula-book`, `sub` = id do usuário, claims `perfil` e `bibliotecaId` (bibliotecários), validade de 8 horas.
3. O frontend guarda a sessão em `localStorage` e envia `Authorization: Bearer <token>` em toda chamada (`api/client.ts`).
4. O Spring Security (OAuth2 Resource Server) valida assinatura e validade. O `jwtDecoder` também recusa o token se o usuário não existir mais ou estiver inativo, o que derruba a sessão na hora.
5. A claim `perfil` vira a role `ROLE_COMUM`, `ROLE_BIBLIOTECARIO` ou `ROLE_ADMIN`.
6. Resposta `401` no frontend: a sessão é limpa e o usuário volta a `/login`.

Senhas são guardadas com BCrypt (`senhaHash`, nunca serializado: `@JsonIgnore`). O autocadastro (`POST /api/auth/cadastro`) sempre cria `COMUM`, ignorando qualquer `tipo` enviado.

### 5.2 Matriz de autorização (como está no `SecurityConfig`)

| Recurso | COMUM | BIBLIOTECARIO | ADMIN |
| --- | --- | --- | --- |
| `POST /api/auth/login`, `/api/auth/cadastro` | público | público | público |
| `GET` de livros, bibliotecas, categorias, exemplares | sim | sim | sim |
| `GET /api/bibliotecas/todas` | não | não | sim |
| Criar/editar livro, categoria, biblioteca | não | não | sim |
| Cadastrar exemplar, marcar indisponível, reativar | não | sim (só a própria biblioteca) | não |
| Criar/cancelar reserva, destinos, reservas próprias | sim | não | não |
| Painel de reservas da biblioteca | não | sim | não |
| `GET /api/reservas`, `POST /api/reservas/expirar-vencidas` | não | não | sim |
| `/api/conta/**` (dados do próprio usuário) | sim | não | não |
| Registrar empréstimo e devolução | não | sim (só a própria biblioteca) | não |
| `GET /api/emprestimos/ativos` | não | sim (recortado) | sim |
| `GET /api/emprestimos`, `/usuario/**` | os seus | da própria biblioteca | todos |
| Buscar usuários para o balcão | não | sim (só COMUM) | sim |
| Bibliotecários (`/api/usuarios/**`) | não | não | sim |
| Confirmar chegada, a receber, saindo | não | sim (só o destino confirma) | não |
| Decidir pedido, avulsa, acompanhamento | não | não | sim |
| `GET /api/transferencias` | as suas | não | todas |
| Histórico de circulação, dashboard, relatórios | não | não | sim |
| Registrar interesse, ver os seus | sim | não | não |
| Listar e alterar demandas | não | não | sim |
| Notificações | as suas | as suas | as suas |

Os recortes por dono e por biblioteca (por exemplo, bibliotecário de outra biblioteca recebendo `403` ao devolver) ficam nos controllers.

No frontend, `RotaProtegida` redireciona quem não tem sessão para `/login` e quem tem outro perfil para a tela inicial do próprio perfil (`/`, `/biblioteca` ou `/admin`).

---

## 6. Modelo de dados

Tabelas criadas pelo Hibernate a partir das entidades em `model/`. Todos os `id` são `IDENTITY`. Colunas em `snake_case`.

| Tabela | Colunas relevantes | Chaves estrangeiras | Restrições |
| --- | --- | --- | --- |
| `categoria` | `nome` (100), `descricao` | — | `nome` obrigatório |
| `biblioteca` | `nome`, `endereco`, `email`, `telefone` (20), `ativa`, `criada_em` | — | `nome` e `ativa` obrigatórios |
| `usuario` | `nome`, `email`, `senha_hash`, `tipo` (COMUM, BIBLIOTECARIO, ADMIN), `ativo`, `bloqueado_ate`, `criado_em` | `biblioteca_id` → `biblioteca` (só bibliotecários) | `email` único; `nome`, `email`, `senha_hash`, `tipo`, `ativo` obrigatórios |
| `livro` | `titulo`, `autor`, `isbn` (20), `editora`, `ano_publicacao`, `sinopse` | `categoria_id` → `categoria` | `isbn` único; `titulo` e `autor` obrigatórios |
| `exemplar` | `status`, `estado_conservacao` (NOVO, BOM, USADO, DANIFICADO), `adicionado_em` | `livro_id` → `livro`; `biblioteca_id` → `biblioteca` | `livro_id`, `biblioteca_id`, `status` obrigatórios |
| `emprestimo` | `data_emprestimo`, `data_prev_devolucao`, `data_devolucao`, `status` | `exemplar_id`, `usuario_id`, `biblioteca_id` | todas as FKs, `data_prev_devolucao` e `status` obrigatórios |
| `reserva` | `data_reserva`, `data_expiracao`, `status` | `livro_id`, `usuario_id`, `biblioteca_fila_id`, `biblioteca_destino_id` (obrigatórias); `exemplar_id` (opcional) | `data_expiracao`, `status` obrigatórios |
| `solicitacao_transferencia` | `status`, `data_solicitacao`, `data_conclusao`, `observacoes` | `livro_id`, `biblioteca_origem_id`, `biblioteca_destino_id`, `solicitante_id` (obrigatórias); `exemplar_id`, `aprovador_id`, `reserva_id` (opcionais) | `status` obrigatório |
| `historico_circulacao` | `evento` (50), `data_evento`, `observacoes`, `responsavel` (nome de quem fez a ação ou "Sistema") | `exemplar_id` (obrigatória), `usuario_id`, `biblioteca_id` | — |
| `demanda_aquisicao` | `titulo`, `autor`, `isbn`, `total_solicitacoes`, `status`, `criada_em`, `atualizada_em` | — | `titulo`, `total_solicitacoes`, `status` obrigatórios |
| `demanda_solicitante` | `criado_em` | `demanda_id`, `usuario_id` | único (`demanda_id`, `usuario_id`) |
| `notificacao` | `titulo` (150), `mensagem` (1000), `tipo` (40), `lida`, `criada_em`, `link` (200), `referencia_id` | `usuario_id` | índice (`usuario_id`, `lida`) |

Observações: `reserva.posicaoFila` é calculado (`@Transient`), não é coluna. `solicitacao_transferencia` com `status = APROVADA` e `exemplar_id` nulo significa "aprovada, aguardando exemplar". O exemplar em trânsito continua com `biblioteca_id` da origem até a chegada.

---

## 7. Referência da API REST

Prefixo `/api`. Respostas em JSON, exceto erros, que vêm em texto simples. "Autenticado" = qualquer perfil logado.

### 7.1 `AuthController` — `/api/auth`

| Método | Caminho | Perfil | Finalidade |
| --- | --- | --- | --- |
| POST | `/auth/login` | público | `{email, senha}` → `{token, expiraEm, usuario:{id, nome, email, perfil, bibliotecaId, bibliotecaNome}}` |
| POST | `/auth/cadastro` | público | `{nome, email, senha}`; cria COMUM e já devolve a sessão |
| GET | `/auth/me` | autenticado | Usuário do token |

### 7.2 `LivroController` — `/api/livros`

| Método | Caminho | Perfil | Finalidade |
| --- | --- | --- | --- |
| GET | `/livros/busca` | autenticado | Busca na rede; parâmetros opcionais `termo`, `autor`, `isbn`, `categoriaId`, `anoDe`, `anoAte` |
| GET | `/livros/{id}` | autenticado | Ficha com disponibilidade por biblioteca |
| GET | `/livros` | autenticado | Catálogo completo |
| POST | `/livros` | ADMIN | `{titulo, autor, isbn, editora, anoPublicacao, sinopse, categoriaId}` |
| PUT | `/livros/{id}` | ADMIN | Editar livro |

### 7.3 `CategoriaController` — `/api/categorias`

| Método | Caminho | Perfil | Finalidade |
| --- | --- | --- | --- |
| GET | `/categorias` | autenticado | Lista por nome |
| POST | `/categorias` | ADMIN | `{nome, descricao}` |
| PUT | `/categorias/{id}` | ADMIN | Editar |

### 7.4 `BibliotecaController` — `/api/bibliotecas`

| Método | Caminho | Perfil | Finalidade |
| --- | --- | --- | --- |
| GET | `/bibliotecas` | autenticado | Bibliotecas ativas |
| GET | `/bibliotecas/todas` | ADMIN | Inclusive inativas |
| GET | `/bibliotecas/{id}` | autenticado | Uma biblioteca |
| POST | `/bibliotecas` | ADMIN | `{nome, endereco, email, telefone}` |
| PUT | `/bibliotecas/{id}` | ADMIN | Editar |
| PATCH | `/bibliotecas/{id}/desativar`, `/{id}/ativar` | ADMIN | Ativar ou desativar |

### 7.5 `ExemplarController` — `/api/exemplares`

| Método | Caminho | Perfil | Finalidade |
| --- | --- | --- | --- |
| GET | `/exemplares`, `/exemplares/biblioteca/{id}`, `/exemplares/livro/{id}` | autenticado | Consultas |
| POST | `/exemplares` | BIBLIOTECARIO | `{livroId, conservacao (NOVO, BOM, USADO), quantidade (1 a 50)}`; cria na biblioteca do token e devolve a lista criada |
| PATCH | `/exemplares/{id}/indisponivel` | BIBLIOTECARIO | Baixa (só DISPONIVEL ou RESERVADO); corpo opcional `{motivo}` |
| PATCH | `/exemplares/{id}/reativar` | BIBLIOTECARIO | Reativa um INDISPONIVEL |

### 7.6 `ReservaController` — `/api/reservas`

| Método | Caminho | Perfil | Finalidade |
| --- | --- | --- | --- |
| POST | `/reservas` | COMUM | `{livroId, bibliotecaFilaId, bibliotecaDestinoId}` |
| PATCH | `/reservas/{id}/cancelar` | COMUM | Cancelar a própria reserva |
| GET | `/reservas/usuario/{id}` | COMUM | As próprias reservas (o id do caminho é ignorado) |
| GET | `/reservas/posicao/{livroId}?bibliotecaId=` | autenticado | Posição que o usuário ocuparia na fila |
| GET | `/reservas/destinos?livroId=&bibliotecaFilaId=` | COMUM | Bibliotecas de retirada possíveis, com o motivo das bloqueadas |
| GET | `/reservas/biblioteca` | BIBLIOTECARIO | Reservas prontas e filas da própria biblioteca |
| GET | `/reservas` | ADMIN | Todas |
| POST | `/reservas/expirar-vencidas` | ADMIN | Roda na hora a expiração (também agendada a cada 60 s) |

### 7.7 `ContaController` — `/api/conta` (COMUM)

| Método | Caminho | Finalidade |
| --- | --- | --- |
| GET | `/conta/reservas` | Reservas ativas com posição, prazo e etapa da transferência |
| GET | `/conta/emprestimos` | Empréstimos em aberto, total x/3 e bloqueio |
| GET | `/conta/historico` | Empréstimos devolvidos e reservas encerradas, com filtros `tipo`, `de`, `ate` e paginação |

### 7.8 `EmprestimoController` — `/api/emprestimos`

| Método | Caminho | Perfil | Finalidade |
| --- | --- | --- | --- |
| POST | `/emprestimos/registrar` | BIBLIOTECARIO | `{exemplarId, usuarioId}` (o usuário é o tomador); prazo fixo de 14 dias |
| POST | `/emprestimos/devolver` | BIBLIOTECARIO | `{emprestimoId, condicaoExemplar (BOM ou DANIFICADO)}`; devolve o empréstimo com o exemplar no novo status |
| GET | `/emprestimos/ativos` | BIBLIOTECARIO, ADMIN | Em aberto (bibliotecário vê só a sua biblioteca) |
| GET | `/emprestimos/situacao/{usuarioId}` | COMUM, BIBLIOTECARIO | Se o usuário está apto (limite e bloqueio) |
| GET | `/emprestimos`, `/emprestimos/usuario/{id}` | autenticado | Recortados por perfil |

### 7.9 `TransferenciaController` — `/api/transferencias`

| Método | Caminho | Perfil | Finalidade |
| --- | --- | --- | --- |
| GET | `/transferencias` | COMUM, ADMIN | Admin: todas; COMUM: as ligadas às próprias reservas |
| GET | `/transferencias/pedidos-pendentes` | ADMIN | Pedidos PENDENTE com exemplar retido ou não e a capacidade da origem |
| GET | `/transferencias/acompanhamento?status=&pagina=&tamanho=` | ADMIN | Lista paginada |
| GET | `/transferencias/destinos-avulsa` | ADMIN | Bibliotecas que podem receber, com motivo das bloqueadas |
| GET | `/transferencias/resumo` | ADMIN | Contadores |
| POST | `/transferencias/avulsa` | ADMIN | `{exemplarIds:[...], bibliotecaDestinoId, observacoes}`; tudo ou nada; criadas já `EM_TRANSITO` |
| PATCH | `/transferencias/{id}/aprovar` | ADMIN | Aprovar pedido |
| PATCH | `/transferencias/{id}/rejeitar?motivo=` | ADMIN | Rejeitar pedido |
| GET | `/transferencias/biblioteca/a-receber`, `/biblioteca/saindo` | BIBLIOTECARIO | Em trânsito para a / saindo da própria biblioteca |
| PATCH | `/transferencias/{id}/confirmar-chegada` | BIBLIOTECARIO do destino | Corpo opcional `{danificado: true, observacao}` |

### 7.10 Demais controllers

| Controller | Método e caminho | Perfil | Finalidade |
| --- | --- | --- | --- |
| `DemandaController` | `POST /demandas` | COMUM | `{titulo, autor}`: registra interesse |
| | `GET /demandas/minhas` | COMUM | Interesses do usuário |
| | `GET /demandas?status=&pagina=&tamanho=` | ADMIN | Mais pedidas primeiro |
| | `PATCH /demandas/{id}/status` | ADMIN | `{status}`: ABERTA → EM_ANALISE → APROVADA ou REJEITADA |
| `NotificacaoController` | `GET /notificacoes?pagina=&tamanho=` | autenticado | As do usuário, mais recentes primeiro |
| | `GET /notificacoes/nao-lidas/contagem` | autenticado | `{naoLidas}` |
| | `PATCH /notificacoes/{id}/lida`, `PATCH /notificacoes/marcar-todas-lidas` | autenticado | Marcar como lida |
| | `POST /notificacoes/verificar-vencimentos` | ADMIN | Roda na hora a rotina de vencimento/atraso |
| `HistoricoController` | `GET /historico?evento=&bibliotecaId=&exemplarId=&de=&ate=&ocultarFila=&pagina=&tamanho=` | ADMIN | Eventos recentes |
| | `GET /historico/eventos` | ADMIN | Eventos possíveis com rótulo |
| | `GET /historico/exemplar/{id}` | ADMIN | Linha do tempo do exemplar |
| `AdminController` | `GET /admin/dashboard` | ADMIN | Indicadores da rede |
| | `GET /admin/relatorios?de=&ate=` | ADMIN | Seis relatórios em tabelas |
| `UsuarioController` | `GET`/`POST /usuarios/bibliotecarios` | ADMIN | Listar e cadastrar `{nome, email, senha, bibliotecaId}` |
| | `PATCH /usuarios/bibliotecarios/{id}/desativar`, `/ativar` | ADMIN | Ativar ou desativar |
| | `GET /usuarios/busca?termo=`, `GET /usuarios/tipo/{tipo}` | BIBLIOTECARIO, ADMIN | Busca do balcão (bibliotecário vê só COMUM) |
| | `GET /usuarios`, `GET /usuarios/{id}` | ADMIN | Consultas |

---

## 8. Máquinas de estado e regras

### 8.1 Exemplar

Estados: `DISPONIVEL`, `EMPRESTADO`, `EMPRESTADO_RESERVADO` (emprestado e há fila do título na biblioteca), `RESERVADO` (separado para alguém), `EM_TRANSFERENCIA`, `INDISPONIVEL`. Transições permitidas (`StatusExemplar`); qualquer outra é recusada por `mudarStatus`:

| De | Para | Quando |
| --- | --- | --- |
| (novo) | `DISPONIVEL` ou `RESERVADO` | Cadastro; `RESERVADO` se a biblioteca tem fila do título |
| `DISPONIVEL` | `EMPRESTADO` | Empréstimo |
| `DISPONIVEL` | `EM_TRANSFERENCIA` | Transferência avulsa |
| `DISPONIVEL` | `INDISPONIVEL` | Baixa |
| `EMPRESTADO` | `DISPONIVEL` | Devolução em bom estado sem fila |
| `EMPRESTADO` | `EMPRESTADO_RESERVADO` | Alguém entrou na fila |
| `EMPRESTADO` ou `EMPRESTADO_RESERVADO` | `INDISPONIVEL` | Devolução danificada (não promove a fila) |
| `EMPRESTADO_RESERVADO` | `RESERVADO` | Devolução em bom estado: separado para o 1º da fila |
| `EMPRESTADO_RESERVADO` | `EMPRESTADO` | A fila esvaziou |
| `RESERVADO` | `EMPRESTADO` ou `EMPRESTADO_RESERVADO` | Retirada pelo reservante (com ou sem mais fila) |
| `RESERVADO` | `DISPONIVEL` ou `RESERVADO` | Cancelamento/expiração: libera ou passa ao próximo da fila |
| `RESERVADO` | `EM_TRANSFERENCIA` | Pedido aprovado sai da origem |
| `RESERVADO` | `INDISPONIVEL` | Baixa; a reserva volta à fila na mesma posição |
| `EM_TRANSFERENCIA` | `RESERVADO` ou `DISPONIVEL` | Chegada com ou sem reserva esperando |
| `EM_TRANSFERENCIA` | `INDISPONIVEL` | Chegou danificado |
| `INDISPONIVEL` | `DISPONIVEL` ou `RESERVADO` | Reativação (com fila, atende o 1º) |

A devolução de um exemplar cujo pedido já está aprovado faz `EMPRESTADO_RESERVADO → RESERVADO → EM_TRANSFERENCIA` na mesma transação.

### 8.2 Invariante da fila

Para cada par (livro, biblioteca): se houver reserva `PENDENTE` com essa biblioteca de fila, todo exemplar emprestado do título lá é `EMPRESTADO_RESERVADO`; sem fila, é `EMPRESTADO`. Chame `EstadoExemplarService.sincronizarMarcaDeFila(livro, biblioteca)` após qualquer operação que mexa em fila ou empréstimo. Também não pode existir exemplar `RESERVADO` sem reserva associada.

### 8.3 Reserva

`PENDENTE` (na fila) → `DISPONIVEL` (pronta, 3 dias para retirar) → `RETIRADA`. Com transferência: `PENDENTE` → `AGUARDANDO_TRANSFERENCIA` (exemplar separado ou a caminho) → `DISPONIVEL` → `RETIRADA`. Saídas: `CANCELADA` (pelo usuário, enquanto ativa) e `EXPIRADA` (prazo de retirada vencido). Baixa do exemplar separado ou chegada danificada devolvem a reserva a `PENDENTE` sem perder a posição.

Regras de criação (`ReservaService.criar`): só COMUM ativo; a biblioteca da fila precisa ter o título e nenhum exemplar `DISPONIVEL`; nada de duas reservas ativas do mesmo título nem reservar título que já está com o usuário. Retirada em outra biblioteca exige destino sem o título, capacidade da origem e destino válido.

### 8.4 Empréstimo

Status gravados: `ATIVO` e `DEVOLVIDO`. O atraso é calculado pela data prevista (o seed também usa `ATRASADO`, que o código trata como em aberto). Prazo fixo de 14 dias. Limite de 3 em aberto, de títulos distintos. Devolução com atraso bloqueia novos empréstimos por 2 dias para cada dia de atraso (`usuario.bloqueado_ate`).

### 8.5 Transferência

`PENDENTE` (pedido sem decisão, com ou sem exemplar retido) → `APROVADA` (sem exemplar = "aguardando exemplar") → `EM_TRANSITO` → `CONCLUIDA`. Saídas: `REJEITADA` (a retirada volta para a biblioteca da fila) e `CANCELADA` (reserva cancelada antes da saída, ou capacidade da origem perdida no despacho). A avulsa nasce `APROVADA` e segue logo para `EM_TRANSITO`. Só o bibliotecário do destino confirma a chegada; ao chegar, o exemplar passa a pertencer ao destino.

### 8.6 Capacidade da origem e destino válido

- **Capacidade (`RegrasTransferenciaService.capacidade`):** `total` = exemplares do título na origem (qualquer status); `abertas` = transferências do título saindo dela em `PENDENTE`, `APROVADA` ou `EM_TRANSITO`. Uma nova só é aceita se `abertas + novas ≤ total − 1`. Verificada ao criar reserva com retirada em outra biblioteca, na aprovação, no despacho e na avulsa (em lote, por grupo origem × título, tudo ou nada).
- **Destino válido (`motivoBloqueioDestino`):** biblioteca ativa com ao menos um bibliotecário ativo.

---

## 9. Testes

### 9.1 Backend

```
cd backend/circula-book
mvn test
mvn test -Dtest=RoteirosSeedTest
```

| Suíte | Banco | O que cobre |
| --- | --- | --- |
| `MaquinaEstadosExemplarTest` | H2 em memória | Todas as transições do exemplar, inválidas recusadas, invariante da fila |
| `controller/*Test` (`ReservaFilaComumTest`, `TransferenciasTest`, `NotificacoesTest`, `CadastroPermissoesTest`, `DemandasTest`, `HistoricoCirculacaoTest`, `PainelAdminTest`, `SessaoUsuarioInativoTest`) | H2 em memória | API real com JWT; cada teste monta os próprios dados (`ApoioApiTest`) |
| `SeedDemonstracaoTest` | PostgreSQL local, schema `seed_teste` | O `data.sql` real: usuários, estados dos exemplares, invariantes, capacidade, contagens do roteiro |
| `RoteirosSeedTest` | PostgreSQL local, schema `seed_teste` | Roteiros AU, ES, U, B e A da especificação pela API; o seed é recarregado antes de cada teste (`ApoioSeedTest`) |

As suítes sobre o seed usam o mesmo banco do desenvolvimento, mas num schema separado; não afetam o backend em execução. Exigem o PostgreSQL ligado.

### 9.2 Ponta a ponta (Playwright)

Instale o navegador uma vez: `npx playwright install chromium`.

```
cd frontend
npm run test:e2e                         # todas as specs; resumo PASS/FAIL por arquivo
npx playwright test e2e/comum.spec.ts    # uma spec
npm run test:e2e:demo                    # roteiro de gravação (cenas 1 a 7)
```

O `globalSetup` (`e2e/global-setup.mjs`) reinicia o backend antes de cada execução, então cada spec parte do seed limpo; para pular isso, use `E2E_SEM_REINICIO=1`. O Vite sobe sozinho. Com `DEMO_LENTA=1`, o roteiro abre o navegador em câmera lenta. Para deixar o backend com dados limpos sem rodar testes: `npm run backend:reiniciar` (log em `backend/circula-book/target/e2e-backend.log`).

| Spec | Cobre |
| --- | --- |
| `auth.spec.ts` | Login dos 7 usuários, cadastro, rotas por perfil, sessão e token |
| `comum.spec.ts` | Busca, ficha, fila, retirada em outra biblioteca, cancelamentos, meus empréstimos e histórico |
| `transferencias.spec.ts` | Decisão do Admin, avulsa em lote, ciclo completo e chegada danificada |
| `permissoes.spec.ts` | Cadastros de cada perfil, acervo, bibliotecas e bibliotecários |
| `notificacoes.spec.ts` | Sino nos três perfis e avisos dos eventos |
| `admin.spec.ts` | Demandas, histórico, dashboard e relatórios |
| `estados.spec.ts`, `limpeza.spec.ts` | Devolução danificada; ausência de termos removidos |
| `demo-gravacao.spec.ts` | Roteiro do vídeo de demonstração, com tempo por cena |

---

## 10. Problemas comuns e contribuição

| Problema | Causa provável | Solução |
| --- | --- | --- |
| `Port 8080 was already in use` | Outro backend rodando | Pare o processo ou use `npm run backend:reiniciar` (para o que estiver na 8080 e sobe de novo) |
| `Connection refused` / `password authentication failed` ao subir | PostgreSQL desligado ou usuário/banco não criados | Ligue o serviço e refaça a seção 3.2; confira `DB_URL`, `DB_USER`, `DB_PASSWORD` |
| `JWT_SECRET precisa ter pelo menos 32 caracteres.` | Segredo curto | Use um valor maior em `JWT_SECRET` |
| Acentos trocados nos dados do seed | `data.sql` lido sem UTF-8 | Mantenha `spring.sql.init.encoding=UTF-8` e salve o arquivo em UTF-8 |
| Tela volta para o login sozinha | Token expirado (8 h) ou usuário desativado | Entrar de novo |
| `Acesso negado para o seu perfil.` | Chamada fora da matriz de autorização | Use o perfil certo (seção 5.2) |
| Dados criados sumiram | `ddl-auto=create` recria o banco a cada início | Comportamento esperado em desenvolvimento |
| Testes do seed falham com erro de conexão | PostgreSQL desligado | Ligue o banco; as suítes H2 não dependem dele |
| Frontend não carrega dados | Backend parado ou fora da porta 8080 | Suba o backend; o proxy do Vite aponta para `localhost:8080` |
| Specs Playwright falham por dados alterados | Backend reaproveitado sem reinício | Rode sem `E2E_SEM_REINICIO` ou use `npm run test:e2e` |

### Contribuição

- Crie uma branch a partir da mais atualizada, com prefixo pelo assunto (`feat/...`, `docs/...`).
- Commits pequenos, um por assunto, com mensagem em português.
- Antes de abrir o pull request: `mvn test`, `npm run build`, `npm run lint` e `npm run test:e2e`.
- Não altere as regras de negócio das seções 3 a 6 da especificação sem decisão do dono do projeto.
- Mudança de esquema: altere as entidades e o `data.sql` (não há ferramenta de migração).

---

## 11. Diagramas recomendados

Nenhum diagrama está desenhado neste repositório. Abaixo, a análise de quais valem a pena e o que cada um deve mostrar, para desenho numa ferramenta externa.

### 11.1 Resumo e ordem de inclusão

| Ordem | Diagrama | Manual | Prioridade |
| --- | --- | --- | --- |
| Dev 1 | Implantação | Desenvolvedor, seção 1 | Essencial |
| Dev 2 | Modelo lógico do banco | Desenvolvedor, seção 6 | Essencial |
| Dev 3 | Máquina de estados do exemplar | Desenvolvedor, seção 8.1 | Essencial |
| Dev 4 | Estados da reserva, da transferência e do empréstimo | Desenvolvedor, seções 8.3 a 8.5 | Desejável |
| Dev 5 | Sequência do login com JWT | Desenvolvedor, seção 5.1 | Desejável |
| Dev 6 | Sequência do ciclo reserva → aprovação → devolução → chegada → retirada | Desenvolvedor, seção 8.5 | Essencial |
| Dev 7 | Atividades da devolução | Desenvolvedor, seção 8.4 | Desejável |
| Dev 8 | Atividades da avulsa em lote | Desenvolvedor, seção 8.6 | Desejável |
| Usuário 1 | Casos de uso por perfil | Usuário, seção 2 | Essencial |
| Usuário 2 | Ciclo da reserva com transferência (versão simplificada do Dev 6) | Usuário, seção 4.1 | Desejável |

Descartados: um diagrama de classes completo (as tabelas da seção 6 e a lista de classes-chave já cumprem o papel, e ele envelheceria a cada mudança) e um diagrama de componentes do frontend (a estrutura de pastas é plana e está na seção 1.5).

### 11.2 Diagrama de implantação

- **Objetivo:** mostrar onde cada parte roda e por onde passa cada requisição.
- **Nós:** Navegador do usuário (SPA React); Servidor web (em desenvolvimento, o Vite na porta 5173; em produção, servidor web servindo `dist/`); Servidor de aplicação com JVM 17 rodando `circula-book-0.0.1-SNAPSHOT.jar` na porta 8080; Servidor PostgreSQL na 5432 com o banco `circula_book_db`.
- **Ligações:** Navegador → Servidor web (HTTP, arquivos estáticos e `/api`); Servidor web → Backend (proxy de `/api`, HTTP); Backend → PostgreSQL (JDBC). Anotar no navegador "token JWT em localStorage, header Authorization".
- **Notas a incluir:** variáveis `JWT_SECRET`, `DB_URL`, `DB_USER`, `DB_PASSWORD`; rotinas agendadas no backend (expiração de reservas e avisos de vencimento a cada 60 s); Docker como evolução prevista (tracejado).
- **Base:** `frontend/vite.config.ts`, `application.properties`, `CorsConfig`, seção 4 deste manual.

### 11.3 Modelo lógico do banco

- **Objetivo:** referência das tabelas e relacionamentos.
- **Entidades e colunas-chave:** as 12 tabelas da seção 6 com PK `id` e as FKs listadas.
- **Relacionamentos e cardinalidades:**
  - `categoria` 1 — 0..N `livro`
  - `biblioteca` 1 — 0..N `usuario` (só bibliotecários têm biblioteca; demais com FK nula)
  - `livro` 1 — 0..N `exemplar`; `biblioteca` 1 — 0..N `exemplar`
  - `exemplar` 1 — 0..N `emprestimo`; `usuario` 1 — 0..N `emprestimo`; `biblioteca` 1 — 0..N `emprestimo`
  - `livro` 1 — 0..N `reserva`; `usuario` 1 — 0..N `reserva`; `biblioteca` 1 — 0..N `reserva` como fila e 1 — 0..N como destino; `exemplar` 0..1 — 0..N `reserva`
  - `livro` 1 — 0..N `solicitacao_transferencia`; `biblioteca` 1 — 0..N como origem e como destino; `usuario` 1 — 0..N como solicitante e 0..1 — 0..N como aprovador; `exemplar` 0..1 — 0..N; `reserva` 0..1 — 0..N
  - `exemplar` 1 — 0..N `historico_circulacao`; `usuario` 0..1 — 0..N; `biblioteca` 0..1 — 0..N
  - `demanda_aquisicao` 1 — 1..N `demanda_solicitante`; `usuario` 1 — 0..N `demanda_solicitante` (par único)
  - `usuario` 1 — 0..N `notificacao`
- **Destacar:** unicidade de `usuario.email`, `livro.isbn` e (`demanda_id`, `usuario_id`).
- **Base:** pacote `model/`.

### 11.4 Casos de uso por perfil

- **Objetivo:** visão do que cada perfil faz; entra no Manual do Usuário.
- **Atores:** Visitante, Usuário comum, Bibliotecário, Administrador; ator secundário "Sistema" (rotinas agendadas).
- **Casos:** Visitante: entrar, cadastrar-se. Usuário comum: buscar livros, ver ficha, entrar na fila (estende: retirar em outra biblioteca), cancelar reserva, ver minhas reservas, ver meus empréstimos, ver histórico, pedir livro, ver notificações. Bibliotecário: registrar empréstimo, registrar devolução (estende: aplicar bloqueio por atraso; inclui: promover fila), cadastrar exemplares, marcar indisponível, reativar, confirmar chegada, ver reservas aguardando retirada, ver notificações. Administrador: ver painel, decidir pedido de transferência, criar transferência avulsa em lote, manter catálogo, categorias, bibliotecas e bibliotecários, conduzir demandas, consultar histórico, ver relatórios, ver notificações. Sistema: expirar reservas vencidas, avisar vencimento e atraso.
- **Regras a anotar:** Admin não empresta, não devolve, não cadastra exemplar nem confirma chegada; bibliotecário só na própria biblioteca.
- **Base:** `App.tsx`, `Header.tsx`, `SecurityConfig`.

### 11.5 Máquina de estados do exemplar

- **Objetivo:** a regra mais importante do sistema; quem mexe em `service/` precisa dela.
- **Estados:** os 6 da seção 8.1, mais o estado inicial (cadastro).
- **Transições, na ordem:** exatamente as linhas da tabela 8.1, rotuladas com o gatilho (empréstimo, devolução bom/danificado, entrou/esvaziou fila, retirada, cancelamento/expiração, pedido aprovado, avulsa, chegada, chegou danificado, baixa, reativação).
- **Condições a mostrar como guardas:** "fila vazia" vs. "há fila" nas devoluções, retiradas, cancelamentos, chegadas e reativações; "pedido aprovado" na devolução que vai direto a `EM_TRANSFERENCIA`.
- **Nota:** a invariante da fila (8.2) e a observação de que `DISPONIVEL → RESERVADO` direto não existe.
- **Base:** `model/StatusExemplar`, `EstadoExemplarService`, `FilaEsperaService`.

### 11.6 Estados da reserva, da transferência e do empréstimo

- **Objetivo:** três máquinas pequenas lado a lado.
- **Reserva:** `PENDENTE` → `DISPONIVEL` (exemplar separado, sem transferência) ou → `AGUARDANDO_TRANSFERENCIA` (com transferência) → `DISPONIVEL` (chegou) → `RETIRADA` (empréstimo). De `PENDENTE`, `AGUARDANDO_TRANSFERENCIA` ou `DISPONIVEL` → `CANCELADA` (usuário). `DISPONIVEL` → `EXPIRADA` (3 dias). `AGUARDANDO_TRANSFERENCIA` ou `DISPONIVEL` → `PENDENTE` (baixa do exemplar ou chegada danificada, mantendo a posição).
- **Transferência:** `PENDENTE` → `APROVADA` (Admin) → `EM_TRANSITO` (exemplar sai) → `CONCLUIDA` (chegada). `PENDENTE` → `EM_TRANSITO` direto quando o exemplar já estava retido. `PENDENTE` → `REJEITADA`. `PENDENTE`/`APROVADA` → `CANCELADA` (reserva cancelada ou capacidade perdida no despacho). Avulsa: início → `APROVADA` → `EM_TRANSITO`.
- **Empréstimo:** `ATIVO` → `DEVOLVIDO`, com nota "atrasado = data prevista no passado".
- **Base:** `ReservaService`, `TransferenciaService`, `FilaEsperaService`, `EmprestimoService`.

### 11.7 Sequência do login com JWT

- **Participantes:** Usuário, Tela de login (`Login.tsx`), `AuthContext`, `api/client.ts`, `AuthController`, `AuthService`, `UsuarioRepository`, `SecurityConfig` (filtro do Resource Server).
- **Passos:** usuário envia e-mail e senha → `POST /api/auth/login` → `AuthService` busca o usuário e confere o BCrypt → [senha errada: 401] [inativo: 403] → gera JWT HS256 (sub, perfil, bibliotecaId, 8 h) → resposta com token e usuário → `AuthContext` grava em `localStorage` e redireciona pelo perfil. Segunda parte: chamada qualquer com `Authorization: Bearer` → filtro valida assinatura, validade e se o usuário está ativo → role a partir de `perfil` → regra do `SecurityConfig` → [sem permissão: 403] → controller. Ramificação final: resposta 401 → `api/client.ts` limpa a sessão e vai para `/login`.
- **Base:** `AuthService`, `SecurityConfig`, `frontend/src/auth/`, `api/client.ts`.

### 11.8 Sequência do ciclo reserva → aprovação → devolução com despacho → chegada → retirada

- **Objetivo:** o fluxo que cruza todos os perfis; exemplo do seed com O Hobbit (Usuário 3, fila na Central, retirada na Vila Isabel).
- **Participantes:** Usuário comum, Administrador, Bibliotecário da origem, Bibliotecário do destino, `ReservaService`, `TransferenciaService`, `EmprestimoService`, `FilaEsperaService`, `EstadoExemplarService`, `NotificacaoService`, banco.
- **Passos:** (1) usuário cria a reserva com retirada em outra biblioteca → validações (sem disponível na fila, destino sem o título, capacidade da origem, destino válido) → reserva `PENDENTE` + transferência `PENDENTE` sem exemplar → exemplares emprestados viram `EMPRESTADO_RESERVADO` → Admin notificado. (2) Admin aprova → revalida capacidade → `APROVADA` → usuário notificado. (3) Bibliotecário da origem registra a devolução em bom estado → `FilaEsperaService.liberar` → 1º da fila tem pedido aprovado → revalida capacidade [se falhar: pedido `CANCELADA`, reserva fica com retirada na origem, notifica] → exemplar `RESERVADO` → `EM_TRANSFERENCIA`, pedido `EM_TRANSITO`, reserva `AGUARDANDO_TRANSFERENCIA` → notifica usuário, destino e origem. (4) Bibliotecário do destino confirma a chegada → exemplar passa ao destino, `RESERVADO`, pedido `CONCLUIDA`, reserva `DISPONIVEL` por 3 dias → notifica usuário e bibliotecário. (5) Bibliotecário do destino registra o empréstimo → exemplar `EMPRESTADO`, reserva `RETIRADA`.
- **Ramificação alternativa a mostrar:** devolução antes da aprovação → exemplar fica `RESERVADO` retido e o Admin recebe aviso urgente; ao aprovar, vai direto a `EM_TRANSITO`; ao rejeitar, a reserva fica `DISPONIVEL` na origem.
- **Base:** `ReservaService.criar`, `TransferenciaService.aprovar/confirmarChegada`, `EmprestimoService.devolver`, `FilaEsperaService`.

### 11.9 Atividades da devolução

- **Início:** bibliotecário escolhe o empréstimo em aberto da própria biblioteca.
- **Decisões, na ordem:** (1) está atrasado? sim → calcular dias e bloquear por 2 × dias, notificar o usuário. (2) condição? Danificado → exemplar `INDISPONIVEL`, fim (sem promover fila). Bom → (3) há reserva `PENDENTE` do título nesta biblioteca? não → `DISPONIVEL`, fim. sim → separar para o 1º → (4) a reserva tem pedido de transferência? não → reserva `DISPONIVEL` por 3 dias, notificar. sim, `PENDENTE` → reter e avisar o Admin. sim, `APROVADA` → a origem ainda pode ceder? sim → despachar; não → cancelar pedido e retirada na origem. (5) Ressincronizar a marca de fila. Empréstimo `DEVOLVIDO` e eventos no histórico.
- **Base:** `EmprestimoService.devolver`, `FilaEsperaService`.

### 11.10 Atividades da transferência avulsa em lote

- **Início:** Admin seleciona exemplares disponíveis e um destino.
- **Passos:** validar que há seleção e destino → destino ativo e com bibliotecário ativo → cada exemplar `DISPONIVEL` e com origem diferente do destino → agrupar por (origem, título) → para cada grupo, `abertas + selecionados ≤ total − 1`? → se algum grupo ou exemplar falhar: juntar os motivos e recusar tudo ("Nenhuma transferência foi criada"), sem gravar nada → senão, para cada exemplar: criar transferência `APROVADA`, mudar para `EM_TRANSFERENCIA`, pedido `EM_TRANSITO`, notificar destino e origem.
- **Base:** `TransferenciaService.criarAvulsas`, `RegrasTransferenciaService`.
