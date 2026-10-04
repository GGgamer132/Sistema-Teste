import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

// Etapa 7 — demandas (U-14, A-17), histórico de circulação (A-18), dashboard (A-01) e
// relatórios (A-19), contra backend + frontend reais. Cada teste monta os próprios dados
// pela API; contagens são conferidas por DIFERENÇA, porque o seed antigo continua no banco.

const API = "http://localhost:8080/api";
const SENHA = "senha123";
const ROBERTO = "roberto.dias@circulabook.org.br";
const FERNANDA = "fernanda.reis@circulabook.org.br"; // Biblioteca Central (id 4)
const CARLOS = "carlos.lima@circulabook.org.br"; // Biblioteca Vila Isabel (id 1)
const ANA = "ana.souza@email.com";
const BRUNO = "bruno.alves@email.com";

type Cabecalho = { Authorization: string };

function vigiar(page: Page) {
  const problemas: string[] = [];
  page.on("console", (m) => {
    if (m.type() === "error" && !/Failed to load resource: .* status of 4\d\d/.test(m.text())) {
      problemas.push(`console: ${m.text()}`);
    }
  });
  page.on("pageerror", (e) => problemas.push(`pageerror: ${e.message}`));
  page.on("response", (r) => r.status() >= 500 && problemas.push(`HTTP ${r.status()} ${r.url()}`));
  return problemas;
}

async function entrar(page: Page, email: string) {
  await page.goto("/login");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill(SENHA);
  await page.getByRole("button", { name: "Entrar" }).click();
  await page.waitForURL((url) => !url.pathname.endsWith("/login"));
}

async function sair(page: Page) {
  await page.getByRole("button", { name: "Sair" }).click();
  await page.waitForURL(/\/login$/);
}

async function token(request: APIRequestContext, email: string): Promise<Cabecalho> {
  const r = await request.post(`${API}/auth/login`, { data: { email, senha: SENHA } });
  expect(r.ok()).toBeTruthy();
  return { Authorization: `Bearer ${(await r.json()).token}` };
}

async function ok<T>(resp: Promise<import("@playwright/test").APIResponse>): Promise<T> {
  const r = await resp;
  if (!r.ok()) throw new Error(`${r.status()} ${r.url()}: ${await r.text()}`);
  return (await r.json()) as T;
}

let seq = 0;
const unico = () => `${Date.now().toString().slice(-6)}${Math.floor(Math.random() * 90 + 10)}${++seq}`;

const menu = (page: Page) => page.getByRole("navigation", { name: "Menu principal" });
const confirmarModal = (page: Page, rotulo: string | RegExp) =>
  page.getByRole("dialog").getByRole("button", { name: rotulo }).click();

// ───────────────────────── Bloco 1: demandas ─────────────────────────

test("U-14: busca vazia leva ao pedido; outro usuário soma; o mesmo é barrado; Admin avisado", async ({ page }) => {
  const u = unico();
  const titulo = `Torto Arado ${u}`;
  const problemas = vigiar(page);

  await entrar(page, ANA);
  await page.getByLabel("Buscar por título, autor ou ISBN").fill(titulo);
  await page.getByRole("button", { name: "Buscar", exact: true }).click();
  await expect(page.getByText("0 livros encontrados")).toBeVisible();
  await page.getByRole("button", { name: /Registrar interesse neste livro/ }).click();
  await expect(page).toHaveURL(/\/interesse\?titulo=/);
  await expect(page.getByLabel("Título", { exact: true })).toHaveValue(titulo);
  await page.getByLabel("Autor", { exact: true }).fill("Itamar Vieira Junior");
  await page.getByRole("button", { name: /Registrar interesse/ }).click();
  await expect(page.getByRole("dialog")).toContainText(titulo);
  await confirmarModal(page, "Registrar");
  await expect(page.getByText(/A administração da rede vai avaliar a compra/)).toBeVisible();
  await expect(page.getByRole("row").filter({ hasText: titulo })).toContainText("1 pessoa");

  // A mesma pessoa, escrevendo diferente, é barrada
  await page.getByLabel("Título", { exact: true }).fill(`  torto   ARADO ${u} `);
  await page.getByLabel("Autor", { exact: true }).fill("itamar vieira júnior");
  await page.getByRole("button", { name: /Registrar interesse/ }).click();
  await confirmarModal(page, "Registrar");
  await expect(page.getByText("Você já registrou interesse neste livro.")).toBeVisible();
  await sair(page);

  // Outra pessoa, pelo menu, soma ao pedido
  await entrar(page, BRUNO);
  await menu(page).getByRole("link", { name: "Pedir um Livro" }).click();
  await expect(page).toHaveURL(/\/interesse$/);
  await page.getByLabel("Título", { exact: true }).fill(`TORTO ÁRADO ${u}`);
  await page.getByLabel("Autor", { exact: true }).fill("Itamar  Vieira Junior");
  await page.getByRole("button", { name: /Registrar interesse/ }).click();
  await confirmarModal(page, "Registrar");
  await expect(page.getByText(`Agora 2 pessoas pediram "${titulo}"`)).toBeVisible();
  await sair(page);

  // Sino do Admin: "nova demanda" (uma só, da criação)
  await entrar(page, ROBERTO);
  await page.getByRole("button", { name: /^Notificações/ }).click();
  const avisos = page.getByRole("dialog", { name: "Notificações" }).getByTestId("notificacao").filter({ hasText: titulo });
  await expect(avisos).toHaveCount(1);
  await expect(avisos).toContainText("Nova demanda de aquisição");
  await avisos.getByRole("button").first().click();
  await expect(page).toHaveURL(/\/admin\/demandas$/);
  await expect(page.getByRole("row").filter({ hasText: titulo })).toContainText("2 pessoas");
  expect(problemas).toEqual([]);
});

