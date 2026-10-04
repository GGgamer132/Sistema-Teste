/**
 * Empréstimos da Biblioteca — tela de gestão do bibliotecário (UC09/UC10/UC17).
 */
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, nomeExemplar, diasDeAtraso, formatarData } from "../api/client";
import type { Emprestimo } from "../types";
import {
  Badge,
  Botao,
  Card,
  Carregando,
  Chip,
  Entrada,
  Erro,
  GrupoRadio,
  Selecao,
  Sucesso,
  TituloPagina,
  Trilha,
} from "../components/ui";
import TabelaPaginada, { type Coluna } from "../components/TabelaPaginada";
import ModalConfirmacao, { ResumoModal } from "../components/ModalConfirmacao";
import { useUsuarioLogado } from "../auth/contexto";

const DIAS_BLOQUEIO_POR_ATRASO = 2;

type Situacao = "ATIVO" | "ATRASADO" | "DEVOLVIDO";
type Condicao = "BOM" | "DANIFICADO";

function situacaoDe(emprestimo: Emprestimo): Situacao {
  if (emprestimo.status === "DEVOLVIDO" || emprestimo.dataDevolucao) {
    return "DEVOLVIDO";
  }
  return emprestimo.status === "ATRASADO" ||
    diasDeAtraso(emprestimo.dataPrevDevolucao) > 0
    ? "ATRASADO"
    : "ATIVO";
}

const ORDEM: Record<Situacao, number> = {
  ATRASADO: 0,
  ATIVO: 1,
  DEVOLVIDO: 2,
};

