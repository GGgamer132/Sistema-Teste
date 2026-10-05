import { expect, test, type APIRequestContext, type Page } from "@playwright/test";
import { CENTRAL_NOME, idBiblioteca, idLivro, VI_NOME } from "./apoio";

// Etapa 5b — telas de transferências e retirada (A-02 a A-11, B-10, B-12, B-13, B-20 sem o sino)
// contra backend + frontend reais. O seed atual não tem os cenários da §8, então cada teste
// monta a própria rede pela API: o Admin cria livros, uma biblioteca de destino com
// bibliotecária e outra sem equipe; BibC (Central) e a bibliotecária do destino cadastram
// exemplares e registram empréstimos; leitores criados na hora reservam.

const API = "http://localhost:8080/api";
const SENHA = "senha123";
const ADMIN = "admin@circulabook.com";
const BIB_C = "bibliotecariocentral@circulabook.com"; // Biblioteca Central
const U1 = "usuario1@circulabook.com";
let CENTRAL = 0;

// Ids das bibliotecas do seed, achados pelo nome
test.beforeAll(async ({ request }) => {
  CENTRAL = await idBiblioteca(request, CENTRAL_NOME);
});

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

const unico = () => `${Date.now().toString().slice(-6)}${Math.floor(Math.random() * 90 + 10)}`;

interface Leitor { id: number; nome: string; email: string; h: Cabecalho }

interface Cenario {
  u: string;
  admin: Cabecalho;
  bibC: Cabecalho;
  dest: Cabecalho;
  destino: { id: number; nome: string; bibliotecaria: string };
  semEquipe: { id: number; nome: string };
  livros: Record<"hobbit" | "l1984" | "dom" | "harry", { id: number; titulo: string }>;
  /** Empréstimos dos 2 Hobbit da Central (a, b). */
  empHobbit: number[];
  empHarry: number;
  a: Leitor; b: Leitor; c: Leitor;
  /** Pedido gerado pela reserva de C: Hobbit, fila na Central, retirada no destino. */
  pedidoId: number;
}

async function novoLeitor(request: APIRequestContext, nome: string, u: string): Promise<Leitor> {
  const email = `${nome.toLowerCase()}.${u}@teste.com`;
  const s = await ok<{ token: string; usuario: { id: number; nome: string } }>(
    request.post(`${API}/auth/cadastro`, { data: { nome: `${nome} ${u}`, email, senha: SENHA } }),
  );
  return { id: s.usuario.id, nome: s.usuario.nome, email, h: { Authorization: `Bearer ${s.token}` } };
}

/**
 * Hobbit — Central: 2 emprestados (a, b); c na fila com retirada no destino (pedido PENDENTE)
 * 1984   — Central: 2 disponíveis (lote com os 2 viola a capacidade)
 * Dom    — Central: 3 disponíveis; destino: 1 disponível (avulsa para quem já tem o título)
 * Harry  — destino: 1 emprestado (a); b na fila do destino com retirada lá
 */