test("A-17: Admin conduz a demanda (Aberta → Em análise → Aprovada) e filtra por situação", async ({ page, request }) => {
  const u = unico();
  const titulo = `Ensaio sobre a Cegueira ${u}`;
  for (const email of [ANA, BRUNO]) {
    await ok(request.post(`${API}/demandas`, {
      headers: await token(request, email), data: { titulo, autor: "José Saramago" },
    }));
  }
  const problemas = vigiar(page);
  await entrar(page, ROBERTO);
  await page.goto("/admin/demandas");
  const linha = page.getByRole("row").filter({ hasText: titulo });
  await expect(linha).toContainText("2 pessoas");
  await expect(linha).toContainText("Aberta");
  await expect(linha.getByRole("button", { name: "Aprovar compra" })).toHaveCount(0);

  await linha.getByRole("button", { name: "Iniciar análise" }).click();
  await expect(page.getByRole("dialog")).toContainText("Aberta → Em análise");
  await confirmarModal(page, "Iniciar análise");
  await expect(page.getByText(`"${titulo}" agora está em análise.`)).toBeVisible();
  await expect(linha).toContainText("Em análise");

  await linha.getByRole("button", { name: "Aprovar compra" }).click();
  await confirmarModal(page, "Aprovar compra");
  await expect(linha).toContainText("Aprovada");
  await expect(linha).toContainText("Encerrada");

  await page.getByLabel("Filtrar por situação").selectOption("APROVADA");
  await expect(page.getByRole("row").filter({ hasText: titulo })).toHaveCount(1);
  await page.getByLabel("Filtrar por situação").selectOption("ABERTA");
  await expect(page.getByRole("row").filter({ hasText: titulo })).toHaveCount(0);
  expect(problemas).toEqual([]);
});

test("Rotas do Admin bloqueadas para COMUM e bibliotecário", async ({ page }) => {
  const rotas = ["/admin", "/admin/demandas", "/admin/historico", "/admin/relatorios"];
  for (const [email, inicio] of [[ANA, /\/$/], [CARLOS, /\/biblioteca$/]] as const) {
    await entrar(page, email);
    for (const rota of rotas) {
      await page.goto(rota);
      await expect(page).toHaveURL(inicio);
    }
    await sair(page);
  }
});

// ───────────────────────── Bloco 2: histórico ─────────────────────────

async function novoLeitor(request: APIRequestContext, nome: string, u: string) {
  const email = `${nome.toLowerCase()}.${u}@teste.com`;
  const s = await ok<{ token: string; usuario: { id: number; nome: string } }>(
    request.post(`${API}/auth/cadastro`, { data: { nome: `${nome} ${u}`, email, senha: SENHA } }),
  );
  return { id: s.usuario.id, nome: s.usuario.nome, h: { Authorization: `Bearer ${s.token}` } };
}

/**
 * Ciclo completo de um exemplar da Central: cadastro, empréstimo, fila, devolução que
 * despacha para a Vila Isabel (reserva com retirada lá), chegada, empréstimo e devolução.
 */
