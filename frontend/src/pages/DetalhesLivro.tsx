/**
 * TELA 3 — Detalhes do Livro (UC02).
 * Mostra a ficha do título e a disponibilidade em cada biblioteca da rede.
 * O botão de cada linha leva para a reserva (RN03).
 */
import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { LivroResumo } from "../types";
import type { Rota } from "../App";
import {
  Badge, BadgeSituacao, Botao, Card, Carregando, CapaLivro, Erro, LinhaResumo, Trilha,
} from "../components/ui";

export default function DetalhesLivro({
  livroId,
  navegar,
}: {
  livroId: number;
  navegar: (r: Rota) => void;
}) {
  const [livro, setLivro] = useState<LivroResumo | null>(null);
  const [erro, setErro] = useState("");

  useEffect(() => {
    api.get<LivroResumo>(`/livros/${livroId}`)
      .then(setLivro)
      .catch((e) => setErro(e.message));
  }, [livroId]);

  if (erro) return <Erro mensagem={erro} />;
  if (!livro) return <Carregando texto="Carregando detalhes do título..." />;

  const semExemplarLivre = livro.disponiveis === 0;

  return (
    <>
      <Trilha
        itens={[
          { rotulo: "Buscar Livros", onClick: () => navegar({ nome: "busca" }) },
          { rotulo: "Resultados", onClick: () => window.history.back() },
          { rotulo: livro.titulo },
        ]}
      />

      <div className="flex flex-col lg:flex-row gap-8 items-start">
        {/* Coluna da capa + ficha técnica */}
        <div className="w-full lg:w-[320px] shrink-0 flex flex-col gap-5">
          <CapaLivro tamanho="lg" />
          <Card className="p-5 flex flex-col gap-3">
            <LinhaResumo rotulo="Editora" valor={livro.editora ?? "—"} />
            <LinhaResumo rotulo="Ano de publicação" valor={livro.anoPublicacao ?? "—"} />
            <LinhaResumo rotulo="ISBN" valor={livro.isbn ?? "—"} />
            <LinhaResumo rotulo="Categoria" valor={livro.categoria ?? "—"} />
          </Card>
        </div>

        {/* Coluna principal */}
        <div className="flex-1 min-w-0 flex flex-col gap-6">
          <div>
            <h1 className="text-[32px] font-bold text-[#2c3e50] leading-tight">
              {livro.titulo}
            </h1>
            <p className="text-[18px] text-[#66707d] mt-2">{livro.autor}</p>
            <div className="flex flex-wrap items-center gap-2 mt-4">
              {livro.categoria && <Badge tom="cinza">{livro.categoria}</Badge>}
              <BadgeSituacao situacao={livro.situacao} />
              {livro.situacao === "DISPONIVEL" && (
                <span className="text-[13px] text-[#66707d]">
                  em {livro.bibliotecasComDisponivel}{" "}
                  {livro.bibliotecasComDisponivel === 1 ? "biblioteca" : "bibliotecas"}
                </span>
              )}
            </div>
          </div>

          {livro.sinopse && (
            <div>
              <h2 className="text-[15px] font-semibold text-[#2c3e50] mb-2">Sinopse</h2>
              <p className="text-[14px] leading-relaxed text-[#66707d] max-w-[70ch]">
                {livro.sinopse}
              </p>
            </div>
          )}

          <Card className="p-6">
            <h2 className="text-[17px] font-semibold text-[#2c3e50] mb-4">
              Disponibilidade na rede
            </h2>

            {(!livro.disponibilidade || livro.disponibilidade.length === 0) && (
              <div className="rounded-[10px] bg-[#f5f7fa] px-4 py-6 text-center text-[14px] text-[#66707d]">
                Nenhum exemplar deste título está cadastrado na rede ainda.
              </div>
            )}

            <div className="flex flex-col gap-3">
              {livro.disponibilidade?.map((d) => (
                <div
                  key={d.bibliotecaId}
                  className="flex flex-wrap items-center justify-between gap-4
                             rounded-[10px] bg-[#f5f7fa] px-4 py-4"
                >
                  <div className="min-w-0">
                    <p className="text-[15px] font-semibold text-[#2c3e50]">
                      {d.bibliotecaNome}
                    </p>
                    <p className="text-[13px] text-[#66707d]">
                      {d.endereco} ·{" "}
                      {d.disponiveis > 0
                        ? `${d.disponiveis} ${d.disponiveis === 1 ? "exemplar disponível" : "exemplares disponíveis"}`
                        : "Todos emprestados"}
                    </p>
                  </div>
                  <Botao
                    variante={d.disponiveis > 0 ? "secundario" : "primario"}
                    onClick={() =>
                      navegar({
                        nome: "reserva",
                        livroId: livro.id,
                        bibliotecaId: d.bibliotecaId,
                      })
                    }
                  >
                    {d.disponiveis > 0 ? "Retirar aqui" : "Entrar na fila"}
                  </Botao>
                </div>
              ))}
            </div>

            {semExemplarLivre && livro.totalExemplares > 0 && (
              <p className="mt-4 text-[13px] text-[#66707d]">
                Todos os exemplares estão emprestados. Ao reservar, você entra na fila
                de espera e é avisado assim que um exemplar for devolvido (RN03).
              </p>
            )}
          </Card>
        </div>
      </div>
    </>
  );
}