function Indicador({
  titulo,
  valor,
  cor,
  ativo,
  onClick,
}: {
  titulo: string;
  valor: number;
  cor: string;
  ativo: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex-1 min-w-[160px] rounded-[12px] bg-white p-5 text-left border-2
        shadow-[0_2px_10px_rgba(0,0,0,0.08)] transition-colors
        ${ativo ? "border-[#1976d2]" : "border-transparent hover:border-[#d7e6fa]"}`}
    >
      <p className="text-[13px] text-[#66707d]">{titulo}</p>
      <p className="text-[30px] font-bold mt-1" style={{ color: cor }}>
        {valor}
      </p>
    </button>
  );
}

export default function EmprestimosBiblioteca() {
  const { bibliotecaNome } = useUsuarioLogado();
  const navigate = useNavigate();
  const [todos, setTodos] = useState<Emprestimo[] | null>(null);
  const [erro, setErro] = useState("");
  const [sucesso, setSucesso] = useState("");

  const [filtro, setFiltro] = useState<"TODOS" | Situacao>("TODOS");
  const [busca, setBusca] = useState("");
  const [periodo, setPeriodo] = useState("TODOS");

  const [alvo, setAlvo] = useState<Emprestimo | null>(null);
  const [condicao, setCondicao] = useState<Condicao>("BOM");
  const [enviando, setEnviando] = useState(false);

  function carregar() {
    api
      // O servidor já devolve só os empréstimos da biblioteca do bibliotecário logado
      .get<Emprestimo[]>("/emprestimos")
      .then(setTodos)
      .catch((e) => setErro(e.message));
  }
  useEffect(carregar, []);

  const contagem = useMemo(() => {
    const contagemAtual = { ATIVO: 0, ATRASADO: 0, DEVOLVIDO: 0 };
    (todos ?? []).forEach(
      (emprestimo) => contagemAtual[situacaoDe(emprestimo)]++,
    );
    return contagemAtual;
  }, [todos]);

  const dados = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    const limite =
      // eslint-disable-next-line react-hooks/purity
      periodo === "TODOS" ? 0 : Date.now() - Number(periodo) * 86_400_000;
    return (todos ?? [])
      .filter(
        (emprestimo) => filtro === "TODOS" || situacaoDe(emprestimo) === filtro,
      )
      .filter(
        (emprestimo) =>
          !limite || new Date(emprestimo.dataEmprestimo).getTime() >= limite,
      )
      .filter(
        (emprestimo) =>
          !termo ||
          emprestimo.usuario.nome.toLowerCase().includes(termo) ||
          emprestimo.exemplar.livro.titulo.toLowerCase().includes(termo),
      )
      .sort(
        (a, b) =>
          ORDEM[situacaoDe(a)] - ORDEM[situacaoDe(b)] ||
          new Date(a.dataPrevDevolucao).getTime() -
            new Date(b.dataPrevDevolucao).getTime(),
      );
  }, [todos, filtro, busca, periodo]);

  async function devolver() {
    if (!alvo) return;
    setEnviando(true);
    setErro("");
    setSucesso("");
    try {
      await api.post("/emprestimos/devolver", {
        emprestimoId: alvo.id,
        condicaoExemplar: condicao,
      });
      const atraso = diasDeAtraso(alvo.dataPrevDevolucao);
      setSucesso(
        atraso > 0
          ? `Devolução registrada com ${atraso} dia(s) de atraso. ${alvo.usuario.nome} ficou bloqueado por ${atraso * DIAS_BLOQUEIO_POR_ATRASO} dias.`
          : `Devolução de “${alvo.exemplar.livro.titulo}” registrada dentro do prazo.`,
      );
      setCondicao("BOM");
      carregar();
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setEnviando(false);
      setAlvo(null);
    }
  }

  if (!todos && !erro) {
    return <Carregando texto="Carregando empréstimos da biblioteca..." />;
  }

  const colunas: Coluna<Emprestimo>[] = [
    {
      titulo: "Exemplar",
      render: (emprestimo) => (
        <div>
          <p className="font-semibold text-[#2c3e50]">
            {emprestimo.exemplar.livro.titulo}
          </p>
          <p className="text-[12px] text-[#66707d]">
            {nomeExemplar(emprestimo.exemplar.id)} ·{" "}
            {emprestimo.exemplar.livro.autor}
          </p>
        </div>
      ),
    },
    { titulo: "Usuário", render: (emprestimo) => emprestimo.usuario.nome },
    {
      titulo: "Empréstimo",
      render: (emprestimo) => formatarData(emprestimo.dataEmprestimo),
    },
    {
      titulo: "Devolução prevista",
      render: (emprestimo) => {
        const atraso = diasDeAtraso(emprestimo.dataPrevDevolucao);
        const aberto = situacaoDe(emprestimo) !== "DEVOLVIDO";
        return (
          <div>
            <p>{formatarData(emprestimo.dataPrevDevolucao)}</p>
            {aberto && atraso > 0 && (
              <p className="text-[12px] font-semibold text-[#d32f2f]">
                há {atraso} dia(s)
              </p>
            )}
            {!aberto && (
              <p className="text-[12px] text-[#66707d]">
                devolvido em {formatarData(emprestimo.dataDevolucao)}
              </p>
            )}
          </div>
        );
      },
    },
    {
      titulo: "Status",
      render: (emprestimo) => {
        const situacao = situacaoDe(emprestimo);
        if (situacao === "ATRASADO")
          return <Badge tom="vermelho">🔴 Atrasado</Badge>;
        if (situacao === "ATIVO")
          return <Badge tom="azul">🔵 Em andamento</Badge>;
        return <Badge tom="verde">🟢 Devolvido</Badge>;
      },
    },
    {
      titulo: "Ação",
      className: "text-right",
      render: (emprestimo) =>
        situacaoDe(emprestimo) === "DEVOLVIDO" ? (
          <span className="text-[#66707d]">—</span>
        ) : (
          <Botao variante="secundario" onClick={() => setAlvo(emprestimo)}>
            Registrar devolução
          </Botao>
        ),
    },
  ];

  const atrasoAlvo = alvo ? diasDeAtraso(alvo.dataPrevDevolucao) : 0;

  return (
    <>
      <Trilha itens={[{ rotulo: "Empréstimos" }]} />
      <div className="flex flex-wrap items-end justify-between gap-4">
        <TituloPagina
          titulo="Empréstimos da Biblioteca"
          subtitulo={`${bibliotecaNome ?? ""} · acompanhe o que está emprestado e registre devoluções`}
        />
        <Botao onClick={() => navigate("/biblioteca/emprestimo")}>
          ＋ Novo empréstimo
        </Botao>
      </div>

      {erro && <Erro mensagem={erro} />}
      {sucesso && <Sucesso mensagem={sucesso} />}

      <div className="flex flex-wrap gap-4">
        <Indicador
          titulo="Em andamento"
          valor={contagem.ATIVO}
          cor="#1976d2"
          ativo={filtro === "ATIVO"}
          onClick={() => setFiltro("ATIVO")}
        />
        <Indicador
          titulo="Atrasados"
          valor={contagem.ATRASADO}
          cor="#d32f2f"
          ativo={filtro === "ATRASADO"}
          onClick={() => setFiltro("ATRASADO")}
        />
        <Indicador
          titulo="Devolvidos"
          valor={contagem.DEVOLVIDO}
          cor="#388e3c"
          ativo={filtro === "DEVOLVIDO"}
          onClick={() => setFiltro("DEVOLVIDO")}
        />
      </div>

      <Card className="overflow-hidden">
        <div className="p-5 flex flex-wrap items-center gap-4">
          <div className="flex flex-wrap gap-2">
            <Chip ativo={filtro === "TODOS"} onClick={() => setFiltro("TODOS")}>
              Todos
            </Chip>
            <Chip ativo={filtro === "ATIVO"} onClick={() => setFiltro("ATIVO")}>
              Em andamento
            </Chip>
            <Chip
              ativo={filtro === "ATRASADO"}
              onClick={() => setFiltro("ATRASADO")}
            >
              Atrasados
            </Chip>
            <Chip
              ativo={filtro === "DEVOLVIDO"}
              onClick={() => setFiltro("DEVOLVIDO")}
            >
              Devolvidos
            </Chip>
          </div>
          <div className="flex-1 min-w-[220px]">
            <Entrada
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
              placeholder="🔎 Usuário, título ou código do exemplar..."
            />
          </div>
          <div className="w-[190px]">
            <Selecao
              value={periodo}
              onChange={(e) => setPeriodo(e.target.value)}
            >
              <option value="TODOS">Todo o período</option>
              <option value="7">Últimos 7 dias</option>
              <option value="30">Últimos 30 dias</option>
              <option value="90">Últimos 90 dias</option>
            </Selecao>
          </div>
        </div>

        <TabelaPaginada
          key={`${filtro}|${busca}|${periodo}`}
          dados={dados}
          colunas={colunas}
          chave={(emprestimo) => emprestimo.id}
          porPagina={10}
          classeLinha={(emprestimo) =>
            situacaoDe(emprestimo) === "ATRASADO" ? "bg-[#fdf3f3]" : ""
          }
          textoVazio="Nenhum empréstimo corresponde aos filtros."
        />
      </Card>

      <ModalConfirmacao
        aberto={!!alvo}
        titulo="Confirmar devolução?"
        confirmarRotulo="Confirmar devolução"
        tom="verde"
        carregando={enviando}
        onConfirmar={devolver}
        onCancelar={() => setAlvo(null)}
      >
        {alvo && (
          <>
            <ResumoModal
              linhas={[
                ["Livro", alvo.exemplar.livro.titulo],
                [
                  "Exemplar",
                  nomeExemplar(alvo.exemplar.id),
                ],
                ["Usuário", alvo.usuario.nome],
                ["Prevista para", formatarData(alvo.dataPrevDevolucao)],
              ]}
            />
            {atrasoAlvo > 0 ? (
              <p className="rounded-[8px] bg-[#fce5e5] px-3 py-2 text-[#d32f2f] font-medium">
                🔴 {atrasoAlvo} dia(s) de atraso. O usuário ficará bloqueado
                para novos empréstimos por{" "}
                {atrasoAlvo * DIAS_BLOQUEIO_POR_ATRASO} dias.
              </p>
            ) : (
              <p className="rounded-[8px] bg-[#dbf0db] px-3 py-2 text-[#2b6e2e] font-medium">
                🟢 Devolução dentro do prazo.
              </p>
            )}
            <div>
              <p className="mb-2 text-[13px] font-semibold text-[#2c3e50]">
                Condição do exemplar
              </p>
              <GrupoRadio<Condicao>
                valor={condicao}
                onChange={setCondicao}
                opcoes={[
                  { valor: "BOM", rotulo: "Bom estado" },
                  { valor: "DANIFICADO", rotulo: "Danificado" },
                ]}
              />
            </div>
          </>
        )}
      </ModalConfirmacao>
    </>
  );
}
