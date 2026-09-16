/**
 * TELA 5 — Reservar Livro (UC03 / RN03).
 * Só faz sentido quando não há exemplar livre; o backend recusa o contrário.
 */
import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { Biblioteca, LivroResumo, Reserva } from "../types";
import type { Rota } from "../App";
import {
  BadgeSituacao, Botao, Campo, CapaLivro, Callout, CardResumo, Carregando, DuasColunas,
  Erro, LinhaResumo, SectionCard, Selecao, Sucesso, TituloPagina, Trilha,
} from "../components/ui";

// Na ausência de login, o usuário da comunidade é fixo (Ana Souza, id 6).
const USUARIO_LOGADO = 6;

export default function ReservarLivro({
  livroId,
  bibliotecaId,
  navegar,
}: {
  livroId: number;
  bibliotecaId?: number;
  navegar: (r: Rota) => void;
}) {
  const [livro, setLivro] = useState<LivroResumo | null>(null);
  const [bibliotecas, setBibliotecas] = useState<Biblioteca[]>([]);
  const [destino, setDestino] = useState<number | "">(bibliotecaId ?? "");
  const [posicao, setPosicao] = useState<number | null>(null);

  const [erro, setErro] = useState("");
  const [sucesso, setSucesso] = useState("");
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    Promise.all([
      api.get<LivroResumo>(`/livros/${livroId}`),
      api.get<Biblioteca[]>("/bibliotecas"),
      api.get<{ posicao: number }>(`/reservas/posicao/${livroId}`),
    ])
      .then(([l, b, p]) => {
        setLivro(l);
        setBibliotecas(b);
        setPosicao(p.posicao);
        if (!bibliotecaId && b.length) setDestino(b[0].id);
      })
      .catch((e) => setErro(e.message));
  }, [livroId, bibliotecaId]);

  async function confirmar() {
    if (!destino) return;
    setEnviando(true);
    setErro("");
    setSucesso("");
    try {
      const r = await api.post<Reserva>("/reservas", {
        livroId,
        usuarioId: USUARIO_LOGADO,
        bibliotecaDestinoId: destino,
      });
      setSucesso(
        `Reserva confirmada para ${r.livro.titulo}, retirada em ${r.bibliotecaDestino.nome}. ` +
          `Você será avisado assim que um exemplar for devolvido.`,
      );
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  if (erro && !livro) return <Erro mensagem={erro} />;
  if (!livro) return <Carregando texto="Carregando título..." />;

  const nomeDestino = bibliotecas.find((b) => b.id === destino)?.nome ?? "—";

  return (
    <>
      <Trilha
        itens={[
          { rotulo: "Buscar Livros", onClick: () => navegar({ nome: "busca" }) },
          { rotulo: livro.titulo, onClick: () => navegar({ nome: "detalhesLivro", livroId }) },
          { rotulo: "Reservar" },
        ]}
      />
      <TituloPagina
        titulo="Reservar Livro"
        subtitulo="Fila de espera na rede Circula Book · UC03 - Fazer reserva de exemplar"
      />

      {erro && <Erro mensagem={erro} />}
      {sucesso && <Sucesso mensagem={sucesso} />}

      <DuasColunas
        esquerda={
          <>
            <SectionCard titulo="Livro selecionado">
              <div className="flex items-center gap-5">
                <CapaLivro tamanho="md" />
                <div className="min-w-0">
                  <h3 className="text-[18px] font-semibold text-[#2c3e50]">{livro.titulo}</h3>
                  <p className="text-[14px] text-[#66707d] mt-1">{livro.autor}</p>
                  <p className="text-[13px] text-[#66707d]">
                    {[livro.categoria, livro.anoPublicacao].filter(Boolean).join(" · ")}
                  </p>
                  <div className="flex flex-wrap items-center gap-3 mt-3">
                    <BadgeSituacao situacao={livro.situacao} />
                    <span className="text-[13px] text-[#66707d]">
                      {livro.disponiveis > 0
                        ? `${livro.disponiveis} exemplar(es) disponível(is) agora`
                        : `Todos emprestados · ${livro.naFilaDeEspera} ${livro.naFilaDeEspera === 1 ? "pessoa" : "pessoas"} na fila`}
                    </span>
                  </div>
                </div>
              </div>
            </SectionCard>

            <SectionCard titulo="1. Biblioteca de retirada">
              <Campo label="Escolha a biblioteca onde deseja retirar o exemplar">
                <Selecao
                  value={destino}
                  onChange={(e) => setDestino(Number(e.target.value))}
                >
                  {bibliotecas.map((b) => (
                    <option key={b.id} value={b.id}>{b.nome}</option>
                  ))}
                </Selecao>
              </Campo>
            </SectionCard>

            <SectionCard titulo="2. Observação">
              <p className="rounded-[8px] bg-[#f5f7fa] px-4 py-4 text-[14px] leading-relaxed text-[#66707d]">
                {livro.disponiveis > 0
                  ? "Há exemplar disponível na rede. Pela RN03, a reserva só vale quando todos estão emprestados — procure a biblioteca para retirada imediata."
                  : "Nenhum exemplar está disponível no momento em nenhuma biblioteca da rede. Ao confirmar, você entra automaticamente na fila de espera (RN03)."}
              </p>
            </SectionCard>
          </>
        }
        direita={
          <>
            <Callout tipo="info" titulo="Como funciona a fila de espera (RN03)">
              Você ocupará a posição {posicao ?? "—"} na fila. Quando um exemplar for
              devolvido, você terá 3 dias corridos para retirá-lo antes que a reserva
              expire.
            </Callout>

            <Callout tipo="aviso" titulo="Atenção ao prazo de retirada">
              Reservas não retiradas em até 3 dias após a disponibilização voltam
              automaticamente para a fila (RN03).
            </Callout>

            <CardResumo
              titulo="Resumo da reserva"
              rodape={
                <Botao
                  onClick={confirmar}
                  disabled={!destino || enviando || !!sucesso}
                  className="w-full"
                >
                  {enviando ? "Confirmando..." : "📌 Confirmar Reserva"}
                </Botao>
              }
            >
              <LinhaResumo rotulo="Livro" valor={livro.titulo} />
              <LinhaResumo rotulo="Biblioteca" valor={nomeDestino} />
              <LinhaResumo rotulo="Posição na fila" valor={`${posicao ?? "—"}º lugar`} />
              <LinhaResumo rotulo="Prazo p/ retirada" valor="3 dias corridos" />
            </CardResumo>
          </>
        }
      />
    </>
  );
}
