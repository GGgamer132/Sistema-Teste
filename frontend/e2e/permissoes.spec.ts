import { expect, test, type APIRequestContext, type Page } from "@playwright/test";
import { CENTRAL_NOME, idBiblioteca } from "./apoio";

// Etapa 3 — permissões de cadastro (B-14 a B-19, A-12 a A-16).
// Roda contra backend + frontend reais; usuários da §8.1 (senha senha123).

const API = "http://localhost:8080/api";
const SENHA = "senha123";
const BIB_VI = "bibliotecariovilaisabel@circulabook.com"; // Vila Isabel
const BIB_C = "bibliotecariocentral@circulabook.com"; // Central
const ADMIN = "admin@circulabook.com";
const BIB_T = "bibliotecariotijuca@circulabook.com"; // Tijuca (sem acervo no seed)
const U1 = "usuario1@circulabook.com";
let CENTRAL = 0;

// Ids das bibliotecas do seed, achados pelo nome
test.beforeAll(async ({ request }) => {
  CENTRAL = await idBiblioteca(request, CENTRAL_NOME);
});

function vigiar(page: Page) {
  const problemas: string[] = [];
  page.on("console", (m) => {
    // 4xx esperados (validações recusadas pelo servidor) aparecem no console do navegador
    // como "Failed to load resource"; eles são exibidos na tela e não são erro da aplicação.
    if (m.type() === "error" && !/Failed to load resource: .* status of 4\d\d/.test(m.text())) {
      problemas.push(`console: ${m.text()}`);
    }
  });
  page.on("pageerror", (e) => problemas.push(`pageerror: ${e.message}`));
  page.on("response", (r) => r.status() >= 500 && problemas.push(`HTTP ${r.status()} ${r.url()}`));
  return problemas;
}

async function entrar(page: Page, email: string, senha = SENHA) {
  await page.goto("/login");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill(senha);
  await page.getByRole("button", { name: "Entrar" }).click();
  await page.waitForURL((url) => !url.pathname.endsWith("/login"));
}

async function token(request: APIRequestContext, email: string) {
  const r = await request.post(`${API}/auth/login`, { data: { email, senha: SENHA } });
  return { Authorization: `Bearer ${(await r.json()).token}` };
}

/** Clica o botão de confirmação do modal aberto. */
async function confirmarModal(page: Page, rotulo: string | RegExp) {
  await page.getByRole("dialog").getByRole("button", { name: rotulo }).click();
}

const unico = () => Date.now().toString().slice(-7);

// ───────────────────────── Bibliotecário ─────────────────────────

test("B-14: cadastrar 3 exemplares de livro existente, sem opção de livro novo nem código", async ({ page }) => {
  const problemas = vigiar(page);
  await entrar(page, BIB_VI);
  await page.getByRole("link", { name: "Cadastrar Exemplares" }).click();
  await expect(page).toHaveURL(/\/biblioteca\/exemplares$/);

  await expect(page.getByText(/novo título/i)).toHaveCount(0);
  await expect(page.getByText(/c[oó]digo/i)).toHaveCount(0);

  await page.getByLabel("Buscar livro do catálogo").fill("Dom Casmurro");
  await page.getByRole("row", { name: /Dom Casmurro/ }).getByRole("button", { name: "Selecionar" }).click();
  await page.getByRole("button", { name: "Bom", exact: true }).click();
  await page.getByLabel("Quantidade (1 a 50)").fill("3");
  await page.getByRole("button", { name: /Cadastrar Exemplares/ }).click();
  await expect(page.getByRole("dialog")).toContainText("Dom Casmurro");
  await confirmarModal(page, "Cadastrar");

  await expect(
    page.getByText(/3 exemplar\(es\) de "Dom Casmurro" cadastrado\(s\) na Biblioteca Comunitária de Vila Isabel: Exemplar nº \d+, Exemplar nº \d+, Exemplar nº \d+\./),
  ).toBeVisible();
  expect(problemas).toEqual([]);
});

