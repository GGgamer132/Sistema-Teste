// Suíte e2e repetível: cada arquivo de spec é um grupo que roda num backend recém-iniciado
// (o globalSetup do Playwright reinicia o backend a cada execução), então nenhum grupo
// depende do que o anterior alterou. Uso: npm run test:e2e [-- arquivo1.spec.ts ...]
import { spawnSync } from "node:child_process";
import { readdirSync } from "node:fs";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const AQUI = dirname(fileURLToPath(import.meta.url));
const pedidos = process.argv.slice(2);
const grupos = pedidos.length > 0
  ? pedidos
  : readdirSync(AQUI).filter((f) => f.endsWith(".spec.ts")).sort();

const resultado = [];
for (const g of grupos) {
  const inicio = Date.now();
  console.log(`\n=== ${g} ===`);
  const r = spawnSync(`npx playwright test e2e/${g.replace(/^e2e\//, "")}`, {
    cwd: dirname(AQUI), shell: true, stdio: "inherit",
  });
  resultado.push({ grupo: g, ok: r.status === 0, s: ((Date.now() - inicio) / 1000).toFixed(0) });
}

console.log("\nResumo da suíte e2e");
for (const r of resultado) console.log(`${r.ok ? "PASS" : "FAIL"}  ${r.grupo}  (${r.s} s)`);
process.exit(resultado.every((r) => r.ok) ? 0 : 1);
