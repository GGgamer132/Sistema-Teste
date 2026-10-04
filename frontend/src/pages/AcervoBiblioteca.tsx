/**
 * B7 — Gerenciar exemplares do acervo da própria biblioteca.
 * Marcar indisponível (T13, com motivo) vale para exemplares disponíveis ou
 * reservados; Reativar (T14/T14b) para os indisponíveis. Tudo com confirmação.
 */
import { useEffect, useMemo, useState } from "react";
import { api, nomeExemplar } from "../api/client";
import type { Exemplar } from "../types";
import { useUsuarioLogado } from "../auth/contexto";
import {
  AreaTexto, Badge, Botao, Campo, Carregando, Chip, Entrada, Erro, SectionCard,
  Sucesso, TituloPagina, Trilha,
} from "../components/ui";
import TabelaPaginada, { type Coluna } from "../components/TabelaPaginada";
import ModalConfirmacao, { ResumoModal } from "../components/ModalConfirmacao";

type Status = Exemplar["status"];

const ROTULO: Record<Status, string> = {
  DISPONIVEL: "Disponível",
  EMPRESTADO: "Emprestado",
  EMPRESTADO_RESERVADO: "Emprestado (com fila)",
  RESERVADO: "Reservado",
  EM_TRANSFERENCIA: "Em transferência",
  INDISPONIVEL: "Indisponível",
};

const TOM: Record<Status, "verde" | "amarelo" | "vermelho" | "azul" | "cinza"> = {
  DISPONIVEL: "verde",
  EMPRESTADO: "azul",
  EMPRESTADO_RESERVADO: "azul",
  RESERVADO: "amarelo",
  EM_TRANSFERENCIA: "amarelo",
  INDISPONIVEL: "vermelho",
};

const CONSERVACAO: Record<string, string> = {
  NOVO: "Novo", BOM: "Bom", USADO: "Usado", DANIFICADO: "Danificado",
};

type Filtro = "TODOS" | "DISPONIVEL" | "EMPRESTADOS" | "RESERVADO" | "INDISPONIVEL";

type Acao = { tipo: "indisponivel" | "reativar"; exemplar: Exemplar };