async function cicloCompleto(request: APIRequestContext, u: string) {
  const admin = await token(request, ROBERTO);
  const fernanda = await token(request, FERNANDA);
  const carlos = await token(request, CARLOS);
  const livro = await ok<{ id: number }>(request.post(`${API}/livros`, {
    headers: admin, data: { titulo: `Ciclo ${u}`, autor: `Autor ${u}` },
  }));
  const [ex1, ex2] = (await ok<{ id: number }[]>(request.post(`${API}/exemplares`, {
    headers: fernanda, data: { livroId: livro.id, conservacao: "BOM", quantidade: 2 },
  }))).map((e) => e.id);
  const a = await novoLeitor(request, "Alfa", u);
  const b = await novoLeitor(request, "Beta", u);
  const c = await novoLeitor(request, "Gama", u);
  const emprestar = async (h: Cabecalho, exemplarId: number, usuarioId: number) =>
    (await ok<{ id: number }>(request.post(`${API}/emprestimos/registrar`, { headers: h, data: { exemplarId, usuarioId } }))).id;
  const devolver = (h: Cabecalho, emprestimoId: number) =>
    ok(request.post(`${API}/emprestimos/devolver`, { headers: h, data: { emprestimoId, condicaoExemplar: "BOM" } }));

  const empA = await emprestar(fernanda, ex1, a.id);
  await emprestar(fernanda, ex2, b.id);
  await ok(request.post(`${API}/reservas`, {
    headers: c.h, data: { livroId: livro.id, bibliotecaFilaId: 4, bibliotecaDestinoId: 1 },
  }));
  const pendentes = await ok<{ id: number; livroId: number }[]>(
    request.get(`${API}/transferencias/pedidos-pendentes`, { headers: admin }),
  );
  const pedido = pendentes.find((p) => p.livroId === livro.id)!.id;
  await ok(request.patch(`${API}/transferencias/${pedido}/aprovar`, { headers: admin }));
  await devolver(fernanda, empA);
  await ok(request.patch(`${API}/transferencias/${pedido}/confirmar-chegada`, { headers: carlos, data: {} }));
  const empC = await emprestar(carlos, ex1, c.id);
  await devolver(carlos, empC);
  return { ex1, leitor: c, titulo: `Ciclo ${u}` };
}

test("A-18: eventos recentes com filtros e linha do tempo do exemplar com ciclo completo", async ({ page, request }) => {
  const u = unico();
  const { ex1, leitor, titulo } = await cicloCompleto(request, u);
  const problemas = vigiar(page);
  await entrar(page, ROBERTO);
  await menu(page).getByRole("link", { name: "Histórico" }).click();
  await expect(page).toHaveURL(/\/admin\/historico$/);

  // Filtro pelo nº do exemplar
  await page.getByLabel("Nº do exemplar").fill(String(ex1));
  await page.getByRole("button", { name: "Filtrar" }).click();
  await expect(page.getByTestId("total-eventos")).toContainText("9 evento(s)");

  // Filtro por evento + biblioteca
  await page.getByLabel("Nº do exemplar").fill("");
  await page.getByLabel("Evento").selectOption({ label: "Chegada de transferência" });
  await page.getByLabel("Biblioteca").selectOption({ label: "Biblioteca Vila Isabel" });
  await page.getByRole("button", { name: "Filtrar" }).click();
  await expect(page.getByTestId("total-eventos")).not.toContainText("9 evento(s)");
  const chegada = page.getByRole("row").filter({ hasText: `Exemplar nº ${ex1}` });
  await expect(chegada).toContainText("Chegada de transferência");
  await expect(chegada).toContainText("Carlos Lima");
  await expect(chegada).toContainText(titulo);

  // Clicar no exemplar abre a linha do tempo
  await chegada.getByRole("button", { name: new RegExp(`Exemplar nº ${ex1}`) }).click();
  await expect(page).toHaveURL(new RegExp(`[?]exemplar=${ex1}$`));
  const linha = page.getByTestId("linha-do-tempo");
  await expect(linha).toContainText(`${titulo}`);
  await expect(linha).toContainText("hoje na Biblioteca Vila Isabel · Disponível");
  const eventos = linha.getByTestId("evento-linha");
  await expect(eventos).toHaveCount(9);
  const esperado = [
    ["Cadastro no acervo", "Fernanda Reis"],
    ["Empréstimo", "Fernanda Reis"],
    ["Fila de espera", leitor.nome],
    ["Devolução", "Fernanda Reis"],
    ["Saída para transferência", "Fernanda Reis"],
    ["Chegada de transferência", "Carlos Lima"],
    ["Reserva", "Carlos Lima"],
    ["Empréstimo", "Carlos Lima"],
    ["Devolução", "Carlos Lima"],
  ];
  for (let i = 0; i < esperado.length; i++) {
    await expect(eventos.nth(i)).toContainText(esperado[i][0]);
    await expect(eventos.nth(i)).toContainText(`por ${esperado[i][1]}`);
  }
  await expect(eventos.nth(7)).toContainText(`Emprestado a ${leitor.nome} por 14 dias`);
  await expect(page.locator("body")).not.toContainText(/TRANSFERENCIA_|EMPRESTADO_RESERVADO/);

  // Busca direta por nº na linha do tempo e erro para exemplar inexistente
  await page.getByLabel("Exemplar nº").fill("999999");
  await page.getByRole("button", { name: "Ver linha do tempo" }).click();
  await expect(page.getByText("Exemplar nº 999999 não encontrado.")).toBeVisible();
  expect(problemas).toEqual([]);
});

