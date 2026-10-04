import { expect, test, type Page } from "@playwright/test";

// Usuários de demonstração (seção 8.1 do CONTEXTO), senha "senha123"
const ANA = "ana.souza@email.com";
const CARLOS = "carlos.lima@circulabook.org.br";
const ROBERTO = "roberto.dias@circulabook.org.br";
const CHAVE = "circulabook.sessao";

async function entrar(page: Page, email: string, senha = "senha123") {
  await page.goto("/login");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill(senha);
  await page.getByRole("button", { name: "Entrar" }).click();
}

test.describe("Sem sessão", () => {
  for (const rota of ["/", "/meus-emprestimos", "/biblioteca", "/admin", "/admin/transferencias"]) {
    test(`${rota} redireciona para /login`, async ({ page }) => {
      await page.goto(rota);
      await expect(page).toHaveURL(/\/login$/);
      await expect(page.getByRole("link", { name: "Cadastre-se" })).toBeVisible();
    });
  }
});

test.describe("Login", () => {
  test("COMUM entra e cai em /", async ({ page }) => {
    await entrar(page, ANA);
    await expect(page).toHaveURL(/5173\/$/);
    await expect(page.getByTestId("identificacao")).toContainText("Ana Souza · Usuário da Comunidade");
    await expect(page.locator("header select")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Sair" })).toBeVisible();
  });

  test("BIBLIOTECARIO entra e cai em /biblioteca com dados da própria biblioteca", async ({ page }) => {
    await entrar(page, CARLOS);
    await expect(page).toHaveURL(/\/biblioteca$/);
    await expect(page.getByTestId("identificacao")).toContainText("Carlos Lima · Bibliotecário — Biblioteca Vila Isabel");
    await expect(page.getByText("Biblioteca Vila Isabel · acompanhe")).toBeVisible();
    await expect(page.locator("header select")).toHaveCount(0);
  });

  test("ADMIN entra e cai em /admin/catalogo; tela de transferências carrega com o token", async ({ page }) => {
    await entrar(page, ROBERTO);
    await expect(page).toHaveURL(/\/admin\/catalogo$/);
    await expect(page.getByTestId("identificacao")).toContainText("Roberto Dias · Administrador da Rede");
    await page.getByRole("link", { name: "Transferências" }).click();
    await expect(page).toHaveURL(/\/admin\/transferencias$/);
    await expect(page.getByText(/Acesso negado|Sessão inválida|Falha na requisição/)).toHaveCount(0);
  });

  test("senha errada mostra erro genérico e continua no /login", async ({ page }) => {
    await entrar(page, ANA, "errada");
    await expect(page.getByText("E-mail ou senha inválidos")).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
    expect(await page.evaluate((k) => localStorage.getItem(k), CHAVE)).toBeNull();
  });

  test("e-mail inexistente mostra o mesmo erro genérico", async ({ page }) => {
    await entrar(page, "ninguem@email.com");
    await expect(page.getByText("E-mail ou senha inválidos")).toBeVisible();
  });

  test("COMUM vê os próprios empréstimos (API autorizada pelo token)", async ({ page }) => {
    await entrar(page, ANA);
    await expect(page).toHaveURL(/5173\/$/);
    await page.getByRole("link", { name: "Meus Empréstimos" }).click();
    await expect(page).toHaveURL(/\/meus-emprestimos$/);
    await expect(page.getByText(/Acesso negado|Sessão inválida|Usuário não encontrado/)).toHaveCount(0);
  });
});

test.describe("Autocadastro", () => {
  test("cadastro válido cria COMUM e já entra logado", async ({ page }) => {
    const email = `e2e.${Date.now()}@email.com`;
    await page.goto("/login");
    await page.getByRole("link", { name: "Cadastre-se" }).click();
    await expect(page).toHaveURL(/\/cadastro$/);
    await page.getByLabel("Nome").fill("Usuária E2E");
    await page.getByLabel("E-mail").fill(email);
    await page.getByLabel("Senha (mínimo 6 caracteres)").fill("abc123");
    await page.getByLabel("Confirmar senha").fill("abc123");
    await page.getByRole("button", { name: "Cadastrar" }).click();
    await expect(page).toHaveURL(/5173\/$/);
    await expect(page.getByTestId("identificacao")).toContainText("Usuária E2E · Usuário da Comunidade");
  });

  test("confirmação diferente mostra erro", async ({ page }) => {
    await page.goto("/cadastro");
    await page.getByLabel("Nome").fill("Fulano");
    await page.getByLabel("E-mail").fill("fulano@email.com");
    await page.getByLabel("Senha (mínimo 6 caracteres)").fill("abc123");
    await page.getByLabel("Confirmar senha").fill("abc124");
    await page.getByRole("button", { name: "Cadastrar" }).click();
    await expect(page.getByText("A confirmação não confere com a senha.")).toBeVisible();
    await expect(page).toHaveURL(/\/cadastro$/);
  });

  test("senha curta mostra erro", async ({ page }) => {
    await page.goto("/cadastro");
    await page.getByLabel("Nome").fill("Fulano");
    await page.getByLabel("E-mail").fill("fulano@email.com");
    await page.getByLabel("Senha (mínimo 6 caracteres)").fill("123");
    await page.getByLabel("Confirmar senha").fill("123");
    await page.getByRole("button", { name: "Cadastrar" }).click();
    await expect(page.getByText("A senha deve ter pelo menos 6 caracteres.")).toBeVisible();
  });

  test("e-mail repetido mostra o erro do servidor", async ({ page }) => {
    await page.goto("/cadastro");
    await page.getByLabel("Nome").fill("Outra Ana");
    await page.getByLabel("E-mail").fill(ANA);
    await page.getByLabel("Senha (mínimo 6 caracteres)").fill("abc123");
    await page.getByLabel("Confirmar senha").fill("abc123");
    await page.getByRole("button", { name: "Cadastrar" }).click();
    await expect(page.getByText("Já existe uma conta com este e-mail.")).toBeVisible();
    await expect(page).toHaveURL(/\/cadastro$/);
  });
});

test.describe("Autorização por perfil no front", () => {
  test("COMUM abrindo /admin e /biblioteca volta para /", async ({ page }) => {
    await entrar(page, ANA);
    await expect(page).toHaveURL(/5173\/$/);
    for (const rota of ["/admin", "/admin/transferencias", "/biblioteca", "/biblioteca/emprestimo"]) {
      await page.goto(rota);
      await expect(page).toHaveURL(/5173\/$/);
    }
  });

  test("BIBLIOTECARIO abrindo /admin volta para /biblioteca", async ({ page }) => {
    await entrar(page, CARLOS);
    await expect(page).toHaveURL(/\/biblioteca$/);
    await page.goto("/admin");
    await expect(page).toHaveURL(/\/biblioteca$/);
  });

  test("logado, /login volta para a tela inicial do perfil", async ({ page }) => {
    await entrar(page, ROBERTO);
    await expect(page).toHaveURL(/\/admin\/catalogo$/);
    await page.goto("/login");
    await expect(page).toHaveURL(/\/admin\/catalogo$/);
  });
});

test.describe("Sessão", () => {
  test("Sair limpa a sessão e bloqueia as rotas", async ({ page }) => {
    await entrar(page, ANA);
    await expect(page).toHaveURL(/5173\/$/);
    await page.getByRole("button", { name: "Sair" }).click();
    await expect(page).toHaveURL(/\/login$/);
    expect(await page.evaluate((k) => localStorage.getItem(k), CHAVE)).toBeNull();
    await page.goto("/meus-emprestimos");
    await expect(page).toHaveURL(/\/login$/);
  });

  test("token removido do localStorage volta ao /login", async ({ page }) => {
    await entrar(page, ANA);
    await expect(page).toHaveURL(/5173\/$/);
    await page.evaluate((k) => localStorage.removeItem(k), CHAVE);
    await page.goto("/minhas-reservas");
    await expect(page).toHaveURL(/\/login$/);
  });

  test("token adulterado volta ao /login (servidor responde 401)", async ({ page }) => {
    await entrar(page, ANA);
    await expect(page).toHaveURL(/5173\/$/);
    await page.evaluate((k) => {
      const s = JSON.parse(localStorage.getItem(k)!);
      s.token = s.token.slice(0, -4) + "xxxx";
      localStorage.setItem(k, JSON.stringify(s));
    }, CHAVE);
    await page.goto("/meus-emprestimos");
    await expect(page).toHaveURL(/\/login$/);
    expect(await page.evaluate((k) => localStorage.getItem(k), CHAVE)).toBeNull();
  });

  test("token adulterado sem recarregar: a próxima chamada à API leva ao /login", async ({ page }) => {
    await entrar(page, CARLOS);
    await expect(page).toHaveURL(/\/biblioteca$/);
    await page.evaluate((k) => {
      const s = JSON.parse(localStorage.getItem(k)!);
      s.token = s.token.slice(0, -4) + "xxxx";
      localStorage.setItem(k, JSON.stringify(s));
    }, CHAVE);
    await page.getByRole("link", { name: "Registrar Devolução" }).click();
    await expect(page).toHaveURL(/\/login$/);
  });
});

test.describe("Telas que usavam ator fixo agora usam a sessão", () => {
  const SEM_ERRO = /Acesso negado|Sessão inválida|Usuário não encontrado|Falha na requisição/;

  test("COMUM: Minhas Reservas carrega só as próprias", async ({ page }) => {
    await entrar(page, "bruno.alves@email.com");
    await page.getByRole("link", { name: "Minhas Reservas" }).click();
    await expect(page).toHaveURL(/\/minhas-reservas$/);
    await expect(page.getByText(SEM_ERRO)).toHaveCount(0);
    await expect(page.getByText("Ana Souza")).toHaveCount(0);
  });

  test("BIBLIOTECARIO: telas do balcão carregam com a biblioteca da sessão", async ({ page }) => {
    await entrar(page, "fernanda.reis@circulabook.org.br");
    await expect(page).toHaveURL(/\/biblioteca$/);
    await expect(page.getByText("Biblioteca Central · acompanhe")).toBeVisible();
    for (const [link, texto] of [
      ["Registrar Empréstimo", "Biblioteca Central · identifique"],
      ["Registrar Devolução", "Biblioteca Central · UC10"],
    ]) {
      await page.getByRole("link", { name: link }).click();
      await expect(page.getByText(texto)).toBeVisible();
      await expect(page.getByText(SEM_ERRO)).toHaveCount(0);
    }
    await page.getByRole("link", { name: "Cadastrar Exemplar" }).click();
    await expect(page).toHaveURL(/\/biblioteca\/exemplares$/);
    await expect(page.getByText(SEM_ERRO)).toHaveCount(0);
  });

  test("ADMIN: transferência em trânsito não oferece 'Confirmar chegada'", async ({ page }) => {
    await entrar(page, ROBERTO);
    await page.goto("/admin/transferencias");
    await expect(page.getByText(SEM_ERRO)).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Confirmar chegada" })).toHaveCount(0);
  });
});
