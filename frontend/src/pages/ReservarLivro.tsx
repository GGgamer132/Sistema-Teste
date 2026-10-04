/**
 * Reservar Livro (UC03).
 * O usuário entra na fila de uma biblioteca onde todos os exemplares estão emprestados
 * e escolhe onde quer retirar. Retirar em outra biblioteca dispara um pedido de transferência.
 * Os destinos possíveis (e o motivo de cada bloqueado) vêm do servidor.
 */
import { useEffect, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { api, qs } from "../api/client";
import type { Biblioteca, DestinoRetirada, LivroResumo, Reserva } from "../types";
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
  TituloPagina,
  Trilha,
  Vazio,
} from "../components/ui";
import ModalConfirmacao, { ResumoModal } from "../components/ModalConfirmacao";

type Retirada = "PROPRIA" | "OUTRA";

export default function ReservarLivro() {
  const { livroId: livroIdParam } = useParams();
  const livroId = Number(livroIdParam);
  const [searchParams] = useSearchParams();
  const bibliotecaParam = Number(searchParams.get("biblioteca")) || undefined;
  const navigate = useNavigate();

  const [livro, setLivro] = useState<LivroResumo | null>(null);
  const [bibliotecas, setBibliotecas] = useState<Biblioteca[]>([]);
  const [posicao, setPosicao] = useState<number | null>(null);
  /** Destinos para "retirar em outra biblioteca"; null enquanto carrega. */
  const [destinos, setDestinos] = useState<DestinoRetirada[] | null>(null);
  const [erroDestinos, setErroDestinos] = useState("");

  const [bibFila, setBibFila] = useState<number | "">("");
  const [retirada, setRetirada] = useState<Retirada>("PROPRIA");
  const [bibDestino, setBibDestino] = useState<number | "">("");

  const [modalAberto, setModalAberto] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState("");

  useEffect(() => {
    Promise.all([
      api.get<LivroResumo>(`/livros/${livroId}`),
      api.get<Biblioteca[]>("/bibliotecas"),
    ])
      .then(([l, b]) => {
        setLivro(l);
        setBibliotecas(b);

        const disponibilidade = l.disponibilidade ?? [];
        const fila = disponibilidade.filter(
          (x) => x.disponiveis === 0 && x.totalExemplares > 0,
        );
        setBibFila(
          fila.find((x) => x.bibliotecaId === bibliotecaParam)?.bibliotecaId ??
            fila[0]?.bibliotecaId ??
            "",
        );
      })
      .catch((e) => setErro(e.message));
  }, [livroId, bibliotecaParam]);

  useEffect(() => {
    if (bibFila === "") return;
    api
      .get<{ posicao: number }>(
        `/reservas/posicao/${livroId}?bibliotecaId=${bibFila}`,
      )
      .then((p) => setPosicao(p.posicao))
      .catch(() => setPosicao(null));
  }, [livroId, bibFila]);

  // Cada fila tem seus destinos: depende de quem já tem o título e de quantos
  // exemplares a biblioteca da fila pode ceder sem ficar sem o livro.
  useEffect(() => {
    if (bibFila === "") return;
    let ativo = true;
    api
      .get<DestinoRetirada[]>(
        "/reservas/destinos" + qs({ livroId, bibliotecaFilaId: bibFila }),
      )
      .then((d) => {
        if (!ativo) return;
        setDestinos(d);
        setErroDestinos("");
        setBibDestino(d.find((x) => x.permitido)?.bibliotecaId ?? "");
        if (!d.some((x) => x.permitido)) setRetirada("PROPRIA");
      })
      .catch((e) => {
        if (!ativo) return;
        setDestinos([]);
        setErroDestinos((e as Error).message);
        setBibDestino("");
        setRetirada("PROPRIA");
      });
    return () => {
      ativo = false;
    };
  }, [livroId, bibFila]);

  if (erro && !livro) return <Erro mensagem={erro} />;
  if (!livro) return <Carregando texto="Carregando título..." />;

  const disponibilidade = livro.disponibilidade ?? [];
  const paraFila = disponibilidade.filter(
    (d) => d.disponiveis === 0 && d.totalExemplares > 0,
  );
  const liberados = (destinos ?? []).filter((d) => d.permitido);
  /** Se todos os destinos estão bloqueados pelo mesmo motivo, ele explica a opção desabilitada. */
  const motivosBloqueio = [
    ...new Set((destinos ?? []).map((d) => d.motivo).filter(Boolean)),
  ];

  const nomeBib = (id: number | "") =>
    bibliotecas.find((b) => b.id === id)?.nome ??
    disponibilidade.find((d) => d.bibliotecaId === id)?.bibliotecaNome ??
    destinos?.find((d) => d.bibliotecaId === id)?.nome ??
    "—";

  const comTransferencia = retirada === "OUTRA";
  const idRetirada = comTransferencia ? bibDestino : bibFila;
  const podeConfirmar =
    bibFila !== "" && (!comTransferencia || bibDestino !== "");

  async function executar() {
    setEnviando(true);
    setErro("");
    try {
      const r = await api.post<Reserva>("/reservas", {
        livroId,
        bibliotecaFilaId: bibFila,
        bibliotecaDestinoId: idRetirada,
      });
      const lugar = r.posicaoFila ? ` Você é o ${r.posicaoFila}º da fila.` : "";
      navigate("/minhas-reservas", {
        state: {
          sucesso: comTransferencia
            ? `Reserva de "${livro?.titulo}" confirmada na fila da ${nomeBib(bibFila)}, com retirada na ${nomeBib(bibDestino)}.${lugar} ` +
              "O pedido de transferência foi enviado ao administrador."
            : `Reserva de "${livro?.titulo}" confirmada na ${nomeBib(bibFila)}.${lugar}`,
        },
      });
    } catch (e) {
      setErro((e as Error).message);
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
        subtitulo="Entre na fila de espera e escolha onde prefere retirar o exemplar"
      />

      {erro && <Erro mensagem={erro} />}

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

            <SectionCard titulo="1. Em qual biblioteca você quer entrar na fila?">
              {paraFila.length === 0 ? (
                <Vazio texto="Nenhuma biblioteca está com todos os exemplares emprestados. Como há exemplar livre, vá a uma unidade e faça o empréstimo presencialmente." />
              ) : (
                <Campo label="Bibliotecas com todos os exemplares emprestados">
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

            {paraFila.length > 0 && (
              <SectionCard titulo="2. Onde você quer retirar o livro?">
                <GrupoRadio<Retirada>
                  valor={retirada}
                  onChange={setRetirada}
                  opcoes={[
                    {
                      valor: "PROPRIA",
                      rotulo: `🏠 Na própria biblioteca (${nomeBib(bibFila)})`,
                    },
                    {
                      valor: "OUTRA",
                      rotulo: "🚚 Em outra biblioteca",
                      desabilitada: destinos === null || liberados.length === 0,
                    },
                  ]}
                />

                {destinos !== null && liberados.length === 0 && (
                  <div
                    className="mt-3 text-[13px] text-[#66707d]"
                    data-testid="outra-indisponivel"
                  >
                    <p>Retirar em outra biblioteca não está disponível para esta fila:</p>
                    {erroDestinos ? (
                      <p className="mt-1">{erroDestinos}</p>
                    ) : destinos.length === 0 ? (
                      <p className="mt-1">Não há outras bibliotecas ativas na rede.</p>
                    ) : (
                      <ul className="mt-1 list-disc pl-5">
                        {motivosBloqueio.map((m) => (
                          <li key={m}>{m}</li>
                        ))}
                      </ul>
                    )}
                  </div>
                )}

                {comTransferencia && destinos && (
                  <fieldset className="mt-5 flex flex-col gap-2">
                    <legend className="mb-2 text-[13px] font-medium text-[#2c3e50]">
                      Biblioteca de retirada
                    </legend>
                    {destinos.map((d) => (
                      <label
                        key={d.bibliotecaId}
                        className={`flex items-start gap-3 rounded-[8px] border px-4 py-3 text-[14px] ${
                          d.permitido
                            ? bibDestino === d.bibliotecaId
                              ? "border-[#1976d2] bg-[#e8f0fc] cursor-pointer"
                              : "border-[#e0e0e0] bg-white cursor-pointer"
                            : "border-[#e0e0e0] bg-[#f5f7fa] text-[#9aa3ad] cursor-not-allowed"
                        }`}
                      >
                        <input
                          type="radio"
                          name="destino"
                          className="mt-1"
                          value={d.bibliotecaId}
                          checked={bibDestino === d.bibliotecaId}
                          disabled={!d.permitido}
                          onChange={() => setBibDestino(d.bibliotecaId)}
                        />
                        <span>
                          <span className="font-medium">{d.nome}</span>
                          {!d.permitido && d.motivo && (
                            <span className="block text-[12px] mt-1">{d.motivo}</span>
                          )}
                        </span>
                      </label>
                    ))}
                  </fieldset>
                )}
              </SectionCard>
            )}
          </>
        }
        direita={
          <>
            {comTransferencia ? (
              <>
                <Callout
                  tipo="info"
                  titulo="Como funciona a retirada em outra biblioteca"
                >
                  1) Você entra na fila da {nomeBib(bibFila)}. 2) O
                  administrador avalia o pedido de transferência. 3) Quando um
                  exemplar for devolvido na sua vez, ele segue para a{" "}
                  {nomeBib(bibDestino)}. 4) Você é avisado quando ele chegar e
                  tem 3 dias para retirar.
                </Callout>
                <Callout tipo="aviso" titulo="Se o pedido for recusado">
                  Sua reserva continua na fila e a retirada passa a ser na{" "}
                  {nomeBib(bibFila)}. Você será avisado e pode cancelar se
                  preferir.
                </Callout>
              </>
            ) : (
              <>
                <Callout tipo="info" titulo="Como funciona a fila de espera">
                  Você será avisado quando um exemplar for devolvido e terá 3
                  dias corridos para retirá-lo. Depois disso a reserva expira e
                  passa para o próximo.
                </Callout>
                <Callout tipo="aviso" titulo="Quando posso reservar?">
                  Só é possível reservar em bibliotecas onde todos os exemplares
                  estão emprestados. Havendo exemplar livre, o empréstimo é
                  presencial.
                </Callout>
              </>
            )}

            <CardResumo
              titulo="Resumo da reserva"
              rodape={
                <Botao
                  onClick={() => setModalAberto(true)}
                  disabled={!podeConfirmar || enviando}
                  className="w-full"
                >
                  📌 Entrar na fila
                </Botao>
              }
            >
              <LinhaResumo rotulo="Livro" valor={livro.titulo} />
              <LinhaResumo
                rotulo="Fila na biblioteca"
                valor={nomeBib(bibFila)}
              />
              <LinhaResumo rotulo="Retirada em" valor={nomeBib(idRetirada)} />
              <LinhaResumo
                rotulo="Posição na fila"
                valor={posicao ? `${posicao}º lugar` : "—"}
              />
              <LinhaResumo
                rotulo="Transferência"
                valor={comTransferencia ? "Sim, depende de aprovação" : "Não"}
              />
              <LinhaResumo rotulo="Prazo p/ retirada" valor="3 dias corridos" />
            </CardResumo>
          </>
        }
      />

      <ModalConfirmacao
        aberto={modalAberto}
        titulo="Confirmar reserva?"
        confirmarRotulo="Entrar na fila"
        carregando={enviando}
        onConfirmar={executar}
        onCancelar={() => setModalAberto(false)}
      >
        <p>
          Você entrará na <strong>posição {posicao ?? "—"}</strong> da fila da{" "}
          <strong>{nomeBib(bibFila)}</strong>.
        </p>
        <ResumoModal
          linhas={[
            ["Livro", livro.titulo],
            ["Fila em", nomeBib(bibFila)],
            ["Retirada em", nomeBib(idRetirada)],
          ]}
        />
        {comTransferencia && (
          <p className="rounded-[8px] bg-[#ffecd0] px-3 py-2 text-[13px] text-[#c76400]">
            ⚠️ Retirar em outra biblioteca exige a aprovação do administrador.
            Se for recusado, a retirada volta a ser na {nomeBib(bibFila)}.
          </p>
        )}
      </ModalConfirmacao>
    </>
  );
}
