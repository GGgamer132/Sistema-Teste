/**
 * TELA 5 — Reservar Livro (UC03 / RN03).
 * Só faz sentido quando não há exemplar livre; o backend recusa o contrário.
 */
import { useEffect, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import type { Biblioteca, Exemplar, LivroResumo, Reserva } from "../types";
import {
  BadgeSituacao,
  Botao,
  Campo,
  CapaLivro,
  Callout,
  CardResumo,
  Carregando,
  DuasColunas,
  Erro,
  GrupoRadio,
  LinhaResumo,
  SectionCard,
  Selecao,
  Sucesso,
  TituloPagina,
  Trilha,
  Vazio,
} from "../components/ui";
import ModalConfirmacao, { ResumoModal } from "../components/ModalConfirmacao";

const USUARIO_LOGADO = 6;

type Modo = "FILA" | "TRANSFERENCIA";

export default function ReservarLivro() {
  const { livroId: livroIdParam } = useParams();
  const livroId = Number(livroIdParam);
  const [searchParams] = useSearchParams();
  const bibliotecaParam = Number(searchParams.get("biblioteca")) || undefined;

  const [livro, setLivro] = useState<LivroResumo | null>(null);
  const [bibliotecas, setBibliotecas] = useState<Biblioteca[]>([]);
  const [exemplares, setExemplares] = useState<Exemplar[]>([]);
  const [posicao, setPosicao] = useState<number | null>(null);

  const [modo, setModo] = useState<Modo>("FILA");
  const [bibFila, setBibFila] = useState<number | "">("");
  const [bibOrigem, setBibOrigem] = useState<number | "">("");
  const [bibDestino, setBibDestino] = useState<number | "">("");

  const [modalAberto, setModalAberto] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState("");
  const [sucesso, setSucesso] = useState("");

  useEffect(() => {
    Promise.all([
      api.get<LivroResumo>(`/livros/${livroId}`),
      api.get<Biblioteca[]>("/bibliotecas"),
      api.get<Exemplar[]>("/exemplares"),
      api.get<{ posicao: number }>(`/reservas/posicao/${livroId}`),
    ])
      .then(([l, b, ex, p]) => {
        setLivro(l);
        setBibliotecas(b);
        setExemplares(ex);
        setPosicao(p.posicao);

        const disponibilidade = l.disponibilidade ?? [];
        const fila = disponibilidade.filter(
          (x) => x.disponiveis === 0 && x.totalExemplares > 0,
        );
        const livres = disponibilidade
          .filter((x) => x.disponiveis > 0)
          .sort((x, y) => y.disponiveis - x.disponiveis);
        const comTitulo = new Set(
          disponibilidade
            .filter((x) => x.totalExemplares > 0)
            .map((x) => x.bibliotecaId),
        );

        setBibFila(
          fila.find((x) => x.bibliotecaId === bibliotecaParam)?.bibliotecaId ??
            fila[0]?.bibliotecaId ??
            "",
        );
        setBibOrigem(livres[0]?.bibliotecaId ?? "");
        setBibDestino(
          b.find((x) => x.ativa !== false && !comTitulo.has(x.id))?.id ?? "",
        );
        setModo(fila.length > 0 ? "FILA" : "TRANSFERENCIA");
      })
      .catch((e) => setErro(e.message));
  }, [livroId, bibliotecaParam]);

  if (erro && !livro) return <Erro mensagem={erro} />;
  if (!livro) return <Carregando texto="Carregando título..." />;

  const disponibilidade = livro.disponibilidade ?? [];
  const paraFila = disponibilidade.filter(
    (d) => d.disponiveis === 0 && d.totalExemplares > 0,
  );
  const comExemplarLivre = disponibilidade.filter((d) => d.disponiveis > 0);
  const comTitulo = new Set(
    disponibilidade
      .filter((d) => d.totalExemplares > 0)
      .map((d) => d.bibliotecaId),
  );
  const destinosPossiveis = bibliotecas.filter(
    (b) => b.ativa !== false && !comTitulo.has(b.id),
  );

  const nomeBib = (id: number | "") =>
    bibliotecas.find((b) => b.id === id)?.nome ??
    disponibilidade.find((d) => d.bibliotecaId === id)?.bibliotecaNome ??
    "—";

  const podeConfirmar =
    !sucesso &&
    (modo === "FILA" ? bibFila !== "" : bibOrigem !== "" && bibDestino !== "");

  async function executar() {
    setEnviando(true);
    setErro("");
    try {
      if (modo === "FILA") {
        const reserva = await api.post<Reserva>("/reservas", {
          livroId,
          usuarioId: USUARIO_LOGADO,
          bibliotecaDestinoId: bibFila,
        });
        setSucesso(
          `Reserva confirmada para ${reserva.livro.titulo} na ${reserva.bibliotecaDestino.nome}. ` +
            "Você será avisado assim que um exemplar for devolvido.",
        );
      } else {
        const exemplar = exemplares.find(
          (e) =>
            e.livro.id === livroId &&
            e.biblioteca.id === bibOrigem &&
            e.status === "DISPONIVEL",
        );
        if (!exemplar) {
          throw new Error(
            "Nenhum exemplar disponível na biblioteca de origem agora. Atualize a página.",
          );
        }
        await api.post("/transferencias", {
          exemplarId: exemplar.id,
          bibliotecaDestinoId: bibDestino,
          solicitanteId: USUARIO_LOGADO,
        });
        setSucesso(
          `Solicitação enviada! A transferência de ${nomeBib(bibOrigem)} para ${nomeBib(bibDestino)} ` +
            "aguarda a aprovação do administrador da rede.",
        );
      }
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setEnviando(false);
      setModalAberto(false);
    }
  }

  return (
    <>
      <Trilha
        itens={[
          { rotulo: "Buscar Livros", to: "/" },
          { rotulo: livro.titulo, to: `/livro/${livroId}` },
          { rotulo: "Reservar" },
        ]}
      />
      <TituloPagina
        titulo="Reservar Livro"
        subtitulo="Entre na fila de espera ou peça a transferência de um exemplar para a biblioteca mais perto de você"
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
                  <h3 className="text-[18px] font-semibold text-[#2c3e50]">
                    {livro.titulo}
                  </h3>
                  <p className="text-[14px] text-[#66707d] mt-1">
                    {livro.autor}
                  </p>
                  <p className="text-[13px] text-[#66707d]">
                    {[livro.categoria, livro.anoPublicacao]
                      .filter(Boolean)
                      .join(" · ")}
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

            <SectionCard titulo="1. O que você deseja fazer?">
              <GrupoRadio<Modo>
                valor={modo}
                onChange={setModo}
                opcoes={[
                  {
                    valor: "FILA",
                    rotulo: "🏠 Reservar na biblioteca (fila de espera)",
                  },
                  {
                    valor: "TRANSFERENCIA",
                    rotulo: "🚚 Solicitar transferência",
                  },
                ]}
              />
            </SectionCard>

            {modo === "FILA" ? (
              <SectionCard titulo="2. Biblioteca da reserva">
                {paraFila.length === 0 ? (
                  <Vazio texto="Não há biblioteca com todos os exemplares emprestados. Como há exemplar livre, procure uma unidade para o empréstimo presencial ou solicite a transferência." />
                ) : (
                  <Campo label="Bibliotecas onde todos os exemplares estão emprestados">
                    <Selecao
                      value={bibFila}
                      onChange={(e) => setBibFila(Number(e.target.value))}
                    >
                      {paraFila.map((d) => (
                        <option key={d.bibliotecaId} value={d.bibliotecaId}>
                          {d.bibliotecaNome} — 0 de {d.totalExemplares}{" "}
                          disponíveis
                        </option>
                      ))}
                    </Selecao>
                  </Campo>
                )}
              </SectionCard>
            ) : (
              <SectionCard titulo="2. Origem e destino da transferência">
                {comExemplarLivre.length === 0 ? (
                  <Vazio texto="Nenhuma biblioteca tem exemplar disponível para transferir agora. Use a reserva para entrar na fila." />
                ) : (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                    <Campo label="Biblioteca de origem (tem exemplar livre)">
                      <Selecao
                        value={bibOrigem}
                        onChange={(e) => setBibOrigem(Number(e.target.value))}
                      >
                        {comExemplarLivre.map((d) => (
                          <option key={d.bibliotecaId} value={d.bibliotecaId}>
                            {d.bibliotecaNome} — {d.disponiveis} disponível(is)
                          </option>
                        ))}
                      </Selecao>
                    </Campo>

                    <Campo label="Biblioteca de destino (sem nenhum exemplar)">
                      {destinosPossiveis.length === 0 ? (
                        <p className="text-[13px] text-[#66707d] pt-3">
                          Todas as bibliotecas da rede já possuem este título.
                        </p>
                      ) : (
                        <Selecao
                          value={bibDestino}
                          onChange={(e) =>
                            setBibDestino(Number(e.target.value))
                          }
                        >
                          {destinosPossiveis.map((b) => (
                            <option key={b.id} value={b.id}>
                              {b.nome}
                            </option>
                          ))}
                        </Selecao>
                      )}
                    </Campo>
                  </div>
                )}
              </SectionCard>
            )}
          </>
        }
        direita={
          <>
            {modo === "FILA" ? (
              <>
                <Callout tipo="info" titulo="Como funciona a fila de espera">
                  Você será avisado quando um exemplar for devolvido e terá 3
                  dias corridos para retirá-lo. Depois disso a reserva expira e
                  passa para o próximo da fila.
                </Callout>
                <Callout tipo="aviso" titulo="Quando posso reservar?">
                  Só é possível reservar em bibliotecas onde todos os exemplares
                  estão emprestados. Havendo exemplar livre, o empréstimo é
                  presencial.
                </Callout>
              </>
            ) : (
              <>
                <Callout tipo="info" titulo="Aprovação do administrador">
                  A solicitação fica <strong>pendente</strong> até o
                  administrador da rede aprovar. Você pode acompanhar o
                  andamento em “Minhas Reservas”.
                </Callout>
                <Callout tipo="aviso" titulo="Só para bibliotecas sem o título">
                  O destino precisa não ter nenhum exemplar deste livro, para a
                  transferência realmente ampliar o acesso.
                </Callout>
              </>
            )}

            <CardResumo
              titulo={
                modo === "FILA"
                  ? "Resumo da reserva"
                  : "Resumo da transferência"
              }
              rodape={
                <Botao
                  onClick={() => setModalAberto(true)}
                  disabled={!podeConfirmar || enviando}
                  className="w-full"
                >
                  {modo === "FILA"
                    ? "📌 Reservar"
                    : "🚚 Solicitar transferência"}
                </Botao>
              }
            >
              <LinhaResumo rotulo="Livro" valor={livro.titulo} />
              {modo === "FILA" ? (
                <>
                  <LinhaResumo rotulo="Biblioteca" valor={nomeBib(bibFila)} />
                  <LinhaResumo
                    rotulo="Posição na fila"
                    valor={`${posicao ?? "—"}º lugar`}
                  />
                  <LinhaResumo
                    rotulo="Prazo p/ retirada"
                    valor="3 dias corridos"
                  />
                </>
              ) : (
                <>
                  <LinhaResumo rotulo="Origem" valor={nomeBib(bibOrigem)} />
                  <LinhaResumo rotulo="Destino" valor={nomeBib(bibDestino)} />
                  <LinhaResumo
                    rotulo="Situação inicial"
                    valor="Pendente de aprovação"
                  />
                </>
              )}
            </CardResumo>
          </>
        }
      />

      <ModalConfirmacao
        aberto={modalAberto}
        titulo={
          modo === "FILA"
            ? "Confirmar reserva?"
            : "Confirmar solicitação de transferência?"
        }
        confirmarRotulo={
          modo === "FILA" ? "Confirmar reserva" : "Enviar solicitação"
        }
        carregando={enviando}
        onConfirmar={executar}
        onCancelar={() => setModalAberto(false)}
      >
        {modo === "FILA" ? (
          <>
            <p>
              Você entrará na <strong>posição {posicao ?? "—"}</strong> da fila
              de espera.
            </p>
            <ResumoModal
              linhas={[
                ["Livro", livro.titulo],
                ["Biblioteca", nomeBib(bibFila)],
              ]}
            />
          </>
        ) : (
          <>
            <p>
              Será criada uma solicitação que o administrador da rede precisa
              aprovar antes do envio do exemplar.
            </p>
            <ResumoModal
              linhas={[
                ["Livro", livro.titulo],
                ["De", nomeBib(bibOrigem)],
                ["Para", nomeBib(bibDestino)],
              ]}
            />
          </>
        )}
      </ModalConfirmacao>
    </>
  );
}