async function montar(request: APIRequestContext): Promise<Cenario> {
  const u = unico();
  const admin = await token(request, ADMIN);
  const bibC = await token(request, BIB_C);

  const cat = await ok<{ id: number }>(request.post(`${API}/categorias`, { headers: admin, data: { nome: `Cat ${u}` } }));
  const livro = async (titulo: string) => {
    const l = await ok<{ id: number }>(request.post(`${API}/livros`, {
      headers: admin, data: { titulo, autor: `Autor ${u}`, categoriaId: cat.id },
    }));
    return { id: l.id, titulo };
  };
  const livros = {
    hobbit: await livro(`O Hobbit ${u}`),
    l1984: await livro(`1984 ${u}`),
    dom: await livro(`Dom Casmurro ${u}`),
    harry: await livro(`Harry Potter ${u}`),
  };

  const bib = await ok<{ id: number; nome: string }>(
    request.post(`${API}/bibliotecas`, { headers: admin, data: { nome: `Biblioteca Destino ${u}` } }),
  );
  const bibliotecaria = `bibliotecariodestino${u}@circulabook.com`;
  await ok(request.post(`${API}/usuarios/bibliotecarios`, {
    headers: admin, data: { nome: `Bibliotecário Destino ${u}`, email: bibliotecaria, senha: SENHA, bibliotecaId: bib.id },
  }));
  const semEquipe = await ok<{ id: number; nome: string }>(
    request.post(`${API}/bibliotecas`, { headers: admin, data: { nome: `Biblioteca Sem Equipe ${u}` } }),
  );
  const dest = await token(request, bibliotecaria);

  const exemplares = async (h: Cabecalho, livroId: number, quantidade: number) =>
    (await ok<{ id: number }[]>(request.post(`${API}/exemplares`, {
      headers: h, data: { livroId, conservacao: "BOM", quantidade },
    }))).map((e) => e.id);
  const hobbit = await exemplares(bibC, livros.hobbit.id, 2);
  await exemplares(bibC, livros.l1984.id, 2);
  await exemplares(bibC, livros.dom.id, 3);
  await exemplares(dest, livros.dom.id, 1);
  const harry = await exemplares(dest, livros.harry.id, 1);

  const a = await novoLeitor(request, "Alice", u);
  const b = await novoLeitor(request, "Bento", u);
  const c = await novoLeitor(request, "Celia", u);
  const emprestar = async (h: Cabecalho, exemplarId: number, usuarioId: number) =>
    (await ok<{ id: number }>(request.post(`${API}/emprestimos/registrar`, { headers: h, data: { exemplarId, usuarioId } }))).id;
  const empHobbit = [await emprestar(bibC, hobbit[0], a.id), await emprestar(bibC, hobbit[1], b.id)];
  const empHarry = await emprestar(dest, harry[0], a.id);

  await ok(request.post(`${API}/reservas`, {
    headers: c.h, data: { livroId: livros.hobbit.id, bibliotecaFilaId: CENTRAL, bibliotecaDestinoId: bib.id },
  }));
  await ok(request.post(`${API}/reservas`, {
    headers: b.h, data: { livroId: livros.harry.id, bibliotecaFilaId: bib.id, bibliotecaDestinoId: bib.id },
  }));
  const pendentes = await ok<{ id: number; livroId: number }[]>(
    request.get(`${API}/transferencias/pedidos-pendentes`, { headers: admin }),
  );
  const pedidoId = pendentes.find((p) => p.livroId === livros.hobbit.id)!.id;

  return {
    u, admin, bibC, dest, destino: { id: bib.id, nome: bib.nome, bibliotecaria }, semEquipe,
    livros, empHobbit, empHarry, a, b, c, pedidoId,
  };
}

async function devolverApi(request: APIRequestContext, h: Cabecalho, emprestimoId: number) {
  await ok(request.post(`${API}/emprestimos/devolver`, { headers: h, data: { emprestimoId, condicaoExemplar: "BOM" } }));
}

async function situacaoPedido(request: APIRequestContext, admin: Cabecalho, id: number) {
  const todas = await ok<{ id: number; status: string; exemplar: { id: number } | null }[]>(
    request.get(`${API}/transferencias`, { headers: admin }),
  );
  return todas.find((t) => t.id === id)!;
}

async function minhaReserva(request: APIRequestContext, l: Leitor, livroId: number) {
  const r = await ok<{ livroId: number; status: string; bibliotecaRetirada: string }[]>(
    request.get(`${API}/conta/reservas`, { headers: l.h }),
  );
  return r.find((x) => x.livroId === livroId);
}

const confirmarModal = (page: Page, rotulo: string | RegExp) =>
  page.getByRole("dialog").getByRole("button", { name: rotulo }).click();

const cardPedido = (page: Page, titulo: string) => page.getByTestId("pedido").filter({ hasText: titulo });

// ───────────────────────── Admin: decisão ─────────────────────────

test("A-02 / A-03: pedido sem exemplar mostra o título; aprovar fica aguardando exemplar; sem confirmar chegada", async ({ page, request }) => {
  const c = await montar(request);
  const problemas = vigiar(page);
  await entrar(page, ADMIN);
  await page.getByRole("navigation").getByRole("link", { name: "Transferências" }).click();
  await expect(page).toHaveURL(/\/admin\/transferencias$/);

  const card = cardPedido(page, c.livros.hobbit.titulo);
  await expect(card).toContainText(`Pedido de ${c.c.nome}`);
  await expect(card).toContainText(`Biblioteca Central → ${c.destino.nome}`);
  await expect(card).toContainText("Será vinculado na devolução");
  await expect(card).toContainText("pode ceder este");
  await expect(page.getByRole("button", { name: /Confirmar chegada/ })).toHaveCount(0);
  await expect(page.locator("body")).not.toContainText(/\bRN\d{2}\b/);

  await card.getByRole("button", { name: /Aprovar/ }).click();
  await expect(page.getByRole("dialog")).toContainText("aguardando exemplar");
  await confirmarModal(page, "Aprovar");
  await expect(page.getByText(`Pedido de "${c.livros.hobbit.titulo}" aprovado. Ele segue`)).toBeVisible();
  await expect(card).toHaveCount(0);
  expect((await situacaoPedido(request, c.admin, c.pedidoId)).status).toBe("APROVADA");

  await page.getByLabel("Filtrar por situação").selectOption("APROVADA_AGUARDANDO_EXEMPLAR");
  const linha = page.getByRole("row").filter({ hasText: c.livros.hobbit.titulo });
  await expect(linha).toContainText("Aprovada, aguardando exemplar");
  await expect(linha).toContainText(`Reserva de ${c.c.nome}`);
  expect(problemas).toEqual([]);
});

