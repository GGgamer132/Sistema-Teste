/**
 * Sino de notificações do cabeçalho (todos os perfis, §6).
 * A contagem de não lidas é atualizada por polling a cada 30 s, a cada troca de rota
 * e ao abrir o painel. Clicar numa notificação marca como lida e leva à tela dela.
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { Notificacao, Pagina } from "../types";

const INTERVALO_MS = 30_000;

/** "há 5 min", "há 2 h", ou a data. */
function quando(iso: string): string {
  const min = Math.floor((Date.now() - new Date(iso).getTime()) / 60_000);
  if (min < 1) return "agora";
  if (min < 60) return `há ${min} min`;
  if (min < 24 * 60) return `há ${Math.floor(min / 60)} h`;
  return new Date(iso).toLocaleDateString("pt-BR");
}

export default function Sino() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [naoLidas, setNaoLidas] = useState(0);
  const [aberto, setAberto] = useState(false);
  const [lista, setLista] = useState<Notificacao[] | null>(null);
  const [erro, setErro] = useState("");
  const caixa = useRef<HTMLDivElement>(null);

  const atualizarContagem = useCallback(() => {
    api
      .get<{ naoLidas: number }>("/notificacoes/nao-lidas/contagem")
      .then((r) => setNaoLidas(r.naoLidas))
      .catch(() => {
        /* sem rede: mantém o último valor; a próxima rodada tenta de novo */
      });
  }, []);

  const carregarLista = useCallback(() => {
    api
      .get<Pagina<Notificacao>>("/notificacoes?tamanho=20")
      .then((p) => {
        setLista(p.itens);
        setErro("");
      })
      .catch((e) => setErro((e as Error).message));
  }, []);

  // Polling + a cada troca de rota
  useEffect(() => {
    atualizarContagem();
    const t = window.setInterval(atualizarContagem, INTERVALO_MS);
    return () => window.clearInterval(t);
  }, [atualizarContagem, pathname]);

  // Fecha ao clicar fora ou com Esc
  useEffect(() => {
    if (!aberto) return;
    const fora = (e: MouseEvent) => {
      if (caixa.current && !caixa.current.contains(e.target as Node)) setAberto(false);
    };
    const esc = (e: KeyboardEvent) => e.key === "Escape" && setAberto(false);
    document.addEventListener("mousedown", fora);
    document.addEventListener("keydown", esc);
    return () => {
      document.removeEventListener("mousedown", fora);
      document.removeEventListener("keydown", esc);
    };
  }, [aberto]);

  function alternar() {
    if (!aberto) {
      carregarLista();
      atualizarContagem();
    }
    setAberto(!aberto);
  }

  async function marcarLida(n: Notificacao) {
    if (n.lida) return;
    await api.patch(`/notificacoes/${n.id}/lida`);
    setLista((l) => l?.map((x) => (x.id === n.id ? { ...x, lida: true } : x)) ?? null);
    setNaoLidas((c) => Math.max(0, c - 1));
  }

  async function abrir(n: Notificacao) {
    try {
      await marcarLida(n);
    } finally {
      setAberto(false);
      if (n.link) navigate(n.link);
    }
  }

  async function marcarTodas() {
    await api.patch("/notificacoes/marcar-todas-lidas");
    setLista((l) => l?.map((x) => ({ ...x, lida: true })) ?? null);
    setNaoLidas(0);
  }

  return (
    <div className="relative" ref={caixa}>
      <button
        type="button"
        onClick={alternar}
        aria-label={naoLidas > 0 ? `Notificações (${naoLidas} não lidas)` : "Notificações"}
        aria-expanded={aberto}
        className="relative flex h-9 w-9 items-center justify-center rounded-full hover:bg-white/15"
      >
        <span aria-hidden className="text-[18px]">🔔</span>
        {naoLidas > 0 && (
          <span
            data-testid="contador-notificacoes"
            className="absolute -top-1 -right-1 min-w-[18px] h-[18px] rounded-full bg-[#e53935] px-1
                       text-[11px] font-bold leading-[18px] text-white text-center"
          >
            {naoLidas > 99 ? "99+" : naoLidas}
          </span>
        )}
      </button>

      {aberto && (
        <div
          role="dialog"
          aria-label="Notificações"
          className="absolute right-0 top-11 z-50 w-[360px] max-w-[calc(100vw-24px)] rounded-[12px]
                     border border-[#e0e0e0] bg-white text-[#2c3e50] shadow-lg"
        >
          <div className="flex items-center justify-between border-b border-[#eceff3] px-4 py-3">
            <span className="text-[15px] font-semibold">Notificações</span>
            <button
              type="button"
              onClick={marcarTodas}
              disabled={naoLidas === 0}
              className="text-[12px] text-[#1976d2] hover:underline disabled:text-[#9aa3ad] disabled:no-underline"
            >
              Marcar todas como lidas
            </button>
          </div>

          <div className="max-h-[420px] overflow-y-auto">
            {erro && <p className="px-4 py-3 text-[13px] text-[#d32f2f]">{erro}</p>}
            {lista === null && !erro && (
              <p className="px-4 py-6 text-center text-[13px] text-[#66707d]">Carregando...</p>
            )}
            {lista?.length === 0 && (
              <p className="px-4 py-6 text-center text-[13px] text-[#66707d]">
                Você não tem notificações.
              </p>
            )}
            <ul>
              {lista?.map((n) => (
                <li
                  key={n.id}
                  data-testid="notificacao"
                  className={`flex gap-2 border-b border-[#eceff3] last:border-b-0 ${n.lida ? "bg-white" : "bg-[#eef5fd]"}`}
                >
                  <button
                    type="button"
                    onClick={() => abrir(n)}
                    className="flex-1 min-w-0 px-4 py-3 text-left hover:bg-[#f5f7fa]"
                  >
                    <span className="flex items-center gap-2">
                      {!n.lida && <span aria-label="não lida" className="h-2 w-2 shrink-0 rounded-full bg-[#1976d2]" />}
                      <span className={`text-[13px] ${n.lida ? "font-medium" : "font-semibold"}`}>{n.titulo}</span>
                    </span>
                    <span className="mt-1 block text-[12px] leading-[1.4] text-[#4a5561]">{n.mensagem}</span>
                    <span className="mt-1 block text-[11px] text-[#9aa3ad]">{quando(n.criadaEm)}</span>
                  </button>
                  {!n.lida && (
                    <button
                      type="button"
                      onClick={() => marcarLida(n)}
                      className="shrink-0 self-start px-3 py-3 text-[11px] text-[#1976d2] hover:underline"
                    >
                      Marcar como lida
                    </button>
                  )}
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}
    </div>
  );
}
