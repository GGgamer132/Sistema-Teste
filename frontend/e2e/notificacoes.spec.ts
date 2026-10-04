import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

// Etapa 6 — notificações in-app (U-15, B-07, B-20, A-20) e cabeçalho responsivo,
// contra backend + frontend reais. Cada teste monta os próprios dados pela API.

const API = "http://localhost:8080/api";
const SENHA = "senha123";
const ROBERTO = "roberto.dias@circulabook.org.br";
const FERNANDA = "fernanda.reis@circulabook.org.br"; // Biblioteca Central (id 4)
const CARLOS = "carlos.lima@circulabook.org.br"; // Biblioteca Vila Isabel (id 1)
const ANA = "ana.souza@email.com";
const CAMILA = "camila.duarte@email.com";
const CENTRAL = 4;
const VILA_ISABEL = 1;

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
const unico = () => `${Date.now().toString().slice(-6)}${Math.floor(Math.random() * 90 + 10)}`;

interface Leitor { id: number; nome: string; email: string; h: Cabecalho }

async function novoLeitor(request: APIRequestContext, nome: string, u: string): Promise<Leitor> {
  const email = `${nome.toLowerCase()}.${u}@teste.com`;
  const s = await ok<{ token: string; usuario: { id: number; nome: string } }>(
    request.post(`${API}/auth/cadastro`, { data: { nome: `${nome} ${u}`, email, senha: SENHA } }),
  );
  return { id: s.usuario.id, nome: s.usuario.nome, email, h: { Authorization: `Bearer ${s.token}` } };
}

/** Livro com N exemplares numa biblioteca, todos emprestados a leitores novos; devolve os empréstimos. */
async function tituloEmprestado(
  request: APIRequestContext, admin: Cabecalho, bibliotecario: Cabecalho, titulo: string, n: number, u: string,
) {
  const l = await ok<{ id: number }>(request.post(`${API}/livros`, { headers: admin, data: { titulo, autor: `Autor ${u}` } }));
  const ids = (await ok<{ id: number }[]>(request.post(`${API}/exemplares`, {
    headers: bibliotecario, data: { livroId: l.id, conservacao: "BOM", quantidade: n },
  }))).map((e) => e.id);
  const emps: number[] = [];
  for (let i = 0; i < n; i++) {
    const quem = await novoLeitor(request, `Portador${i}`, `${u}x${++seq}`);
    emps.push((await ok<{ id: number }>(request.post(`${API}/emprestimos/registrar`, {
      headers: bibliotecario, data: { exemplarId: ids[i], usuarioId: quem.id },
    }))).id);
  }
  return { livroId: l.id, emps };
}

async function reservar(request: APIRequestContext, h: Cabecalho, livroId: number, fila: number, destino: number) {
  await ok(request.post(`${API}/reservas`, { headers: h, data: { livroId, bibliotecaFilaId: fila, bibliotecaDestinoId: destino } }));
}

async function devolver(request: APIRequestContext, h: Cabecalho, emprestimoId: number) {
  await ok(request.post(`${API}/emprestimos/devolver`, { headers: h, data: { emprestimoId, condicaoExemplar: "BOM" } }));
}

const sino = (page: Page) => page.getByRole("button", { name: /^Notificações/ });
const contador = (page: Page) => page.getByTestId("contador-notificacoes");
const painel = (page: Page) => page.getByRole("dialog", { name: "Notificações" });

// ───────────────────────── U-15 ─────────────────────────

test("U-15: contador vermelho, marcar lida, atualização sem recarregar, marcar todas zera, clicar leva à tela", async ({ page, request }) => {
  const u = unico();
  const admin = await token(request, ROBERTO);
  const fernanda = await token(request, FERNANDA);
  const leitor = await novoLeitor(request, "Leitora", u);
  const x = await tituloEmprestado(request, admin, fernanda, `Livro X ${u}`, 1, u);
  const y = await tituloEmprestado(request, admin, fernanda, `Livro Y ${u}`, 1, u);
  const z = await tituloEmprestado(request, admin, fernanda, `Livro Z ${u}`, 1, u);
  for (const t of [x, y, z]) await reservar(request, leitor.h, t.livroId, CENTRAL, CENTRAL);
  await devolver(request, fernanda, x.emps[0]);
  await devolver(request, fernanda, y.emps[0]); // 2 avisos de "pronta para retirada"

  const problemas = vigiar(page);
  await page.clock.install();
  await entrar(page, leitor.email);
  await expect(contador(page)).toHaveText("2");
  await expect(contador(page)).toHaveCSS("background-color", "rgb(229, 57, 53)");

  await sino(page).click();
  const itens = painel(page).getByTestId("notificacao");
  await expect(itens).toHaveCount(2);
  await expect(itens.first()).toContainText(`Livro Y ${u}`); // mais recente primeiro
  await expect(itens.first()).toContainText("Reserva pronta para retirada");
  await itens.first().getByRole("button", { name: "Marcar como lida" }).click();
  await expect(contador(page)).toHaveText("1");
  await expect(itens.first().getByRole("button", { name: "Marcar como lida" })).toHaveCount(0);
  await page.keyboard.press("Escape");

  // Chega outra notificação: o polling de 30 s atualiza o contador sem recarregar
  await devolver(request, fernanda, z.emps[0]);
  await page.clock.runFor(31_000);
  await expect(contador(page)).toHaveText("2");

  await sino(page).click();
  await painel(page).getByRole("button", { name: "Marcar todas como lidas" }).click();
  await expect(contador(page)).toHaveCount(0);
  await expect(painel(page).getByRole("button", { name: "Marcar como lida" })).toHaveCount(0);

  // Clicar numa notificação leva à tela relacionada
  await itens.filter({ hasText: `Livro Z ${u}` }).getByRole("button").first().click();
  await expect(page).toHaveURL(/\/minhas-reservas$/);
  await expect(painel(page)).toHaveCount(0);
  expect(problemas).toEqual([]);
});

