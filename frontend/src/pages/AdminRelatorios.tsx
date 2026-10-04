/**
 * Relatórios gerenciais (A10) — SOMENTE tabelas, sem gráficos.
 * O período vale para empréstimos, devoluções com atraso, livros mais emprestados,
 * demandas e transferências; o acervo e os atrasos em aberto mostram a situação de agora.
 */
import { useEffect, useState } from "react";
import { api, formatarData, qs } from "../api/client";
import type { Relatorios } from "../types";
import {
  Badge,
  Botao,
  Campo,
  Carregando,
  Entrada,
  Erro,
  SectionCard,
  TituloPagina,
  Trilha,
} from "../components/ui";
import TabelaPaginada, { type Coluna } from "../components/TabelaPaginada";

type Linha<K extends keyof Relatorios> = Relatorios[K] extends (infer T)[] ? T : never;

const num = (n: number) => <span className="font-semibold">{n}</span>;

function Tabela<T>({ id, dados, colunas, chave, vazio }: {
  id: string;
  dados: T[];
  colunas: Coluna<T>[];
  chave: (t: T) => string | number;
  vazio: string;
}) {
  return (
    <div className="rounded-[10px] border border-[#e0e0e0] overflow-hidden" data-testid={id}>
      <TabelaPaginada dados={dados} colunas={colunas} chave={chave} porPagina={10} textoVazio={vazio} />
    </div>
  );
}

/** "aaaa-mm-dd" -> "dd/mm/aaaa" sem passar por fuso horário. */
function dataCurta(iso: string): string {
  const [a, m, d] = iso.split("-");
  return `${d}/${m}/${a}`;
}

