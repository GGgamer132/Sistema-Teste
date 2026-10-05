import { defineConfig, devices } from "@playwright/test";

/**
 * Testes ponta a ponta contra o backend REAL. O globalSetup reinicia o backend
 * (seed limpo da §8) a cada execução; `npm run test:e2e` roda cada arquivo num
 * backend recém-iniciado. O Vite sobe sozinho.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  reporter: "list",
  globalSetup: "./e2e/global-setup.mjs",
  use: {
    baseURL: "http://localhost:5173",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "npm run dev",
    url: "http://localhost:5173",
    reuseExistingServer: true,
  },
});