test("U-15: trocar de tela também atualiza o contador; clicar numa não lida marca e navega", async ({ page, request }) => {
  const u = unico();
  const admin = await token(request, ROBERTO);
  const fernanda = await token(request, FERNANDA);
  const leitor = await novoLeitor(request, "Leitor", u);
  const x = await tituloEmprestado(request, admin, fernanda, `Livro W ${u}`, 1, u);
  await reservar(request, leitor.h, x.livroId, CENTRAL, CENTRAL);

  const problemas = vigiar(page);
  await entrar(page, leitor.email);
  await expect(contador(page)).toHaveCount(0);
  await sino(page).click();
  await expect(painel(page)).toContainText("Você não tem notificações.");
  await page.keyboard.press("Escape");

  await devolver(request, fernanda, x.emps[0]);
  await page.getByRole("navigation", { name: "Menu principal" }).getByRole("link", { name: "Meu Histórico" }).click();
  await expect(contador(page)).toHaveText("1");
  await sino(page).click();
  await painel(page).getByTestId("notificacao").first().getByRole("button").first().click();
  await expect(page).toHaveURL(/\/minhas-reservas$/);
  await expect(contador(page)).toHaveCount(0);
  expect(problemas).toEqual([]);
});

// ───────────────────────── B-20 / A-20 / B-07 ─────────────────────────

test("B-20: sino do bibliotecário avisa a reserva pronta para retirada", async ({ page, request }) => {
  const u = unico();
  const admin = await token(request, ROBERTO);
  const carlos = await token(request, CARLOS);
  const leitor = await novoLeitor(request, "Bia", u);
  const h = await tituloEmprestado(request, admin, carlos, `Harry ${u}`, 1, u);
  await reservar(request, leitor.h, h.livroId, VILA_ISABEL, VILA_ISABEL);
  await devolver(request, carlos, h.emps[0]);

  const problemas = vigiar(page);
  await entrar(page, CARLOS);
  await expect(contador(page)).toBeVisible();
  await sino(page).click();
  const aviso = painel(page).getByTestId("notificacao").filter({ hasText: `Harry ${u}` });
  await expect(aviso).toContainText("Reserva aguardando retirada");
  await expect(aviso).toContainText(`${leitor.nome} virá buscar`);
  await aviso.getByRole("button").first().click();
  await expect(page).toHaveURL(/\/biblioteca\/reservas$/);
  await expect(page.getByRole("row").filter({ hasText: leitor.nome })).toContainText(`Harry ${u}`);
  expect(problemas).toEqual([]);
});

test("A-20: sino do Admin avisa novo pedido e exemplar retido (urgente)", async ({ page, request }) => {
  const u = unico();
  const admin = await token(request, ROBERTO);
  const fernanda = await token(request, FERNANDA);
  const leitor = await novoLeitor(request, "Caio", u);
  const hobbit = await tituloEmprestado(request, admin, fernanda, `Hobbit ${u}`, 2, u);
  await reservar(request, leitor.h, hobbit.livroId, CENTRAL, VILA_ISABEL);

  const problemas = vigiar(page);
  await entrar(page, ROBERTO);
  await expect(contador(page)).toBeVisible();
  await sino(page).click();
  const novo = painel(page).getByTestId("notificacao").filter({ hasText: `Hobbit ${u}` });
  await expect(novo).toContainText("Novo pedido de transferência");
  await expect(novo).toContainText(leitor.nome);
  await page.keyboard.press("Escape");

  // Fernanda devolve antes da decisão: exemplar retido
  await devolver(request, fernanda, hobbit.emps[0]);
  await page.getByRole("navigation", { name: "Menu principal" }).getByRole("link", { name: "Catálogo" }).click();
  await sino(page).click();
  const urgente = painel(page).getByTestId("notificacao").filter({ hasText: "Urgente: exemplar retido" }).first();
  await expect(urgente).toContainText(`Hobbit ${u}`);
  await urgente.getByRole("button").first().click();
  await expect(page).toHaveURL(/\/admin\/transferencias$/);
  await expect(page.getByTestId("pedido").filter({ hasText: `Hobbit ${u}` })).toContainText("Exemplar já separado");
  expect(problemas).toEqual([]);
});