test("A-04: rejeitar devolve a retirada para a biblioteca da fila", async ({ page, request }) => {
  const c = await montar(request);
  const problemas = vigiar(page);
  await entrar(page, ADMIN);
  await page.goto("/admin/transferencias");
  await cardPedido(page, c.livros.hobbit.titulo).getByRole("button", { name: /Rejeitar/ }).click();
  await expect(page.getByRole("dialog")).toContainText("a retirada volta para a Biblioteca Central");
  await page.getByRole("dialog").getByLabel("Motivo").fill("Malote indisponível");
  await confirmarModal(page, "Rejeitar");
  await expect(page.getByText(/rejeitado\. A reserva de .* continua na fila da Biblioteca Central/)).toBeVisible();

  const r = await minhaReserva(request, c.c, c.livros.hobbit.id);
  expect(r?.status).toBe("PENDENTE");
  expect(r?.bibliotecaRetirada).toBe("Biblioteca Central");
  await page.getByLabel("Filtrar por situação").selectOption("REJEITADA");
  await expect(page.getByRole("row").filter({ hasText: c.livros.hobbit.titulo })).toContainText("Rejeitada");
  expect(problemas).toEqual([]);
});

test("A-05: com exemplar já separado, aprovar envia direto (em trânsito)", async ({ page, request }) => {
  const c = await montar(request);
  await devolverApi(request, c.bibC, c.empHobbit[0]); // pedido PENDENTE: exemplar fica retido
  const problemas = vigiar(page);
  await entrar(page, ADMIN);
  await page.goto("/admin/transferencias");
  const card = cardPedido(page, c.livros.hobbit.titulo);
  await expect(card).toContainText("Exemplar já separado");
  await card.getByRole("button", { name: /Aprovar/ }).click();
  await expect(page.getByRole("dialog")).toContainText("sai agora em trânsito");
  await confirmarModal(page, "Aprovar");
  await expect(page.getByText(/já está a caminho da Biblioteca Destino/)).toBeVisible();
  expect((await situacaoPedido(request, c.admin, c.pedidoId)).status).toBe("EM_TRANSITO");
  expect(problemas).toEqual([]);
});

test("A-06: segunda aprovação mostra 'já foi processada'; COMUM e bibliotecário são barrados", async ({ page, request }) => {
  const c = await montar(request);
  const problemas = vigiar(page);
  await entrar(page, ADMIN);
  await page.goto("/admin/transferencias");
  const card = cardPedido(page, c.livros.hobbit.titulo);
  await expect(card).toBeVisible();
  // Outra aba/administrador aprova antes
  await ok(request.patch(`${API}/transferencias/${c.pedidoId}/aprovar`, { headers: c.admin }));
  await card.getByRole("button", { name: /Aprovar/ }).click();
  await confirmarModal(page, "Aprovar");
  await expect(page.getByText(/já foi processada/)).toBeVisible();
  await expect(card).toHaveCount(0);

  for (const email of [U1, BIB_C]) {
    const h = await token(request, email);
    expect((await request.patch(`${API}/transferencias/${c.pedidoId}/aprovar`, { headers: h })).status()).toBe(403);
    expect((await request.get(`${API}/transferencias/pedidos-pendentes`, { headers: h })).status()).toBe(403);
  }
  await page.getByRole("button", { name: "Sair" }).click();
  await entrar(page, U1);
  await page.goto("/admin/transferencias");
  await expect(page).toHaveURL(/\/$/);
  await page.getByRole("button", { name: "Sair" }).click();
  await entrar(page, BIB_C);
  await page.goto("/admin/transferencias");
  await expect(page).toHaveURL(/\/biblioteca$/);
  expect(problemas).toEqual([]);
});

