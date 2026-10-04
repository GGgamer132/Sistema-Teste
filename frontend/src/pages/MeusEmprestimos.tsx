/**
 * Meus Empréstimos — o usuário acompanha o que está com ele, os prazos
 * e se está apto a pegar novos livros (UC05 / UC08).
 */
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api, diasDeAtraso, formatarData } from "../api/client";
import type { Emprestimo, SituacaoUsuario } from "../types";
import {
  Badge,
  Botao,
  Callout,
  CapaLivro,
  CardResumo,
  Carregando,
  DuasColunas,
  Erro,
  LinhaResumo,
  SectionCard,
  TituloPagina,
  Trilha,
  Vazio,
} from "../components/ui";
import TabelaPaginada, { type Coluna } from "../components/TabelaPaginada";
import { useUsuarioLogado } from "../auth/contexto";

const DIAS_BLOQUEIO_POR_ATRASO = 2;

type Situacao = "ATIVO" | "ATRASADO" | "DEVOLVIDO";

/** O status no banco pode estar desatualizado; o atraso também é derivado da data. */
function situacaoDe(e: Emprestimo): Situacao {
  if (e.status === "DEVOLVIDO" || e.dataDevolucao) return "DEVOLVIDO";
  return e.status === "ATRASADO" || diasDeAtraso(e.dataPrevDevolucao) > 0
    ? "ATRASADO"
    : "ATIVO";
}

/** Dias que faltam até a devolução (arredondado para cima; 0 = vence hoje). */
function diasRestantes(prev: string): number {
  return Math.ceil((new Date(prev).getTime() - Date.now()) / 86_400_000);
}

function PrazoBadge({ e }: { e: Emprestimo }) {
  if (situacaoDe(e) === "ATRASADO") {
    const d = Math.max(1, diasDeAtraso(e.dataPrevDevolucao));
    return <Badge tom="vermelho">Atrasado há {d} {d === 1 ? "dia" : "dias"}</Badge>;
  }
  const r = diasRestantes(e.dataPrevDevolucao);
  if (r <= 0) return <Badge tom="amarelo">Vence hoje</Badge>;
  if (r <= 3) return <Badge tom="amarelo">Faltam {r} {r === 1 ? "dia" : "dias"}</Badge>;
  return <Badge tom="verde">Faltam {r} dias</Badge>;
}

