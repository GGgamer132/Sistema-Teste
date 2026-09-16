# Circula Book — código do frontend e backend

Este pacote contém as 8 telas do protótipo implementadas em React + Tailwind e
os endpoints correspondentes em Spring Boot, seguindo o mesmo padrão de camadas
do `sistema-estoque-blackboard`.

---

## 1. Onde colocar cada arquivo

Copie mantendo exatamente esta estrutura dentro do repositório `Sistema-Circula-Book`:

```
Sistema-Circula-Book/
├── backend/circula-book/
│   ├── pom.xml                                    ← SUBSTITUIR (ver aviso abaixo)
│   └── src/main/
│       ├── java/com/circulabook/
│       │   ├── config/CorsConfig.java             ← já existe, mantém igual
│       │   ├── model/          (10 arquivos)      ← NOVO
│       │   ├── repository/     (9 arquivos)       ← NOVO
│       │   ├── dto/            (8 arquivos)       ← NOVO
│       │   ├── service/        (9 arquivos)       ← NOVO
│       │   └── controller/     (9 arquivos)       ← NOVO
│       └── resources/
│           ├── application.properties             ← SUBSTITUIR
│           └── data.sql                           ← NOVO
│
└── frontend/
    ├── index.html                                 ← SUBSTITUIR (título/idioma)
    └── src/
        ├── main.tsx                               ← SUBSTITUIR
        ├── App.tsx                                ← SUBSTITUIR
        ├── index.css                              ← SUBSTITUIR
        ├── types.ts                               ← NOVO
        ├── api/client.ts                          ← NOVO
        ├── components/  (ui.tsx, Header.tsx, Layout.tsx)
        └── pages/       (8 telas)
```

Podem apagar `frontend/src/App.css` e `frontend/src/assets/` — eram do template
do Vite e não são mais usados.

> **Aviso sobre o `pom.xml`:** a única mudança real é que a dependência
> `org.postgresql` perdeu o `<scope>runtime</scope>`. O `PgNotificationListener`
> importa `org.postgresql.PGConnection` em tempo de compilação; com escopo
> `runtime` o projeto **não compila**.

---

## 2. O que cada camada faz

### Backend

| Camada | Responsabilidade |
|---|---|
| `model/` | Entidades JPA. O Hibernate cria as tabelas a partir delas. `Exemplar` é o "quadro" (blackboard) sobre o qual todos os especialistas atuam — equivale à tabela `Estoque` do sistema anterior. |
| `repository/` | Interfaces `JpaRepository`. A maioria só declara *query methods* (`findByLivroAndStatus`); a única query escrita à mão é a busca com filtros opcionais, em `LivroRepository`. |
| `dto/` | Objetos de entrada e saída das telas. Evitam expor a entidade inteira e carregam campos calculados (ex.: `situacao`, `disponiveis`). |
| `service/` | Onde moram as regras de negócio RN01–RN13. Nenhuma regra fica no controller. |
| `controller/` | Endpoints REST. Seguem o padrão do estoque: `try/catch RuntimeException` devolvendo `badRequest()` com a mensagem. |

Serviços principais:

- **`LivroService`** — busca na rede (RN09) e cálculo do badge de disponibilidade.
- **`ExemplarService`** — cadastro (RN10 valida o bibliotecário; RN11 deixa o exemplar único como `INDISPONIVEL`, e o libera quando chega um segundo).
- **`EmprestimoService`** — RN01 (limite de 3 e título repetido), RN02 (14 dias), RN12 (2 dias de bloqueio por dia de atraso) e a promoção do próximo da fila na devolução.
- **`ReservaService`** — RN03: só permite reservar quando não há exemplar livre.
- **`TransferenciaService`** — RN04 (só ADMIN aprova), RN05 (fluxo de status) e RN13 (usuário comum pode solicitar).

Trio da arquitetura Blackboard, espelhando o sistema de estoque:

| Estoque | Circula Book |
|---|---|
| Trigger no `UPDATE` de `estoque` quando cai abaixo do limiar | Trigger no `INSERT` de `reserva` com status `PENDENTE` |
| `PgNotificationListener` escuta `reposicao_necessaria` | `PgNotificationListener` escuta `reserva_sem_exemplar` |
| `EspecialistaReposicaoService` sugere transferência ou compra | `EspecialistaTransferenciaService` sugere transferência entre bibliotecas |

Ou seja: quando alguém entra na fila de espera e existe um exemplar livre em
outra unidade, o sistema **cria sozinho** uma solicitação pendente, que aparece
na Tela 8 para o Admin aprovar.

### Frontend

| Arquivo | Responsabilidade |
|---|---|
| `api/client.ts` | Único ponto que fala HTTP. Já trata o erro em texto puro que o backend devolve e tem helpers de data. |
| `types.ts` | Espelha os DTOs do Java. Se mudarem um DTO, mudem aqui também. |
| `components/ui.tsx` | Todos os componentes visuais do protótipo (Card, Badge, Callout, Botao, Campo...). Centralizar aqui evita repetir classes Tailwind em 8 telas. |
| `components/Header.tsx` | Barra azul. O menu muda conforme o perfil, como nas 3 variantes do Figma. |
| `App.tsx` | Roteador por estado (sem `react-router`, para não mexer no `package.json`). |
| `pages/` | Uma tela por arquivo, na mesma ordem do protótipo. |

**Trocador de perfil:** como ainda não existe login, o header tem um seletor
(Usuário / Bibliotecário / Admin) que troca o menu e a tela inicial. É o que
permite demonstrar as 8 telas sem autenticação. Os IDs estão fixos no topo das
páginas: `USUARIO_LOGADO = 6` (Ana Souza), `BIBLIOTECARIO_LOGADO = 2` (Carlos
Lima), `ADMIN_LOGADO = 1` (Roberto Dias). Quando o login entrar, é só trocar
essas constantes pelo usuário da sessão.