// ───────────────────────── Bloco 3: dashboard ─────────────────────────

type Kpis = Record<string, number>;

async function kpis(request: APIRequestContext, admin: Cabecalho): Promise<Kpis> {
  const d = await ok<Record<string, unknown>>(request.get(`${API}/admin/dashboard`, { headers: admin }));
  const m: Kpis = {};
  for (const [k, v] of Object.entries(d)) if (typeof v === "number") m[k] = v;
  return m;
}

test("A-01: /admin é o painel e os KPIs batem com as operações do teste (por diferença)", async ({ page, request }) => {
  const u = unico();
  const admin = await token(request, ROBERTO);
  const fernanda = await token(request, FERNANDA);
  const antes = await kpis(request, admin);

  // 2 exemplares novos na Central, os 2 emprestados, 1 leitor na fila e 1 demanda nova
  const livro = await ok<{ id: number }>(request.post(`${API}/livros`, {
    headers: admin, data: { titulo: `Painel ${u}`, autor: `Autor ${u}` },
  }));
  const ids = (await ok<{ id: number }[]>(request.post(`${API}/exemplares`, {
    headers: fernanda, data: { livroId: livro.id, conservacao: "BOM", quantidade: 2 },
  }))).map((e) => e.id);
  for (const [i, exemplarId] of ids.entries()) {
    const l = await novoLeitor(request, `Kpi${i}`, u);
    await ok(request.post(`${API}/emprestimos/registrar`, { headers: fernanda, data: { exemplarId, usuarioId: l.id } }));
  }
  const fila = await novoLeitor(request, "Fila", u);
  await ok(request.post(`${API}/reservas`, {
    headers: fila.h, data: { livroId: livro.id, bibliotecaFilaId: 4, bibliotecaDestinoId: 4 },
  }));
  await ok(request.post(`${API}/demandas`, { headers: fila.h, data: { titulo: `Demanda ${u}`, autor: "Alguém" } }));

  const esperado: Kpis = {
    ...antes,
    exemplaresTotal: antes.exemplaresTotal + 2,
    emprestimosAtivos: antes.emprestimosAtivos + 2,
    reservasEmFila: antes.reservasEmFila + 1,
    demandasAbertas: antes.demandasAbertas + 1,
  };

  const problemas = vigiar(page);
  await entrar(page, ROBERTO);
  await expect(page).toHaveURL(/\/admin$/);
  await expect(page.getByRole("heading", { name: "Painel da Rede" })).toBeVisible();
  for (const chave of ["exemplaresTotal", "emprestimosAtivos", "reservasEmFila", "demandasAbertas",
                       "bibliotecasAtivas", "emprestimosAtrasados", "transferenciasPendentes",
                       "transferenciasEmTransito", "reservasProntas", "transferenciasAguardandoExemplar"]) {
    await expect(page.getByTestId(`kpi-${chave}`).getByTestId("valor")).toHaveText(String(esperado[chave]));
  }
  await expect(page.getByTestId("exemplares-por-status")).toContainText("Emprestado (com fila)");
  // Só cards e tabelas
  await expect(page.locator("main canvas")).toHaveCount(0);
  await expect(page.locator("main svg")).toHaveCount(0);

  await page.getByTestId("kpi-demandasAbertas").click();
  await expect(page).toHaveURL(/\/admin\/demandas$/);
  await menu(page).getByRole("link", { name: "Painel" }).click();
  await expect(page).toHaveURL(/\/admin$/);
  expect(problemas).toEqual([]);
});

