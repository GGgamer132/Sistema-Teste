import { expect, test, type APIRequestContext, type Page } from "@playwright/test";
import { CENTRAL_NOME, idBiblioteca, VI_NOME } from "./apoio";

// Etapa 4b — telas do usuário COMUM (U-01 a U-13, U-16) contra backend + frontend reais.
// O seed atual não tem os cenários da §8, então cada teste monta os próprios dados pela API:
// o Admin cria categoria, livros, uma biblioteca de destino com bibliotecário ativo e outra
// sem bibliotecário; BibC (Central) e BibVI (Vila Isabel) cadastram exemplares e
// registram empréstimos para leitores criados na hora.

const API = "http://localhost:8080/api";
const SENHA = "senha123";
const ADMIN = "admin@circulabook.com";
const BIB_C = "bibliotecariocentral@circulabook.com"; // Biblioteca Central
const BIB_VI = "bibliotecariovilaisabel@circulabook.com"; // Vila Isabel
const U1 = "usuario1@circulabook.com";
const U2 = "usuario2@circulabook.com";
let CENTRAL = 0;
let VILA_ISABEL = 0;

// Ids das bibliotecas do seed, achados pelo nome
test.beforeAll(async ({ request }) => {
  CENTRAL = await idBiblioteca(request, CENTRAL_NOME);
  VILA_ISABEL = await idBiblioteca(request, VI_NOME);
});

type Cabecalho = { Authorization: string };

