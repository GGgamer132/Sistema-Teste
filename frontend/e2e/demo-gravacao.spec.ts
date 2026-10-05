import { expect, test, type Page } from "@playwright/test";
import { writeFileSync } from "node:fs";
import {
  ADMIN, API, BIB_C, BIB_T, BIB_VI, CENTRAL_NOME, TIJUCA_NOME, U1, U2, U3, VI_NOME,
  confirmarModal, dataEmDias, entrar, idLivro, ok, sair, token, vigiar,
} from "./apoio";

// Roteiro de gravação do vídeo (§9.0), cenas 1 a 7, NA ORDEM, a partir do seed limpo
// (o globalSetup reinicia o backend antes). As cenas dependem umas das outras.
// DEMO_LENTA=1 abre o navegador com slowMo e pausas curtas, para ensaiar a gravação:
//   DEMO_LENTA=1 npx playwright test e2e/demo-gravacao.spec.ts   (PowerShell: $env:DEMO_LENTA=1; ...)

const LENTA = process.env.DEMO_LENTA === "1";
test.use({ actionTimeout: 15_000, ...(LENTA ? { headless: false, launchOptions: { slowMo: 300 } } : {}) });
test.setTimeout(LENTA ? 20 * 60_000 : 5 * 60_000);

/** Pausa para quem assiste (só com DEMO_LENTA). */
const pausa = (page: Page, ms = 1200) => (LENTA ? page.waitForTimeout(ms) : Promise.resolve());

const sino = (page: Page) => page.getByRole("button", { name: /^Notificações/ });
const painelSino = (page: Page) => page.getByRole("dialog", { name: "Notificações" });
const menu = (page: Page) => page.getByRole("navigation");

async function buscar(page: Page, termo: string) {
  await page.goto("/");
  await page.getByLabel("Buscar por título, autor ou ISBN").fill(termo);
  await page.getByRole("button", { name: "Buscar", exact: true }).click();
  await expect(page).toHaveURL(/\/resultados\?/);
}

async function abrirLivro(page: Page, titulo: string) {
  await buscar(page, titulo);
  await page.getByRole("button", { name: "Ver detalhes" }).first().click();
  await expect(page).toHaveURL(/\/livro\/\d+$/);
  await expect(page.getByRole("heading", { name: titulo }).first()).toBeVisible();
}

const linhaBiblioteca = (page: Page, nome: string) => page.getByRole("row").filter({ hasText: nome });

/** Devolução pela tela "Registrar Devolução" do bibliotecário logado. */
async function devolver(page: Page, leitor: string, titulo: string) {
  await page.getByRole("link", { name: "Registrar Devolução" }).click();
  await page.getByPlaceholder("Digitar nome do usuário...").fill(leitor);
  await page.getByRole("button", { name: new RegExp(titulo) }).click();
  await page.getByRole("button", { name: "Bom estado" }).click();
}

async function confirmarDevolucao(page: Page) {
  await page.getByRole("button", { name: /Confirmar Devolução/ }).click();
  await page.getByRole("button", { name: "Confirmar devolução", exact: true }).click();
}

/** "A receber": confirma a chegada da linha com o texto dado. */
async function confirmarChegada(page: Page, texto: string | RegExp) {
  await page.getByRole("tabpanel").getByRole("row").filter({ hasText: texto }).first()
    .getByRole("button", { name: "Confirmar chegada" }).click();
  await confirmarModal(page, "Confirmar chegada");
  await expect(page.getByRole("dialog")).toHaveCount(0);
}

