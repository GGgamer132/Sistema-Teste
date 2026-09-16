/**
 * TELA 8 — Solicitações de Transferência (UC13 / UC24).
 * Painel do Admin: aprova ou rejeita as solicitações pendentes (RN04)
 * e acompanha o histórico recente.
 */
import { useEffect, useState } from "react";
import { api, formatarData } from "../api/client";
import type { SolicitacaoTransferencia } from "../types";
import {
  BadgeTransferencia, Botao, CapaLivro, CardResumo, Callout, Carregando, DuasColunas,
  Erro, LinhaResumo, SectionCard, Sucesso, TituloPagina, Trilha, Vazio,
} from "../components/ui";

// Sem login, o admin é fixo: Roberto Dias (id 1).
const ADMIN_LOGADO = 1;

interface Resumo {
  pendentes: number;
  emTransito: number;
  concluidas: number;
  rejeitadas: number;
}

export default function TransferenciasAdmin() {
  const [pendentes, setPendentes] = useState<SolicitacaoTransferencia[]>([]);
  const [historico, setHistorico] = useState<SolicitacaoTransferencia[]>([]);
  const [resumo, setResumo] = useState<Resumo | null>(null);

  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState("");
  const [sucesso, setSucesso] = useState("");
  const [processando, setProcessando] = useState<number | null>(null);

  function carregar() {
    setCarregando(true);
    Promise.all([
      api.get<SolicitacaoTransferencia[]>("/transferencias/pendentes"),
      api.get<SolicitacaoTransferencia[]>("/transferencias/historico"),
      api.get<Resumo>("/transferencias/resumo"),
    ])
      .then(([p, h, r]) => {
        setPendentes(p);
        setHistorico(h);
        setResumo(r);
      })
      .catch((e) => setErro(e.message))
      .finally(() => setCarregando(false));
  }

  useEffect(carregar, []);

  async function decidir(id: number, acao: "aprovar" | "rejeitar") {
    setProcessando(id);
    setErro("");
    setSucesso("");
    try {
      await api.patch(`/transferencias/${id}/${acao}?adminId=${ADMIN_LOGADO}`);
      setSucesso(
        acao === "aprovar"
          ? `Transferência #${id} aprovada. O exemplar entrou em trânsito (RN05).`
          : `Transferência #${id} rejeitada. O exemplar voltou a ficar disponível na origem.`,
      );
      carregar();
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setProcessando(null);
    }
  }

  /** Descreve quem pediu, diferenciando usuário comum de bibliotecário (RN13). */
  function descreverSolicitante(s: SolicitacaoTransferencia): string {
    const papel =
      s.solicitante.tipo === "COMUM"
        ? "Usuário da comunidade"
        : s.solicitante.tipo === "ADMIN"
          ? "Administrador"
          : "Bibliotecário";
    return `Exemplar ${s.exemplar.codigoBarras ?? `#${s.exemplar.id}`} · Solicitado por ${s.solicitante.nome} (${papel})`;
  }

  if (carregando) return <Carregando texto="Carregando solicitações..." />;

  return (
    <>
      <Trilha itens={[{ rotulo: "Dashboard" }, { rotulo: "Solicitações de Transferência" }]} />
      <TituloPagina
        titulo="Solicitações de Transferência"
        subtitulo="Administração da Rede · UC13/UC24 - Aprovar/rejeitar transferência de exemplares"
      />

      {erro && <Erro mensagem={erro} />}
      {sucesso && <Sucesso mensagem={sucesso} />}

      <DuasColunas
        esquerda={
          <>
            <SectionCard titulo={`1. Solicitações pendentes de aprovação (${pendentes.length})`}>
              {pendentes.length === 0 && (
                <Vazio texto="Nenhuma solicitação aguardando decisão. O sistema continua monitorando a rede." />
              )}

              <div className="flex flex-col gap-3">
                {pendentes.map((s) => (
                  <div key={s.id} className="flex flex-wrap items-center gap-4 rounded-[10px] bg-[#f5f7fa] p-4">
                    <CapaLivro tamanho="sm" />

                    <div className="flex-1 min-w-[240px]">
                      <p className="text-[15px] font-semibold text-[#2c3e50]">
                        {s.exemplar.livro.titulo} — {s.exemplar.livro.autor}
                      </p>
                      <p className="text-[13px] text-[#66707d] mt-1">
                        {descreverSolicitante(s)}
                      </p>
                      <p className="text-[13px] text-[#125ca8] mt-1">
                        🔁 {s.bibliotecaOrigem.nome} → {s.bibliotecaDestino.nome}
                      </p>
                      <div className="mt-2">
                        <BadgeTransferencia status={s.status} />
                      </div>
                      {s.observacoes && (
                        <p className="mt-2 text-[12px] text-[#66707d] max-w-[60ch]">
                          {s.observacoes}
                        </p>
                      )}
                    </div>

                    <div className="flex flex-col gap-2 w-[150px]">
                      <Botao
                        variante="verde"
                        onClick={() => decidir(s.id, "aprovar")}
                        disabled={processando === s.id}
                      >
                        ✅ Aprovar
                      </Botao>
                      <Botao
                        variante="perigo"
                        onClick={() => decidir(s.id, "rejeitar")}
                        disabled={processando === s.id}
                      >
                        ❌ Rejeitar
                      </Botao>
                    </div>
                  </div>
                ))}
              </div>
            </SectionCard>

            <SectionCard titulo="2. Histórico recente de transferências">
              {historico.length === 0 && (
                <Vazio texto="Nenhuma transferência processada ainda." />
              )}

              <div className="flex flex-col gap-3">
                {historico.map((s) => (
                  <div key={s.id} className="flex flex-wrap items-center gap-4 rounded-[10px] bg-[#f5f7fa] p-4">
                    <CapaLivro tamanho="sm" />

                    <div className="flex-1 min-w-[240px]">
                      <p className="text-[15px] font-semibold text-[#2c3e50]">
                        {s.exemplar.livro.titulo} — {s.exemplar.livro.autor}
                      </p>
                      <p className="text-[13px] text-[#66707d] mt-1">
                        Exemplar {s.exemplar.codigoBarras ?? `#${s.exemplar.id}`}
                        {s.aprovador ? ` · Decidida por ${s.aprovador.nome}` : ""}
                      </p>
                      <p className="text-[13px] text-[#66707d] mt-1">
                        De {s.bibliotecaOrigem.nome} → {s.bibliotecaDestino.nome}
                      </p>
                    </div>

                    <div className="flex flex-col items-end gap-2 w-[130px]">
                      <BadgeTransferencia status={s.status} />
                      <span className="text-[12px] text-[#66707d]">
                        {formatarData(s.dataConclusao ?? s.dataSolicitacao)}
                      </span>
                      {(s.status === "EM_TRANSITO" || s.status === "APROVADA") && (
                        <button
                          onClick={async () => {
                            try {
                              await api.patch(
                                `/transferencias/${s.id}/confirmar-chegada?responsavelId=${ADMIN_LOGADO}`,
                              );
                              setSucesso(`Chegada do exemplar confirmada em ${s.bibliotecaDestino.nome}.`);
                              carregar();
                            } catch (e) {
                              setErro((e as Error).message);
                            }
                          }}
                          className="text-[12px] font-semibold text-[#1976d2] hover:underline"
                        >
                          Confirmar chegada
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </SectionCard>
          </>
        }
        direita={
          <>
            <Callout tipo="info" titulo="Aprovação de transferências (RN04)">
              Toda solicitação de transferência entre bibliotecas precisa ser aprovada pelo
              Admin do sistema antes do envio. Apenas exemplares com status DISPONÍVEL podem
              ser transferidos.
            </Callout>

            <Callout tipo="aviso" titulo="Fluxo de status do exemplar (RN05)">
              Ao aprovar, o exemplar muda de DISPONÍVEL para EM_TRANSFERENCIA. Ao confirmar a
              chegada, o status retorna para DISPONÍVEL na biblioteca de destino.
            </Callout>

            <CardResumo
              titulo="Resumo das Solicitações"
              rodape={
                <Botao variante="secundario" onClick={carregar} className="w-full">
                  🔄 Atualizar Lista
                </Botao>
              }
            >
              <LinhaResumo rotulo="Pendentes" valor={resumo?.pendentes ?? 0} destaque="laranja" />
              <LinhaResumo rotulo="Em trânsito" valor={resumo?.emTransito ?? 0} destaque="azul" />
              <LinhaResumo rotulo="Concluídas" valor={resumo?.concluidas ?? 0} destaque="verde" />
              <LinhaResumo rotulo="Rejeitadas" valor={resumo?.rejeitadas ?? 0} destaque="vermelho" />
            </CardResumo>
          </>
        }
      />
    </>
  );
}
