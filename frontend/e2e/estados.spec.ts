import { expect, test } from "@playwright/test";

// Etapa 2 (máquina de estados): devolução DANIFICADO pela tela, sem erro 500 nem de console.
// Marcar indisponível/reativar não tem tela ainda; é coberto pelos testes do backend e por curl.

const API = "http://localhost:8080/api";

test("BIBLIOTECARIO: devolução DANIFICADO tira o exemplar de circulação", async ({ page, request }) => {
  const problemas: string[] = [];
  page.on("console", (m) => m.type() === "error" && problemas.push(`console: ${m.text()}`));
  page.on("pageerror", (e) => problemas.push(`pageerror: ${e.message}`));
  page.on("response", (r) => r.status() >= 500 && problemas.push(`HTTP ${r.status()} ${r.url()}`));

  // Dados próprios do teste: um leitor novo pega emprestado um exemplar disponível da VI
  const login = await request.post(`${API}/auth/login`, {
    data: { email: "carlos.lima@circulabook.org.br", senha: "senha123" },
  });
  const { token } = await login.json();
  const auth = { Authorization: `Bearer ${token}` };
  const nome = `Leitor Danificado ${Date.now()}`;
  const cad = await request.post(`${API}/auth/cadastro`, {
    data: { nome, email: `danificado.${Date.now()}@email.com`, senha: "abc123" },
  });
  const leitor = (await cad.json()).usuario;
  const exemplares = await (await request.get(`${API}/exemplares/biblioteca/1`, { headers: auth })).json();
  const livre = exemplares.find((e: { status: string }) => e.status === "DISPONIVEL");
  const emp = await request.post(`${API}/emprestimos/registrar`, {
    headers: auth,
    data: { exemplarId: livre.id, usuarioId: leitor.id },
  });
  expect(emp.ok()).toBeTruthy();

  // Fluxo na tela
  await page.goto("/login");
  await page.getByLabel("E-mail").fill("carlos.lima@circulabook.org.br");
  await page.getByLabel("Senha").fill("senha123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page).toHaveURL(/\/biblioteca$/);
  await page.getByRole("link", { name: "Registrar Devolução" }).click();
  await page.getByPlaceholder("Digitar nome do usuário...").fill(nome);
  await page.getByRole("button", { name: new RegExp(`Exemplar nº ${livre.id}`) }).click();
  await page.getByRole("button", { name: "Danificado", exact: true }).click();
  await page.getByRole("button", { name: /Confirmar Devolução/ }).click();
  await page.getByRole("button", { name: "Confirmar devolução", exact: true }).click();
  await expect(page.getByText("O exemplar danificado saiu de circulação")).toBeVisible();

  const depois = await (await request.get(`${API}/exemplares/biblioteca/1`, { headers: auth })).json();
  const exemplar = depois.find((e: { id: number }) => e.id === livre.id);
  expect(exemplar.status).toBe("INDISPONIVEL");
  expect(exemplar.estadoConservacao).toBe("DANIFICADO");
  expect(problemas).toEqual([]);
});