// ───────────────────────── Bloco 4: relatórios ─────────────────────────

interface LinhaBib { bibliotecaId: number; total?: number; realizados?: number; devolvidos?: number }

test("A-19: relatórios só em tabelas, batendo com as operações (por diferença) e com período", async ({ page, request }) => {
  const u = unico();
  const admin = await token(request, ROBERTO);
  const fernanda = await token(request, FERNANDA);
  const antes = await ok<{ acervo: LinhaBib[]; emprestimos: LinhaBib[] }>(
    request.get(`${API}/admin/relatorios`, { headers: admin }),
  );
  const central = (l: LinhaBib[]) => l.find((x) => x.bibliotecaId === 4)!;

  // 2 exemplares novos na Central; um deles emprestado, devolvido e emprestado de novo
  const titulo = `Relatorio ${u}`;
  const livro = await ok<{ id: number }>(request.post(`${API}/livros`, { headers: admin, data: { titulo, autor: `Autor ${u}` } }));
  const [ex] = (await ok<{ id: number }[]>(request.post(`${API}/exemplares`, {
    headers: fernanda, data: { livroId: livro.id, conservacao: "BOM", quantidade: 2 },
  }))).map((e) => e.id);
  const a = await novoLeitor(request, "Rel", u);
  const emp = await ok<{ id: number }>(request.post(`${API}/emprestimos/registrar`, { headers: fernanda, data: { exemplarId: ex, usuarioId: a.id } }));
  await ok(request.post(`${API}/emprestimos/devolver`, { headers: fernanda, data: { emprestimoId: emp.id, condicaoExemplar: "BOM" } }));
  await ok(request.post(`${API}/emprestimos/registrar`, { headers: fernanda, data: { exemplarId: ex, usuarioId: a.id } }));

  const problemas = vigiar(page);
  await entrar(page, ROBERTO);
  await menu(page).getByRole("link", { name: "Relatórios" }).click();
  await expect(page).toHaveURL(/\/admin\/relatorios$/);
  await expect(page.getByTestId("periodo")).toContainText("todo o período");

  const linhaCentral = (id: string) => page.getByTestId(id).getByRole("row").filter({ hasText: "Biblioteca Central" });
  await expect(linhaCentral("rel-acervo").getByRole("cell").last()).toHaveText(String(central(antes.acervo).total! + 2));
  await expect(linhaCentral("rel-emprestimos").getByRole("cell").nth(1)).toHaveText(String(central(antes.emprestimos).realizados! + 2));
  await expect(linhaCentral("rel-emprestimos").getByRole("cell").nth(2)).toHaveText(String(central(antes.emprestimos).devolvidos! + 1));
  const livroLinha = page.getByTestId("rel-livros").getByRole("row").filter({ hasText: titulo });
  await expect(livroLinha.getByRole("cell").last()).toHaveText("2");
  for (const id of ["rel-acervo", "rel-emprestimos", "rel-atrasos", "rel-livros", "rel-demandas", "rel-transferencias"]) {
    await expect(page.getByTestId(id).locator("table")).toHaveCount(1);
  }
  await expect(page.getByTestId("rel-transferencias")).toContainText("Em trânsito");

  // Só tabelas: nada de canvas ou SVG de gráfico
  await expect(page.locator("main canvas")).toHaveCount(0);
  await expect(page.locator("main svg")).toHaveCount(0);

  // Período a partir de amanhã: nenhum empréstimo novo
  const amanha = new Date(Date.now() + 86_400_000);
  const iso = `${amanha.getFullYear()}-${String(amanha.getMonth() + 1).padStart(2, "0")}-${String(amanha.getDate()).padStart(2, "0")}`;
  await page.getByLabel("De", { exact: true }).fill(iso);
  await page.getByRole("button", { name: "Aplicar" }).click();
  await expect(page.getByTestId("periodo")).toContainText(`de ${iso.split("-").reverse().join("/")} até hoje`);
  await expect(page.getByTestId("rel-livros")).toContainText("Nenhum empréstimo no período.");
  await expect(linhaCentral("rel-emprestimos").getByRole("cell").nth(1)).toHaveText("0");

  // Período invertido: erro do servidor
  await page.getByLabel("Até", { exact: true }).fill("2020-01-01");
  await page.getByRole("button", { name: "Aplicar" }).click();
  await expect(page.getByText("A data inicial não pode ser depois da data final.")).toBeVisible();
  expect(problemas).toEqual([]);
});