test("Roteiro de gravação 9.0: cenas 1 a 7 na ordem, sem erro", async ({ page, request }) => {
  const problemas = vigiar(page);
  const tempos: { cena: string; segundos: number }[] = [];
  const cena = async (nome: string, corpo: () => Promise<void>) => {
    const inicio = Date.now();
    await test.step(nome, corpo);
    tempos.push({ cena: nome, segundos: Math.round((Date.now() - inicio) / 100) / 10 });
  };

  // ─── Cena 1: Usuário 1 "do zero" ───
  await cena("1. Usuário do zero (U1)", async () => {
    await entrar(page, U1);
    await expect(page).toHaveURL(/\/$/);
    await expect(sino(page)).toHaveAccessibleName("Notificações"); // sem contador
    await sino(page).click();
    await expect(painelSino(page)).toContainText("Você não tem notificações.");
    await sino(page).click();
    await menu(page).getByRole("link", { name: "Meus Empréstimos" }).click();
    await expect(page.getByText("0 de 3")).toBeVisible();
    await expect(page.getByText("Você não tem livros emprestados no momento.")).toBeVisible();
    await menu(page).getByRole("link", { name: "Minhas Reservas" }).click();
    await expect(page.getByText(/Você não tem reservas em andamento/)).toBeVisible();
    await pausa(page);

    // Dom Casmurro: tudo disponível -> "Como retirar" informativo, sem reservar
    await abrirLivro(page, "Dom Casmurro");
    await expect(linhaBiblioteca(page, CENTRAL_NOME)).toContainText("3");
    await linhaBiblioteca(page, CENTRAL_NOME).getByRole("button", { name: "Como retirar" }).click();
    await expect(page.getByRole("dialog")).toContainText("presencialmente");
    await expect(page.getByRole("dialog").getByRole("button", { name: /reserv/i })).toHaveCount(0);
    await pausa(page);
    await confirmarModal(page, "Entendi");

    // Duna: Central sem disponível -> fila, retirada na própria Central
    await abrirLivro(page, "Duna");
    await linhaBiblioteca(page, CENTRAL_NOME).getByRole("button", { name: "Entrar na fila" }).click();
    await expect(page.getByText("1º lugar")).toBeVisible();
    await page.getByRole("button", { name: /Na própria biblioteca/ }).click();
    await page.getByRole("button", { name: /Entrar na fila/ }).click();
    await expect(page.getByRole("dialog")).toContainText("posição 1");
    await pausa(page);
    await confirmarModal(page, "Entrar na fila");
    await expect(page).toHaveURL(/\/minhas-reservas$/);
    const reservaDuna = page.getByTestId("reserva").filter({ hasText: "Duna" });
    await expect(reservaDuna).toContainText("1º na fila");
    await expect(reservaDuna).toContainText("NA FILA");
    await pausa(page);

    // Grande Sertão: "Em outra biblioteca" bloqueado (a Central só tem 1 exemplar)
    await abrirLivro(page, "Grande Sertão: Veredas");
    await linhaBiblioteca(page, CENTRAL_NOME).getByRole("button", { name: "Entrar na fila" }).click();
    await expect(page.getByRole("button", { name: /Em outra biblioteca/ })).toBeDisabled();
    await expect(page.getByTestId("outra-indisponivel")).toContainText("tem só 1 exemplar deste título");
    await pausa(page);

    // Ensaio sobre a Cegueira: fora do catálogo -> registrar interesse (2 -> 3) e repetir é barrado
    await buscar(page, "Ensaio sobre a Cegueira");
    await expect(page.getByText(/0 livros encontrados/)).toBeVisible();
    await page.getByRole("button", { name: /Registrar interesse neste livro/ }).click();
    await expect(page.getByLabel("Título", { exact: true })).toHaveValue("Ensaio sobre a Cegueira");
    await page.getByLabel("Autor", { exact: true }).fill("José Saramago");
    await page.getByRole("button", { name: /Registrar interesse/ }).click();
    await confirmarModal(page, "Registrar");
    await expect(page.getByText('Agora 3 pessoas pediram "Ensaio sobre a Cegueira"')).toBeVisible();
    await pausa(page);
    await page.getByLabel("Título", { exact: true }).fill("Ensaio sobre a Cegueira");
    await page.getByLabel("Autor", { exact: true }).fill("José Saramago");
    await page.getByRole("button", { name: /Registrar interesse/ }).click();
    await confirmarModal(page, "Registrar");
    await expect(page.getByText("Você já registrou interesse neste livro.")).toBeVisible();
    await pausa(page);
    await sair(page);
  });

  // ─── Cena 2: usuários com histórico (U3, depois U2) ───
  await cena("2. Usuários com histórico (U3 e U2)", async () => {
    await entrar(page, U3);
    await expect(sino(page)).toHaveAccessibleName("Notificações (1 não lidas)");
    await sino(page).click();
    await expect(painelSino(page).getByTestId("notificacao")).toHaveCount(1);
    await expect(painelSino(page)).toContainText("Harry Potter e a Pedra Filosofal");
    await expect(painelSino(page)).toContainText(VI_NOME);
    await pausa(page);
    await sino(page).click();
    await menu(page).getByRole("link", { name: "Minhas Reservas" }).click();
    const harry = page.getByTestId("reserva").filter({ hasText: "Harry Potter" });
    await expect(harry).toContainText(`Retire até ${dataEmDias(2)}`);
    const hobbit = page.getByTestId("reserva").filter({ hasText: "O Hobbit" });
    await expect(hobbit).toContainText("Transferência pendente");
    await expect(hobbit).toContainText(`Fila na ${CENTRAL_NOME} → retirada na ${VI_NOME}`);
    await pausa(page);
    await menu(page).getByRole("link", { name: "Meus Empréstimos" }).click();
    await expect(page.getByText("1 de 3")).toBeVisible();
    await pausa(page);
    await sair(page);

    await entrar(page, U2);
    await menu(page).getByRole("link", { name: "Meus Empréstimos" }).click();
    await expect(page.getByText("3 de 3")).toBeVisible();
    await expect(page.getByTestId("emprestimo").filter({ hasText: "Duna" })).toContainText("Atrasado há 6 dias");
    await sino(page).click();
    await expect(painelSino(page).getByTestId("notificacao").filter({ hasText: "Empréstimo atrasado" }))
      .toContainText("Duna");
    await sino(page).click();
    await pausa(page);
    await menu(page).getByRole("link", { name: "Meu Histórico" }).click();
    await expect(page.getByRole("row").filter({ hasText: "Sapiens" }).first()).toBeVisible();
    await expect(page.getByRole("row").filter({ hasText: "O Pequeno Príncipe" }).first()).toBeVisible();
    await pausa(page);
    await sair(page);
  });

  // ─── Cena 3: Admin decide ao vivo ───
  await cena("3. Admin decide ao vivo", async () => {
    await entrar(page, ADMIN);
    await expect(page).toHaveURL(/\/admin$/);
    await expect(page.getByRole("heading", { name: "Painel da Rede" })).toBeVisible();
    await expect(page.getByTestId("exemplares-por-status")).toBeVisible();
    await pausa(page);

    await sino(page).click();
    const pedido = painelSino(page).getByTestId("notificacao").filter({ hasText: "Novo pedido de transferência" });
    await expect(pedido).toContainText("O Hobbit");
    await pausa(page);
    await pedido.getByRole("button").first().click();
    await expect(page).toHaveURL(/\/admin\/transferencias$/);

    // Aprovar o pedido do Hobbit (sem exemplar separado ainda)
    const card = page.getByTestId("pedido").filter({ hasText: "O Hobbit" });
    await expect(card).toContainText("Pedido de Usuário 3");
    await expect(card).toContainText(`${CENTRAL_NOME} → ${VI_NOME}`);
    await expect(card).toContainText("Será vinculado na devolução");
    await expect(page.getByRole("button", { name: /Confirmar chegada/ })).toHaveCount(0);
    await card.getByRole("button", { name: /Aprovar/ }).click();
    await expect(page.getByRole("dialog")).toContainText("aguardando exemplar");
    await pausa(page);
    await confirmarModal(page, "Aprovar");
    await expect(page.getByText('Pedido de "O Hobbit" aprovado. Ele segue')).toBeVisible();
    await pausa(page);

    // Avulsa em lote: 2 de 1984 da Central -> recusada (tudo ou nada)
    await page.getByRole("button", { name: /Nova transferência avulsa/ }).click();
    const destino = page.getByLabel("Biblioteca de destino");
    await page.getByLabel("Buscar exemplar").fill("1984");
    const central1984 = page.getByRole("row").filter({ hasText: CENTRAL_NOME }).getByRole("checkbox");
    await expect(central1984).toHaveCount(2);
    await central1984.nth(0).check();
    await central1984.nth(1).check();
    await destino.selectOption({ label: TIJUCA_NOME });
    await page.getByRole("button", { name: /Transferir selecionados \(2\)/ }).click();
    await expect(page.getByRole("dialog")).toContainText(TIJUCA_NOME);
    await confirmarModal(page, "Transferir");
    const erro = page.getByTestId("erro-avulsa");
    await expect(erro).toContainText("Nenhuma transferência foi criada");
    await expect(erro).toContainText(`"1984" na ${CENTRAL_NOME}: 2 selecionado(s), mas só 1 pode(m) sair`);
    await pausa(page, 2000);

    // Refaz: 1 de 1984 + 2 de Dom Casmurro -> Tijuca
    await central1984.nth(1).uncheck();
    await page.getByLabel("Buscar exemplar").fill("Dom Casmurro");
    const centralDom = page.getByRole("row").filter({ hasText: CENTRAL_NOME }).getByRole("checkbox");
    await centralDom.nth(0).check();
    await centralDom.nth(1).check();
    await page.getByRole("button", { name: /Transferir selecionados \(3\)/ }).click();
    await confirmarModal(page, "Transferir");
    await expect(page.getByText(`3 transferência(s) avulsa(s) criada(s) para a ${TIJUCA_NOME}`)).toBeVisible();
    await pausa(page);

    // Acompanhamento: as 3 novas + o Fahrenheit do seed em trânsito; o Hobbit aguardando exemplar
    await page.getByLabel("Filtrar por situação").selectOption("EM_TRANSITO");
    await expect(page.getByRole("row").filter({ hasText: TIJUCA_NOME })).toHaveCount(3);
    await expect(page.getByRole("row").filter({ hasText: "Fahrenheit 451" })).toContainText("Em trânsito");
    await page.getByLabel("Filtrar por situação").selectOption("APROVADA_AGUARDANDO_EXEMPLAR");
    await expect(page.getByRole("row").filter({ hasText: "O Hobbit" })).toContainText("Aprovada, aguardando exemplar");
    await pausa(page);

    // (opcional) Demandas: Torto Arado de "Em análise" para "Aprovada"
    await menu(page).getByRole("link", { name: "Demandas" }).click();
    const torto = page.getByRole("row").filter({ hasText: "Torto Arado" });
    await expect(torto).toContainText("Em análise");
    await torto.getByRole("button", { name: "Aprovar compra" }).click();
    await confirmarModal(page, "Aprovar compra");
    await expect(torto).toContainText("Aprovada");
    await pausa(page);
    await sair(page);
  });

  // ─── Cena 4: Bibliotecário da Central ───
  await cena("4. Bibliotecário da Central", async () => {
    await entrar(page, BIB_C);
    await expect(page).toHaveURL(/\/biblioteca$/);
    await pausa(page);

    // Hobbit do Usuário 2 (Bom): pedido aprovado -> segue direto para transferência
    await devolver(page, "Usuário 2", "O Hobbit");
    await confirmarDevolucao(page);
    await expect(page.getByText(/O exemplar seguiu direto para transferência/)).toBeVisible();
    await pausa(page, 2000);

    // Duna do Usuário 2, atrasado 6 dias: modal avisa; bloqueio de 12 dias; reservado para U1
    await devolver(page, "Usuário 2", "Duna");
    await expect(page.getByText("Devolução com atraso")).toBeVisible();
    await expect(page.getByText(/6 dia\(s\) de atraso identificados/)).toBeVisible();
    await page.getByRole("button", { name: /Confirmar Devolução/ }).click();
    await expect(page.getByRole("dialog")).toContainText(`Usuário bloqueado até ${dataEmDias(12)}`);
    await pausa(page);
    await page.getByRole("button", { name: "Confirmar devolução", exact: true }).click();
    await expect(page.getByText(/6 dia\(s\) de atraso\. Usuário 2 ficou bloqueado por 12 dias/)).toBeVisible();
    await expect(page.getByText(/ficou reservado para o 1º da fila/)).toBeVisible();
    await pausa(page, 2000);

    // Reservas aguardando retirada: Usuário 1 com Duna -> Novo empréstimo pelo atalho
    await menu(page).getByRole("link", { name: "Reservas" }).click();
    const pronta = page.getByRole("row").filter({ hasText: "Usuário 1" });
    await expect(pronta).toContainText("Duna");
    await pausa(page);
    await pronta.getByRole("button", { name: "Novo empréstimo" }).click();
    await expect(page).toHaveURL(/\/biblioteca\/emprestimo\?usuario=/);
    await page.getByRole("button", { name: /Confirmar Empréstimo/ }).click();
    await confirmarModal(page, "Confirmar empréstimo");
    await expect(page.getByText("Empréstimo registrado: Duna para Usuário 1")).toBeVisible();
    await expect(page.getByText(`devolução prevista em ${dataEmDias(14)}`)).toBeVisible();
    await pausa(page);

    // Usuário 2 tenta pegar outro livro: bloqueado
    await page.getByRole("link", { name: "Registrar Empréstimo" }).click();
    await page.getByLabel("Nome ou e-mail do usuário").fill("Usuário 2");
    await page.getByRole("button", { name: /Usuário 2/ }).click();
    await expect(page.getByText("Empréstimo bloqueado")).toBeVisible();
    await pausa(page, 2000);
    await sair(page);
  });

  // ─── Cena 5: Bibliotecário de Vila Isabel ───
  await cena("5. Bibliotecário de Vila Isabel", async () => {
    await entrar(page, BIB_VI);
    await menu(page).getByRole("link", { name: "Transferências" }).click();
    const aReceber = page.getByRole("tabpanel");
    await expect(aReceber.getByRole("row").filter({ hasText: "O Hobbit" })).toContainText("Reservado para Usuário 3");
    await expect(aReceber.getByRole("row").filter({ hasText: "Fahrenheit 451" })).toBeVisible();
    await pausa(page);
    await confirmarChegada(page, "Fahrenheit 451");
    await expect(page.getByText(new RegExp(`confirmada\. Ele entrou no acervo da ${VI_NOME}`))).toBeVisible();
    await confirmarChegada(page, "O Hobbit");
    await expect(page.getByText("Ele está separado para Usuário 3, que tem 3 dias para retirar.")).toBeVisible();
    await pausa(page);

    // Reservas aguardando retirada: Harry e Hobbit do Usuário 3 -> empresta os dois (3/3)
    for (const titulo of ["Harry Potter e a Pedra Filosofal", "O Hobbit"]) {
      await menu(page).getByRole("link", { name: "Reservas" }).click();
      const linha = page.getByRole("row").filter({ hasText: "Usuário 3" }).filter({ hasText: titulo });
      await expect(linha).toBeVisible();
      await pausa(page, 800);
      await linha.getByRole("button", { name: "Novo empréstimo" }).click();
      await page.getByRole("button", { name: /Confirmar Empréstimo/ }).click();
      await confirmarModal(page, "Confirmar empréstimo");
      await expect(page.getByText(`Empréstimo registrado: ${titulo} para Usuário 3`)).toBeVisible();
    }
    const deU3 = await ok<{ status: string }[]>(
      request.get(`${API}/emprestimos/usuario/0`, { headers: await token(request, U3) }),
    );
    expect(deU3.filter((e) => e.status !== "DEVOLVIDO")).toHaveLength(3); // Usuário 3 com 3/3
    await pausa(page);

    // (opcional) Gerenciar exemplares: reativar o Steve Jobs
    await page.getByRole("link", { name: "Acervo" }).click();
    await page.getByRole("button", { name: /^Indisponíveis/ }).click();
    const steve = page.getByRole("row").filter({ hasText: "Steve Jobs" });
    await steve.getByRole("button", { name: "Reativar" }).click();
    await confirmarModal(page, "Reativar");
    await expect(page.getByText(/reativado: agora disponível/)).toBeVisible();
    await pausa(page);
    await sair(page);
  });

  // ─── Cena 6: Bibliotecário da Tijuca ───
  await cena("6. Bibliotecário da Tijuca", async () => {
    await entrar(page, BIB_T);
    await page.getByRole("link", { name: "Acervo" }).click();
    await expect(page.getByText("Nenhum exemplar encontrado.")).toBeVisible();
    await pausa(page);

    await page.getByRole("link", { name: "Cadastrar Exemplares" }).click();
    await page.getByLabel("Buscar livro do catálogo").fill("Memórias Póstumas");
    await page.getByRole("row", { name: /Memórias Póstumas de Brás Cubas/ }).getByRole("button", { name: "Selecionar" }).click();
    await page.getByRole("button", { name: "Novo", exact: true }).click();
    await page.getByLabel("Quantidade (1 a 50)").fill("2");
    await page.getByRole("button", { name: /Cadastrar Exemplares/ }).click();
    await confirmarModal(page, "Cadastrar");
    await expect(page.getByText(
      new RegExp(`2 exemplar\\(es\\) de "Memórias Póstumas de Brás Cubas" cadastrado\\(s\\) na ${TIJUCA_NOME}`),
    )).toBeVisible();
    await pausa(page);

    // A receber: os 3 exemplares da avulsa -> confirmar chegada de cada um
    await menu(page).getByRole("link", { name: "Transferências" }).click();
    const linhas = page.getByRole("tabpanel").getByRole("row").filter({ hasText: CENTRAL_NOME });
    await expect(linhas).toHaveCount(3);
    for (let i = 0; i < 3; i++) {
      await confirmarChegada(page, CENTRAL_NOME);
      await expect(linhas).toHaveCount(2 - i);
    }
    await expect(page.getByText("Nenhum exemplar a caminho desta biblioteca.")).toBeVisible();
    await pausa(page);
    await sair(page);
  });

  // ─── Cena 7: Fechamento (U1 e Admin) ───
  await cena("7. Fechamento (U1 e Admin)", async () => {
    await entrar(page, U1);
    await expect(sino(page)).toHaveAccessibleName(/Notificações \(\d+ não lidas\)/);
    await sino(page).click();
    await expect(painelSino(page).getByTestId("notificacao").filter({ hasText: "Reserva pronta para retirada" }))
      .toContainText("Duna");
    await sino(page).click();
    await menu(page).getByRole("link", { name: "Meus Empréstimos" }).click();
    await expect(page.getByText("1 de 3")).toBeVisible();
    await expect(page.getByTestId("emprestimo").filter({ hasText: "Duna" })).toBeVisible();
    await pausa(page);
    await sair(page);

    // Admin: linha do tempo do Hobbit que viajou (devolução -> saída -> chegada -> empréstimo)
    const admin = await token(request, ADMIN);
    const hobbits = await ok<{ id: number; status: string; biblioteca: { nome: string } }[]>(
      request.get(`${API}/exemplares/livro/${await idLivro(request, "O Hobbit")}`, { headers: admin }),
    );
    const viajante = hobbits.find((e) => e.biblioteca.nome === VI_NOME);
    expect(viajante?.status).toBe("EMPRESTADO");

    await entrar(page, ADMIN);
    await menu(page).getByRole("link", { name: "Histórico" }).click();
    await page.getByLabel("Exemplar nº").fill(String(viajante!.id));
    await page.getByRole("button", { name: "Ver linha do tempo" }).click();
    const eventos = page.getByTestId("linha-do-tempo").getByTestId("evento-linha");
    const rotulos = (await eventos.allTextContents()).join(" | ");
    const ordem = ["Devolução", "Saída para transferência", "Chegada de transferência", "Empréstimo"];
    let ultimo = -1;
    for (const r of ordem) {
      const pos = rotulos.indexOf(r, ultimo + 1);
      expect(pos, `"${r}" depois do evento anterior na linha do tempo`).toBeGreaterThan(ultimo);
      ultimo = pos;
    }
    await expect(page.getByTestId("linha-do-tempo")).not.toContainText("Fila de espera"); // marcas ocultas
    await pausa(page, 2000);

    await menu(page).getByRole("link", { name: "Relatórios" }).click();
    await expect(page.getByRole("heading", { name: "Relatórios" })).toBeVisible();
    await expect(page.locator("main table").first()).toBeVisible();
    await expect(page.locator("main canvas")).toHaveCount(0);
    await pausa(page, 2000);
  });

  const total = tempos.reduce((s, t) => s + t.segundos, 0);
  console.log("\nDuração das cenas (DEMO_LENTA " + (LENTA ? "ligado" : "desligado") + "):");
  for (const t of tempos) console.log(`  ${t.cena.padEnd(40)} ${t.segundos.toFixed(1)} s`);
  console.log(`  ${"Total".padEnd(40)} ${total.toFixed(1)} s`);
  writeFileSync(test.info().outputPath("tempos.json"), JSON.stringify({ tempos, total }, null, 2));

  // Nenhum erro 500 e nenhum erro de console durante todo o roteiro
  expect(problemas).toEqual([]);
});
