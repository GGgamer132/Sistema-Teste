/**
 * Reservas da biblioteca (B6) — só a biblioteca do bibliotecário logado.
 *  - Aguardando retirada: reservas prontas (usuário, livro, exemplar, prazo), com atalho
 *    para o Novo empréstimo já preenchido.
 *  - Filas de espera por título.
 */
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, formatarData, nomeExemplar } from "../api/client";
import type { ReservasBiblioteca as Dados } from "../types";
import { useUsuarioLogado } from "../auth/contexto";
import {
  Badge,
  Botao,
  Callout,
  Carregando,
  DuasColunas,
  Erro,
  SectionCard,
  TituloPagina,
  Trilha,
  Vazio,
} from "../components/ui";
import TabelaPaginada, { type Coluna } from "../components/TabelaPaginada";

type Pronta = Dados["aguardandoRetirada"][number];

/** Dias corridos até o fim do prazo (0 = vence hoje). */
function diasAte(iso: string): number {
  return Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000);
}

export default function ReservasBiblioteca() {
  const { bibliotecaNome } = useUsuarioLogado();
  const navigate = useNavigate();
  const [dados, setDados] = useState<Dados | null>(null);
  const [erro, setErro] = useState("");

  useEffect(() => {
    api
      .get<Dados>("/reservas/biblioteca")
      .then(setDados)
      .catch((e) => setErro(e.message));
  }, []);

  if (dados === null && !erro) {
    return <Carregando texto="Carregando reservas..." />;
  }

  const colunas: Coluna<Pronta>[] = [
    {
      titulo: "Leitor",
      render: (r) => (
        <div>
          <p className="font-semibold text-[#2c3e50]">{r.usuario}</p>
          <p className="text-[12px] text-[#66707d]">{r.email}</p>
        </div>
      ),
    },
    { titulo: "Livro", render: (r) => r.titulo },
    { titulo: "Exemplar", render: (r) => (r.exemplarId ? nomeExemplar(r.exemplarId) : "—") },
    {
      titulo: "Prazo",
      render: (r) => {
        const d = diasAte(r.retireAte);
        return (
          <div className="flex flex-col gap-1">
            <span>Retirar até {formatarData(r.retireAte)}</span>
            <span>
              <Badge tom={d <= 1 ? "vermelho" : "amarelo"}>
                {d <= 0 ? "Vence hoje" : `Faltam ${d} ${d === 1 ? "dia" : "dias"}`}
              </Badge>
            </span>
          </div>
        );
      },
    },
    {
      titulo: "Ação",
      className: "text-right",
      render: (r) => (
        <Botao
          onClick={() =>
            navigate(`/biblioteca/emprestimo?usuario=${r.usuarioId}&exemplar=${r.exemplarId ?? ""}`)
          }
        >
          Novo empréstimo
        </Botao>
      ),
    },
  ];

  return (
    <>
      <Trilha itens={[{ rotulo: "Empréstimos", to: "/biblioteca" }, { rotulo: "Reservas" }]} />
      <TituloPagina
        titulo="Reservas"
        subtitulo={`${bibliotecaNome ?? ""} · livros separados aguardando retirada e filas de espera`}
      />

      {erro && <Erro mensagem={erro} />}

      <DuasColunas
        esquerda={
          <>
            <SectionCard titulo={`Aguardando retirada (${dados?.aguardandoRetirada.length ?? 0})`}>
              <div className="rounded-[10px] border border-[#e0e0e0] overflow-hidden">
                <TabelaPaginada
                  dados={dados?.aguardandoRetirada ?? []}
                  colunas={colunas}
                  chave={(r) => r.reservaId}
                  porPagina={8}
                  textoVazio="Nenhuma reserva aguardando retirada nesta biblioteca."
                />
              </div>
            </SectionCard>

            <SectionCard titulo="Filas de espera por título">
              {dados?.filas.length === 0 && (
                <Vazio texto="Nenhuma fila de espera nesta biblioteca." />
              )}
              <div className="flex flex-col gap-3">
                {dados?.filas.map((f) => (
                  <div key={f.livroId} data-testid="fila" className="rounded-[10px] bg-[#f5f7fa] p-4">
                    <p className="text-[15px] font-semibold text-[#2c3e50]">
                      {f.titulo}{" "}
                      <span className="text-[13px] font-normal text-[#66707d]">
                        · {f.fila.length} {f.fila.length === 1 ? "pessoa" : "pessoas"} na fila
                      </span>
                    </p>
                    <ol className="mt-2 flex flex-col gap-1 text-[13px] text-[#2c3e50]">
                      {f.fila.map((n) => (
                        <li key={n.reservaId}>
                          {n.posicao}º — {n.usuario}{" "}
                          <span className="text-[#66707d]">
                            (desde {formatarData(n.dataReserva)}; retirada na {n.bibliotecaRetirada})
                          </span>
                        </li>
                      ))}
                    </ol>
                  </div>
                ))}
              </div>
            </SectionCard>
          </>
        }
        direita={
          <Callout tipo="info" titulo="Retirada">
            O leitor tem 3 dias para retirar o exemplar separado. Depois disso a
            reserva expira e o exemplar vai para o próximo da fila.
          </Callout>
        }
      />
    </>
  );
}