export default function MeusEmprestimos() {
  const { id: usuarioId } = useUsuarioLogado();
  const [lista, setLista] = useState<Emprestimo[] | null>(null);
  const [situacao, setSituacao] = useState<SituacaoUsuario | null>(null);
  const [erro, setErro] = useState("");

  function carregar() {
    Promise.all([
      api.get<Emprestimo[]>(`/emprestimos/usuario/${usuarioId}`),
      api.get<SituacaoUsuario>(`/emprestimos/situacao/${usuarioId}`),
    ])
      .then(([emp, sit]) => {
        setLista(emp);
        setSituacao(sit);
      })
      .catch((e) => setErro(e.message));
  }
  useEffect(carregar, [usuarioId]);

  const abertos = useMemo(
    () =>
      (lista ?? [])
        .filter((e) => situacaoDe(e) !== "DEVOLVIDO")
        .sort(
          (a, b) =>
            new Date(a.dataPrevDevolucao).getTime() -
            new Date(b.dataPrevDevolucao).getTime(),
        ),
    [lista],
  );

  const devolvidos = useMemo(
    () =>
      (lista ?? [])
        .filter((e) => situacaoDe(e) === "DEVOLVIDO")
        .sort(
          (a, b) =>
            new Date(b.dataDevolucao ?? b.dataEmprestimo).getTime() -
            new Date(a.dataDevolucao ?? a.dataEmprestimo).getTime(),
        ),
    [lista],
  );

  const atrasados = abertos.filter((e) => situacaoDe(e) === "ATRASADO").length;
  const limite = situacao?.limite ?? 3;

  const colunasHistorico: Coluna<Emprestimo>[] = [
    {
      titulo: "Livro",
      render: (e) => (
        <div>
          <p className="font-semibold text-[#2c3e50]">{e.exemplar.livro.titulo}</p>
          <p className="text-[12px] text-[#66707d]">{e.exemplar.livro.autor}</p>
        </div>
      ),
    },
    { titulo: "Biblioteca", render: (e) => e.biblioteca.nome },
    { titulo: "Emprestado em", render: (e) => formatarData(e.dataEmprestimo) },
    { titulo: "Devolvido em", render: (e) => formatarData(e.dataDevolucao) },
    {
      titulo: "Situação",
      render: (e) => {
        const dev = e.dataDevolucao ? new Date(e.dataDevolucao).getTime() : 0;
        const prev = new Date(e.dataPrevDevolucao).getTime();
        return dev > prev + 86_400_000 ? (
          <Badge tom="amarelo">Devolvido com atraso</Badge>
        ) : (
          <Badge tom="verde">Devolvido</Badge>
        );
      },
    },
  ];

  if (lista === null && !erro) {
    return <Carregando texto="Carregando seus empréstimos..." />;
  }

  return (
    <>
      <Trilha itens={[{ rotulo: "Meus Empréstimos" }]} />
      <TituloPagina
        titulo="Meus Empréstimos"
        subtitulo="Livros que estão com você, prazos de devolução e sua situação na rede"
      />

      {erro && <Erro mensagem={erro} />}

      {situacao?.bloqueado && (
        <Callout tipo="aviso" titulo="Empréstimos bloqueados">
          {situacao.motivo ||
            `Você está bloqueado para novos empréstimos${
              situacao.bloqueadoAte
                ? ` até ${formatarData(situacao.bloqueadoAte)}`
                : ""
            }.`}
        </Callout>
      )}
      {!situacao?.bloqueado && atrasados > 0 && (
        <Callout tipo="aviso" titulo="Você tem livros atrasados">
          Devolva o quanto antes na biblioteca onde retirou. Cada dia de atraso
          gera {DIAS_BLOQUEIO_POR_ATRASO} dias de bloqueio para novos
          empréstimos.
        </Callout>
      )}

      <DuasColunas
        esquerda={
          <>
            <SectionCard titulo={`1. Em andamento (${abertos.length})`}>
              {abertos.length === 0 && (
                <Vazio texto="Você não tem livros emprestados no momento." />
              )}

              <div className="flex flex-col gap-3">
                {abertos.map((e) => {
                  const atrasado = situacaoDe(e) === "ATRASADO";
                  return (
                    <div
                      key={e.id}
                      className={`flex flex-wrap items-center gap-4 rounded-[10px] p-4 ${
                        atrasado ? "bg-[#fce5e5]" : "bg-[#f5f7fa]"
                      }`}
                    >
                      <CapaLivro tamanho="sm" />
                      <div className="flex-1 min-w-[240px] flex flex-col gap-1">
                        <Link
                          to={`/livro/${e.exemplar.livro.id}`}
                          className="text-[15px] font-semibold text-[#2c3e50] hover:text-[#1976d2]"
                        >
                          {e.exemplar.livro.titulo}
                        </Link>
                        <p className="text-[13px] text-[#66707d]">
                          {e.exemplar.livro.autor}
                        </p>
                        <p className="text-[13px] text-[#125ca8]">
                          📍 {e.biblioteca.nome}
                          {e.exemplar.codigoBarras &&
                            ` · Exemplar ${e.exemplar.codigoBarras}`}
                        </p>
                        <p className="text-[12px] text-[#66707d]">
                          Retirado em {formatarData(e.dataEmprestimo)} · devolver
                          até <strong>{formatarData(e.dataPrevDevolucao)}</strong>
                        </p>
                      </div>
                      <PrazoBadge e={e} />
                    </div>
                  );
                })}
              </div>
            </SectionCard>

            <SectionCard titulo="2. Já devolvidos">
              <div className="rounded-[10px] border border-[#e0e0e0] overflow-hidden">
                <TabelaPaginada
                  dados={devolvidos}
                  colunas={colunasHistorico}
                  chave={(e) => e.id}
                  porPagina={6}
                  textoVazio="Você ainda não devolveu nenhum livro."
                />
              </div>
            </SectionCard>
          </>
        }
        direita={
          <>
            <Callout tipo="info" titulo="Como devolver">
              A devolução é feita presencialmente: leve o livro à biblioteca onde
              retirou e o bibliotecário registra no sistema. O prazo padrão é de
              14 dias.
            </Callout>

            <CardResumo
              titulo="Minha situação"
              rodape={
                <Link to="/">
                  <Botao variante="primario" className="w-full">
                    🔎 Buscar livros
                  </Botao>
                </Link>
              }
            >
              <LinhaResumo
                rotulo="Empréstimos em aberto"
                valor={`${situacao?.emprestimosAtivos ?? abertos.length} de ${limite}`}
                destaque={
                  (situacao?.emprestimosAtivos ?? abertos.length) >= limite
                    ? "vermelho"
                    : "azul"
                }
              />
              <LinhaResumo
                rotulo="Atrasados"
                valor={atrasados}
                destaque={atrasados > 0 ? "vermelho" : "verde"}
              />
              <LinhaResumo
                rotulo="Apto a emprestar"
                valor={situacao?.apto ? "Sim" : "Não"}
                destaque={situacao?.apto ? "verde" : "vermelho"}
              />
              {situacao?.bloqueadoAte && situacao.bloqueado && (
                <LinhaResumo
                  rotulo="Bloqueado até"
                  valor={formatarData(situacao.bloqueadoAte)}
                  destaque="vermelho"
                />
              )}
            </CardResumo>
          </>
        }
      />
    </>
  );
}
