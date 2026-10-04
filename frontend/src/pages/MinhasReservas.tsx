/**
 * Minhas Reservas — acompanhamento da fila de espera do usuário (UC05 / UC07).
 *
 * Mostra, para cada reserva ativa:
 *  - a posição na fila DA biblioteca (reserva é por biblioteca);
 *  - onde o usuário entrou na fila e onde vai retirar;
 *  - o andamento do pedido de transferência, quando a retirada é em outra biblioteca;
 *  - a data limite de retirada, quando o exemplar já está liberado.
 */
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api, formatarData } from "../api/client";
import type { Reserva, SolicitacaoTransferencia } from "../types";
import {
  BadgeReserva,
  Botao,
  CapaLivro,
  Callout,
  CardResumo,
  Carregando,
  DuasColunas,
  Erro,
  LinhaResumo,
  SectionCard,
  Sucesso,
  TituloPagina,
  Trilha,
  Vazio,
} from "../components/ui";
import ModalConfirmacao, { ResumoModal } from "../components/ModalConfirmacao";
import TabelaPaginada, { type Coluna } from "../components/TabelaPaginada";
import { useUsuarioLogado } from "../auth/contexto";


const ATIVAS: Reserva["status"][] = [
  "PENDENTE",
  "AGUARDANDO_TRANSFERENCIA",
  "DISPONIVEL",
];

/** Mensagem principal do card, conforme o momento da reserva. */
function mensagemDe(
  r: Reserva,
  posicao: number,
  transf?: SolicitacaoTransferencia,
): { texto: string; tom: "neutro" | "azul" | "verde" } {
  const comTransf = r.bibliotecaFila.id !== r.bibliotecaDestino.id;

  if (r.status === "DISPONIVEL") {
    return {
      tom: "verde",
      texto: `Seu exemplar está pronto! Retire na ${r.bibliotecaDestino.nome} até ${formatarData(r.dataExpiracao)}.`,
    };
  }

  if (r.status === "AGUARDANDO_TRANSFERENCIA") {
    if (transf?.status === "EM_TRANSITO") {
      return {
        tom: "azul",
        texto: `Seu exemplar já está a caminho da ${r.bibliotecaDestino.nome}. Você será avisado quando ele chegar.`,
      };
    }
    return {
      tom: "azul",
      texto: `Um exemplar foi separado para você na ${r.bibliotecaFila.nome}, mas ainda aguarda a aprovação do administrador para seguir viagem.`,
    };
  }

  // PENDENTE
  if (!comTransf) {
    return {
      tom: "neutro",
      texto: `Você é o ${posicao}º da fila na ${r.bibliotecaFila.nome}. Avisaremos quando for a sua vez.`,
    };
  }
  if (transf?.status === "APROVADA") {
    return {
      tom: "neutro",
      texto: `Você é o ${posicao}º da fila na ${r.bibliotecaFila.nome}. O administrador já aprovou a retirada na ${r.bibliotecaDestino.nome}; o exemplar seguirá viagem assim que for devolvido.`,
    };
  }
  return {
    tom: "neutro",
    texto: `Você é o ${posicao}º da fila na ${r.bibliotecaFila.nome}. A retirada na ${r.bibliotecaDestino.nome} aguarda a aprovação do administrador.`,
  };
}

