/**
 * Cliente HTTP único do app.
 *
 * O Vite está configurado (vite.config.ts) para redirecionar /api
 * para http://localhost:8080, então basta usar caminhos relativos.
 */

const BASE = "/api";

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const resp = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });

  if (!resp.ok) {
    // O backend devolve a mensagem de erro em texto puro no badRequest()
    const texto = await resp.text();
    throw new Error(texto || `Falha na requisição (${resp.status})`);
  }

  if (resp.status === 204) return undefined as T;

  const corpo = await resp.text();
  return corpo ? (JSON.parse(corpo) as T) : (undefined as T);
}

export const api = {
  get:   <T>(path: string) => request<T>(path),
  post:  <T>(path: string, body?: unknown) =>
           request<T>(path, { method: "POST", body: JSON.stringify(body ?? {}) }),
  patch: <T>(path: string, body?: unknown) =>
           request<T>(path, { method: "PATCH", body: JSON.stringify(body ?? {}) }),
};

/** Monta querystring ignorando campos vazios. */
export function qs(params: Record<string, string | number | undefined | null>): string {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && String(v).trim() !== "") {
      p.append(k, String(v));
    }
  });
  const s = p.toString();
  return s ? `?${s}` : "";
}

/** Formata ISO date-time do Java para dd/mm/aaaa. */
export function formatarData(iso?: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleDateString("pt-BR");
}

/** Dias de atraso entre a data prevista e hoje (0 se estiver em dia). */
export function diasDeAtraso(dataPrevista?: string | null): number {
  if (!dataPrevista) return 0;
  const prev = new Date(dataPrevista).getTime();
  const hoje = Date.now();
  if (hoje <= prev) return 0;
  return Math.floor((hoje - prev) / (1000 * 60 * 60 * 24));
}
