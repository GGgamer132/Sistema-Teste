/**
 * Painel da Rede (A1) — tela inicial do Admin. Só cards e tabelas (sem gráficos):
 * bibliotecas, exemplares por situação, empréstimos, reservas, transferências e demandas.
 */
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import type { Dashboard } from "../types";
import { Card, Carregando, Erro, SectionCard, TituloPagina } from "../components/ui";

interface Kpi {
  chave: keyof Dashboard;
  rotulo: string;
  detalhe?: (d: Dashboard) => string;
  to: string;
  alerta?: boolean;
}

const GRUPOS: { titulo: string; itens: Kpi[] }[] = [
  {
    titulo: "Rede",
    itens: [
      { chave: "bibliotecasAtivas", rotulo: "Bibliotecas ativas", detalhe: (d) => `de ${d.bibliotecasTotal} cadastradas`, to: "/admin/bibliotecas" },
      { chave: "exemplaresTotal", rotulo: "Exemplares no acervo", to: "/admin/relatorios" },
    ],
  },
  {
    titulo: "Empréstimos e reservas",
    itens: [
      { chave: "emprestimosAtivos", rotulo: "Empréstimos ativos", to: "/admin/relatorios" },
      { chave: "emprestimosAtrasados", rotulo: "Empréstimos atrasados", to: "/admin/relatorios", alerta: true },
      { chave: "reservasEmFila", rotulo: "Reservas na fila", to: "/admin/historico" },
      { chave: "reservasProntas", rotulo: "Prontas para retirada", to: "/admin/historico" },
    ],
  },
  {
    titulo: "Transferências e demandas",
    itens: [
      { chave: "transferenciasPendentes", rotulo: "Transferências aguardando decisão", to: "/admin/transferencias", alerta: true },
      { chave: "transferenciasAguardandoExemplar", rotulo: "Aprovadas, aguardando exemplar", to: "/admin/transferencias" },
      { chave: "transferenciasEmTransito", rotulo: "Em trânsito", to: "/admin/transferencias" },
      { chave: "demandasAbertas", rotulo: "Demandas abertas", detalhe: (d) => `${d.demandasEmAnalise} em análise`, to: "/admin/demandas" },
    ],
  },
];

export default function AdminDashboard() {
  const [dados, setDados] = useState<Dashboard | null>(null);
  const [erro, setErro] = useState("");

  useEffect(() => {
    api
      .get<Dashboard>("/admin/dashboard")
      .then(setDados)
      .catch((e) => setErro(e.message));
  }, []);

  if (!dados && !erro) return <Carregando texto="Carregando o painel da rede..." />;

  return (
    <>
      <TituloPagina titulo="Painel da Rede" subtitulo="Situação da rede Circula Book agora" />
      {erro && <Erro mensagem={erro} />}

      {dados &&
        GRUPOS.map((g) => (
          <SectionCard key={g.titulo} titulo={g.titulo}>
            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
              {g.itens.map((k) => {
                const valor = dados[k.chave] as number;
                const destaque = k.alerta && valor > 0;
                return (
                  <Link key={k.chave} to={k.to} data-testid={`kpi-${k.chave}`}>
                    <Card className={`p-4 h-full hover:shadow-md ${destaque ? "border-[#d32f2f]" : ""}`}>
                      <p className="text-[13px] text-[#66707d]">{k.rotulo}</p>
                      <p
                        data-testid="valor"
                        className={`mt-1 text-[28px] font-bold ${destaque ? "text-[#d32f2f]" : "text-[#2c3e50]"}`}
                      >
                        {valor}
                      </p>
                      {k.detalhe && <p className="text-[12px] text-[#66707d]">{k.detalhe(dados)}</p>}
                    </Card>
                  </Link>
                );
              })}
            </div>
          </SectionCard>
        ))}

      {dados && (
        <SectionCard titulo="Exemplares por situação">
          <div className="rounded-[10px] border border-[#e0e0e0] overflow-hidden">
            <table className="w-full text-left text-[14px]" data-testid="exemplares-por-status">
              <thead>
                <tr className="border-b border-[#e0e0e0] bg-[#f5f7fa]">
                  <th className="px-4 py-3 text-[12px] font-semibold uppercase tracking-wide text-[#66707d]">Situação</th>
                  <th className="px-4 py-3 text-[12px] font-semibold uppercase tracking-wide text-[#66707d] text-right">Exemplares</th>
                </tr>
              </thead>
              <tbody>
                {dados.exemplaresPorStatus.map((s) => (
                  <tr key={s.status} className="border-b border-[#eceff3] last:border-b-0">
                    <td className="px-4 py-3">{s.rotulo}</td>
                    <td className="px-4 py-3 text-right font-semibold">{s.total}</td>
                  </tr>
                ))}
                <tr className="bg-[#f5f7fa]">
                  <td className="px-4 py-3 font-semibold">Total</td>
                  <td className="px-4 py-3 text-right font-bold">{dados.exemplaresTotal}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </SectionCard>
      )}
    </>
  );
}