test("B-14: quantidade fora de 1-50 bloqueia o cadastro", async ({ page }) => {
  await entrar(page, BIB_VI);
  await page.goto("/biblioteca/exemplares");
  await page.getByLabel("Buscar livro do catálogo").fill("Dom Casmurro");
  await page.getByRole("row", { name: /Dom Casmurro/ }).getByRole("button", { name: "Selecionar" }).click();
  await page.getByLabel("Quantidade (1 a 50)").fill("51");
  await expect(page.getByText("Informe uma quantidade de 1 a 50.")).toBeVisible();
  await expect(page.getByRole("button", { name: /Cadastrar Exemplares/ })).toBeDisabled();
});

test("B-15: BibT cadastra o 1º exemplar de Memórias Póstumas; fica DISPONIVEL e emprestável", async ({ page, request }) => {
  const problemas = vigiar(page);
  await entrar(page, BIB_T);
  await page.goto("/biblioteca/exemplares");
  await page.getByLabel("Buscar livro do catálogo").fill("Memórias Póstumas");
  await page.getByRole("row", { name: /Memórias Póstumas de Brás Cubas/ }).getByRole("button", { name: "Selecionar" }).click();
  await page.getByRole("button", { name: /Cadastrar Exemplares/ }).click();
  await confirmarModal(page, "Cadastrar");
  const msg = page.getByText(/1 exemplar\(es\) de "Memórias Póstumas de Brás Cubas" cadastrado\(s\) na Biblioteca Popular da Tijuca/);
  await expect(msg).toBeVisible();
  const numero = Number((await msg.textContent())!.match(/Exemplar nº (\d+)/)![1]);

  // Aparece na lista de empréstimo e pode ser emprestado (sem RN11)
  await page.getByRole("link", { name: "Registrar Empréstimo" }).click();
  await expect(page.getByText(`Exemplar nº ${numero}`)).toBeVisible();
  const auth = await token(request, BIB_T);
  const leitor = await (await request.post(`${API}/auth/cadastro`, {
    data: { nome: `Leitor B15 ${unico()}`, email: `b15.${unico()}@teste.com`, senha: "abc123" },
  })).json();
  const emp = await request.post(`${API}/emprestimos/registrar`, {
    headers: auth, data: { exemplarId: numero, usuarioId: leitor.usuario.id },
  });
  expect(emp.status()).toBe(200);
  expect(problemas).toEqual([]);
});

test("B-16: BibVI cadastrando exemplar na Central pela API recebe 403", async ({ request }) => {
  const auth = await token(request, BIB_VI);
  const r = await request.post(`${API}/exemplares`, {
    headers: auth, data: { livroId: 1, bibliotecaId: CENTRAL, quantidade: 1 },
  });
  expect(r.status()).toBe(403);
  expect(await r.text()).toContain("sua biblioteca");
});

