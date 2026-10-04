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
  await expect(page.getByLabel("Título")).toHaveValue(titulo);
  await page.getByLabel("Autor").fill("Itamar Vieira Junior");
  await page.getByRole("button", { name: /Registrar interesse/ }).click();
  await expect(page.getByRole("dialog")).toContainText(titulo);
  await confirmarModal(page, "Registrar");
  await expect(page.getByText(/A administração da rede vai avaliar a compra/)).toBeVisible();
  await expect(page.getByRole("row").filter({ hasText: titulo })).toContainText("1 pessoa");

  // A mesma pessoa, escrevendo diferente, é barrada
  await page.getByLabel("Título").fill(`  torto   ARADO ${u} `);
  await page.getByLabel("Autor").fill("itamar vieira júnior");
  await page.getByRole("button", { name: /Registrar interesse/ }).click();
  await confirmarModal(page, "Registrar");
  await expect(page.getByText("Você já registrou interesse neste livro.")).toBeVisible();
  await sair(page);

  // Outra pessoa, pelo menu, soma ao pedido
  await entrar(page, BRUNO);
  await menu(page).getByRole("link", { name: "Pedir um Livro" }).click();
  await page.getByLabel("Título").fill(`TORTO ÁRADO ${u}`);
  await page.getByLabel("Autor").fill("Itamar  Vieira Junior");
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