function vigiar(page: Page) {
  const problemas: string[] = [];
  page.on("console", (m) => {
    // 4xx esperados (recusas do servidor) aparecem como "Failed to load resource" e são exibidos na tela
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

const unico = () => `${Date.now().toString().slice(-6)}${Math.floor(Math.random() * 90 + 10)}`;

interface Leitor {
  id: number;
  email: string;
  h: Cabecalho;
}

interface Cenario {
  u: string;
  admin: Cabecalho;
  bibC: Cabecalho;
  bibVI: Cabecalho;
  leitor: Leitor;
  b1: Leitor;
  livros: Record<"duna" | "sertao" | "orwell" | "capitaes" | "sapiens", { id: number; titulo: string }>;
  /** Empréstimos dos 2 exemplares de Duna na Central (b1, b2). */
  empDuna: number[];
  destino: { id: number; nome: string; bibliotecario: string };
  semBibliotecario: { id: number; nome: string };
}

async function novoLeitor(request: APIRequestContext, nome: string, u: string): Promise<Leitor> {
  const email = `${nome.toLowerCase()}.${u}@teste.com`;
  const s = await ok<{ token: string; usuario: { id: number } }>(
    request.post(`${API}/auth/cadastro`, { data: { nome: `${nome} ${u}`, email, senha: SENHA } }),
  );
  return { id: s.usuario.id, email, h: { Authorization: `Bearer ${s.token}` } };
}

/**
 * Monta a rede de um teste (nomes com sufixo único):
 *  Duna      — Central: 2 emprestados (b1, b2)        → fila na Central, destinos liberados
 *  Sertão    — Central: 1 emprestado (b1)             → origem com 1 só exemplar
 *  Orwell    — Central: 1 disponível                  → "Como retirar", sem reserva
 *  Capitães  — Vila Isabel: 1 disponível              → Central não tem o título
 *  Sapiens   — Central: 2 emprestados; VI: 1 disponível → destino VI já tem o título
 */
async function montar(request: APIRequestContext): Promise<Cenario> {
  const u = unico();
  const admin = await token(request, ADMIN);
  const bibC = await token(request, BIB_C);
  const bibVI = await token(request, BIB_VI);

  const cat = await ok<{ id: number }>(
    request.post(`${API}/categorias`, { headers: admin, data: { nome: `Categoria ${u}` } }),
  );
  const livro = async (titulo: string, autor: string) => {
    const l = await ok<{ id: number }>(
      request.post(`${API}/livros`, { headers: admin, data: { titulo, autor, categoriaId: cat.id } }),
    );
    return { id: l.id, titulo };
  };
  const livros = {
    duna: await livro(`Duna ${u}`, "Frank Herbert"),
    sertao: await livro(`Grande Sertão ${u}`, "Guimarães Rosa"),
    orwell: await livro(`A Revolução dos Bichos ${u}`, "George Orwell"),
    capitaes: await livro(`Capitães da Areia ${u}`, "Jorge Amado"),
    sapiens: await livro(`Sapiens ${u}`, "Yuval Noah Harari"),
  };

  const bib = await ok<{ id: number; nome: string }>(
    request.post(`${API}/bibliotecas`, { headers: admin, data: { nome: `Biblioteca Destino ${u}` } }),
  );
  const bibliotecario = `bibliotecariodestino${u}@circulabook.com`;
  await ok(
    request.post(`${API}/usuarios/bibliotecarios`, {
      headers: admin,
      data: { nome: `Bibliotecário Destino ${u}`, email: bibliotecario, senha: SENHA, bibliotecaId: bib.id },
    }),
  );
  const vazia = await ok<{ id: number; nome: string }>(
    request.post(`${API}/bibliotecas`, { headers: admin, data: { nome: `Biblioteca Sem Equipe ${u}` } }),
  );

  const exemplares = async (h: Cabecalho, livroId: number, quantidade: number) =>
    (await ok<{ id: number }[]>(
      request.post(`${API}/exemplares`, { headers: h, data: { livroId, conservacao: "BOM", quantidade } }),
    )).map((e) => e.id);
  const duna = await exemplares(bibC, livros.duna.id, 2);
  const sertao = await exemplares(bibC, livros.sertao.id, 1);
  await exemplares(bibC, livros.orwell.id, 1);
  await exemplares(bibVI, livros.capitaes.id, 1);
  const sapiens = await exemplares(bibC, livros.sapiens.id, 2);
  await exemplares(bibVI, livros.sapiens.id, 1);

  const leitor = await novoLeitor(request, "Leitor", u);
  const b1 = await novoLeitor(request, "Beatriz", u);
  const b2 = await novoLeitor(request, "Breno", u);
  const emprestar = async (exemplarId: number, usuarioId: number) =>
    (await ok<{ id: number }>(
      request.post(`${API}/emprestimos/registrar`, { headers: bibC, data: { exemplarId, usuarioId } }),
    )).id;
  const empDuna = [await emprestar(duna[0], b1.id), await emprestar(duna[1], b2.id)];
  await emprestar(sertao[0], b1.id);
  await emprestar(sapiens[0], b1.id);
  await emprestar(sapiens[1], b2.id);

  return {
    u, admin, bibC, bibVI, leitor, b1, livros, empDuna,
    destino: { id: bib.id, nome: bib.nome, bibliotecario },
    semBibliotecario: vazia,
  };
}

async function reservarApi(request: APIRequestContext, h: Cabecalho, livroId: number, fila: number, destino: number) {
  return request.post(`${API}/reservas`, {
    headers: h,
    data: { livroId, bibliotecaFilaId: fila, bibliotecaDestinoId: destino },
  });
}

/** Pedido de transferência do título (o mais recente), visto pelo Admin. */
async function transferenciaDo(request: APIRequestContext, admin: Cabecalho, livroId: number) {
  const todas = await ok<{ id: number; status: string; exemplar: unknown; livro: { id: number } }[]>(
    request.get(`${API}/transferencias`, { headers: admin }),
  );
  return todas.filter((t) => t.livro.id === livroId).sort((a, b) => b.id - a.id)[0];
}

async function confirmarModal(page: Page, rotulo: string | RegExp) {
  await page.getByRole("dialog").getByRole("button", { name: rotulo }).click();
}

const linhaDaBiblioteca = (page: Page, nome: string) =>
  page.getByRole("row").filter({ hasText: nome });

// ───────────────────────── U-01 a U-04 ─────────────────────────

test("U-01 / U-02: busca na URL, ficha por biblioteca e 'Como retirar' sem botão de reservar", async ({ page, request }) => {
  const c = await montar(request);
  const problemas = vigiar(page);
  await entrar(page, c.leitor.email);

  await page.getByLabel("Buscar por título, autor ou ISBN").fill(`Revolução dos Bichos ${c.u}`);
  await page.getByRole("button", { name: "Buscar", exact: true }).click();
  await expect(page).toHaveURL(/\/resultados\?.*termo=/);
  await expect(page.getByText(c.livros.orwell.titulo)).toBeVisible();
  await page.getByRole("button", { name: "Ver detalhes" }).click();
  await expect(page).toHaveURL(new RegExp(`/livro/${c.livros.orwell.id}$`));

  const linha = linhaDaBiblioteca(page, "Biblioteca Central");
  await expect(linha).toContainText("1");
  await expect(linha.getByRole("button", { name: "Como retirar" })).toBeVisible();
  await expect(page.getByRole("button", { name: /Entrar na fila|Reservar/ })).toHaveCount(0);
  await linha.getByRole("button", { name: "Como retirar" }).click();
  await expect(page.getByRole("dialog")).toContainText("presencialmente");
  await expect(page.getByRole("dialog").getByRole("button", { name: /reserv/i })).toHaveCount(0);
  await confirmarModal(page, "Entendi");

  // Breadcrumb "Resultados" preserva a busca
  await page.getByRole("link", { name: "Resultados" }).click();
  await expect(page).toHaveURL(/\/resultados\?.*termo=/);
  await expect(page.getByText(c.livros.orwell.titulo).first()).toBeVisible();
  expect(problemas).toEqual([]);
});

test("U-03 / U-04: com exemplar livre ou sem o título não há fila (UI e API)", async ({ page, request }) => {
  const c = await montar(request);
  const r3 = await reservarApi(request, c.leitor.h, c.livros.orwell.id, CENTRAL, CENTRAL);
  expect(r3.status()).toBe(400);
  expect(await r3.text()).toMatch(/disponível\(is\).*presencialmente/);
  const r4 = await reservarApi(request, c.leitor.h, c.livros.capitaes.id, CENTRAL, CENTRAL);
  expect(r4.status()).toBe(400);
  expect(await r4.text()).toContain("não possui exemplares deste título");

  const problemas = vigiar(page);
  await entrar(page, c.leitor.email);
  await page.goto(`/livro/${c.livros.orwell.id}/reservar?biblioteca=${CENTRAL}`);
  await expect(page.getByText(/faça o empréstimo presencialmente/)).toBeVisible();
  await expect(page.getByRole("button", { name: /Entrar na fila/ })).toBeDisabled();
  expect(problemas).toEqual([]);
});

// ───────────────────────── U-05 a U-09 ─────────────────────────

test("U-05 / U-10: fila na própria biblioteca, 1º lugar, sem transferência; 'retire até' quando liberada", async ({ page, request }) => {
  const c = await montar(request);
  const problemas = vigiar(page);
  await entrar(page, c.leitor.email);

  await page.goto(`/livro/${c.livros.duna.id}`);
  await linhaDaBiblioteca(page, "Biblioteca Central").getByRole("button", { name: "Entrar na fila" }).click();
  await expect(page).toHaveURL(new RegExp(`/livro/${c.livros.duna.id}/reservar\\?biblioteca=${CENTRAL}`));
  await expect(page.getByText("1º lugar")).toBeVisible();
  await page.getByRole("button", { name: /Na própria biblioteca/ }).click();
  await page.getByRole("button", { name: /Entrar na fila/ }).click();
  await expect(page.getByRole("dialog")).toContainText("posição 1");
  await confirmarModal(page, "Entrar na fila");

  await expect(page).toHaveURL(/\/minhas-reservas$/);
  await expect(page.getByText(/confirmada na Biblioteca Central\. Você é o 1º da fila/)).toBeVisible();
  const card = page.getByTestId("reserva").filter({ hasText: c.livros.duna.titulo });
  await expect(card).toContainText("1º na fila");
  await expect(card).toContainText("NA FILA");
  await expect(card).toContainText("Fila na Biblioteca Central → retirada na Biblioteca Central");
  await expect(card).not.toContainText("Transferência");
  expect(await transferenciaDo(request, c.admin, c.livros.duna.id)).toBeUndefined();

  // BibC recebe um Duna de volta: a reserva fica pronta e aparece o prazo
  await ok(request.post(`${API}/emprestimos/devolver`, {
    headers: c.bibC, data: { emprestimoId: c.empDuna[0], condicaoExemplar: "BOM" },
  }));
  await page.reload();
  await expect(card).toContainText("PRONTA PARA RETIRADA");
  const prazo = new Date(Date.now() + 3 * 86_400_000).toLocaleDateString("pt-BR");
  await expect(card).toContainText(`Retire até ${prazo}`);
  expect(problemas).toEqual([]);
});

test("U-06: retirada em outra biblioteca — destinos liberados e bloqueados com motivo", async ({ page, request }) => {
  const c = await montar(request);
  const problemas = vigiar(page);
  await entrar(page, c.leitor.email);

  await page.goto(`/livro/${c.livros.duna.id}/reservar?biblioteca=${CENTRAL}`);
  await page.getByRole("button", { name: /Em outra biblioteca/ }).click();
  const destinos = page.getByRole("group", { name: "Biblioteca de retirada" });
  await expect(destinos.getByRole("radio", { name: /Biblioteca Central/ })).toHaveCount(0);
  await expect(destinos.getByRole("radio", { name: c.destino.nome })).toBeEnabled();
  await expect(destinos.getByRole("radio", { name: "Biblioteca Comunitária de Vila Isabel" })).toBeEnabled();
  await expect(destinos.getByRole("radio", { name: new RegExp(c.semBibliotecario.nome) })).toBeDisabled();
  await expect(destinos.getByText(c.semBibliotecario.nome).locator("..")).toContainText("bibliotecário ativo");
  await expect(page.locator("body")).not.toContainText(/RN\d/);

  await destinos.getByRole("radio", { name: c.destino.nome }).check();
  await page.getByRole("button", { name: /Entrar na fila/ }).click();
  await expect(page.getByRole("dialog")).toContainText(c.destino.nome);
  await confirmarModal(page, "Entrar na fila");

  await expect(page).toHaveURL(/\/minhas-reservas$/);
  const card = page.getByTestId("reserva").filter({ hasText: c.livros.duna.titulo });
  await expect(card).toContainText(`retirada na ${c.destino.nome}`);
  await expect(card).toContainText("Transferência pendente");
  const t = await transferenciaDo(request, c.admin, c.livros.duna.id);
  expect(t.status).toBe("PENDENTE");
  expect(t.exemplar).toBeNull();
  expect(problemas).toEqual([]);
});

test("U-07: origem com 1 só exemplar desabilita 'Em outra biblioteca'; API também recusa", async ({ page, request }) => {
  const c = await montar(request);
  const problemas = vigiar(page);
  await entrar(page, c.leitor.email);
  await page.goto(`/livro/${c.livros.sertao.id}/reservar?biblioteca=${CENTRAL}`);
  await expect(page.getByRole("button", { name: /Em outra biblioteca/ })).toBeDisabled();
  await expect(page.getByTestId("outra-indisponivel")).toContainText("tem só 1 exemplar deste título");
  // Na própria biblioteca continua possível
  await expect(page.getByRole("button", { name: /Entrar na fila/ })).toBeEnabled();

  const r = await reservarApi(request, c.leitor.h, c.livros.sertao.id, CENTRAL, c.destino.id);
  expect(r.status()).toBe(400);
  expect(await r.text()).toContain("só 1 exemplar");
  expect(problemas).toEqual([]);
});

test("U-08: destino que já tem o título fica bloqueado; API recusa", async ({ page, request }) => {
  const c = await montar(request);
  const problemas = vigiar(page);
  await entrar(page, c.leitor.email);
  await page.goto(`/livro/${c.livros.sapiens.id}/reservar?biblioteca=${CENTRAL}`);
  await page.getByRole("button", { name: /Em outra biblioteca/ }).click();
  const destinos = page.getByRole("group", { name: "Biblioteca de retirada" });
  await expect(destinos.getByRole("radio", { name: "Biblioteca Comunitária de Vila Isabel" })).toBeDisabled();
  await expect(destinos.getByText("Biblioteca Comunitária de Vila Isabel", { exact: true }).locator(".."))
    .toContainText("já possui exemplares deste título");

  const r = await reservarApi(request, c.leitor.h, c.livros.sapiens.id, CENTRAL, VILA_ISABEL);
  expect(r.status()).toBe(400);
  expect(await r.text()).toContain("já possui exemplares deste título");
  expect(problemas).toEqual([]);
});

test("U-09: título já emprestado e reserva duplicada mostram o erro do servidor", async ({ page, request }) => {
  const c = await montar(request);
  const problemas = vigiar(page);

  // Beatriz está com um Duna
  await entrar(page, c.b1.email);
  await page.goto(`/livro/${c.livros.duna.id}/reservar?biblioteca=${CENTRAL}`);
  await page.getByRole("button", { name: /Entrar na fila/ }).click();
  await confirmarModal(page, "Entrar na fila");
  await expect(page.getByText(/já está com um exemplar de ".*Duna.*" emprestado/)).toBeVisible();
  await expect(page).toHaveURL(/\/reservar/);

  // O leitor já tem reserva ativa do título
  expect((await reservarApi(request, c.leitor.h, c.livros.duna.id, CENTRAL, CENTRAL)).ok()).toBeTruthy();
  await page.getByRole("button", { name: "Sair" }).click();
  await entrar(page, c.leitor.email);
  await page.goto(`/livro/${c.livros.duna.id}/reservar?biblioteca=${CENTRAL}`);
  await page.getByRole("button", { name: /Entrar na fila/ }).click();
  await confirmarModal(page, "Entrar na fila");
  await expect(page.getByText("Você já possui uma reserva ativa para este título.")).toBeVisible();
  expect(problemas).toEqual([]);
});

// ───────────────────────── U-11 / U-12 ─────────────────────────

test("U-11: cancelar com transferência aprovada aguardando exemplar", async ({ page, request }) => {
  const c = await montar(request);
  expect((await reservarApi(request, c.leitor.h, c.livros.duna.id, CENTRAL, c.destino.id)).ok()).toBeTruthy();
  const t = await transferenciaDo(request, c.admin, c.livros.duna.id);
  await ok(request.patch(`${API}/transferencias/${t.id}/aprovar`, { headers: c.admin }));

  const problemas = vigiar(page);
  await entrar(page, c.leitor.email);
  await page.getByRole("navigation").getByRole("link", { name: "Minhas Reservas" }).click();
  const card = page.getByTestId("reserva").filter({ hasText: c.livros.duna.titulo });
  await expect(card).toContainText("Aprovada, aguardando exemplar");

  await card.getByRole("button", { name: "Cancelar reserva" }).click();
  const modal = page.getByRole("dialog");
  await expect(modal).toContainText("Você perderá seu lugar na fila");
  await expect(modal).not.toContainText("viagem continua");
  await confirmarModal(page, "Cancelar reserva");
  await expect(page.getByText(`Reserva de "${c.livros.duna.titulo}" cancelada.`)).toBeVisible();
  await expect(card).toHaveCount(0);
  expect((await transferenciaDo(request, c.admin, c.livros.duna.id)).status).toBe("CANCELADA");

  // A reserva cancelada vai para o histórico
  await page.getByRole("navigation").getByRole("link", { name: "Meu Histórico" }).click();
  await expect(page.getByRole("row").filter({ hasText: c.livros.duna.titulo })).toContainText("Cancelada");
  expect(problemas).toEqual([]);
});

test("U-12: cancelar com transferência em trânsito avisa que a viagem continua; chega DISPONIVEL", async ({ page, request }) => {
  const c = await montar(request);
  expect((await reservarApi(request, c.leitor.h, c.livros.duna.id, CENTRAL, c.destino.id)).ok()).toBeTruthy();
  const t = await transferenciaDo(request, c.admin, c.livros.duna.id);
  await ok(request.patch(`${API}/transferencias/${t.id}/aprovar`, { headers: c.admin }));
  await ok(request.post(`${API}/emprestimos/devolver`, {
    headers: c.bibC, data: { emprestimoId: c.empDuna[0], condicaoExemplar: "BOM" },
  }));
  expect((await transferenciaDo(request, c.admin, c.livros.duna.id)).status).toBe("EM_TRANSITO");

  const problemas = vigiar(page);
  await entrar(page, c.leitor.email);
  await page.goto("/minhas-reservas");
  const card = page.getByTestId("reserva").filter({ hasText: c.livros.duna.titulo });
  await expect(card).toContainText("Em trânsito");
  await expect(card).toContainText("AGUARDANDO TRANSFERÊNCIA");
  await card.getByRole("button", { name: "Cancelar reserva" }).click();
  await expect(page.getByRole("dialog")).toContainText("A viagem continua");
  await confirmarModal(page, "Cancelar reserva");
  await expect(page.getByText(`Reserva de "${c.livros.duna.titulo}" cancelada.`)).toBeVisible();
  expect((await transferenciaDo(request, c.admin, c.livros.duna.id)).status).toBe("EM_TRANSITO");

  // Chegada confirmada pela bibliotecária do destino: o exemplar fica DISPONIVEL lá
  const dest = await token(request, c.destino.bibliotecario);
  await ok(request.patch(`${API}/transferencias/${t.id}/confirmar-chegada`, { headers: dest }));
  await page.goto(`/livro/${c.livros.duna.id}`);
  await expect(linhaDaBiblioteca(page, c.destino.nome).getByRole("button", { name: "Como retirar" })).toBeVisible();
  expect(problemas).toEqual([]);
});

// ───────────────────────── U-13 ─────────────────────────

test("U-13: Meus Empréstimos e Histórico só do Usuário 2; 3/3; Duna atrasado; bloqueio com data", async ({ page, request }) => {
  // Seed (§8.4): Usuário 2 tem 3/3 — O Hobbit (há 3 dias), Grande Sertão (há 6) e Duna (atrasado há 6)
  const problemas = vigiar(page);
  await entrar(page, U2);
  await page.getByRole("navigation").getByRole("link", { name: "Meus Empréstimos" }).click();
  await expect(page).toHaveURL(/\/meus-emprestimos$/);
  const cards = page.getByTestId("emprestimo");
  await expect(cards).toHaveCount(3);
  await expect(page.getByText("3 de 3")).toBeVisible();
  const atrasado = cards.filter({ hasText: "Duna" });
  await expect(atrasado).toContainText(/Atrasado há 6 dias/);
  await expect(atrasado).toContainText(`devolver até ${new Date(Date.now() - 6 * 86_400_000).toLocaleDateString("pt-BR")}`);
  await expect(page.getByText("Empréstimos bloqueados")).toHaveCount(0);
  // Prazo de 14 dias no empréstimo em dia (emprestado há 3 dias, vence em 11)
  const emDia = cards.filter({ hasText: "O Hobbit" });
  await expect(emDia).toContainText(`devolver até ${new Date(Date.now() + 11 * 86_400_000).toLocaleDateString("pt-BR")}`);

  // BibC recebe o Duna atrasado: 6 dias de atraso = 12 dias de bloqueio
  const bibC = await token(request, BIB_C);
  const ativos = await ok<{ id: number; usuario: { email: string }; exemplar: { livro: { titulo: string } } }[]>(
    request.get(`${API}/emprestimos/ativos`, { headers: bibC }),
  );
  const emp = ativos.find((e) => e.usuario.email === U2 && e.exemplar.livro.titulo === "Duna");
  expect(emp).toBeDefined();
  await ok(request.post(`${API}/emprestimos/devolver`, {
    headers: bibC, data: { emprestimoId: emp!.id, condicaoExemplar: "BOM" },
  }));

  await page.reload();
  await expect(cards).toHaveCount(2);
  await expect(page.getByText("2 de 3")).toBeVisible();
  const fim = new Date(Date.now() + 12 * 86_400_000).toLocaleDateString("pt-BR");
  await expect(page.getByText("Empréstimos bloqueados")).toBeVisible();
  await expect(page.getByText(`novos empréstimos até ${fim}`)).toBeVisible();

  // Histórico: só registros do Usuário 2 (Sapiens, O Pequeno Príncipe, reserva cancelada e o Duna de agora)
  await page.getByRole("navigation").getByRole("link", { name: "Meu Histórico" }).click();
  await expect(page).toHaveURL(/\/historico$/);
  const linhas = page.getByRole("row").filter({ hasText: /Empréstimo|Reserva/ });
  await expect(linhas.filter({ hasText: "Duna" })).toContainText("Devolvido com 6 dia(s) de atraso.");
  await expect(linhas.filter({ hasText: "Sapiens" })).toHaveCount(1);
  await expect(linhas.filter({ hasText: "O Pequeno Príncipe" })).toHaveCount(1);
  await expect(linhas.filter({ hasText: "Dom Casmurro" })).toContainText("Cancelada");
  await expect(page.getByText(/Código Limpo|Anne Frank/)).toHaveCount(0); // são do Usuário 3
  const total = await linhas.count();

  await page.getByLabel("Tipo").selectOption("RESERVA");
  await page.getByRole("button", { name: "Filtrar" }).click();
  await expect(linhas.filter({ hasText: "Empréstimo" })).toHaveCount(0);

  await page.getByLabel("Tipo").selectOption("");
  const amanha = new Date(Date.now() + 86_400_000);
  const iso = `${amanha.getFullYear()}-${String(amanha.getMonth() + 1).padStart(2, "0")}-${String(amanha.getDate()).padStart(2, "0")}`;
  await page.getByLabel("De", { exact: true }).fill(iso);
  await page.getByRole("button", { name: "Filtrar" }).click();
  await expect(page.getByText("Nenhum registro no período.")).toBeVisible();

  await page.getByRole("button", { name: "Limpar" }).click();
  await expect(linhas).toHaveCount(total);
  expect(problemas).toEqual([]);
});

// ───────────────────────── U-16 e rotas ─────────────────────────

test("U-16: COMUM em /biblioteca e /admin volta para a busca; menu sem 'Em construção' nem jargão", async ({ page }) => {
  const problemas = vigiar(page);
  await entrar(page, U1);
  for (const rota of ["/biblioteca", "/biblioteca/emprestimo", "/admin", "/admin/catalogo"]) {
    await page.goto(rota);
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByRole("heading", { name: "Buscar Livros na Rede" })).toBeVisible();
  }
  await expect(page.getByRole("combobox", { name: /perfil/i })).toHaveCount(0);
  for (const item of ["Buscar Livros", "Meus Empréstimos", "Minhas Reservas", "Meu Histórico"]) {
    await page.getByRole("navigation").getByRole("link", { name: item }).click();
    await expect(page.getByText(/em constru[cç][aã]o/i)).toHaveCount(0);
    await expect(page.locator("body")).not.toContainText(/\bRN\d{2}\b/);
  }
  expect(problemas).toEqual([]);
});

test("Admin: /admin é o painel da rede (não há mais redirecionamento)", async ({ page }) => {
  const problemas = vigiar(page);
  await entrar(page, ADMIN);
  await expect(page).toHaveURL(/\/admin$/);
  await expect(page.getByRole("heading", { name: "Painel da Rede" })).toBeVisible();
  await expect(page.getByText(/em constru[cç][aã]o/i)).toHaveCount(0);
  expect(problemas).toEqual([]);
});
