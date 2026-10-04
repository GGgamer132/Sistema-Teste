import { defineConfig, devices } from "@playwright/test";

/**
 * Testes ponta a ponta contra o backend REAL (suba antes com
 * `mvn spring-boot:run` em backend/circula-book). O Vite sobe sozinho.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  reporter: "list",
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