test("B-07: devolução com atraso avisa o bloqueio com a data final", async ({ page, request }) => {
  // Seed: Camila está com Memórias Póstumas da Central atrasado há 4 dias -> 8 dias de bloqueio
  const fernanda = await token(request, FERNANDA);
  const ativos = await ok<{ id: number; usuario: { email: string } }[]>(
    request.get(`${API}/emprestimos/ativos`, { headers: fernanda }),
  );
  const emp = ativos.find((e) => e.usuario.email === CAMILA);
  expect(emp).toBeDefined();
  await devolver(request, fernanda, emp!.id);

  const problemas = vigiar(page);
  await entrar(page, CAMILA);
  await sino(page).click();
  const aviso = painel(page).getByTestId("notificacao").filter({ hasText: "Empréstimos bloqueados" });
  const fim = new Date(Date.now() + 8 * 86_400_000).toLocaleDateString("pt-BR");
  await expect(aviso).toContainText(`4 dia(s) de atraso`);
  await expect(aviso).toContainText(`até ${fim}`);
  await aviso.getByRole("button").first().click();
  await expect(page).toHaveURL(/\/meus-emprestimos$/);
  await expect(page.getByText("Empréstimos bloqueados")).toBeVisible();
  expect(problemas).toEqual([]);
});

// ───────────────────────── Cabeçalho ─────────────────────────

test("Sino presente nos 3 perfis", async ({ page }) => {
  for (const email of [ANA, CARLOS, ROBERTO]) {
    await entrar(page, email);
    await expect(sino(page)).toBeVisible();
    await page.getByRole("button", { name: "Sair" }).click();
  }
});

/** Nenhum link do menu em linha encosta na identificação, no sino ou nos botões. */
async function semSobreposicao(page: Page) {
  const links = page.getByRole("navigation", { name: "Menu principal" }).getByRole("link");
  await expect(links.first()).toBeVisible();
  const n = await links.count();
  const ultimo = (await links.nth(n - 1).boundingBox())!;
  const direita = [
    page.getByTestId("identificacao"),
    sino(page),
    page.getByRole("button", { name: "Sair" }),
  ];
  for (const el of direita) {
    if (!(await el.isVisible())) continue;
    const box = (await el.boundingBox())!;
    expect(ultimo.x + ultimo.width).toBeLessThanOrEqual(box.x);
  }
  // Nada escondido sob o cabeçalho: todos os links visíveis
  for (let i = 0; i < n; i++) await expect(links.nth(i)).toBeVisible();
}

test("Menu sem sobreposição em telas largas e recolhível em telas estreitas", async ({ page }) => {
  const problemas = vigiar(page);
  for (const [email, largura] of [[CARLOS, 1280], [ROBERTO, 1024], [ANA, 1024]] as const) {
    await page.setViewportSize({ width: largura, height: 800 });
    await entrar(page, email);
    await semSobreposicao(page);
    await page.getByRole("button", { name: "Sair" }).click();
  }

  // Bibliotecário em 1024 e 390 px: menu recolhível com os 7 itens
  for (const largura of [1024, 390]) {
    await page.setViewportSize({ width: largura, height: 800 });
    await entrar(page, CARLOS);
    await expect(page.getByRole("navigation", { name: "Menu principal" })).toBeHidden();
    await expect(sino(page)).toBeVisible();
    await page.getByRole("button", { name: /Menu/ }).click();
    const menu = page.getByRole("navigation", { name: "Menu", exact: true });
    await expect(menu.getByRole("link")).toHaveCount(7);
    await menu.getByRole("link", { name: "Reservas" }).click();
    await expect(page).toHaveURL(/\/biblioteca\/reservas$/);
    await expect(menu).toHaveCount(0);
    const sair = (await page.getByRole("button", { name: "Sair" }).boundingBox())!;
    expect(sair.x + sair.width).toBeLessThanOrEqual(largura);
    await page.getByRole("button", { name: "Sair" }).click();
  }
  expect(problemas).toEqual([]);
});
