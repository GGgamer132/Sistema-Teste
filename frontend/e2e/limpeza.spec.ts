import { expect, test, type Page } from "@playwright/test";

// Etapa de limpeza: sem código de barras, sem RN11/PERDIDO, prazo fixo de 14 dias.
// Toda tela percorrida não pode gerar erro 500 nem erro no console.

const SENHA = "senha123";

function vigiar(page: Page) {
  const problemas: string[] = [];
  page.on("console", (m) => {
    if (m.type() === "error") problemas.push(`console: ${m.text()}`);
  });
  page.on("pageerror", (e) => problemas.push(`pageerror: ${e.message}`));
  page.on("response", (r) => {
    if (r.status() >= 500) problemas.push(`HTTP ${r.status()} ${r.url()}`);
  });
  return problemas;
}

async function entrar(page: Page, email: string) {
  await page.goto("/login");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill(SENHA);
  await page.getByRole("button", { name: "Entrar" }).click();
}

const SEM_JARGAO = /c[oó]digo de barras|tombo|RN\d{2}|Perdido/i;

test("COMUM: busca de livros e telas do usuário", async ({ page }) => {
  const problemas = vigiar(page);
  await entrar(page, "usuario2@circulabook.com"); // seed: Usuário 2 tem empréstimos ativos
  await expect(page).toHaveURL(/5173\/$/);
  await page.getByLabel("Buscar por título, autor ou ISBN").fill("Dom Casmurro");
  await page.getByRole("button", { name: "Buscar" }).click();
  await expect(page).toHaveURL(/\/resultados/);
  await expect(page.getByText("Dom Casmurro").first()).toBeVisible();
  await page.getByRole("link", { name: "Meus Empréstimos" }).click();
  await expect(page.getByText(/Exemplar nº \d+/).first()).toBeVisible();
  await page.getByRole("link", { name: "Minhas Reservas" }).click();
  await expect(page).toHaveURL(/\/minhas-reservas$/);
  await expect(page.locator("body")).not.toContainText(SEM_JARGAO);
  expect(problemas).toEqual([]);
});

test("BIBLIOTECARIO: empréstimo sem seletor de prazo, devolução em 14 dias", async ({ page }) => {
  const problemas = vigiar(page);
  await entrar(page, "bibliotecariovilaisabel@circulabook.com");
  await expect(page).toHaveURL(/\/biblioteca$/);
  await page.getByRole("link", { name: "Registrar Empréstimo" }).click();

  await expect(page.locator("main select")).toHaveCount(0);
  await expect(page.locator('input[value="14 dias corridos"]')).toBeVisible();
  await expect(page.getByText(/Exemplar nº \d+/).first()).toBeVisible();
  await expect(page.locator("body")).not.toContainText(SEM_JARGAO);

  // Tomador novo a cada execução, para não depender de empréstimos anteriores
  const nome = `Leitor E2E ${Date.now()}`;
  const cadastro = await page.request.post("http://localhost:8080/api/auth/cadastro", {
    data: { nome, email: `leitor.${Date.now()}@teste.com`, senha: "abc123" },
  });
  expect(cadastro.ok()).toBeTruthy();
  await page.reload();
  await page.getByLabel("Nome ou e-mail do usuário").fill(nome);
  await page.getByRole("button", { name: new RegExp(nome) }).click();
  await expect(page.getByText("Nenhuma pendência encontrada")).toBeVisible();
  await page.getByRole("button", { name: "Selecionar" }).first().click();
  await page.getByRole("button", { name: /Confirmar Empréstimo/ }).click();
  await expect(page.getByText("Confirmar empréstimo?")).toBeVisible();
  await page.getByRole("button", { name: "Confirmar empréstimo", exact: true }).click();

  const esperado = new Date(Date.now() + 14 * 86_400_000).toLocaleDateString("pt-BR");
  await expect(page.getByText(`devolução prevista em ${esperado}`)).toBeVisible();

  // Devolução: sem opção "Perdido", exemplares por número
  await page.getByRole("link", { name: "Registrar Devolução" }).click();
  await expect(page.getByText(/Exemplar nº \d+/).first()).toBeVisible();
  await expect(page.locator("body")).not.toContainText(SEM_JARGAO);

  // Cadastro de exemplar: sem campo de código
  await page.getByRole("link", { name: "Cadastrar Exemplar" }).click();
  await expect(page).toHaveURL(/\/biblioteca\/exemplares$/);
  await expect(page.locator("body")).not.toContainText(SEM_JARGAO);
  await expect(page.getByText(/único exemplar/i)).toHaveCount(0);
  expect(problemas).toEqual([]);
});

test("ADMIN: transferência avulsa lista exemplares por número", async ({ page }) => {
  const problemas = vigiar(page);
  await entrar(page, "admin@circulabook.com");
  await expect(page).toHaveURL(/\/admin$/);
  await page.getByRole("link", { name: "Transferências", exact: true }).click();
  await page.getByRole("button", { name: /Nova transferência avulsa/ }).click();
  await expect(page.getByRole("columnheader", { name: "Exemplar" }).last()).toBeVisible();
  await expect(page.getByText(/Exemplar nº \d+/).first()).toBeVisible();
  await expect(page.locator("body")).not.toContainText(SEM_JARGAO);
  expect(problemas).toEqual([]);
});
