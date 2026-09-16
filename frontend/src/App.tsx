/**
 * Roteador do aplicativo.
 *
 * Optei por um roteamento simples baseado em estado, em vez de react-router,
 * para não adicionar dependência nova ao projeto: o Circula Book tem 8 telas
 * com navegação linear, e isso mantém o `npm install` do grupo intacto.
 * Se no futuro precisarem de URLs compartilháveis e histórico do navegador,
 * a troca para react-router-dom fica isolada neste arquivo.
 */
import { useState } from "react";
import Layout from "./components/Layout";
import type { Perfil } from "./types";

import BuscaLivros, { type Filtros } from "./pages/BuscaLivros";
import ResultadosBusca from "./pages/ResultadosBusca";
import DetalhesLivro from "./pages/DetalhesLivro";
import RegistrarEmprestimo from "./pages/RegistrarEmprestimo";
import ReservarLivro from "./pages/ReservarLivro";
import RegistrarDevolucao from "./pages/RegistrarDevolucao";
import CadastrarExemplar from "./pages/CadastrarExemplar";
import TransferenciasAdmin from "./pages/TransferenciasAdmin";

export type Rota =
  | { nome: "busca" }
  | { nome: "resultados"; filtros: Filtros }
  | { nome: "detalhesLivro"; livroId: number }
  | { nome: "reserva"; livroId: number; bibliotecaId?: number }
  | { nome: "emprestimo" }
  | { nome: "devolucao" }
  | { nome: "cadastrarExemplar" }
  | { nome: "transferencias" };

/** Ao trocar de perfil, cai na tela inicial daquele ator. */
const TELA_INICIAL: Record<Perfil, Rota> = {
  COMUM: { nome: "busca" },
  BIBLIOTECARIO: { nome: "emprestimo" },
  ADMIN: { nome: "transferencias" },
};

export default function App() {
  const [perfil, setPerfil] = useState<Perfil>("COMUM");
  const [rota, setRota] = useState<Rota>({ nome: "busca" });

  function trocarPerfil(novo: Perfil) {
    setPerfil(novo);
    setRota(TELA_INICIAL[novo]);
  }

  function renderizar() {
    switch (rota.nome) {
      case "busca":
        return <BuscaLivros navegar={setRota} />;
      case "resultados":
        return <ResultadosBusca filtros={rota.filtros} navegar={setRota} />;
      case "detalhesLivro":
        return <DetalhesLivro livroId={rota.livroId} navegar={setRota} />;
      case "reserva":
        return (
          <ReservarLivro
            livroId={rota.livroId}
            bibliotecaId={rota.bibliotecaId}
            navegar={setRota}
          />
        );
      case "emprestimo":
        return <RegistrarEmprestimo />;
      case "devolucao":
        return <RegistrarDevolucao />;
      case "cadastrarExemplar":
        return <CadastrarExemplar />;
      case "transferencias":
        return <TransferenciasAdmin />;
    }
  }

  return (
    <Layout
      perfil={perfil}
      rotaAtual={rota.nome}
      navegar={setRota}
      trocarPerfil={trocarPerfil}
    >
      {renderizar()}
    </Layout>
  );
}