export default function MinhasReservas() {
  const { id: usuarioId } = useUsuarioLogado();
  const [todas, setTodas] = useState<Reserva[] | null>(null);
  const [transferencias, setTransferencias] = useState<
    SolicitacaoTransferencia[]
  >([]);
  const [erro, setErro] = useState("");
  const [sucesso, setSucesso] = useState("");

  const [alvo, setAlvo] = useState<Reserva | null>(null);
  const [cancelando, setCancelando] = useState(false);

  function carregar() {
    // /reservas/usuario traz só as minhas, já com a posição na fila;
    // /transferencias mostra em que pé está o pedido de cada reserva.
    Promise.all([
      api.get<Reserva[]>(`/reservas/usuario/${usuarioId}`),
      api.get<SolicitacaoTransferencia[]>("/transferencias"),
    ])
      .then(([reservas, transf]) => {
        setTodas(reservas);
        setTransferencias(transf);
      })
      .catch((e) => setErro(e.message));
  }
  useEffect(carregar, [usuarioId]);

  const minhas = useMemo(() => todas ?? [], [todas]);

  const ativas = useMemo(
    () =>
      minhas
        .filter((r) => ATIVAS.includes(r.status))
        .sort(
          (a, b) =>
            new Date(a.dataReserva).getTime() -
            new Date(b.dataReserva).getTime(),
        ),
    [minhas],
  );

  const historico = useMemo(
    () =>
      minhas
        .filter((r) => !ATIVAS.includes(r.status))
        .sort(
          (a, b) =>
            new Date(b.dataReserva).getTime() -
            new Date(a.dataReserva).getTime(),
        ),
    [minhas],
  );

  /** Posição na fila da biblioteca, calculada pelo servidor. */
  function posicaoDe(r: Reserva): number {
    return Math.max(1, r.posicaoFila ?? 1);
  }

  /** Pedido de transferência mais recente ligado à reserva. */
  function transferenciaDe(r: Reserva): SolicitacaoTransferencia | undefined {
    return transferencias
      .filter((t) => t.reserva?.id === r.id)
      .sort((a, b) => b.id - a.id)[0];
  }

  async function cancelar() {
    if (!alvo) return;
    setCancelando(true);
    setErro("");
    setSucesso("");
    try {
      await api.patch(`/reservas/${alvo.id}/cancelar`);
      setSucesso(`Reserva de "${alvo.livro.titulo}" cancelada.`);
      setAlvo(null);
      carregar();
    } catch (e) {
      setErro((e as Error).message);
      setAlvo(null);
    } finally {
      setCancelando(false);
    }
  }

  const colunasHistorico: Coluna<Reserva>[] = [
    {
      titulo: "Livro",
      render: (r) => (
        <div>
          <p className="font-semibold text-[#2c3e50]">{r.livro.titulo}</p>
          <p className="text-[12px] text-[#66707d]">{r.livro.autor}</p>
        </div>
      ),
    },
    { titulo: "Fila", render: (r) => r.bibliotecaFila.nome },
    { titulo: "Retirada", render: (r) => r.bibliotecaDestino.nome },
    { titulo: "Reservado em", render: (r) => formatarData(r.dataReserva) },
    { titulo: "Situação", render: (r) => <BadgeReserva status={r.status} /> },
  ];

  if (todas === null && !erro) {
    return <Carregando texto="Carregando suas reservas..." />;
  }

  const transfDoAlvo = alvo ? transferenciaDe(alvo) : undefined;

  return (
    <>
      <Trilha itens={[{ rotulo: "Minhas Reservas" }]} />
      <TituloPagina
        titulo="Minhas Reservas"
        subtitulo="Acompanhe sua posição nas filas de espera e a retirada dos livros reservados"
      />

      {erro && <Erro mensagem={erro} />}
      {sucesso && <Sucesso mensagem={sucesso} />}

      <DuasColunas
        esquerda={
          <>
            <SectionCard titulo={`1. Reservas em andamento (${ativas.length})`}>
              {ativas.length === 0 && (
                <Vazio texto="Você não tem reservas em andamento. Quando todos os exemplares de um livro estiverem emprestados numa biblioteca, você pode entrar na fila dela." />
              )}

              <div className="flex flex-col gap-3">
                {ativas.map((r) => {
                  const transf = transferenciaDe(r);
                  const msg = mensagemDe(r, posicaoDe(r), transf);
                  const caixa = {
                    neutro: "bg-white border-[#e0e0e0] text-[#2c3e50]",
                    azul: "bg-[#deedfc] border-[#1976d2] text-[#125ca8]",
                    verde: "bg-[#dbf0db] border-[#388e3c] text-[#2b6e2e]",
                  }[msg.tom];

                  return (
                    <div
                      key={r.id}
                      className="flex flex-wrap items-start gap-4 rounded-[10px] bg-[#f5f7fa] p-4"
                    >
                      <CapaLivro tamanho="sm" />

                      <div className="flex-1 min-w-[260px] flex flex-col gap-2">
                        <div className="flex flex-wrap items-center gap-3">
                          <Link
                            to={`/livro/${r.livro.id}`}
                            className="text-[15px] font-semibold text-[#2c3e50] hover:text-[#1976d2]"
                          >
                            {r.livro.titulo}
                          </Link>
                          <BadgeReserva status={r.status} />
                        </div>
                        <p className="text-[13px] text-[#66707d]">
                          {r.livro.autor}
                        </p>
                        <p className="text-[13px] text-[#125ca8]">
                          📍 Fila na {r.bibliotecaFila.nome}
                          {r.bibliotecaFila.id !== r.bibliotecaDestino.id &&
                            ` → retirada na ${r.bibliotecaDestino.nome}`}
                        </p>
                        {r.exemplar?.codigoBarras && (
                          <p className="text-[12px] text-[#66707d]">
                            Exemplar separado: {r.exemplar.codigoBarras}
                          </p>
                        )}
                        <p
                          className={`rounded-[8px] border px-3 py-2 text-[13px] leading-[1.45] ${caixa}`}
                        >
                          {msg.texto}
                        </p>
                        <p className="text-[12px] text-[#9aa3ad]">
                          Reservado em {formatarData(r.dataReserva)}
                        </p>
                      </div>

                      <div className="w-[150px]">
                        <Botao
                          variante="perigo"
                          className="w-full"
                          onClick={() => setAlvo(r)}
                        >
                          Cancelar reserva
                        </Botao>
                      </div>
                    </div>
                  );
                })}
              </div>
            </SectionCard>

            <SectionCard titulo="2. Histórico de reservas">
              <div className="rounded-[10px] border border-[#e0e0e0] overflow-hidden">
                <TabelaPaginada
                  dados={historico}
                  colunas={colunasHistorico}
                  chave={(r) => r.id}
                  porPagina={6}
                  textoVazio="Nenhuma reserva encerrada ainda."
                />
              </div>
            </SectionCard>
          </>
        }
        direita={
          <>
            <Callout tipo="info" titulo="Como funciona a fila">
              A fila é de cada biblioteca. Quando um exemplar é devolvido nela,
              o 1º da fila é avisado e tem 3 dias para retirar. Passado o prazo,
              a reserva expira e o próximo é chamado.
            </Callout>

            <Callout tipo="aviso" titulo="Retirada em outra biblioteca">
              Se você escolheu retirar em outra unidade, o administrador precisa
              aprovar o pedido. Se ele recusar, sua reserva continua na fila e a
              retirada passa a ser na biblioteca onde você a fez.
            </Callout>

            <CardResumo
              titulo="Resumo"
              rodape={
                <Botao
                  variante="secundario"
                  className="w-full"
                  onClick={carregar}
                >
                  🔄 Atualizar
                </Botao>
              }
            >
              <LinhaResumo
                rotulo="Na fila"
                valor={ativas.filter((r) => r.status === "PENDENTE").length}
                destaque="laranja"
              />
              <LinhaResumo
                rotulo="Aguardando transferência"
                valor={
                  ativas.filter((r) => r.status === "AGUARDANDO_TRANSFERENCIA")
                    .length
                }
                destaque="azul"
              />
              <LinhaResumo
                rotulo="Prontas para retirada"
                valor={ativas.filter((r) => r.status === "DISPONIVEL").length}
                destaque="verde"
              />
            </CardResumo>
          </>
        }
      />

      <ModalConfirmacao
        aberto={!!alvo}
        titulo="Cancelar esta reserva?"
        confirmarRotulo="Cancelar reserva"
        cancelarRotulo="Manter reserva"
        tom="perigo"
        carregando={cancelando}
        onConfirmar={cancelar}
        onCancelar={() => setAlvo(null)}
      >
        {alvo && (
          <>
            <ResumoModal
              linhas={[
                ["Livro", alvo.livro.titulo],
                ["Fila", alvo.bibliotecaFila.nome],
                ["Retirada", alvo.bibliotecaDestino.nome],
              ]}
            />
            <p>
              Você perderá seu lugar na fila.
              {alvo.status === "DISPONIVEL" &&
                " O exemplar separado será liberado para o próximo da fila."}
              {transfDoAlvo?.status === "EM_TRANSITO" &&
                " O exemplar já está em viagem e continuará até a biblioteca de destino, onde ficará disponível para todos."}
            </p>
          </>
        )}
      </ModalConfirmacao>
    </>
  );
}