test("B-17: exemplar cadastrado na Central com fila do título nasce RESERVADO e atende o 1º", async ({ page, request }) => {
  const problemas = vigiar(page);
  // Monta a fila com dados próprios (não depende do estado do seed):
  // livro novo, 1 exemplar na Central emprestado a um leitor e outro leitor na fila.
  const n = unico();
  const admin = await token(request, ADMIN);
  const bibC = await token(request, BIB_C);
  const titulo = `Livro com fila ${n}`;
  const livro = await (await request.post(`${API}/livros`, { headers: admin, data: { titulo, autor: "Autor Fila" } })).json();
  const [ex] = await (await request.post(`${API}/exemplares`, { headers: bibC, data: { livroId: livro.id } })).json();
  const novoLeitor = async (nome: string) =>
    (await (await request.post(`${API}/auth/cadastro`, {
      data: { nome, email: `${nome.replace(/\s/g, ".").toLowerCase()}.${n}@teste.com`, senha: "abc123" },
    })).json());
  const tomador = await novoLeitor("Tomador Fila");
  const naFila = await novoLeitor("Leitor Fila");
  expect((await request.post(`${API}/emprestimos/registrar`, {
    headers: bibC, data: { exemplarId: ex.id, usuarioId: tomador.usuario.id },
  })).status()).toBe(200);
  expect((await request.post(`${API}/reservas`, {
    headers: { Authorization: `Bearer ${naFila.token}` },
    data: { livroId: livro.id, bibliotecaFilaId: CENTRAL, bibliotecaDestinoId: CENTRAL },
  })).status()).toBe(200);

  await entrar(page, BIB_C);
  await page.goto("/biblioteca/exemplares");
  await page.getByLabel("Buscar livro do catálogo").fill(titulo);
  await page.getByRole("row", { name: new RegExp(titulo) }).getByRole("button", { name: "Selecionar" }).click();
  await page.getByRole("button", { name: /Cadastrar Exemplares/ }).click();
  await confirmarModal(page, "Cadastrar");
  const msg = page.getByText(/já foi\(ram\) separado\(s\) para quem estava na fila/);
  await expect(msg).toBeVisible();
  const numero = (await msg.textContent())!.match(/Exemplar nº (\d+)/)![1];

  await page.getByRole("link", { name: "Acervo" }).click();
  await page.getByLabel("Buscar no acervo").fill(numero);
  await expect(page.getByRole("row", { name: new RegExp(`Exemplar nº ${numero}\\b`) })).toContainText("Reservado");
  expect(problemas).toEqual([]);
});

test("B-18: marcar indisponível (com motivo) e reativar pelo acervo, com modal", async ({ page }) => {
  const problemas = vigiar(page);
  await entrar(page, BIB_VI);
  await page.getByRole("link", { name: "Acervo" }).click();
  await expect(page).toHaveURL(/\/biblioteca\/acervo$/);
  await page.getByRole("button", { name: /^Disponíveis/ }).click();

  const linha = page.getByRole("row").filter({ has: page.getByRole("button", { name: "Marcar indisponível" }) }).first();
  const numero = (await linha.getByRole("cell").first().textContent())!.match(/Exemplar nº (\d+)/)![1];
  await linha.getByRole("button", { name: "Marcar indisponível" }).click();
  await page.getByRole("dialog").getByLabel("Motivo").fill("Capa rasgada");
  await confirmarModal(page, "Marcar indisponível");
  await expect(page.getByText(`Exemplar nº ${numero} (`)).toBeVisible();
  await expect(page.getByText(/marcado como indisponível/)).toBeVisible();

  await page.getByRole("button", { name: /^Indisponíveis/ }).click();
  await page.getByLabel("Buscar no acervo").fill(numero);
  const linhaIndisp = page.getByRole("row", { name: new RegExp(`Exemplar nº ${numero}\\b`) });
  await expect(linhaIndisp).toContainText("Indisponível");
  await linhaIndisp.getByRole("button", { name: "Reativar" }).click();
  await confirmarModal(page, "Reativar");
  await expect(page.getByText(/reativado: agora disponível/)).toBeVisible();
  expect(problemas).toEqual([]);
});

test("B-19: bibliotecário não cria livro/categoria (403) e não tem tela para isso", async ({ page, request }) => {
  const auth = await token(request, BIB_VI);
  expect((await request.post(`${API}/livros`, { headers: auth, data: { titulo: "X", autor: "Y" } })).status()).toBe(403);
  expect((await request.post(`${API}/categorias`, { headers: auth, data: { nome: "X" } })).status()).toBe(403);
  expect((await request.put(`${API}/livros/1`, { headers: auth, data: { titulo: "X", autor: "Y" } })).status()).toBe(403);

  await entrar(page, BIB_VI);
  await expect(page.getByRole("link", { name: "Catálogo" })).toHaveCount(0);
  await page.goto("/admin/catalogo");
  await expect(page).toHaveURL(/\/biblioteca$/);
});

