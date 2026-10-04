/**
 * Histórico de circulação (A4) — Admin.
 *  - Eventos recentes da rede, com filtros (evento, biblioteca, período, exemplar nº).
 *  - Linha do tempo de um exemplar (?exemplar=ID), do cadastro até hoje.
 */
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, nomeExemplar, qs } from "../api/client";
import type { Biblioteca, EventoHistorico, LinhaDoTempo, Pagina } from "../types";
import {
  Badge,
  Botao,
  Campo,
  Carregando,
  Entrada,
  Erro,
  SectionCard,
  Selecao,
  TituloPagina,
  Trilha,
} from "../components/ui";
import TabelaPaginada, { type Coluna } from "../components/TabelaPaginada";

interface Filtros {
  evento: string;
  bibliotecaId: string;
  de: string;
  ate: string;
  exemplarId: string;
}

const VAZIO: Filtros = { evento: "", bibliotecaId: "", de: "", ate: "", exemplarId: "" };
/** Quantos eventos recentes trazer (4 páginas de 50); filtros refinam o resto. */
const MAX_PAGINAS = 4;

const TOM_EVENTO: Record<string, "verde" | "azul" | "amarelo" | "vermelho" | "cinza"> = {
  CADASTRO: "verde",
  EMPRESTIMO: "azul",
  DEVOLUCAO: "verde",
  RESERVA: "amarelo",
  FILA: "cinza",
  BAIXA: "vermelho",
  REATIVACAO: "verde",
  TRANSFERENCIA_SAIDA: "azul",
  TRANSFERENCIA_CHEGADA: "azul",
};

/** dd/mm/aaaa hh:mm */
function dataHora(iso: string): string {
  const d = new Date(iso);
  return `${d.toLocaleDateString("pt-BR")} ${d.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}`;
}

async function buscarRecentes(f: Filtros): Promise<{ itens: EventoHistorico[]; total: number }> {
  const itens: EventoHistorico[] = [];
  let total = 0;
  for (let pagina = 0; pagina < MAX_PAGINAS; pagina++) {
    const p = await api.get<Pagina<EventoHistorico>>("/historico" + qs({ ...f, pagina, tamanho: 50 }));
    itens.push(...p.itens);
    total = p.totalItens;
    if (pagina + 1 >= p.totalPaginas) break;
  }
  return { itens, total };
}