export default function AcervoBiblioteca() {
  const { bibliotecaId, bibliotecaNome } = useUsuarioLogado();
  const [exemplares, setExemplares] = useState<Exemplar[] | null>(null);
  const [busca, setBusca] = useState("");
  const [filtro, setFiltro] = useState<Filtro>("TODOS");
  const [acao, setAcao] = useState<Acao | null>(null);
  const [motivo, setMotivo] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState("");
  const [sucesso, setSucesso] = useState("");

  function carregar() {
    api
      .get<Exemplar[]>(`/exemplares/biblioteca/${bibliotecaId}`)
      .then(setExemplares)
      .catch((e) => setErro(e.message));
  }
  useEffect(carregar, [bibliotecaId]);

  const filtrados = useMemo(() => {
    const t = busca.trim().toLowerCase();
    return (exemplares ?? [])
      .filter((e) => {
        if (filtro === "EMPRESTADOS") return e.status === "EMPRESTADO" || e.status === "EMPRESTADO_RESERVADO";
        return filtro === "TODOS" || e.status === filtro;
      })
      .filter(
        (e) =>
          !t ||
          e.livro.titulo.toLowerCase().includes(t) ||
          e.livro.autor.toLowerCase().includes(t) ||
          String(e.id) === t.replace(/\D/g, ""),
      )
      .sort((a, b) => a.livro.titulo.localeCompare(b.livro.titulo) || a.id - b.id);
  }, [exemplares, busca, filtro]);

  async function confirmar() {
    if (!acao) return;
    setEnviando(true);
    setErro("");
    setSucesso("");
    try {
      const e =
        acao.tipo === "indisponivel"
          ? await api.patch<Exemplar>(`/exemplares/${acao.exemplar.id}/indisponivel`, { motivo })
          : await api.patch<Exemplar>(`/exemplares/${acao.exemplar.id}/reativar`);
      setSucesso(
        acao.tipo === "indisponivel"
          ? `${nomeExemplar(e.id)} (${e.livro.titulo}) marcado como indisponível.` +
              (acao.exemplar.status === "RESERVADO" ? " A reserva voltou para a fila na mesma posição." : "")
          : `${nomeExemplar(e.id)} (${e.livro.titulo}) reativado: agora ${ROTULO[e.status].toLowerCase()}.`,
      );
      setMotivo("");
      carregar();
    } catch (err) {
      setErro((err as Error).message);
    } finally {
      setEnviando(false);
      setAcao(null);
    }
  }

  const colunas: Coluna<Exemplar>[] = [
    {
      titulo: "Exemplar",
      render: (e) => <span className="whitespace-nowrap font-semibold">{nomeExemplar(e.id)}</span>,
    },
    {
      titulo: "Título",
      render: (e) => (
        <div>
          <p className="font-semibold text-[#2c3e50]">{e.livro.titulo}</p>
          <p className="text-[12px] text-[#66707d]">{e.livro.autor}</p>
        </div>
      ),
    },
    { titulo: "Status", render: (e) => <Badge tom={TOM[e.status]}>{ROTULO[e.status]}</Badge> },
    { titulo: "Conservação", render: (e) => CONSERVACAO[e.estadoConservacao ?? ""] ?? e.estadoConservacao ?? "—" },
    {
      titulo: "Ação",
      className: "text-right",
      render: (e) =>
        e.status === "INDISPONIVEL" ? (
          <Botao variante="secundario" className="!px-3 !py-[6px] !text-[13px]"
                 onClick={() => setAcao({ tipo: "reativar", exemplar: e })}>
            Reativar
          </Botao>
        ) : e.status === "DISPONIVEL" || e.status === "RESERVADO" ? (
          <Botao variante="perigo" className="!px-3 !py-[6px] !text-[13px]"
                 onClick={() => setAcao({ tipo: "indisponivel", exemplar: e })}>
            Marcar indisponível
          </Botao>
        ) : (
          <span className="text-[#9aa3ad]">—</span>
        ),
    },
  ];

  if (exemplares === null && !erro) return <Carregando texto="Carregando acervo..." />;

  const contar = (f: Filtro) =>
    (exemplares ?? []).filter((e) =>
      f === "TODOS" ? true
        : f === "EMPRESTADOS" ? e.status === "EMPRESTADO" || e.status === "EMPRESTADO_RESERVADO"
        : e.status === f,
    ).length;

  const filtros: { valor: Filtro; rotulo: string }[] = [
    { valor: "TODOS", rotulo: "Todos" },
    { valor: "DISPONIVEL", rotulo: "Disponíveis" },
    { valor: "EMPRESTADOS", rotulo: "Emprestados" },
    { valor: "RESERVADO", rotulo: "Reservados" },
    { valor: "INDISPONIVEL", rotulo: "Indisponíveis" },
  ];

  return (
    <>
      <Trilha voltarPara="/biblioteca" itens={[{ rotulo: "Empréstimos", to: "/biblioteca" }, { rotulo: "Acervo" }]} />
      <TituloPagina titulo="Acervo da Biblioteca" subtitulo={`${bibliotecaNome ?? ""} · gerencie a situação dos exemplares`} />

      {erro && <Erro mensagem={erro} />}
      {sucesso && <Sucesso mensagem={sucesso} />}

      <SectionCard titulo={`Exemplares (${filtrados.length})`}>
        <div className="flex flex-wrap gap-2 mb-4">
          {filtros.map((f) => (
            <Chip key={f.valor} ativo={filtro === f.valor} onClick={() => setFiltro(f.valor)}>
              {f.rotulo} ({contar(f.valor)})
            </Chip>
          ))}
        </div>
        <Entrada
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
          placeholder="🔎 Buscar por título, autor ou nº do exemplar..."
          aria-label="Buscar no acervo"
        />
        <div className="mt-4 -mx-6">
          <TabelaPaginada dados={filtrados} colunas={colunas} chave={(e) => e.id} textoVazio="Nenhum exemplar encontrado." />
        </div>
      </SectionCard>

      <ModalConfirmacao
        aberto={!!acao}
        titulo={acao?.tipo === "indisponivel" ? "Marcar exemplar como indisponível?" : "Reativar exemplar?"}
        confirmarRotulo={acao?.tipo === "indisponivel" ? "Marcar indisponível" : "Reativar"}
        tom={acao?.tipo === "indisponivel" ? "perigo" : "verde"}
        carregando={enviando}
        onConfirmar={confirmar}
        onCancelar={() => {
          setAcao(null);
          setMotivo("");
        }}
      >
        {acao && (
          <>
            <ResumoModal
              linhas={[
                ["Exemplar", nomeExemplar(acao.exemplar.id)],
                ["Livro", acao.exemplar.livro.titulo],
                ["Situação atual", ROTULO[acao.exemplar.status]],
              ]}
            />
            {acao.tipo === "indisponivel" ? (
              <>
                <Campo label="Motivo">
                  <AreaTexto
                    value={motivo}
                    onChange={(e) => setMotivo(e.target.value)}
                    placeholder="Ex.: capa rasgada, páginas soltas..."
                  />
                </Campo>
                {acao.exemplar.status === "RESERVADO" && (
                  <p>A reserva ligada a este exemplar volta para a fila, mantendo a posição.</p>
                )}
              </>
            ) : (
              <p>O exemplar volta a circular. Se houver fila deste título aqui, ele já é separado para o primeiro.</p>
            )}
          </>
        )}
      </ModalConfirmacao>
    </>
  );
}