test("Menus do bibliotecário levam a telas reais (sem 'Em construção')", async ({ page }) => {
  const problemas = vigiar(page);
  await entrar(page, BIB_VI);
  for (const item of ["Empréstimos", "Registrar Empréstimo", "Registrar Devolução", "Cadastrar Exemplares", "Acervo"]) {
    await page.locator("header nav").getByRole("link", { name: item, exact: true }).click();
    await expect(page.locator("main h1")).toBeVisible();
    await expect(page.getByText("Em construção")).toHaveCount(0);
  }
  expect(problemas).toEqual([]);
});

// ───────────────────────── Administrador ─────────────────────────

test("A-12 e A-13: criar categoria e livro; aparece sem exemplares; editar; bibliotecário recebe 403", async ({ page, browser, request }) => {
  const problemas = vigiar(page);
  const n = unico();
  const categoria = `Categoria E2E ${n}`;
  const titulo = `Livro E2E ${n}`;

  await entrar(page, ADMIN);
  await page.getByRole("link", { name: "Categorias" }).click();
  await page.getByLabel("Nome").fill(categoria);
  await page.getByRole("button", { name: "Cadastrar categoria" }).click();
  await confirmarModal(page, "Cadastrar");
  await expect(page.getByText(`Categoria "${categoria}" cadastrada.`)).toBeVisible();

  await page.getByRole("link", { name: "Catálogo" }).click();
  await expect(page.getByRole("heading", { name: "Catálogo de Livros" })).toBeVisible();
  await page.getByLabel("Título").fill(titulo);
  await page.getByLabel("Autor").fill("Autora E2E");
  await page.getByLabel("ISBN").fill(`978-65-${n.slice(0, 5)}-${n.slice(5, 7)}-1`);
  await page.getByLabel("Ano de publicação").fill("2024");
  await page.getByLabel("Categoria").selectOption({ label: categoria });
  await page.getByRole("button", { name: "Cadastrar livro" }).click();
  await confirmarModal(page, "Cadastrar");
  await expect(page.getByText(`Livro "${titulo}" cadastrado.`)).toBeVisible();

  // ISBN repetido: mensagem do servidor
  await page.getByLabel("Título").fill(`${titulo} cópia`);
  await page.getByLabel("Autor").fill("Outra");
  await page.getByLabel("ISBN").fill(`978-65-${n.slice(0, 5)}-${n.slice(5, 7)}-1`);
  await page.getByRole("button", { name: "Cadastrar livro" }).click();
  await confirmarModal(page, "Cadastrar");
  await expect(page.getByText(/Já existe um livro com o ISBN/)).toBeVisible();

  // A-13: editar
  await page.getByLabel("Buscar no catálogo").fill(titulo);
  await page.getByRole("row", { name: new RegExp(titulo) }).getByRole("button", { name: "Editar" }).click();
  await page.getByLabel("Título").fill(`${titulo} (revisado)`);
  await page.getByRole("button", { name: "Salvar alterações" }).click();
  await confirmarModal(page, "Salvar");
  await expect(page.getByText(`Livro "${titulo} (revisado)" atualizado.`)).toBeVisible();
  await page.getByLabel("Buscar no catálogo").fill(`${titulo} (revisado)`);
  await expect(page.getByRole("row", { name: new RegExp(`${titulo} \\(revisado\\)`) })).toContainText(categoria);

  const livros = await (await request.get(`${API}/livros`, { headers: await token(request, ADMIN) })).json();
  const criado = livros.find((l: { titulo: string }) => l.titulo === `${titulo} (revisado)`);
  const r = await request.put(`${API}/livros/${criado.id}`, {
    headers: await token(request, BIB_VI), data: { titulo: "Hackeado", autor: "X" },
  });
  expect(r.status()).toBe(403);

  // A-12: na busca do usuário comum, "Indisponível: sem exemplares"
  const ctx = await browser.newContext();
  const pagU1 = await ctx.newPage();
  await entrar(pagU1, U1);
  await pagU1.getByLabel("Buscar por título, autor ou ISBN").fill(titulo);
  await pagU1.getByRole("button", { name: "Buscar" }).click();
  await expect(pagU1.getByText(`${titulo} (revisado)`)).toBeVisible();
  await expect(pagU1.getByText("Indisponível: sem exemplares")).toBeVisible();
  await ctx.close();
  expect(problemas).toEqual([]);
});

