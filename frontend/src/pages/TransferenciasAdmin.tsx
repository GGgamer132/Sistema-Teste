/**
 * Solicitações de Transferência — painel do Admin (UC13 / UC24).
 *
 * Fluxo:
 *  - Pedido nasce de uma reserva com retirada em outra biblioteca (PENDENTE, sem exemplar);
 *  - Admin aprova/rejeita o PEDIDO. Aprovado sem exemplar = "aguardando exemplar";
 *  - Quando um exemplar é devolvido na origem, o back o vincula e a viagem começa (EM_TRANSITO);
 *  - Chegada confirmada => CONCLUIDA.
 *  - O Admin também pode criar uma transferência AVULSA (exemplar disponível -> qualquer biblioteca).
 */
import { useEffect, useMemo, useState } from "react";
import { api, formatarData, qs } from "../api/client";
import type {
  Biblioteca,
  Exemplar,
  ResumoTransferencias,
  SolicitacaoTransferencia,
} from "../types";
import {
  AreaTexto,
  Badge,
  BadgeTransferencia,
  Botao,
  Callout,
  CapaLivro,
  Campo,
  CardResumo,
  Carregando,
  DuasColunas,
  Entrada,
  Erro,
  LinhaResumo,
  SectionCard,
  Selecao,
  Sucesso,
  TituloPagina,
  Trilha,
  Vazio,
} from "../components/ui";
import ModalConfirmacao, { ResumoModal } from "../components/ModalConfirmacao";
import TabelaPaginada, { type Coluna } from "../components/TabelaPaginada";

// Sem login, o admin é fixo: Roberto Dias (id 1).

type Decisao = { s: SolicitacaoTransferencia; acao: "aprovar" | "rejeitar" };

/** Texto "EX-001" ou "Aguardando exemplar". */
function rotuloExemplar(s: SolicitacaoTransferencia): string {
  return s.exemplar
    ? (s.exemplar.codigoBarras ?? `#${s.exemplar.id}`)
    : "Aguardando exemplar";
}

/** Quem pediu, e por qual caminho (reserva do usuário ou avulsa do Admin). */
function descreverOrigemPedido(s: SolicitacaoTransferencia): string {
  if (s.reserva) {
    return `Reserva de ${s.solicitante.nome} (usuário da comunidade)`;
  }
  return `Transferência avulsa de ${s.solicitante.nome} (administrador)`;
}

