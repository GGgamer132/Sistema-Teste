/**
 * Reservar Livro (UC03).
 * O usuário entra na fila de uma biblioteca onde todos os exemplares estão emprestados
 * e escolhe onde quer retirar. Retirar em outra biblioteca dispara um pedido de transferência.
 */
import { useEffect, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import type { Biblioteca, LivroResumo, Reserva } from "../types";
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

  const [bibFila, setBibFila] = useState<number | "">("");
  const [retirada, setRetirada] = useState<Retirada>("PROPRIA");
  const [bibDestino, setBibDestino] = useState<number | "">("");

  const [modalAberto, setModalAberto] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState("");
  const [sucesso, setSucesso] = useState("");

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
        setBibDestino(
          b.find((x) => x.ativa !== false && !comTitulo.has(x.id))?.id ?? "",
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

  if (erro && !livro) return <Erro mensagem={erro} />;
  if (!livro) return <Carregando texto="Carregando título..." />;

  const disponibilidade = livro.disponibilidade ?? [];
  const paraFila = disponibilidade.filter(
    (d) => d.disponiveis === 0 && d.totalExemplares > 0,
  );
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

  const comTransferencia = retirada === "OUTRA";
  const idRetirada = comTransferencia ? bibDestino : bibFila;
  const podeConfirmar =
    !sucesso && bibFila !== "" && (!comTransferencia || bibDestino !== "");

  async function executar() {
    setEnviando(true);
    setErro("");
    try {
      await api.post<Reserva>("/reservas", {
        livroId,
        usuarioId: USUARIO_LOGADO,
        bibliotecaFilaId: bibFila,
        bibliotecaDestinoId: idRetirada,
      });
      setSucesso(
        comTransferencia
          ? `Reserva confirmada na fila da ${nomeBib(bibFila)}, com retirada na ${nomeBib(bibDestino)}. ` +
              "O pedido de transferência foi enviado ao administrador e você será avisado do andamento."
          : `Reserva confirmada na ${nomeBib(bibFila)}. ` +
              "Você será avisado assim que um exemplar for devolvido.",
      );
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
        subtitulo="Entre na fila de espera e escolha onde prefere retirar o exemplar"
      />

      {erro && <Erro mensagem={erro} />}
      {sucesso && (
        <>
          <Sucesso mensagem={sucesso} />
          <div>
            <Botao
              variante="secundario"
              onClick={() => navigate(`/livro/${livroId}`)}
            >
              ← Voltar para o livro
            </Botao>
          </div>
        </>
      )}

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
                      desabilitada: destinosPossiveis.length === 0,
                    },
                  ]}
                />

                {destinosPossiveis.length === 0 && (
                  <p className="mt-3 text-[13px] text-[#66707d]">
                    Retirar em outra biblioteca não está disponível: todas as
                    bibliotecas da rede já possuem este título. Nesse caso,
                    procure a biblioteca que tem exemplar livre ou aguarde na
                    fila.
                  </p>
                )}

                {comTransferencia && (
                  <div className="mt-5">
                    <Campo label="Biblioteca de retirada (só aparecem as que não têm nenhum exemplar do título)">
                      <Selecao
                        value={bibDestino}
                        onChange={(e) => setBibDestino(Number(e.target.value))}
                      >
                        {destinosPossiveis.map((b) => (
                          <option key={b.id} value={b.id}>
                            {b.nome}
                            {b.endereco ? ` — ${b.endereco}` : ""}
                          </option>
                        ))}
                      </Selecao>
                    </Campo>
                  </div>
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
