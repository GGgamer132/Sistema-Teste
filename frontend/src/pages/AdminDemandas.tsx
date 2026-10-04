/**
 * Demandas de aquisição (A9) — pedidos de livros que a rede não tem, das mais
 * pedidas para as menos. O Admin conduz: Aberta -> Em análise -> Aprovada | Rejeitada.
 */
import { useEffect, useState } from "react";
import { api, formatarData, todasAsPaginas } from "../api/client";
import type { Demanda, StatusDemanda } from "../types";
import {
  Badge,
  Botao,
  Callout,
  Carregando,
  DuasColunas,
  Erro,
  SectionCard,
  Selecao,
  Sucesso,
  TituloPagina,
  Trilha,
} from "../components/ui";
import ModalConfirmacao, { ResumoModal } from "../components/ModalConfirmacao";
import TabelaPaginada, { type Coluna } from "../components/TabelaPaginada";

const ROTULO: Record<StatusDemanda, string> = {
  ABERTA: "Aberta",
  EM_ANALISE: "Em análise",
  APROVADA: "Aprovada",
  REJEITADA: "Rejeitada",
};

const TOM: Record<StatusDemanda, "cinza" | "azul" | "verde" | "vermelho"> = {
  ABERTA: "cinza",
  EM_ANALISE: "azul",
  APROVADA: "verde",
  REJEITADA: "vermelho",
};

/** Verbo do botão para cada situação de destino. */
const ACAO: Record<StatusDemanda, string> = {
  ABERTA: "Reabrir",
  EM_ANALISE: "Iniciar análise",
  APROVADA: "Aprovar compra",
  REJEITADA: "Rejeitar",
};

const EFEITO: Record<StatusDemanda, string> = {
  ABERTA: "",
  EM_ANALISE: "A demanda passa a ser analisada pela administração.",
  APROVADA: "A compra fica aprovada. Depois disso a situação não muda mais.",
  REJEITADA: "A demanda é encerrada sem compra. Depois disso a situação não muda mais.",
};

export default function AdminDemandas() {
  const [filtro, setFiltro] = useState<"" | StatusDemanda>("");
  const [demandas, setDemandas] = useState<Demanda[] | null>(null);
  const [erro, setErro] = useState("");
  const [sucesso, setSucesso] = useState("");
  const [alvo, setAlvo] = useState<{ d: Demanda; para: StatusDemanda } | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [versao, setVersao] = useState(0);

  useEffect(() => {
    let ativo = true;
    todasAsPaginas<Demanda>("/demandas", { status: filtro })
      .then((l) => ativo && setDemandas(l))
      .catch((e) => ativo && setErro((e as Error).message));
    return () => {
      ativo = false;
    };
  }, [filtro, versao]);

  async function confirmar() {
    if (!alvo) return;
    setEnviando(true);
    setErro("");
    setSucesso("");
    try {
      const d = await api.patch<Demanda>(`/demandas/${alvo.d.id}/status`, { status: alvo.para });
      setSucesso(`"${d.titulo}" agora está ${d.statusRotulo.toLowerCase()}.`);
      setVersao((v) => v + 1);
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setAlvo(null);
      setEnviando(false);
    }
  }

  const colunas: Coluna<Demanda>[] = [
    {
      titulo: "Livro",
      render: (d) => (
        <div>
          <p className="font-semibold text-[#2c3e50]">{d.titulo}</p>
          <p className="text-[12px] text-[#66707d]">{d.autor || "Autor não informado"}</p>
        </div>
      ),
    },
    {
      titulo: "Pedidos",
      render: (d) => (
        <span className="font-semibold">
          {d.totalSolicitacoes} {d.totalSolicitacoes === 1 ? "pessoa" : "pessoas"}
        </span>
      ),
    },
    { titulo: "Situação", render: (d) => <Badge tom={TOM[d.status]}>{d.statusRotulo}</Badge> },
    {
      titulo: "Datas",
      render: (d) => (
        <span className="text-[12px] text-[#66707d]">
          Criada em {formatarData(d.criadaEm)}
          <br />
          Atualizada em {formatarData(d.atualizadaEm)}
        </span>
      ),
    },
    {
      titulo: "Ação",
      className: "text-right",
      render: (d) =>
        d.proximosStatus.length === 0 ? (
          <span className="text-[12px] text-[#9aa3ad]">Encerrada</span>
        ) : (
          <div className="flex justify-end gap-2">
            {d.proximosStatus.map((p) => (
              <Botao
                key={p}
                variante={p === "REJEITADA" ? "perigo" : p === "APROVADA" ? "verde" : "secundario"}
                onClick={() => setAlvo({ d, para: p })}
              >
                {ACAO[p]}
              </Botao>
            ))}
          </div>
        ),
    },
  ];

  return (
    <>
      <Trilha itens={[{ rotulo: "Painel", to: "/admin" }, { rotulo: "Demandas" }]} />
      <TituloPagina
        titulo="Demandas de aquisição"
        subtitulo="Livros que os leitores pediram e a rede ainda não tem, dos mais pedidos para os menos"
      />

      {erro && <Erro mensagem={erro} />}
      {sucesso && <Sucesso mensagem={sucesso} />}

      <DuasColunas
        esquerda={
          <SectionCard
            titulo="Demandas"
            acao={
              <Selecao
                aria-label="Filtrar por situação"
                value={filtro}
                onChange={(e) => setFiltro(e.target.value as "" | StatusDemanda)}
                className="!w-[200px]"
              >
                <option value="">Todas</option>
                {(Object.keys(ROTULO) as StatusDemanda[]).map((s) => (
                  <option key={s} value={s}>
                    {ROTULO[s]}
                  </option>
                ))}
              </Selecao>
            }
          >
            {demandas === null ? (
              <Carregando texto="Carregando demandas..." />
            ) : (
              <div className="rounded-[10px] border border-[#e0e0e0] overflow-hidden">
                <TabelaPaginada
                  key={filtro}
                  dados={demandas}
                  colunas={colunas}
                  chave={(d) => d.id}
                  porPagina={10}
                  textoVazio="Nenhuma demanda nesta situação."
                />
              </div>
            )}
          </SectionCard>
        }
        direita={
          <Callout tipo="info" titulo="Como as demandas andam">
            Toda demanda nasce aberta. Inicie a análise e depois aprove ou rejeite a
            compra. Novos pedidos do mesmo livro continuam somando, em qualquer situação.
          </Callout>
        }
      />

      <ModalConfirmacao
        aberto={!!alvo}
        titulo={alvo ? `${ACAO[alvo.para]}?` : ""}
        confirmarRotulo={alvo ? ACAO[alvo.para] : "Confirmar"}
        tom={alvo?.para === "REJEITADA" ? "perigo" : alvo?.para === "APROVADA" ? "verde" : "normal"}
        carregando={enviando}
        onConfirmar={confirmar}
        onCancelar={() => setAlvo(null)}
      >
        {alvo && (
          <>
            <ResumoModal
              linhas={[
                ["Livro", `${alvo.d.titulo} — ${alvo.d.autor}`],
                ["Pedidos", String(alvo.d.totalSolicitacoes)],
                ["Situação", `${alvo.d.statusRotulo} → ${ROTULO[alvo.para]}`],
              ]}
            />
            <p>{EFEITO[alvo.para]}</p>
          </>
        )}
      </ModalConfirmacao>
    </>
  );
}