export default function AdminHistorico() {
  const [searchParams, setSearchParams] = useSearchParams();
  const exemplarAberto = searchParams.get("exemplar");

  const [eventos, setEventos] = useState<{ codigo: string; rotulo: string }[]>([]);
  const [bibliotecas, setBibliotecas] = useState<Biblioteca[]>([]);
  const [rascunho, setRascunho] = useState<Filtros>(VAZIO);
  const [filtros, setFiltros] = useState<Filtros>(VAZIO);
  const [recentes, setRecentes] = useState<{ itens: EventoHistorico[]; total: number } | null>(null);
  const [linha, setLinha] = useState<LinhaDoTempo | null>(null);
  const [numero, setNumero] = useState(exemplarAberto ?? "");
  const [erro, setErro] = useState("");
  const [erroLinha, setErroLinha] = useState("");

  useEffect(() => {
    Promise.all([
      api.get<{ codigo: string; rotulo: string }[]>("/historico/eventos"),
      api.get<Biblioteca[]>("/bibliotecas"),
    ])
      .then(([e, b]) => {
        setEventos(e);
        setBibliotecas(b);
      })
      .catch((e) => setErro(e.message));
  }, []);

  useEffect(() => {
    let ativo = true;
    buscarRecentes(filtros)
      .then((r) => {
        if (!ativo) return;
        setRecentes(r);
        setErro("");
      })
      .catch((e) => ativo && setErro((e as Error).message));
    return () => {
      ativo = false;
    };
  }, [filtros]);

  useEffect(() => {
    if (!exemplarAberto) return;
    let ativo = true;
    api
      .get<LinhaDoTempo>(`/historico/exemplar/${exemplarAberto}`)
      .then((l) => {
        if (!ativo) return;
        setLinha(l);
        setErroLinha("");
      })
      .catch((e) => {
        if (!ativo) return;
        setLinha(null);
        setErroLinha((e as Error).message);
      });
    return () => {
      ativo = false;
    };
  }, [exemplarAberto]);

  function abrirLinha(id: number | string) {
    setNumero(String(id));
    setSearchParams({ exemplar: String(id) });
  }

  const colunas: Coluna<EventoHistorico>[] = [
    { titulo: "Quando", render: (e) => <span className="whitespace-nowrap text-[13px]">{dataHora(e.data)}</span> },
    { titulo: "Evento", render: (e) => <Badge tom={TOM_EVENTO[e.evento] ?? "cinza"}>{e.eventoRotulo}</Badge> },
    {
      titulo: "Exemplar",
      render: (e) => (
        <button
          type="button"
          onClick={() => abrirLinha(e.exemplarId)}
          className="text-left text-[#1976d2] hover:underline"
        >
          {nomeExemplar(e.exemplarId)}
          <span className="block text-[12px] text-[#66707d]">{e.titulo}</span>
        </button>
      ),
    },
    { titulo: "Biblioteca", render: (e) => e.biblioteca ?? "—" },
    { titulo: "Responsável", render: (e) => e.responsavel },
    { titulo: "Observação", render: (e) => <span className="text-[13px]">{e.observacoes ?? "—"}</span> },
  ];

  return (
    <>
      <Trilha itens={[{ rotulo: "Painel", to: "/admin" }, { rotulo: "Histórico de circulação" }]} />
      <TituloPagina
        titulo="Histórico de circulação"
        subtitulo="Tudo o que aconteceu com cada exemplar da rede: quando, onde, quem fez e por quê"
      />

      {erro && <Erro mensagem={erro} />}

      <SectionCard titulo="Linha do tempo de um exemplar">
        <form
          className="flex flex-wrap items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (numero.trim()) abrirLinha(numero.trim());
          }}
        >
          <Campo label="Exemplar nº" className="w-[160px]">
            <Entrada
              inputMode="numeric"
              value={numero}
              onChange={(e) => setNumero(e.target.value.replace(/\D/g, ""))}
            />
          </Campo>
          <Botao type="submit" disabled={!numero.trim()}>
            Ver linha do tempo
          </Botao>
          {exemplarAberto && (
            <Botao
              variante="secundario"
              onClick={() => {
                setLinha(null);
                setNumero("");
                setSearchParams({});
              }}
            >
              Fechar
            </Botao>
          )}
        </form>

        {exemplarAberto && erroLinha && <div className="mt-4"><Erro mensagem={erroLinha} /></div>}
        {exemplarAberto && !erroLinha && !linha && <Carregando texto="Carregando linha do tempo..." />}
        {exemplarAberto && linha && (
          <div className="mt-5" data-testid="linha-do-tempo">
            <p className="text-[15px] font-semibold text-[#2c3e50]">
              {nomeExemplar(linha.exemplarId)} — {linha.titulo}
            </p>
            <p className="text-[13px] text-[#66707d]">
              {linha.autor} · hoje na {linha.bibliotecaAtual} · {linha.statusRotulo}
            </p>
            {linha.eventos.length === 0 ? (
              <p className="mt-3 text-[13px] text-[#66707d]">Nenhum evento registrado para este exemplar.</p>
            ) : (
              <ol className="mt-4 flex flex-col border-l-2 border-[#deedfc] pl-5">
                {linha.eventos.map((e) => (
                  <li key={e.id} data-testid="evento-linha" className="relative pb-4 last:pb-0">
                    <span className="absolute -left-[27px] top-1 h-3 w-3 rounded-full border-2 border-white bg-[#1976d2]" />
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge tom={TOM_EVENTO[e.evento] ?? "cinza"}>{e.eventoRotulo}</Badge>
                      <span className="text-[12px] text-[#66707d]">{dataHora(e.data)}</span>
                    </div>
                    <p className="mt-1 text-[13px] text-[#2c3e50]">{e.observacoes ?? "—"}</p>
                    <p className="text-[12px] text-[#66707d]">
                      {e.biblioteca ?? "—"} · por {e.responsavel}
                    </p>
                  </li>
                ))}
              </ol>
            )}
          </div>
        )}
      </SectionCard>

      <SectionCard titulo="Eventos recentes da rede">
        <form
          className="flex flex-wrap items-end gap-3 mb-4"
          onSubmit={(e) => {
            e.preventDefault();
            setFiltros({ ...rascunho });
          }}
        >
          <Campo label="Evento" className="w-[220px]">
            <Selecao value={rascunho.evento} onChange={(e) => setRascunho({ ...rascunho, evento: e.target.value })}>
              <option value="">Todos</option>
              {eventos.map((e) => (
                <option key={e.codigo} value={e.codigo}>{e.rotulo}</option>
              ))}
            </Selecao>
          </Campo>
          <Campo label="Biblioteca" className="w-[220px]">
            <Selecao
              value={rascunho.bibliotecaId}
              onChange={(e) => setRascunho({ ...rascunho, bibliotecaId: e.target.value })}
            >
              <option value="">Todas</option>
              {bibliotecas.map((b) => (
                <option key={b.id} value={b.id}>{b.nome}</option>
              ))}
            </Selecao>
          </Campo>
          <Campo label="De" className="w-[160px]">
            <Entrada type="date" value={rascunho.de} onChange={(e) => setRascunho({ ...rascunho, de: e.target.value })} />
          </Campo>
          <Campo label="Até" className="w-[160px]">
            <Entrada type="date" value={rascunho.ate} onChange={(e) => setRascunho({ ...rascunho, ate: e.target.value })} />
          </Campo>
          <Campo label="Nº do exemplar" className="w-[140px]">
            <Entrada
              inputMode="numeric"
              value={rascunho.exemplarId}
              onChange={(e) => setRascunho({ ...rascunho, exemplarId: e.target.value.replace(/\D/g, "") })}
            />
          </Campo>
          <Botao type="submit">Filtrar</Botao>
          <Botao
            variante="secundario"
            onClick={() => {
              setRascunho(VAZIO);
              setFiltros(VAZIO);
            }}
          >
            Limpar
          </Botao>
        </form>

        {recentes === null ? (
          <Carregando texto="Carregando eventos..." />
        ) : (
          <>
            <p className="mb-2 text-[13px] text-[#66707d]" data-testid="total-eventos">
              {recentes.total} evento(s) encontrado(s)
              {recentes.total > recentes.itens.length ? `; mostrando os ${recentes.itens.length} mais recentes` : ""}.
            </p>
            <div className="rounded-[10px] border border-[#e0e0e0] overflow-hidden">
              <TabelaPaginada
                key={JSON.stringify(filtros)}
                dados={recentes.itens}
                colunas={colunas}
                chave={(e) => e.id}
                porPagina={15}
                textoVazio="Nenhum evento com esses filtros."
              />
            </div>
          </>
        )}
      </SectionCard>
    </>
  );
}