test("A-07: com o pedido T1 aberto, a Central não cede outro Hobbit; tela e API recusam com o motivo", async ({ page, request }) => {
  // Seed (§8): a Central tem 2 Hobbit e o pedido T1 (Usuário 3 -> Vila Isabel) já está aberto,
  // então mais uma transferência deixaria a Central sem o livro (abertas + 1 > total - 1).
  const hobbit = await idLivro(request, "O Hobbit");
  const vilaIsabel = await idBiblioteca(request, VI_NOME);
  const problemas = vigiar(page);
  await entrar(page, U1);
  await page.goto(`/livro/${hobbit}/reservar?biblioteca=${CENTRAL}`);
  await expect(page.getByRole("button", { name: /Em outra biblioteca/ })).toBeDisabled();
  await expect(page.getByTestId("outra-indisponivel")).toContainText("deixaria sem o livro");
  await expect(page.getByRole("button", { name: /Entrar na fila/ })).toBeEnabled(); // na própria Central pode

  const u1 = await token(request, U1);
  const r = await request.post(`${API}/reservas`, {
    headers: u1, data: { livroId: hobbit, bibliotecaFilaId: CENTRAL, bibliotecaDestinoId: vilaIsabel },
  });
  expect(r.status()).toBe(400);
  expect(await r.text()).toContain("deixaria sem o livro");
  expect(problemas).toEqual([]);
});

// ───────────────────────── Admin: avulsa em lote ─────────────────────────

test("A-08 / A-10 / A-09 / A-11: lote tudo ou nada mantém a seleção; depois cria para destino que já tem o título", async ({ page, request }) => {
  const c = await montar(request);
  const problemas = vigiar(page);
  await entrar(page, ADMIN);
  await page.goto("/admin/transferencias");
  await page.getByRole("button", { name: /Nova transferência avulsa/ }).click();

  const destino = page.getByLabel("Biblioteca de destino");
  await expect(destino.locator("option", { hasText: c.semEquipe.nome })).toHaveAttribute("disabled", "");
  await expect(page.getByText(`A ${c.semEquipe.nome} ainda não tem bibliotecário ativo`)).toBeVisible();

  // A-08 + A-10: os 2 de 1984 (a Central só tem 2) + 1 Dom Casmurro válido
  await page.getByLabel("Buscar exemplar").fill(`1984 ${c.u}`);
  const caixas1984 = page.getByRole("checkbox", { name: /Selecionar Exemplar nº/ });
  await expect(caixas1984).toHaveCount(2);
  await caixas1984.nth(0).check();
  await caixas1984.nth(1).check();
  await page.getByLabel("Buscar exemplar").fill(`Dom Casmurro ${c.u}`);
  const caixasDom = page.getByRole("checkbox", { name: /Selecionar Exemplar nº/ });
  await expect(caixasDom).toHaveCount(4); // 3 na Central + 1 no destino
  await page.getByRole("row").filter({ hasText: "Biblioteca Central" }).getByRole("checkbox").first().check();
  await destino.selectOption({ label: c.destino.nome });
  await page.getByRole("button", { name: /Transferir selecionados \(3\)/ }).click();
  await expect(page.getByRole("dialog")).toContainText(c.destino.nome);
  await confirmarModal(page, "Transferir");

  const erro = page.getByTestId("erro-avulsa");
  await expect(erro).toContainText("Nenhuma transferência foi criada");
  await expect(erro).toContainText(`"1984 ${c.u}" na Biblioteca Central: 2 selecionado(s), mas só 1 pode(m) sair`);
  await expect(page.getByTestId("selecionados")).toContainText("3 selecionado(s)");
  const doms = await ok<{ status: string; biblioteca: { id: number } }[]>(
    request.get(`${API}/exemplares/livro/${c.livros.dom.id}`, { headers: c.admin }),
  );
  expect(doms.every((e) => e.status === "DISPONIVEL")).toBeTruthy(); // nada foi criado

  // A-09 / A-11: tira um 1984 e acrescenta mais um Dom (o destino já tem Dom Casmurro)
  await page.getByLabel("Buscar exemplar").fill(`1984 ${c.u}`);
  await page.getByRole("checkbox", { name: /Selecionar Exemplar nº/ }).nth(1).uncheck();
  await page.getByLabel("Buscar exemplar").fill(`Dom Casmurro ${c.u}`);
  const naCentral = page.getByRole("row").filter({ hasText: "Biblioteca Central" }).getByRole("checkbox");
  await naCentral.nth(1).check();
  await page.getByRole("button", { name: /Transferir selecionados \(3\)/ }).click();
  await confirmarModal(page, "Transferir");
  await expect(page.getByText(`3 transferência(s) avulsa(s) criada(s) para a ${c.destino.nome}`)).toBeVisible();

  await page.getByLabel("Filtrar por situação").selectOption("EM_TRANSITO");
  const linhas = page.getByRole("row").filter({ hasText: c.u });
  await expect(linhas).toHaveCount(3);
  await expect(linhas.first()).toContainText("Avulsa (administrador)");
  await expect(linhas.first()).toContainText("Em trânsito");
  expect(problemas).toEqual([]);
});