test("A-14 e A-16: Admin cria e edita biblioteca, cria bibliotecário vinculado; ele vê só a sua", async ({ page }) => {
  const problemas = vigiar(page);
  const n = unico();
  // Padrão da §8.1 para a unidade "Unidade<n>": Bibliotecário Unidade<n> / bibliotecariounidade<n>@circulabook.com
  const nomeBib = `Biblioteca Unidade${n}`;
  const email = `bibliotecariounidade${n}@circulabook.com`;

  await entrar(page, ADMIN);
  await page.getByRole("link", { name: "Bibliotecas", exact: true }).click();
  await page.getByLabel("Nome").fill(`${nomeBib} provisória`);
  await page.getByLabel("Endereço").fill("Rua das Flores, 10");
  await page.getByLabel("E-mail").fill(`unidade${n}@circulabook.com`);
  await page.getByLabel("Telefone").fill("(21) 3000-9999");
  await page.getByRole("button", { name: "Cadastrar biblioteca" }).click();
  await confirmarModal(page, "Confirmar");
  await expect(page.getByText(`${nomeBib} provisória cadastrada.`)).toBeVisible();

  // Editar (A-16)
  await page.getByLabel("Buscar biblioteca").fill(`${nomeBib} provisória`);
  await page.getByRole("row", { name: new RegExp(nomeBib) }).getByRole("button", { name: "Editar" }).click();
  await page.getByLabel("Nome").fill(nomeBib);
  await page.getByRole("button", { name: "Salvar alterações" }).click();
  await confirmarModal(page, "Confirmar");
  await expect(page.getByText(`${nomeBib} atualizada.`)).toBeVisible();

  // Bibliotecário vinculado (A-14)
  await page.getByRole("link", { name: "Bibliotecários" }).click();
  await expect(page.getByRole("heading", { name: "Bibliotecários", exact: true })).toBeVisible();
  await expect(page.getByRole("option", { name: /comum/i })).toHaveCount(0);
  await page.getByLabel("Nome").fill(`Bibliotecário Unidade${n}`);
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha inicial (mínimo 6 caracteres)").fill("inicial123");
  await page.getByLabel("Biblioteca").selectOption({ label: nomeBib });
  await page.getByRole("button", { name: "Cadastrar bibliotecário" }).click();
  await confirmarModal(page, "Confirmar");
  await expect(page.getByText(new RegExp(`cadastrado\\(a\\) como bibliotecário\\(a\\) da ${nomeBib}`))).toBeVisible();

  // Mesmo e-mail de novo: erro do servidor
  await page.getByLabel("Nome").fill("Repetida");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha inicial (mínimo 6 caracteres)").fill("inicial123");
  await page.getByLabel("Biblioteca").selectOption({ label: nomeBib });
  await page.getByRole("button", { name: "Cadastrar bibliotecário" }).click();
  await confirmarModal(page, "Confirmar");
  await expect(page.getByText("Já existe uma conta com este e-mail.")).toBeVisible();

  // Loga com o novo bibliotecário: só a nova biblioteca (acervo vazio)
  await page.getByRole("button", { name: "Sair" }).click();
  await entrar(page, email, "inicial123");
  await expect(page).toHaveURL(/\/biblioteca$/);
  await expect(page.getByTestId("identificacao")).toContainText(`Bibliotecário — ${nomeBib}`);
  await expect(page.locator("main h1")).toHaveText("Empréstimos da Biblioteca");
  await page.getByRole("link", { name: "Acervo" }).click();
  await expect(page).toHaveURL(/\/biblioteca\/acervo$/);
  await expect(page.getByText(nomeBib).first()).toBeVisible();
  await expect(page.getByText("Nenhum exemplar encontrado.")).toBeVisible();
  expect(problemas).toEqual([]);
});

