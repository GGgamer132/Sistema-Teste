// Antes de cada execução do Playwright, reinicia o backend para partir do seed limpo (§8).
// Para rodar contra um backend já em execução, sem reiniciar: E2E_SEM_REINICIO=1.
import { reiniciarBackend } from "./backend.mjs";

export default async function globalSetup() {
  if (process.env.E2E_SEM_REINICIO === "1") return;
  await reiniciarBackend();
}
