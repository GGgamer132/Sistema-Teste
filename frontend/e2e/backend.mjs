// Reinicia o backend (mvn spring-boot:run) para que o seed de demonstração (§8) volte ao
// estado inicial: com ddl-auto=create, cada boot recria o schema e roda o data.sql.
// Não existe endpoint de reset; o reinício é o único jeito de limpar os dados.
//
// Uso: node e2e/backend.mjs            (reinicia e deixa o backend rodando em segundo plano)
//      import { reiniciarBackend } from "./backend.mjs"

import { execSync, spawn } from "node:child_process";
import { mkdirSync, openSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const AQUI = dirname(fileURLToPath(import.meta.url));
const PASTA_BACKEND = resolve(AQUI, "../../backend/circula-book");
const LOG = resolve(PASTA_BACKEND, "target/e2e-backend.log");
const PORTA = 8080;
const API = `http://localhost:${PORTA}/api`;
const WINDOWS = process.platform === "win32";

/** PIDs que escutam na porta do backend. */
function pidsNaPorta() {
  try {
    if (WINDOWS) {
      const saida = execSync(`netstat -ano -p tcp | findstr LISTENING | findstr :${PORTA}`, { encoding: "utf8" });
      return [...new Set(saida.split(/\r?\n/)
        .filter((l) => l.split(/\s+/)[2]?.endsWith(`:${PORTA}`))
        .map((l) => l.trim().split(/\s+/).pop())
        .filter((p) => p && p !== "0"))];
    }
    return execSync(`lsof -ti tcp:${PORTA} -sTCP:LISTEN`, { encoding: "utf8" }).split(/\s+/).filter(Boolean);
  } catch {
    return []; // nada escutando
  }
}

function parar() {
  for (const pid of pidsNaPorta()) {
    try {
      execSync(WINDOWS ? `taskkill /PID ${pid} /T /F` : `kill -9 ${pid}`, { stdio: "ignore" });
    } catch {
      // processo já terminou
    }
  }
}

const esperar = (ms) => new Promise((r) => setTimeout(r, ms));

/** Pronto = o login do Admin do seed responde 200. */
async function pronto() {
  try {
    const r = await fetch(`${API}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: "admin@circulabook.com", senha: "senha123" }),
    });
    return r.ok;
  } catch {
    return false;
  }
}

export async function reiniciarBackend({ limiteMs = 180_000 } = {}) {
  const inicio = Date.now();
  parar();
  for (let i = 0; i < 50 && pidsNaPorta().length > 0; i++) await esperar(200);

  mkdirSync(dirname(LOG), { recursive: true });
  const log = openSync(LOG, "w");
  const filho = spawn("mvn -q spring-boot:run", {
    cwd: PASTA_BACKEND,
    shell: true,
    detached: !WINDOWS,
    windowsHide: true,
    stdio: ["ignore", log, log],
  });
  filho.unref();

  while (Date.now() - inicio < limiteMs) {
    if (await pronto()) {
      console.log(`[backend] reiniciado com o seed limpo em ${((Date.now() - inicio) / 1000).toFixed(1)} s (log: ${LOG})`);
      return;
    }
    if (filho.exitCode !== null && pidsNaPorta().length === 0) {
      throw new Error(`O backend terminou ao subir (código ${filho.exitCode}). Veja ${LOG}`);
    }
    await esperar(1000);
  }
  throw new Error(`O backend não respondeu em ${limiteMs / 1000} s. Veja ${LOG}`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  reiniciarBackend().then(
    () => process.exit(0),
    (e) => {
      console.error(e.message);
      process.exit(1);
    },
  );
}
