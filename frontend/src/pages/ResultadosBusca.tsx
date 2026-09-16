/**
 * TELA 2 — Resultados da Busca (UC01 / UC02).
 * Lista os títulos com o badge de disponibilidade calculado pelo backend.
 */
import { useEffect, useState } from "react";
import { api, qs } from "../api/client";
import type { LivroResumo } from "../types";
import type { Rota } from "../App";
import type { Filtros } from "./BuscaLivros";
import {
  BadgeSituacao, Botao, Card, Carregando, CapaLivro, Erro, Trilha, Vazio,
} from "../components/ui";

export default function ResultadosBusca({
  filtros,
  navegar,
}: {
  filtros: Filtros;
  navegar: (r: Rota) => void;
}) {
  const [livros, setLivros] = useState<LivroResumo[] | null>(null);
  const [erro, setErro] = useState("");

  useEffect(() => {
    setLivros(null);
    setErro("");
    api
      .get<LivroResumo[]>(
        "/livros/busca" +
          qs({
            termo: filtros.termo,
            autor: filtros.autor,
            isbn: filtros.isbn,
            categoriaId: filtros.categoriaId,
            anoDe: filtros.anoDe,
            anoAte: filtros.anoAte,
          }),
      )
      .then(setLivros)
      .catch((e) => setErro(e.message));
  }, [filtros]);

  const rotuloBusca = filtros.termo || filtros.autor || "todos os títulos";

  /** Texto auxiliar ao lado do badge, conforme a situação do título. */
  function detalhe(l: LivroResumo): string {
    if (l.situacao === "DISPONIVEL") {
      return l.bibliotecasComDisponivel === 1
        ? "Disponível em 1 biblioteca"
        : `Disponível em ${l.bibliotecasComDisponivel} bibliotecas`;
    }
    if (l.situacao === "AGUARDANDO") {
      return l.naFilaDeEspera > 0
        ? `Todos emprestados · ${l.naFilaDeEspera} na fila de espera`
        : "Todos os exemplares emprestados";
    }
    return "Sem exemplares cadastrados na rede";
  }

  return (
    <>
      <Trilha
        itens={[
          { rotulo: "Buscar Livros", onClick: () => navegar({ nome: "busca" }) },
          { rotulo: "Resultados" },
        ]}
      />

      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-[24px] font-bold text-[#2c3e50]">
            Resultados para “{rotuloBusca}”
          </h1>
          <p className="text-[14px] text-[#66707d] mt-1">
            {livros === null
              ? "Buscando na rede..."
              : `${livros.length} ${livros.length === 1 ? "livro encontrado" : "livros encontrados"} na rede Circula Book`}
          </p>
        </div>
        <Botao variante="secundario" onClick={() => navegar({ nome: "busca" })}>
          Refazer busca
        </Botao>
      </div>

      {erro && <Erro mensagem={erro} />}
      {livros === null && !erro && <Carregando texto="Consultando o acervo da rede..." />}
      {livros?.length === 0 && (
        <Vazio texto="Nenhum título corresponde a esses filtros. Tente ampliar a busca ou registre o interesse para que a rede avalie a aquisição." />
      )}

      <div className="flex flex-col gap-4">
        {livros?.map((l) => (
          <Card key={l.id} className="p-5">
            <div className="flex items-center gap-5">
              <CapaLivro tamanho="md" />

              <div className="flex-1 min-w-0">
                <h2 className="text-[18px] font-semibold text-[#2c3e50] truncate">
                  {l.titulo}
                </h2>
                <p className="text-[14px] text-[#66707d] mt-1">{l.autor}</p>
                <p className="text-[13px] text-[#66707d]">
                  {[l.categoria, l.anoPublicacao].filter(Boolean).join(" · ")}
                </p>
                <div className="flex flex-wrap items-center gap-3 mt-3">
                  <BadgeSituacao situacao={l.situacao} />
                  <span className="text-[13px] text-[#66707d]">{detalhe(l)}</span>
                </div>
              </div>

              <Botao
                variante="secundario"
                onClick={() => navegar({ nome: "detalhesLivro", livroId: l.id })}
                className="shrink-0"
              >
                Ver detalhes
              </Botao>
            </div>
          </Card>
        ))}
      </div>
    </>
  );
}
