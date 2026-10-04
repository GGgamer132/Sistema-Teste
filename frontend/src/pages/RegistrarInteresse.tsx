/**
 * Pedir um livro (U8) — o usuário comum registra interesse num livro que a rede não tem.
 * Pedidos iguais (mesmo título e autor) de pessoas diferentes somam no contador da
 * demanda; a mesma pessoa não repete o pedido. Aceita ?titulo=&autor= (vindo da busca vazia).
 */
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, formatarData } from "../api/client";
import type { InteresseResposta, MeuInteresse } from "../types";
import {
  Badge,
  Botao,
  Callout,
  Campo,
  DuasColunas,
  Entrada,
  Erro,
  SectionCard,
  Sucesso,
  TituloPagina,
  Trilha,
} from "../components/ui";
import ModalConfirmacao, { ResumoModal } from "../components/ModalConfirmacao";
import TabelaPaginada, { type Coluna } from "../components/TabelaPaginada";

const TOM: Record<string, "cinza" | "azul" | "verde" | "vermelho"> = {
  ABERTA: "cinza",
  EM_ANALISE: "azul",
  APROVADA: "verde",
  REJEITADA: "vermelho",
};

export default function RegistrarInteresse() {
  const [searchParams] = useSearchParams();
  const [titulo, setTitulo] = useState(searchParams.get("titulo") ?? "");
  const [autor, setAutor] = useState(searchParams.get("autor") ?? "");
  const [confirmando, setConfirmando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState("");
  const [sucesso, setSucesso] = useState("");
  const [meus, setMeus] = useState<MeuInteresse[]>([]);

  function carregar() {
    api
      .get<MeuInteresse[]>("/demandas/minhas")
      .then(setMeus)
      .catch((e) => setErro(e.message));
  }
  useEffect(carregar, []);

  async function enviar() {
    setEnviando(true);
    setErro("");
    setSucesso("");
    try {
      const r = await api.post<InteresseResposta>("/demandas", {
        titulo: titulo.trim(),
        autor: autor.trim(),
      });
      setSucesso(r.mensagem);
      setTitulo("");
      setAutor("");
      carregar();
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setEnviando(false);
      setConfirmando(false);
    }
  }

  const colunas: Coluna<MeuInteresse>[] = [
    {
      titulo: "Livro",
      render: (m) => (
        <div>
          <p className="font-semibold text-[#2c3e50]">{m.titulo}</p>
          <p className="text-[12px] text-[#66707d]">{m.autor}</p>
        </div>
      ),
    },
    {
      titulo: "Pedidos",
      render: (m) => `${m.totalSolicitacoes} ${m.totalSolicitacoes === 1 ? "pessoa" : "pessoas"}`,
    },
    { titulo: "Situação", render: (m) => <Badge tom={TOM[m.status] ?? "cinza"}>{m.statusRotulo}</Badge> },
    { titulo: "Pedido em", render: (m) => formatarData(m.registradoEm) },
  ];

  const podeEnviar = titulo.trim() !== "" && autor.trim() !== "";

  return (
    <>
      <Trilha itens={[{ rotulo: "Pedir um Livro" }]} />
      <TituloPagina
        titulo="Pedir um Livro"
        subtitulo="Não encontrou o que procurava? Registre o interesse para a rede avaliar a compra"
      />

      {erro && <Erro mensagem={erro} />}
      {sucesso && <Sucesso mensagem={sucesso} />}

      <DuasColunas
        esquerda={
          <>
            <SectionCard titulo="Qual livro você gostaria que a rede tivesse?">
              <form
                className="flex flex-col gap-4"
                onSubmit={(e) => {
                  e.preventDefault();
                  if (podeEnviar) setConfirmando(true);
                }}
              >
                <Campo label="Título">
                  <Entrada value={titulo} onChange={(e) => setTitulo(e.target.value)} maxLength={255} />
                </Campo>
                <Campo label="Autor">
                  <Entrada value={autor} onChange={(e) => setAutor(e.target.value)} maxLength={255} />
                </Campo>
                <div>
                  <Botao type="submit" disabled={!podeEnviar}>
                    📝 Registrar interesse
                  </Botao>
                </div>
              </form>
            </SectionCard>

            <SectionCard titulo="Meus pedidos">
              <div className="rounded-[10px] border border-[#e0e0e0] overflow-hidden">
                <TabelaPaginada
                  dados={meus}
                  colunas={colunas}
                  chave={(m) => m.demandaId}
                  porPagina={8}
                  textoVazio="Você ainda não pediu nenhum livro."
                />
              </div>
            </SectionCard>
          </>
        }
        direita={
          <Callout tipo="info" titulo="Como funciona">
            Pedidos do mesmo livro feitos por pessoas diferentes são somados: quanto
            mais gente pede, maior a prioridade. A administração da rede analisa e
            decide a compra; acompanhe a situação em "Meus pedidos".
          </Callout>
        }
      />

      <ModalConfirmacao
        aberto={confirmando}
        titulo="Registrar interesse?"
        confirmarRotulo="Registrar"
        carregando={enviando}
        onConfirmar={enviar}
        onCancelar={() => setConfirmando(false)}
      >
        <ResumoModal
          linhas={[
            ["Título", titulo.trim()],
            ["Autor", autor.trim()],
          ]}
        />
      </ModalConfirmacao>
    </>
  );
}
