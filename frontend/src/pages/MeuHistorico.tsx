/**
 * Meu Histórico — empréstimos devolvidos e reservas encerradas
 * (retiradas, canceladas, expiradas) do usuário logado.
 * Os filtros vão ao servidor (GET /api/conta/historico); todas as páginas
 * filtradas são trazidas e a TabelaPaginada pagina na tela.
 */
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, formatarData, qs } from "../api/client";
import type { ItemHistorico, Pagina } from "../types";
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
  tipo: "" | "EMPRESTIMO" | "RESERVA";
  de: string;
  ate: string;
}

const VAZIO: Filtros = { tipo: "", de: "", ate: "" };
const TAMANHO = 50; // máximo aceito pelo servidor por página

const STATUS: Record<ItemHistorico["status"], { texto: string; tom: "verde" | "cinza" | "vermelho" | "azul" }> = {
  DEVOLVIDO: { texto: "Devolvido", tom: "verde" },
  RETIRADA: { texto: "Retirada", tom: "azul" },
  CANCELADA: { texto: "Cancelada", tom: "cinza" },
  EXPIRADA: { texto: "Expirada", tom: "vermelho" },
};

/** Busca todas as páginas do histórico com os filtros dados. */
async function buscarTudo(f: Filtros): Promise<ItemHistorico[]> {
  const itens: ItemHistorico[] = [];
  for (let pagina = 0; ; pagina++) {
    const p = await api.get<Pagina<ItemHistorico>>(
      "/conta/historico" + qs({ ...f, pagina, tamanho: TAMANHO }),
    );
    itens.push(...p.itens);
    if (pagina + 1 >= p.totalPaginas) return itens;
  }
}

export default function MeuHistorico() {
  const [rascunho, setRascunho] = useState<Filtros>(VAZIO);
  const [filtros, setFiltros] = useState<Filtros>(VAZIO);
  const [itens, setItens] = useState<ItemHistorico[] | null>(null);
  const [erro, setErro] = useState("");

  useEffect(() => {
    let ativo = true;
    buscarTudo(filtros)
      .then((r) => {
        if (!ativo) return;
        setItens(r);
        setErro("");
      })
      .catch((e) => ativo && setErro((e as Error).message));
    return () => {
      ativo = false;
    };
  }, [filtros]);

  const colunas: Coluna<ItemHistorico>[] = [
    {
      titulo: "Tipo",
      render: (i) => (i.tipo === "EMPRESTIMO" ? "📖 Empréstimo" : "📌 Reserva"),
    },
    {
      titulo: "Livro",
      render: (i) => (
        <div>
          <Link
            to={`/livro/${i.livroId}`}
            className="font-semibold text-[#2c3e50] hover:text-[#1976d2]"
          >
            {i.titulo}
          </Link>
          <p className="text-[12px] text-[#66707d]">{i.autor}</p>
        </div>
      ),
    },
    { titulo: "Biblioteca", render: (i) => i.biblioteca },
    { titulo: "Data", render: (i) => formatarData(i.data) },
    {
      titulo: "Encerramento",
      render: (i) => (i.dataFim ? formatarData(i.dataFim) : "—"),
    },
    {
      titulo: "Situação",
      render: (i) => (
        <div className="flex flex-col gap-1">
          <span>
            <Badge tom={STATUS[i.status].tom}>{STATUS[i.status].texto}</Badge>
          </span>
          <span className="text-[12px] text-[#66707d]">{i.detalhe}</span>
        </div>
      ),
    },
  ];

  function aplicar(e: React.FormEvent) {
    e.preventDefault();
    setFiltros({ ...rascunho });
  }

  function limpar() {
    setRascunho(VAZIO);
    setFiltros(VAZIO);
  }

  return (
    <>
      <Trilha itens={[{ rotulo: "Meu Histórico" }]} />
      <TituloPagina
        titulo="Meu Histórico"
        subtitulo="Empréstimos devolvidos e reservas encerradas"
      />

      {erro && <Erro mensagem={erro} />}

      <SectionCard titulo="Filtros">
        <form onSubmit={aplicar} className="flex flex-wrap items-end gap-4">
          <Campo label="Tipo" className="w-[200px]">
            <Selecao
              aria-label="Tipo"
              value={rascunho.tipo}
              onChange={(e) =>
                setRascunho({ ...rascunho, tipo: e.target.value as Filtros["tipo"] })
              }
            >
              <option value="">Todos</option>
              <option value="EMPRESTIMO">Empréstimos</option>
              <option value="RESERVA">Reservas</option>
            </Selecao>
          </Campo>
          <Campo label="De" className="w-[170px]">
            <Entrada
              type="date"
              aria-label="De"
              value={rascunho.de}
              onChange={(e) => setRascunho({ ...rascunho, de: e.target.value })}
            />
          </Campo>
          <Campo label="Até" className="w-[170px]">
            <Entrada
              type="date"
              aria-label="Até"
              value={rascunho.ate}
              onChange={(e) => setRascunho({ ...rascunho, ate: e.target.value })}
            />
          </Campo>
          <Botao type="submit">Filtrar</Botao>
          <Botao variante="secundario" onClick={limpar}>
            Limpar
          </Botao>
        </form>
      </SectionCard>

      <SectionCard titulo="Registros">
        {itens === null && !erro ? (
          <Carregando texto="Carregando seu histórico..." />
        ) : (
          <div className="rounded-[10px] border border-[#e0e0e0] overflow-hidden">
            <TabelaPaginada
              key={JSON.stringify(filtros)}
              dados={itens ?? []}
              colunas={colunas}
              chave={(i) => `${i.tipo}-${i.id}`}
              porPagina={10}
              textoVazio="Nenhum registro no período."
            />
          </div>
        )}
      </SectionCard>
    </>
  );
}