---

## 3. Mapa: tela → endpoint

| Tela | Endpoints usados |
|---|---|
| 1. Busca | `GET /api/categorias` |
| 2. Resultados | `GET /api/livros/busca?termo=&autor=&isbn=&categoriaId=&anoDe=&anoAte=` |
| 3. Detalhes | `GET /api/livros/{id}` |
| 4. Empréstimo | `GET /api/exemplares`, `GET /api/usuarios/tipo/COMUM`, `GET /api/emprestimos/situacao/{id}`, `POST /api/emprestimos/registrar` |
| 5. Reserva | `GET /api/livros/{id}`, `GET /api/bibliotecas`, `GET /api/reservas/posicao/{livroId}`, `POST /api/reservas` |
| 6. Devolução | `GET /api/emprestimos/ativos`, `POST /api/emprestimos/devolver` |
| 7. Cadastrar exemplar | `GET /api/livros`, `GET /api/categorias`, `GET /api/bibliotecas`, `POST /api/exemplares?bibliotecarioId=` |
| 8. Transferências | `GET /api/transferencias/{pendentes,historico,resumo}`, `PATCH /api/transferencias/{id}/{aprovar,rejeitar,confirmar-chegada}` |

---

## 4. Como rodar

### Passo 1 — Banco de dados

No pgAdmin, crie (se ainda não existirem):

- Login/Group Role: **`circula_user`** / senha **`circula_senha_123`**
- Database: **`circula_book_db`**, owner `circula_user`

Não precisa rodar SQL à mão: o Hibernate cria as tabelas e o `data.sql` insere
os dados de exemplo automaticamente.

### Passo 2 — Backend

```bash
cd backend/circula-book
./mvnw clean install
./mvnw spring-boot:run
```

No console devem aparecer:

```
[BLACKBOARD] Inicializando trigger de monitoramento de reservas...
[BLACKBOARD] Trigger criado! ...
[BLACKBOARD] Listener ativo — aguardando reservas pendentes...
```

Teste rápido: <http://localhost:8080/api/livros>

### Passo 3 — Frontend

Em **outro terminal**:

```bash
cd frontend
npm install
npm run dev
```

Acesse <http://localhost:5173>. O `vite.config.ts` já redireciona `/api` para a
porta 8080, então não há problema de CORS em desenvolvimento.

---

## 5. Roteiro de demonstração

Uma sequência que exercita as regras de negócio e a arquitetura Blackboard:

1. **Perfil Usuário** → busque "Machado" → veja os badges (Dom Casmurro verde,
   Memórias Póstumas amarelo).
2. Abra **Memórias Póstumas** → todos emprestados → clique em **Entrar na fila**
   escolhendo a Biblioteca Méier → confirme a reserva.
3. **Olhe o console do backend**: o trigger disparou, o listener acordou o
   Especialista e ele criou uma sugestão de transferência automática.
4. **Perfil Admin** → a nova solicitação apareceu na lista de pendentes, com a
   justificativa "Sugestão automática do Especialista de Transferência".
   Aprove → o exemplar vai para `EM_TRANSFERENCIA` → **Confirmar chegada**.
5. **Perfil Bibliotecário** → Registrar Devolução → escolha o empréstimo da Ana
   Souza (que está atrasado nos dados mockados) → veja o cálculo da RN12 e o
   "Bloqueado até" antes de confirmar.
6. Ainda como Bibliotecário → Registrar Empréstimo → escolha a Ana Souza → a
   coluna direita agora mostra o bloqueio da RN12 e o botão fica desabilitado.
7. **Cadastrar Exemplar** → cadastre um título novo → o resumo avisa que ele
   nascerá `INDISPONIVEL` por ser exemplar único (RN11).

---

## 6. Observações honestas

- **O backend não foi compilado.** Escrevi tudo seguindo o padrão do projeto de
  estoque e validei manualmente que todo método de repositório e de serviço
  chamado existe de fato, que os pacotes batem com os diretórios e que as chaves
  estão balanceadas nos 46 arquivos. Mas não tive acesso ao Maven Central para
  rodar `mvn compile`, então **rodem o build antes da apresentação** — pode
  sobrar algum ajuste de import.
- **O frontend foi compilado e validado**: `tsc -b` passou sem erros e
  `vite build` gerou o bundle com sucesso.
- **`data.sql` foi testado de verdade** num PostgreSQL 16, contra um schema
  equivalente ao que o Hibernate gera. Todos os INSERTs passaram e as contagens
  de disponibilidade batem com o que as telas esperam.
- **Três campos novos** entraram nas entidades e não estão no modelo lógico que
  vocês entregaram — as telas dependem deles:
  - `Exemplar.codigoBarras` (as telas 4, 6, 7 e 8 mostram "EX-00231");
  - `Exemplar.estadoConservacao` (tela 7);
  - `Usuario.bloqueadoAte` (tela 6, para implementar a RN12).
  Vale atualizar o documento do modelo lógico para incluir os três.
- **`ddl-auto=create` apaga o banco a cada start.** É ótimo para a demo, mas
  quando começarem a cadastrar dados que querem manter, troquem para `update` e
  mudem `spring.sql.init.mode` para `never`.
- **Segurança ficou de fora**, como combinado no roadmap: as senhas em `data.sql`
  são texto mock, não há login e os IDs de usuário estão fixos no frontend. Isso
  é justamente a Sprint 2 do plano de priorização.
