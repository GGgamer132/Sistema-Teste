import { expect, type APIRequestContext, type Page } from "@playwright/test";

// Apoio comum aos specs: credenciais do seed (§8.1) e busca de dados por NOME
// (nunca por id fixo), já que os ids podem mudar se o seed mudar.

export const API = "http://localhost:8080/api";
export const SENHA = "senha123";

export const ADMIN = "admin@circulabook.com";
export const BIB_C = "bibliotecariocentral@circulabook.com";
export const BIB_VI = "bibliotecariovilaisabel@circulabook.com";
export const BIB_T = "bibliotecariotijuca@circulabook.com";
export const U1 = "usuario1@circulabook.com";
export const U2 = "usuario2@circulabook.com";
export const U3 = "usuario3@circulabook.com";

export const CENTRAL_NOME = "Biblioteca Central";
export const VI_NOME = "Biblioteca Comunitária de Vila Isabel";
export const TIJUCA_NOME = "Biblioteca Popular da Tijuca";

export type Cabecalho = { Authorization: string };

/** Coleta erros de console, exceções da página e respostas 5xx (4xx esperados ficam de fora). */
export function vigiar(page: Page) {
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

export async function entrar(page: Page, email: string, senha = SENHA) {
  await page.goto("/login");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill(senha);
  await page.getByRole("button", { name: "Entrar" }).click();
  await page.waitForURL((url) => !url.pathname.endsWith("/login"));
}

export async function sair(page: Page) {
  await page.getByRole("button", { name: "Sair" }).click();
  await page.waitForURL(/\/login$/);
}

export async function token(request: APIRequestContext, email: string): Promise<Cabecalho> {
  const r = await request.post(`${API}/auth/login`, { data: { email, senha: SENHA } });
  expect(r.ok(), `login de ${email}`).toBeTruthy();
  return { Authorization: `Bearer ${(await r.json()).token}` };
}

export async function ok<T>(resp: Promise<import("@playwright/test").APIResponse>): Promise<T> {
  const r = await resp;
  if (!r.ok()) throw new Error(`${r.status()} ${r.url()}: ${await r.text()}`);
  return (await r.json()) as T;
}

/** Id da biblioteca pelo nome exato. */
export async function idBiblioteca(request: APIRequestContext, nome: string): Promise<number> {
  const h = await token(request, ADMIN);
  const lista = await ok<{ id: number; nome: string }[]>(request.get(`${API}/bibliotecas`, { headers: h }));
  const b = lista.find((x) => x.nome === nome);
  if (!b) throw new Error(`Biblioteca "${nome}" não está no seed`);
  return b.id;
}

/** Id do livro pelo título exato. */
export async function idLivro(request: APIRequestContext, titulo: string): Promise<number> {
  const h = await token(request, ADMIN);
  const lista = await ok<{ id: number; titulo: string }[]>(request.get(`${API}/livros`, { headers: h }));
  const l = lista.find((x) => x.titulo === titulo);
  if (!l) throw new Error(`Livro "${titulo}" não está no seed`);
  return l.id;
}

export const confirmarModal = (page: Page, rotulo: string | RegExp) =>
  page.getByRole("dialog").getByRole("button", { name: rotulo }).click();

/** dd/mm/aaaa daqui a N dias (negativo = no passado). */
export const dataEmDias = (dias: number) => new Date(Date.now() + dias * 86_400_000).toLocaleDateString("pt-BR");
