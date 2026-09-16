/**
 * TELA 4 — Registrar Empréstimo (UC09).
 * Três passos: escolher exemplar, escolher usuário, confirmar prazo.
 * A coluna direita mostra em tempo real se o usuário está apto (RN01/RN12).
 */
import { useEffect, useState } from "react";
import { api, formatarData } from "../api/client";
import type { Emprestimo, Exemplar, SituacaoUsuario, Usuario } from "../types";
import {
  Badge, Botao, Campo, CapaLivro, CardResumo, Callout, Carregando, DuasColunas,
  Entrada, Erro, LinhaResumo, SectionCard, Selecao, Sucesso, TituloPagina, Trilha, Vazio,
} from "../components/ui";

const PRAZO_PADRAO = 14; // RN02

export default function RegistrarEmprestimo() {
  const [exemplares, setExemplares] = useState<Exemplar[]>([]);
  const [usuarios, setUsuarios] = useState<Usuario[]>([]);
  const [carregando, setCarregando] = useState(true);

  const [buscaExemplar, setBuscaExemplar] = useState("");
  const [exemplarSel, setExemplarSel] = useState<Exemplar | null>(null);
  const [usuarioSel, setUsuarioSel] = useState<Usuario | null>(null);
  const [situacao, setSituacao] = useState<SituacaoUsuario | null>(null);
  const [prazo, setPrazo] = useState(PRAZO_PADRAO);

  const [erro, setErro] = useState("");
  const [sucesso, setSucesso] = useState("");
  const [enviando, setEnviando] = useState(false);

  // Carrega o acervo e os usuários da comunidade uma única vez
  useEffect(() => {
    Promise.all([
      api.get<Exemplar[]>("/exemplares"),
      api.get<Usuario[]>("/usuarios/tipo/COMUM"),
    ])
      .then(([ex, us]) => {
        setExemplares(ex);
        setUsuarios(us);
      })
      .catch((e) => setErro(e.message))
      .finally(() => setCarregando(false));
  }, []);

  // Sempre que o usuário muda, reconsulta a situação dele no backend
  useEffect(() => {
    if (!usuarioSel) {
      setSituacao(null);
      return;
    }
    api.get<SituacaoUsuario>(`/emprestimos/situacao/${usuarioSel.id}`)
      .then(setSituacao)
      .catch((e) => setErro(e.message));
  }, [usuarioSel]);

  const emprestaveis = exemplares.filter(
    (e) => e.status === "DISPONIVEL" || e.status === "RESERVADO",
  );

  const filtrados = buscaExemplar.trim()
    ? emprestaveis.filter((e) => {
        const t = buscaExemplar.toLowerCase();
        return (
          e.codigoBarras?.toLowerCase().includes(t) ||
          e.livro.titulo.toLowerCase().includes(t) ||
          e.livro.autor.toLowerCase().includes(t)
        );
      })
    : emprestaveis.slice(0, 5);

  const devolucaoPrevista = new Date(Date.now() + prazo * 86_400_000);

  async function confirmar() {
    if (!exemplarSel || !usuarioSel) return;
    setEnviando(true);
    setErro("");
    setSucesso("");
    try {
      const emp = await api.post<Emprestimo>("/emprestimos/registrar", {
        exemplarId: exemplarSel.id,
        usuarioId: usuarioSel.id,
        prazoDias: prazo,
      });
      setSucesso(
        `Empréstimo registrado. ${emp.exemplar.livro.titulo} para ${emp.usuario.nome}, ` +
          `devolução prevista em ${formatarData(emp.dataPrevDevolucao)}.`,
      );
      // Limpa o formulário e recarrega o acervo para refletir o novo status
      setExemplarSel(null);
      setUsuarioSel(null);
      setBuscaExemplar("");
      setPrazo(PRAZO_PADRAO);
      api.get<Exemplar[]>("/exemplares").then(setExemplares).catch(() => {});
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  if (carregando) return <Carregando texto="Carregando acervo..." />;

  const podeConfirmar = !!exemplarSel && !!usuarioSel && situacao?.apto === true && !enviando;

  return (
    <>
      <Trilha itens={[{ rotulo: "Painel da Biblioteca" }, { rotulo: "Registrar Empréstimo" }]} />
      <TituloPagina
        titulo="Registrar Empréstimo"
        subtitulo="Biblioteca Vila Isabel · UC09 - Registrar empréstimo"
      />

      {erro && <Erro mensagem={erro} />}
      {sucesso && <Sucesso mensagem={sucesso} />}

      <DuasColunas
        esquerda={
          <>
            <SectionCard titulo="1. Buscar exemplar">
              <Campo label="Código do exemplar ou título do livro">
                <Entrada
                  value={buscaExemplar}
                  onChange={(e) => setBuscaExemplar(e.target.value)}
                  placeholder="📷 Escanear código de barras ou digitar..."
                />
              </Campo>

              <div className="mt-4 flex flex-col gap-2">
                {filtrados.length === 0 && (
                  <Vazio texto="Nenhum exemplar disponível corresponde a essa busca." />
                )}
                {filtrados.map((e) => {
                  const ativo = exemplarSel?.id === e.id;
                  return (
                    <button
                      key={e.id}
                      onClick={() => setExemplarSel(e)}
                      className={`flex items-center gap-4 rounded-[10px] p-4 text-left border
                        ${ativo ? "border-[#1976d2] bg-[#e8f0fc]" : "border-transparent bg-[#f5f7fa]"}`}
                    >
                      <CapaLivro tamanho="sm" />
                      <div className="flex-1 min-w-0">
                        <p className="text-[15px] font-semibold text-[#2c3e50]">
                          {e.livro.titulo} — {e.livro.autor}
                        </p>
                        <p className="text-[13px] text-[#66707d] mt-1">
                          Exemplar: {e.codigoBarras ?? `#${e.id}`} · {e.biblioteca.nome} ·
                          Status: {e.status}
                        </p>
                      </div>
                      {ativo && <Badge tom="verde">✔ Selecionado</Badge>}
                    </button>
                  );
                })}
              </div>
            </SectionCard>

            <SectionCard titulo="2. Selecionar usuário">
              <Campo label="Usuário da comunidade">
                <Selecao
                  value={usuarioSel?.id ?? ""}
                  onChange={(ev) =>
                    setUsuarioSel(usuarios.find((u) => u.id === Number(ev.target.value)) ?? null)
                  }
                >
                  <option value="">Selecione o usuário...</option>
                  {usuarios.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.nome} — {u.email}
                    </option>
                  ))}
                </Selecao>
              </Campo>

              {situacao && (
                <div className="mt-4 flex items-center gap-4 rounded-[10px] bg-[#f5f7fa] p-4">
                  <span aria-hidden className="text-[30px]">👤</span>
                  <div>
                    <p className="text-[15px] font-semibold text-[#2c3e50]">{situacao.nome}</p>
                    <p className="text-[13px] text-[#66707d]">
                      {situacao.email} · Empréstimos ativos: {situacao.emprestimosAtivos}/
                      {situacao.limite} · Situação:{" "}
                      {situacao.apto ? "Regular" : "Impedido"}
                    </p>
                  </div>
                </div>
              )}
            </SectionCard>

            <SectionCard titulo="3. Prazo do empréstimo">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
                <Campo label="Data do empréstimo">
                  <Entrada value={new Date().toLocaleDateString("pt-BR")} readOnly />
                </Campo>
                <Campo label="Prazo (dias)">
                  <Entrada
                    type="number"
                    min={1}
                    value={prazo}
                    onChange={(e) => setPrazo(Number(e.target.value) || PRAZO_PADRAO)}
                  />
                </Campo>
                <Campo label="Devolução prevista">
                  <Entrada value={devolucaoPrevista.toLocaleDateString("pt-BR")} readOnly />
                </Campo>
              </div>
            </SectionCard>
          </>
        }
        direita={
          <>
            {situacao?.apto && (
              <Callout tipo="sucesso" titulo="Nenhuma pendência encontrada">
                O usuário está apto a realizar este empréstimo.
              </Callout>
            )}
            {situacao && !situacao.apto && (
              <Callout tipo="aviso" titulo="Empréstimo bloqueado">
                {situacao.motivo}
              </Callout>
            )}

            <Callout tipo="info" titulo="Limite de empréstimos (RN01)">
              Máximo de {situacao?.limite ?? 3} exemplares de títulos distintos
              simultaneamente
              {situacao && ` — ${situacao.nome.split(" ")[0]} está usando ${situacao.emprestimosAtivos} de ${situacao.limite}`}.
            </Callout>

            <Callout tipo="aviso" titulo="Atraso bloqueia novos empréstimos (RN12)">
              Cada dia de atraso soma 2 dias de bloqueio para novos empréstimos.
            </Callout>

            <CardResumo
              titulo="Resumo do empréstimo"
              rodape={
                <Botao
                  variante="verde"
                  onClick={confirmar}
                  disabled={!podeConfirmar}
                  className="w-full"
                >
                  {enviando ? "Registrando..." : "✅ Confirmar Empréstimo"}
                </Botao>
              }
            >
              <LinhaResumo rotulo="Livro" valor={exemplarSel?.livro.titulo ?? "—"} />
              <LinhaResumo rotulo="Usuário" valor={usuarioSel?.nome ?? "—"} />
              <LinhaResumo
                rotulo="Devolução prevista"
                valor={devolucaoPrevista.toLocaleDateString("pt-BR")}
              />
            </CardResumo>
          </>
        }
      />
    </>
  );
}