export default function AdminRelatorios() {
  const [rascunho, setRascunho] = useState({ de: "", ate: "" });
  const [periodo, setPeriodo] = useState({ de: "", ate: "" });
  const [dados, setDados] = useState<Relatorios | null>(null);
  const [erro, setErro] = useState("");

  useEffect(() => {
    let ativo = true;
    api
      .get<Relatorios>("/admin/relatorios" + qs(periodo))
      .then((r) => {
        if (!ativo) return;
        setDados(r);
        setErro("");
      })
      .catch((e) => ativo && setErro((e as Error).message));
    return () => {
      ativo = false;
    };
  }, [periodo]);

  const textoPeriodo =
    periodo.de || periodo.ate
      ? `de ${periodo.de ? dataCurta(periodo.de) : "o início"} até ${periodo.ate ? dataCurta(periodo.ate) : "hoje"}`
      : "todo o período";

  const colAcervo: Coluna<Linha<"acervo">>[] = dados
    ? [
        { titulo: "Biblioteca", render: (l) => l.biblioteca },
        ...dados.statusExemplar.map((s) => ({
          titulo: dados.rotuloStatusExemplar[s],
          className: "text-right",
          render: (l: Linha<"acervo">) => l.porStatus[s] ?? 0,
        })),
        { titulo: "Total", className: "text-right", render: (l) => num(l.total) },
      ]
    : [];

  const colEmprestimos: Coluna<Linha<"emprestimos">>[] = [
    { titulo: "Biblioteca", render: (l) => l.biblioteca },
    { titulo: "Realizados", className: "text-right", render: (l) => num(l.realizados) },
    { titulo: "Devolvidos", className: "text-right", render: (l) => l.devolvidos },
    { titulo: "Devolvidos com atraso", className: "text-right", render: (l) => l.devolvidosComAtraso },
    { titulo: "Em aberto agora", className: "text-right", render: (l) => l.emAberto },
  ];

  const colAtrasos: Coluna<Linha<"atrasos">>[] = [
    { titulo: "Leitor", render: (l) => l.leitor },
    { titulo: "Livro", render: (l) => l.titulo },
    { titulo: "Biblioteca", render: (l) => l.biblioteca },
    { titulo: "Devolver até", render: (l) => formatarData(l.dataPrevDevolucao) },
    { titulo: "Dias de atraso", className: "text-right", render: (l) => num(l.diasAtraso) },
    {
      titulo: "Situação",
      render: (l) =>
        l.situacao === "Em aberto" ? (
          <Badge tom="vermelho">Em aberto</Badge>
        ) : (
          <Badge tom="cinza">Devolvido em {formatarData(l.dataDevolucao)}</Badge>
        ),
    },
  ];

  const colLivros: Coluna<Linha<"maisEmprestados">>[] = [
    { titulo: "Livro", render: (l) => l.titulo },
    { titulo: "Autor", render: (l) => l.autor },
    { titulo: "Empréstimos", className: "text-right", render: (l) => num(l.emprestimos) },
  ];

  const colDemandas: Coluna<Linha<"demandasMaisPedidas">>[] = [
    { titulo: "Livro", render: (l) => l.titulo },
    { titulo: "Autor", render: (l) => l.autor },
    { titulo: "Pedidos", className: "text-right", render: (l) => num(l.totalSolicitacoes) },
    { titulo: "Situação", render: (l) => l.statusRotulo },
  ];

  const colTransf: Coluna<Linha<"transferencias">>[] = [
    { titulo: "Situação", render: (l) => l.rotulo },
    { titulo: "De reserva", className: "text-right", render: (l) => l.deReserva },
    { titulo: "Avulsas", className: "text-right", render: (l) => l.avulsas },
    { titulo: "Total", className: "text-right", render: (l) => num(l.total) },
  ];

  return (
    <>
      <Trilha itens={[{ rotulo: "Painel", to: "/admin" }, { rotulo: "Relatórios" }]} />
      <TituloPagina titulo="Relatórios" subtitulo="Números da rede em tabelas, com filtro de período" />

      <SectionCard titulo="Período">
        <form
          className="flex flex-wrap items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            setPeriodo({ ...rascunho });
          }}
        >
          <Campo label="De" className="w-[170px]">
            <Entrada type="date" value={rascunho.de} onChange={(e) => setRascunho({ ...rascunho, de: e.target.value })} />
          </Campo>
          <Campo label="Até" className="w-[170px]">
            <Entrada type="date" value={rascunho.ate} onChange={(e) => setRascunho({ ...rascunho, ate: e.target.value })} />
          </Campo>
          <Botao type="submit">Aplicar</Botao>
          <Botao
            variante="secundario"
            onClick={() => {
              setRascunho({ de: "", ate: "" });
              setPeriodo({ de: "", ate: "" });
            }}
          >
            Todo o período
          </Botao>
          <span className="text-[13px] text-[#66707d]" data-testid="periodo">
            Mostrando {textoPeriodo}.
          </span>
        </form>
      </SectionCard>

      {erro && <Erro mensagem={erro} />}
      {!dados && !erro && <Carregando texto="Gerando relatórios..." />}

      {dados && (
        <>
          <SectionCard titulo="Acervo por biblioteca e situação (agora)">
            <Tabela id="rel-acervo" dados={dados.acervo} colunas={colAcervo} chave={(l) => l.bibliotecaId} vazio="Nenhuma biblioteca." />
          </SectionCard>
          <SectionCard titulo="Empréstimos no período, por biblioteca">
            <Tabela id="rel-emprestimos" dados={dados.emprestimos} colunas={colEmprestimos} chave={(l) => l.bibliotecaId} vazio="Nenhuma biblioteca." />
          </SectionCard>
          <SectionCard titulo="Atrasos (em aberto agora e devolvidos com atraso no período)">
            <Tabela id="rel-atrasos" dados={dados.atrasos} colunas={colAtrasos} chave={(l) => l.emprestimoId} vazio="Nenhum atraso." />
          </SectionCard>
          <SectionCard titulo="Livros mais emprestados no período">
            <Tabela id="rel-livros" dados={dados.maisEmprestados} colunas={colLivros} chave={(l) => l.livroId} vazio="Nenhum empréstimo no período." />
          </SectionCard>
          <SectionCard titulo="Demandas mais pedidas (criadas no período)">
            <Tabela id="rel-demandas" dados={dados.demandasMaisPedidas} colunas={colDemandas} chave={(l) => l.id} vazio="Nenhuma demanda no período." />
          </SectionCard>
          <SectionCard titulo="Transferências por situação (pedidas no período)">
            <Tabela id="rel-transferencias" dados={dados.transferencias} colunas={colTransf} chave={(l) => l.status} vazio="Nenhuma transferência." />
          </SectionCard>
        </>
      )}
    </>
  );
}