export default function TransferenciasAdmin() {
  const [pendentes, setPendentes] = useState<SolicitacaoTransferencia[]>([]);
  const [historico, setHistorico] = useState<SolicitacaoTransferencia[]>([]);
  const [resumo, setResumo] = useState<ResumoTransferencias | null>(null);

  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState("");
  const [sucesso, setSucesso] = useState("");
  const [processando, setProcessando] = useState(false);

  const [decisao, setDecisao] = useState<Decisao | null>(null);
  const [motivo, setMotivo] = useState("");

  // ── Transferência avulsa ──
  const [avulsaAberta, setAvulsaAberta] = useState(false);
  const [exemplares, setExemplares] = useState<Exemplar[] | null>(null);
  const [bibliotecas, setBibliotecas] = useState<Biblioteca[]>([]);
  const [busca, setBusca] = useState("");
  const [exemplarSel, setExemplarSel] = useState<Exemplar | null>(null);
  const [destinoId, setDestinoId] = useState("");
  const [obs, setObs] = useState("");
  const [confirmandoAvulsa, setConfirmandoAvulsa] = useState(false);

  function carregar() {
    setCarregando(true);
    Promise.all([
      api.get<SolicitacaoTransferencia[]>("/transferencias/pendentes"),
      api.get<SolicitacaoTransferencia[]>("/transferencias/historico"),
      api.get<ResumoTransferencias>("/transferencias/resumo"),
    ])
      .then(([p, h, r]) => {
        setPendentes(p);
        setHistorico(h);
        setResumo(r);
      })
      .catch((e) => setErro(e.message))
      .finally(() => setCarregando(false));
  }
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(carregar, []);

  function carregarDadosAvulsa() {
    Promise.all([
      api.get<Exemplar[]>("/exemplares"),
      api.get<Biblioteca[]>("/bibliotecas"),
    ])
      .then(([ex, bib]) => {
        setExemplares(ex.filter((e) => e.status === "DISPONIVEL"));
        setBibliotecas(bib.filter((b) => b.ativa !== false));
      })
      .catch((e) => setErro(e.message));
  }

  /** Carrega exemplares/bibliotecas só quando o Admin abre o formulário avulso. */
  function abrirAvulsa() {
    setAvulsaAberta(true);
    carregarDadosAvulsa();
  }

  function fecharAvulsa() {
    setAvulsaAberta(false);
    setExemplarSel(null);
    setDestinoId("");
    setObs("");
    setBusca("");
  }

  const exemplaresFiltrados = useMemo(() => {
    const t = busca.trim().toLowerCase();
    return (exemplares ?? []).filter(
      (e) =>
        !t ||
        e.livro.titulo.toLowerCase().includes(t) ||
        e.livro.autor.toLowerCase().includes(t) ||
        (e.codigoBarras ?? "").toLowerCase().includes(t) ||
        e.biblioteca.nome.toLowerCase().includes(t),
    );
  }, [exemplares, busca]);

  const destinosPossiveis = bibliotecas.filter(
    (b) => b.id !== exemplarSel?.biblioteca.id,
  );
  const destinoSel = bibliotecas.find((b) => String(b.id) === destinoId);

  // ── Ações ──

  async function decidir() {
    if (!decisao) return;
    const { s, acao } = decisao;
    setProcessando(true);
    setErro("");
    setSucesso("");
    try {
      const params =
        acao === "rejeitar"
          ? qs({ motivo })
          : "";
      await api.patch(`/transferencias/${s.id}/${acao}${params}`);

      if (acao === "rejeitar") {
        setSucesso(
          s.reserva
            ? `Pedido #${s.id} rejeitado. A reserva de ${s.solicitante.nome} continua na fila, com retirada na ${s.bibliotecaOrigem.nome}.`
            : `Pedido #${s.id} rejeitado.`,
        );
      } else if (s.exemplar) {
        setSucesso(
          `Pedido #${s.id} aprovado. Como o exemplar já estava separado, ele saiu em trânsito.`,
        );
      } else {
        setSucesso(
          `Pedido #${s.id} aprovado. Ele aguarda a devolução de um exemplar na ${s.bibliotecaOrigem.nome}; a viagem começa automaticamente.`,
        );
      }
      setDecisao(null);
      setMotivo("");
      carregar();
    } catch (e) {
      setErro((e as Error).message);
      setDecisao(null);
    } finally {
      setProcessando(false);
    }
  }

  async function criarAvulsa() {
    if (!exemplarSel || !destinoId) return;
    setProcessando(true);
    setErro("");
    setSucesso("");
    try {
      await api.post("/transferencias/avulsa", {
        exemplarId: exemplarSel.id,
        bibliotecaDestinoId: Number(destinoId),
        observacoes: obs.trim() || undefined,
      });
      setSucesso(
        `Transferência avulsa criada: "${exemplarSel.livro.titulo}" (${exemplarSel.codigoBarras ?? `#${exemplarSel.id}`}) saiu da ${exemplarSel.biblioteca.nome} rumo à ${destinoSel?.nome}.`,
      );
      setConfirmandoAvulsa(false);
      fecharAvulsa();
      carregar();
    } catch (e) {
      setErro((e as Error).message);
      setConfirmandoAvulsa(false);
      carregarDadosAvulsa(); // o exemplar pode ter mudado de status
    } finally {
      setProcessando(false);
    }
  }

  // ── Colunas da tabela de exemplares (avulsa) ──
  const colunasExemplar: Coluna<Exemplar>[] = [
    {
      titulo: "Livro",
      render: (e) => (
        <div>
          <p className="font-semibold text-[#2c3e50]">{e.livro.titulo}</p>
          <p className="text-[12px] text-[#66707d]">{e.livro.autor}</p>
        </div>
      ),
    },
    { titulo: "Código", render: (e) => e.codigoBarras ?? `#${e.id}` },
    { titulo: "Biblioteca atual", render: (e) => e.biblioteca.nome },
    {
      titulo: "Ação",
      className: "text-right",
      render: (e) =>
        exemplarSel?.id === e.id ? (
          <Badge tom="azul">Selecionado</Badge>
        ) : (
          <Botao
            variante="secundario"
            className="!px-3 !py-[6px] !text-[13px]"
            onClick={() => {
              setExemplarSel(e);
              setDestinoId("");
            }}
          >
            Selecionar
          </Botao>
        ),
    },
  ];

  // ── Colunas do histórico ──
  const colunasHistorico: Coluna<SolicitacaoTransferencia>[] = [
    {
      titulo: "Livro",
      render: (s) => (
        <div>
          <p className="font-semibold text-[#2c3e50]">{s.livro.titulo}</p>
          <p className="text-[12px] text-[#66707d]">{s.livro.autor}</p>
        </div>
      ),
    },
    { titulo: "Exemplar", render: (s) => rotuloExemplar(s) },
    {
      titulo: "Rota",
      render: (s) => (
        <span className="whitespace-nowrap">
          {s.bibliotecaOrigem.nome} → {s.bibliotecaDestino.nome}
        </span>
      ),
    },
    {
      titulo: "Origem do pedido",
      render: (s) => (
        <span className="text-[13px] text-[#66707d]">
          {s.reserva ? `Reserva · ${s.solicitante.nome}` : "Avulsa · Admin"}
        </span>
      ),
    },
    {
      titulo: "Status",
      render: (s) => (
        <BadgeTransferencia status={s.status} semExemplar={!s.exemplar} />
      ),
    },
    {
      titulo: "Data",
      render: (s) => formatarData(s.dataConclusao ?? s.dataSolicitacao),
    },
    {
      titulo: "Ação",
      className: "text-right",
      render: (s) =>
        // A chegada é confirmada pelo bibliotecário da biblioteca de destino
        s.status === "EM_TRANSITO" ? (
          <span className="text-[13px] text-[#66707d]">
            Aguardando confirmação no destino
          </span>
        ) : (
          <span className="text-[#9aa3ad]">—</span>
        ),
    },
  ];

  if (carregando && !resumo) {
    return <Carregando texto="Carregando solicitações..." />;
  }

  return (
    <>
      <Trilha
        itens={[
          { rotulo: "Dashboard", to: "/admin" },
          { rotulo: "Solicitações de Transferência" },
        ]}
        voltarPara="/admin/transferencias"
      />
      <TituloPagina
        titulo="Solicitações de Transferência"
        subtitulo="Administração da Rede · Aprovar pedidos, criar transferências avulsas e acompanhar o trânsito dos exemplares"
      />

      {erro && <Erro mensagem={erro} />}
      {sucesso && <Sucesso mensagem={sucesso} />}

      <DuasColunas
        esquerda={
          <>
            {/* ───────── 1. Pendentes ───────── */}
            <SectionCard
              titulo={`1. Pedidos aguardando decisão (${pendentes.length})`}
            >
              {pendentes.length === 0 && (
                <Vazio texto="Nenhum pedido aguardando decisão. Eles aparecem aqui quando um usuário entra na fila escolhendo retirar em outra biblioteca." />
              )}

              <div className="flex flex-col gap-3">
                {pendentes.map((s) => (
                  <div
                    key={s.id}
                    className="flex flex-wrap items-center gap-4 rounded-[10px] bg-[#f5f7fa] p-4"
                  >
                    <CapaLivro tamanho="sm" />

                    <div className="flex-1 min-w-[240px]">
                      <p className="text-[15px] font-semibold text-[#2c3e50]">
                        {s.livro.titulo} — {s.livro.autor}
                      </p>
                      <p className="text-[13px] text-[#66707d] mt-1">
                        {descreverOrigemPedido(s)}
                      </p>
                      <p className="text-[13px] text-[#125ca8] mt-1">
                        🔁 {s.bibliotecaOrigem.nome} →{" "}
                        {s.bibliotecaDestino.nome}
                      </p>
                      <p className="text-[13px] text-[#66707d] mt-1">
                        📦 {rotuloExemplar(s)}
                      </p>
                      <div className="mt-2">
                        <BadgeTransferencia
                          status={s.status}
                          semExemplar={!s.exemplar}
                        />
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
                        onClick={() => setDecisao({ s, acao: "aprovar" })}
                      >
                        ✅ Aprovar
                      </Botao>
                      <Botao
                        variante="perigo"
                        onClick={() => {
                          setMotivo("");
                          setDecisao({ s, acao: "rejeitar" });
                        }}
                      >
                        ❌ Rejeitar
                      </Botao>
                    </div>
                  </div>
                ))}
              </div>
            </SectionCard>

            {/* ───────── 2. Avulsa ───────── */}
            <SectionCard
              titulo="2. Transferência avulsa"
              acao={
                avulsaAberta ? (
                  <Botao
                    variante="secundario"
                    className="!px-3 !py-[6px] !text-[13px]"
                    onClick={fecharAvulsa}
                  >
                    Fechar
                  </Botao>
                ) : (
                  <Botao
                    variante="primario"
                    className="!px-3 !py-[6px] !text-[13px]"
                    onClick={abrirAvulsa}
                  >
                    ➕ Nova transferência avulsa
                  </Botao>
                )
              }
            >
              {!avulsaAberta && (
                <p className="text-[14px] text-[#66707d]">
                  Mova um exemplar disponível para qualquer biblioteca da rede,
                  sem depender de uma reserva. Ela já nasce aprovada e o
                  exemplar sai em trânsito imediatamente.
                </p>
              )}

              {avulsaAberta && (
                <div className="flex flex-col gap-5">
                  <div className="flex flex-col gap-3">
                    <Campo label="Passo 1 · Escolha o exemplar (somente disponíveis)">
                      <Entrada
                        placeholder="Buscar por título, autor, código ou biblioteca..."
                        value={busca}
                        onChange={(e) => setBusca(e.target.value)}
                      />
                    </Campo>

                    {exemplares === null ? (
                      <Carregando texto="Carregando exemplares..." />
                    ) : (
                      <div className="rounded-[10px] border border-[#e0e0e0] overflow-hidden">
                        <TabelaPaginada
                          key={busca}
                          dados={exemplaresFiltrados}
                          colunas={colunasExemplar}
                          chave={(e) => e.id}
                          porPagina={5}
                          textoVazio="Nenhum exemplar disponível encontrado."
                        />
                      </div>
                    )}
                  </div>

                  {exemplarSel && (
                    <div className="flex flex-col gap-4 rounded-[10px] bg-[#f5f7fa] p-4">
                      <p className="text-[13px] text-[#66707d]">
                        Selecionado:{" "}
                        <strong className="text-[#2c3e50]">
                          {exemplarSel.livro.titulo}
                        </strong>{" "}
                        ({exemplarSel.codigoBarras ?? `#${exemplarSel.id}`}) ·
                        hoje na {exemplarSel.biblioteca.nome}
                      </p>

                      <Campo label="Passo 2 · Biblioteca de destino">
                        <Selecao
                          value={destinoId}
                          onChange={(e) => setDestinoId(e.target.value)}
                        >
                          <option value="">Selecione a biblioteca...</option>
                          {destinosPossiveis.map((b) => (
                            <option key={b.id} value={b.id}>
                              {b.nome}
                            </option>
                          ))}
                        </Selecao>
                      </Campo>

                      <Campo label="Observações (opcional)">
                        <AreaTexto
                          placeholder="Ex.: reforço do acervo para o clube de leitura."
                          value={obs}
                          onChange={(e) => setObs(e.target.value)}
                        />
                      </Campo>

                      <div className="flex justify-end">
                        <Botao
                          variante="primario"
                          disabled={!destinoId}
                          onClick={() => setConfirmandoAvulsa(true)}
                        >
                          🚚 Criar transferência
                        </Botao>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </SectionCard>

            {/* ───────── 3. Histórico ───────── */}
            <SectionCard titulo="3. Acompanhamento e histórico">
              <div className="rounded-[10px] border border-[#e0e0e0] overflow-hidden">
                <TabelaPaginada
                  dados={historico}
                  colunas={colunasHistorico}
                  chave={(s) => s.id}
                  porPagina={8}
                  textoVazio="Nenhuma transferência processada ainda."
                />
              </div>
            </SectionCard>
          </>
        }
        direita={
          <>
            <Callout tipo="info" titulo="Como nasce uma transferência">
              Só existem dois caminhos: (a) um usuário entra na fila de uma
              biblioteca escolhendo retirar em outra, o que gera um pedido para
              você decidir; ou (b) você cria uma transferência avulsa. Nenhum
              outro perfil cria transferências.
            </Callout>

            <Callout tipo="aviso" titulo="Aprovada, aguardando exemplar">
              Quando o pedido é aprovado antes de alguém devolver o livro, ele
              fica aguardando. Assim que um exemplar for devolvido na biblioteca
              da fila e o usuário for o 1º, o sistema vincula o exemplar e a
              viagem começa sozinha.
            </Callout>

            <Callout tipo="sucesso" titulo="Se você rejeitar">
              A reserva não é cancelada: o usuário continua na fila e a retirada
              passa a ser na própria biblioteca da fila.
            </Callout>

            <CardResumo
              titulo="Resumo das Solicitações"
              rodape={
                <Botao
                  variante="secundario"
                  onClick={carregar}
                  className="w-full"
                >
                  🔄 Atualizar Lista
                </Botao>
              }
            >
              <LinhaResumo
                rotulo="Pendentes de decisão"
                valor={resumo?.pendentes ?? 0}
                destaque="laranja"
              />
              <LinhaResumo
                rotulo="Aprovadas, aguardando exemplar"
                valor={resumo?.aguardandoExemplar ?? 0}
                destaque="azul"
              />
              <LinhaResumo
                rotulo="Em trânsito"
                valor={resumo?.emTransito ?? 0}
                destaque="azul"
              />
              <LinhaResumo
                rotulo="Concluídas"
                valor={resumo?.concluidas ?? 0}
                destaque="verde"
              />
              <LinhaResumo
                rotulo="Rejeitadas"
                valor={resumo?.rejeitadas ?? 0}
                destaque="vermelho"
              />
            </CardResumo>
          </>
        }
      />

      {/* ───────── Modal: aprovar / rejeitar pedido ───────── */}
      <ModalConfirmacao
        aberto={!!decisao}
        titulo={
          decisao?.acao === "aprovar"
            ? "Aprovar transferência?"
            : "Rejeitar transferência?"
        }
        confirmarRotulo={decisao?.acao === "aprovar" ? "Aprovar" : "Rejeitar"}
        tom={decisao?.acao === "aprovar" ? "verde" : "perigo"}
        carregando={processando}
        onConfirmar={decidir}
        onCancelar={() => setDecisao(null)}
      >
        {decisao && (
          <>
            <ResumoModal
              linhas={[
                ["Livro", decisao.s.livro.titulo],
                ["Exemplar", rotuloExemplar(decisao.s)],
                ["De", decisao.s.bibliotecaOrigem.nome],
                ["Para", decisao.s.bibliotecaDestino.nome],
                ["Solicitante", decisao.s.solicitante.nome],
              ]}
            />
            {decisao.acao === "aprovar" ? (
              <p>
                {decisao.s.exemplar
                  ? "Um exemplar já está separado para este pedido, então ele sairá imediatamente em trânsito."
                  : "Ainda não há exemplar vinculado. O pedido ficará aprovado, aguardando a devolução de um exemplar na biblioteca de origem."}
              </p>
            ) : (
              <>
                <p>
                  A reserva do usuário continua na fila, com retirada na{" "}
                  <strong>{decisao.s.bibliotecaOrigem.nome}</strong>. Ele será
                  avisado pela mudança de status e poderá cancelar se quiser.
                </p>
                <AreaTexto
                  placeholder="Motivo da rejeição (opcional)"
                  value={motivo}
                  onChange={(e) => setMotivo(e.target.value)}
                />
              </>
            )}
          </>
        )}
      </ModalConfirmacao>

      {/* ───────── Modal: criar avulsa ───────── */}
      <ModalConfirmacao
        aberto={confirmandoAvulsa}
        titulo="Criar transferência avulsa?"
        confirmarRotulo="Criar e despachar"
        carregando={processando}
        onConfirmar={criarAvulsa}
        onCancelar={() => setConfirmandoAvulsa(false)}
      >
        {exemplarSel && destinoSel && (
          <>
            <ResumoModal
              linhas={[
                ["Livro", exemplarSel.livro.titulo],
                [
                  "Exemplar",
                  exemplarSel.codigoBarras ?? `#${exemplarSel.id}`,
                ],
                ["De", exemplarSel.biblioteca.nome],
                ["Para", destinoSel.nome],
              ]}
            />
            <p>
              A transferência já nasce aprovada e o exemplar sai em trânsito
              agora. O bibliotecário da biblioteca de destino confirma a chegada na{" "}
              <strong>{destinoSel.nome}</strong>.
            </p>
          </>
        )}
      </ModalConfirmacao>
    </>
  );
}