// ───────────────────────── Ciclo completo pela interface ─────────────────────────

test("B-12 / B-13 / B-20: aprovar → devolver → 'A receber' → confirmar chegada → emprestar", async ({ page, request }) => {
  const c = await montar(request);
  const problemas = vigiar(page);

  // Admin aprova
  await entrar(page, ADMIN);
  await page.goto("/admin/transferencias");
  await cardPedido(page, c.livros.hobbit.titulo).getByRole("button", { name: /Aprovar/ }).click();
  await confirmarModal(page, "Aprovar");
  await expect(page.getByText(/aprovado\. Ele segue/)).toBeVisible();
  await page.getByRole("button", { name: "Sair" }).click();

  // BibC devolve o Hobbit de Alice pela tela: o exemplar vai direto para trânsito
  await entrar(page, BIB_C);
  await page.getByRole("link", { name: "Registrar Devolução" }).click();
  await page.getByPlaceholder("Digitar nome do usuário...").fill(c.a.nome);
  await page.getByRole("button", { name: new RegExp(c.livros.hobbit.titulo) }).click();
  await page.getByRole("button", { name: "Bom estado" }).click();
  await page.getByRole("button", { name: /Confirmar Devolução/ }).click();
  await page.getByRole("button", { name: "Confirmar devolução", exact: true }).click();
  await expect(page.getByText(/Devolução registrada/)).toBeVisible();
  const t = await situacaoPedido(request, c.admin, c.pedidoId);
  expect(t.status).toBe("EM_TRANSITO");

  // B-13: a origem não vê em "A receber", vê em "Saindo" e não pode confirmar
  await page.getByRole("navigation").getByRole("link", { name: "Transferências" }).click();
  await expect(page).toHaveURL(/\/biblioteca\/transferencias$/);
  await expect(page.getByRole("tabpanel")).not.toContainText(c.livros.hobbit.titulo);
  await page.getByRole("tab", { name: /Saindo/ }).click();
  const saindo = page.getByRole("row").filter({ hasText: c.livros.hobbit.titulo });
  await expect(saindo).toContainText("Em trânsito");
  await expect(saindo.getByRole("button")).toHaveCount(0);
  expect((await request.patch(`${API}/transferencias/${c.pedidoId}/confirmar-chegada`, {
    headers: c.bibC, data: {},
  })).status()).toBe(403);
  await page.getByRole("button", { name: "Sair" }).click();

  // Bibliotecário do destino confirma a chegada
  await entrar(page, c.destino.bibliotecaria);
  await page.getByRole("navigation").getByRole("link", { name: "Transferências" }).click();
  const linha = page.getByRole("row").filter({ hasText: c.livros.hobbit.titulo });
  await expect(linha).toContainText(`Exemplar nº ${t.exemplar!.id}`);
  await expect(linha).toContainText("Biblioteca Central");
  await expect(linha).toContainText(`Reservado para ${c.c.nome}`);
  await linha.getByRole("button", { name: "Confirmar chegada" }).click();
  await expect(page.getByRole("dialog")).toContainText(c.c.nome);
  await confirmarModal(page, "Confirmar chegada");
  await expect(page.getByText(`Ele está separado para ${c.c.nome}, que tem 3 dias para retirar.`)).toBeVisible();

  // B-20: aparece em Reservas aguardando retirada; atalho para o empréstimo
  await page.getByRole("navigation").getByRole("link", { name: "Reservas" }).click();
  const pronta = page.getByRole("row").filter({ hasText: c.c.nome });
  await expect(pronta).toContainText(c.livros.hobbit.titulo);
  await expect(pronta).toContainText(`Exemplar nº ${t.exemplar!.id}`);
  await expect(pronta).toContainText(`Retirar até ${new Date(Date.now() + 3 * 86_400_000).toLocaleDateString("pt-BR")}`);
  await pronta.getByRole("button", { name: "Novo empréstimo" }).click();
  await expect(page).toHaveURL(/\/biblioteca\/emprestimo\?usuario=/);
  await page.getByRole("button", { name: /Confirmar Empréstimo/ }).click();
  await confirmarModal(page, "Confirmar empréstimo");
  await expect(page.getByText(`Empréstimo registrado: ${c.livros.hobbit.titulo} para ${c.c.nome}`)).toBeVisible();

  const ex = await ok<{ id: number; status: string; biblioteca: { id: number } }[]>(
    request.get(`${API}/exemplares/livro/${c.livros.hobbit.id}`, { headers: c.admin }),
  );
  const viajante = ex.find((e) => e.id === t.exemplar!.id)!;
  expect(viajante.status).toBe("EMPRESTADO");
  expect(viajante.biblioteca.id).toBe(c.destino.id);
  expect(problemas).toEqual([]);
});

