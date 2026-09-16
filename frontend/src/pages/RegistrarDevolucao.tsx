/**
 * TELA 6 — Registrar Devolução (UC10).
 * Mostra o cálculo de atraso e o bloqueio resultante (RN12) antes de confirmar.
 */
import { useEffect, useState } from "react";
import { api, diasDeAtraso, formatarData } from "../api/client";
import type { Emprestimo } from "../types";
import {
  Badge, Botao, Campo, CapaLivro, CardResumo, Callout, Carregando, DuasColunas,
  Entrada, Erro, GrupoRadio, LinhaResumo, SectionCard, Sucesso, TituloPagina, Trilha, Vazio,
} from "../components/ui";

type Condicao = "BOM" | "DANIFICADO" | "PERDIDO";

const DIAS_BLOQUEIO_POR_ATRASO = 2; // RN12

export default function RegistrarDevolucao() {
  const [emprestimos, setEmprestimos] = useState<Emprestimo[]>([]);
  const [busca, setBusca] = useState("");
  const [selecionado, setSelecionado] = useState<Emprestimo | null>(null);
  const [condicao, setCondicao] = useState<Condicao>("BOM");

  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState("");
  const [sucesso, setSucesso] = useState("");
  const [enviando, setEnviando] = useState(false);

  function carregar() {
    setCarregando(true);
    api.get<Emprestimo[]>("/emprestimos/ativos")
      .then(setEmprestimos)
      .catch((e) => setErro(e.message))
      .finally(() => setCarregando(false));
  }

  useEffect(carregar, []);

  const filtrados = busca.trim()
    ? emprestimos.filter((e) => {
        const t = busca.toLowerCase();
        return (
          e.usuario.nome.toLowerCase().includes(t) ||
          e.exemplar.livro.titulo.toLowerCase().includes(t) ||
          e.exemplar.codigoBarras?.toLowerCase().includes(t)
        );
      })
    : emprestimos;

  const atraso = selecionado ? diasDeAtraso(selecionado.dataPrevDevolucao) : 0;
  const bloqueioAte = new Date(Date.now() + atraso * DIAS_BLOQUEIO_POR_ATRASO * 86_400_000);

  async function confirmar() {
    if (!selecionado) return;
    setEnviando(true);
    setErro("");
    setSucesso("");
    try {
      await api.post("/emprestimos/devolver", {
        emprestimoId: selecionado.id,
        condicaoExemplar: condicao,
      });
      setSucesso(
        atraso > 0
          ? `Devolução registrada com ${atraso} dia(s) de atraso. ${selecionado.usuario.nome} ` +
            `ficou bloqueado por ${atraso * DIAS_BLOQUEIO_POR_ATRASO} dias (RN12).`
          : "Devolução registrada dentro do prazo. O exemplar voltou para o acervo.",
      );
      setSelecionado(null);
      setCondicao("BOM");
      carregar();
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  if (carregando) return <Carregando texto="Carregando empréstimos em aberto..." />;

  return (
    <>
      <Trilha itens={[{ rotulo: "Painel da Biblioteca" }, { rotulo: "Registrar Devolução" }]} />
      <TituloPagina
        titulo="Registrar Devolução"
        subtitulo="Biblioteca Vila Isabel · UC10 - Registrar devolução"
      />

      {erro && <Erro mensagem={erro} />}
      {sucesso && <Sucesso mensagem={sucesso} />}

      <DuasColunas
        esquerda={
          <>
            <SectionCard titulo="1. Buscar exemplar emprestado">
              <Campo label="Nome do usuário, título ou código do exemplar">
                <Entrada
                  value={busca}
                  onChange={(e) => setBusca(e.target.value)}
                  placeholder="Digitar nome do usuário..."
                />
              </Campo>

              <div className="mt-4 flex flex-col gap-2">
                {filtrados.length === 0 && (
                  <Vazio texto="Nenhum empréstimo em aberto corresponde a essa busca." />
                )}
                {filtrados.map((e) => {
                  const ativo = selecionado?.id === e.id;
                  const d = diasDeAtraso(e.dataPrevDevolucao);
                  return (
                    <button
                      key={e.id}
                      onClick={() => setSelecionado(e)}
                      className={`flex items-center gap-4 rounded-[10px] p-4 text-left border
                        ${ativo ? "border-[#1976d2] bg-[#e8f0fc]" : "border-transparent bg-[#f5f7fa]"}`}
                    >
                      <CapaLivro tamanho="sm" />
                      <div className="flex-1 min-w-0">
                        <p className="text-[15px] font-semibold text-[#2c3e50]">
                          {e.exemplar.livro.titulo} — {e.exemplar.livro.autor}
                        </p>
                        <p className="text-[13px] text-[#66707d] mt-1">
                          Exemplar: {e.exemplar.codigoBarras ?? `#${e.exemplar.id}`} ·
                          Emprestado para: {e.usuario.nome}
                        </p>
                      </div>
                      {d > 0
                        ? <Badge tom="vermelho">🔴 Atrasado</Badge>
                        : <Badge tom="verde">🟢 Em dia</Badge>}
                    </button>
                  );
                })}
              </div>
            </SectionCard>

            <SectionCard titulo="2. Situação da devolução">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
                <Campo label="Devolução prevista">
                  <Entrada value={formatarData(selecionado?.dataPrevDevolucao)} readOnly />
                </Campo>
                <Campo label="Data da devolução (hoje)">
                  <Entrada value={new Date().toLocaleDateString("pt-BR")} readOnly />
                </Campo>
                <Campo label="Dias de atraso">
                  <div
                    className={`h-11 flex items-center rounded-[8px] border px-[14px] text-[14px] font-semibold
                      ${atraso > 0
                        ? "border-[#f3c6c6] bg-[#fce5e5] text-[#d32f2f]"
                        : "border-[#e0e0e0] bg-white text-[#2c3e50]"}`}
                  >
                    {selecionado ? (atraso > 0 ? `🔴 ${atraso} dia(s)` : "Em dia") : "—"}
                  </div>
                </Campo>
              </div>
            </SectionCard>

            <SectionCard titulo="3. Condição do exemplar">
              <GrupoRadio<Condicao>
                valor={condicao}
                onChange={setCondicao}
                opcoes={[
                  { valor: "BOM", rotulo: "Bom estado" },
                  { valor: "DANIFICADO", rotulo: "Danificado" },
                  { valor: "PERDIDO", rotulo: "Perdido" },
                ]}
              />
              {condicao !== "BOM" && (
                <p className="mt-3 text-[13px] text-[#66707d]">
                  Exemplares danificados ou perdidos saem de circulação
                  (status INDISPONIVEL) e o evento é registrado no histórico.
                </p>
              )}
            </SectionCard>
          </>
        }
        direita={
          <>
            {atraso > 0 ? (
              <Callout tipo="aviso" titulo="Devolução com atraso (RN12)">
                {atraso} dia(s) de atraso identificados. Para cada dia de atraso, o usuário
                fica impedido de novos empréstimos por mais {DIAS_BLOQUEIO_POR_ATRASO} dias
                — bloqueio total de {atraso * DIAS_BLOQUEIO_POR_ATRASO} dias.
              </Callout>
            ) : (
              <Callout tipo="sucesso" titulo="Devolução dentro do prazo">
                Nenhuma penalidade será aplicada a este usuário.
              </Callout>
            )}

            <Callout tipo="info" titulo="Atualização automática do Blackboard (RN05/RN06)">
              O status do exemplar volta para DISPONÍVEL e o evento é registrado no
              histórico de circulação. Se houver reserva pendente, o próximo da fila
              é notificado.
            </Callout>

            <CardResumo
              titulo="Resumo da devolução"
              rodape={
                <Botao
                  variante="verde"
                  onClick={confirmar}
                  disabled={!selecionado || enviando}
                  className="w-full"
                >
                  {enviando ? "Registrando..." : "✅ Confirmar Devolução"}
                </Botao>
              }
            >
              <LinhaResumo rotulo="Livro" valor={selecionado?.exemplar.livro.titulo ?? "—"} />
              <LinhaResumo rotulo="Usuário" valor={selecionado?.usuario.nome ?? "—"} />
              <LinhaResumo
                rotulo="Dias de atraso"
                valor={selecionado ? `${atraso} dia(s)` : "—"}
                destaque={atraso > 0 ? "vermelho" : undefined}
              />
              <LinhaResumo
                rotulo="Bloqueado até"
                valor={atraso > 0 ? bloqueioAte.toLocaleDateString("pt-BR") : "—"}
                destaque={atraso > 0 ? "vermelho" : undefined}
              />
            </CardResumo>
          </>
        }
      />
    </>
  );
}