test("A-16: desativar e reativar biblioteca com modal", async ({ page }) => {
  const problemas = vigiar(page);
  const n = unico();
  await entrar(page, ADMIN);
  await page.getByRole("link", { name: "Bibliotecas", exact: true }).click();
  await page.getByLabel("Nome").fill(`Biblioteca Temporária ${n}`);
  await page.getByRole("button", { name: "Cadastrar biblioteca" }).click();
  await confirmarModal(page, "Confirmar");
  await page.getByLabel("Buscar biblioteca").fill(`Temporária ${n}`);
  const linha = page.getByRole("row", { name: new RegExp(`Temporária ${n}`) });
  await linha.getByRole("button", { name: "Desativar" }).click();
  await confirmarModal(page, "Desativar");
  await expect(page.getByText(`Biblioteca Temporária ${n} desativada.`)).toBeVisible();
  await expect(linha).toContainText("Inativa");
  await linha.getByRole("button", { name: "Reativar" }).click();
  await confirmarModal(page, "Confirmar");
  await expect(linha).toContainText("Ativa");
  expect(problemas).toEqual([]);
});

test("A-15: Admin não cria COMUM, exemplar, empréstimo nem devolução (UI e API)", async ({ page, request }) => {
  const auth = await token(request, ADMIN);
  expect((await request.post(`${API}/exemplares`, { headers: auth, data: { livroId: 1 } })).status()).toBe(403);
  expect((await request.post(`${API}/emprestimos/registrar`, { headers: auth, data: { exemplarId: 1, usuarioId: 6 } })).status()).toBe(403);
  expect((await request.post(`${API}/emprestimos/devolver`, { headers: auth, data: { emprestimoId: 1 } })).status()).toBe(403);
  const comum = await request.post(`${API}/usuarios/bibliotecarios`, {
    headers: auth, data: { nome: "Comum", email: `comum.${unico()}@teste.com`, senha: "abc123", bibliotecaId: 1, tipo: "COMUM" },
  });
  expect(comum.status()).toBe(400);
  expect(await comum.text()).toContain("só cadastra bibliotecários");

  await entrar(page, ADMIN);
  for (const proibido of ["Cadastrar Exemplares", "Registrar Empréstimo", "Registrar Devolução", "Acervo"]) {
    await expect(page.getByRole("link", { name: proibido, exact: true })).toHaveCount(0);
  }
  for (const rota of ["/biblioteca/exemplares", "/biblioteca/emprestimo", "/biblioteca/devolucao"]) {
    await page.goto(rota);
    await expect(page).toHaveURL(/\/admin$/);
  }
  await page.goto("/admin/bibliotecarios");
  await expect(page.getByText(/Usuários da comunidade se cadastram sozinhos/)).toBeVisible();
  await expect(page.locator("main select option", { hasText: /comum/i })).toHaveCount(0);
});

test("Menus do Admin levam a telas reais (sem 'Em construção')", async ({ page }) => {
  const problemas = vigiar(page);
  await entrar(page, ADMIN);
  for (const item of ["Transferências", "Catálogo", "Categorias", "Bibliotecas", "Bibliotecários"]) {
    await page.locator("header nav").getByRole("link", { name: item, exact: true }).click();
    await expect(page.locator("main h1")).toBeVisible();
    await expect(page.getByText("Em construção")).toHaveCount(0);
  }
  expect(problemas).toEqual([]);
});