test("Chegada com 'chegou danificado': exemplar sai de circulação e a reserva volta à fila da origem", async ({ page, request }) => {
  const c = await montar(request);
  await ok(request.patch(`${API}/transferencias/${c.pedidoId}/aprovar`, { headers: c.admin }));
  await devolverApi(request, c.bibC, c.empHobbit[0]);
  const t = await situacaoPedido(request, c.admin, c.pedidoId);

  const problemas = vigiar(page);
  await entrar(page, c.destino.bibliotecaria);
  await page.goto("/biblioteca/transferencias");
  await page.getByRole("row").filter({ hasText: c.livros.hobbit.titulo })
    .getByRole("button", { name: "Confirmar chegada" }).click();
  const modal = page.getByRole("dialog");
  await modal.getByLabel("Chegou danificado").check();
  await expect(modal).toContainText("volta para a fila da Biblioteca Central");
  await modal.getByLabel("Observação").fill("Capa molhada");
  await confirmarModal(page, "Confirmar chegada");
  await expect(page.getByText(/registrada como danificada.*voltou para a fila da Biblioteca Central/)).toBeVisible();

  const ex = await ok<{ id: number; status: string; biblioteca: { id: number } }[]>(
    request.get(`${API}/exemplares/livro/${c.livros.hobbit.id}`, { headers: c.admin }),
  );
  expect(ex.find((e) => e.id === t.exemplar!.id)!.status).toBe("INDISPONIVEL");
  const r = await minhaReserva(request, c.c, c.livros.hobbit.id);
  expect(r?.status).toBe("PENDENTE");
  expect(r?.bibliotecaRetirada).toBe("Biblioteca Central");
  expect(problemas).toEqual([]);
});

test("B-10 / B-20: devolução com fila local separa para o 1º; tela de Reservas mostra fila e prazo", async ({ page, request }) => {
  const c = await montar(request);
  const problemas = vigiar(page);
  await entrar(page, c.destino.bibliotecaria);
  await page.getByRole("navigation").getByRole("link", { name: "Reservas" }).click();
  await expect(page).toHaveURL(/\/biblioteca\/reservas$/);
  const fila = page.getByTestId("fila").filter({ hasText: c.livros.harry.titulo });
  await expect(fila).toContainText(`1º — ${c.b.nome}`);
  await expect(page.getByText("Nenhuma reserva aguardando retirada nesta biblioteca.")).toBeVisible();

  // Devolução do Harry de Alice pela tela
  await page.getByRole("link", { name: "Registrar Devolução" }).click();
  await page.getByPlaceholder("Digitar nome do usuário...").fill(c.a.nome);
  await page.getByRole("button", { name: new RegExp(c.livros.harry.titulo) }).click();
  await page.getByRole("button", { name: "Bom estado" }).click();
  await page.getByRole("button", { name: /Confirmar Devolução/ }).click();
  await page.getByRole("button", { name: "Confirmar devolução", exact: true }).click();
  await expect(page.getByText(/Devolução registrada/)).toBeVisible();

  await page.getByRole("navigation").getByRole("link", { name: "Reservas" }).click();
  const pronta = page.getByRole("row").filter({ hasText: c.b.nome });
  await expect(pronta).toContainText(c.livros.harry.titulo);
  await expect(pronta).toContainText("Faltam 3 dias");
  await expect(page.getByTestId("fila").filter({ hasText: c.livros.harry.titulo })).toHaveCount(0);
  expect((await minhaReserva(request, c.b, c.livros.harry.id))?.status).toBe("DISPONIVEL");
  expect(problemas).toEqual([]);
});
