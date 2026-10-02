/**
 * Roteador do aplicativo (react-router-dom).
 * Cada tela tem uma URL própria: o F5 mantém a tela e o botão voltar do
 * navegador funciona.
 */
import { useState } from "react";
import { Navigate, Route, Routes, useNavigate } from "react-router-dom";
import Layout from "./components/Layout";
import type { Perfil } from "./types";

import BuscaLivros from "./pages/BuscaLivros";
import ResultadosBusca from "./pages/ResultadosBusca";
import DetalhesLivro from "./pages/DetalhesLivro";
import ReservarLivro from "./pages/ReservarLivro";
import RegistrarEmprestimo from "./pages/RegistrarEmprestimo";
import RegistrarDevolucao from "./pages/RegistrarDevolucao";
import CadastrarExemplar from "./pages/CadastrarExemplar";
import TransferenciasAdmin from "./pages/TransferenciasAdmin";
import EmConstrucao from "./pages/EmConstrucao";
import EmprestimosBiblioteca from "./pages/EmprestimosBiblioteca";
import MeusEmprestimos from "./pages/MeusEmprestimos";
import MinhasReservas from "./pages/MinhasReservas";

/** Ao trocar de perfil, cai na tela inicial daquele ator. */
const TELA_INICIAL: Record<Perfil, string> = {
  COMUM: "/",
  BIBLIOTECARIO: "/biblioteca",
  ADMIN: "/admin/transferencias", // trocar por /admin quando o Dashboard existir
};

export default function App() {
  const [perfil, setPerfil] = useState<Perfil>("COMUM");
  const navigate = useNavigate();

  function trocarPerfil(novo: Perfil) {
    setPerfil(novo);
    navigate(TELA_INICIAL[novo], { replace: true });
  }

  return (
    <Layout perfil={perfil} trocarPerfil={trocarPerfil}>
      <Routes>
        {/* ── Usuário da comunidade ── */}
        <Route path="/" element={<BuscaLivros />} />
        <Route path="/resultados" element={<ResultadosBusca />} />
        <Route path="/livro/:livroId" element={<DetalhesLivro />} />
        <Route path="/livro/:livroId/reservar" element={<ReservarLivro />} />
        <Route path="/meus-emprestimos" element={<MeusEmprestimos />} />
        <Route path="/minhas-reservas" element={<MinhasReservas />} />
        <Route
          path="/historico"
          element={<EmConstrucao titulo="Meu Histórico" />}
        />

        {/* ── Bibliotecário ── */}
        <Route path="/biblioteca" element={<EmprestimosBiblioteca />} />
        <Route
          path="/biblioteca/emprestimo"
          element={<RegistrarEmprestimo />}
        />
        <Route path="/biblioteca/devolucao" element={<RegistrarDevolucao />} />
        <Route path="/biblioteca/exemplares" element={<CadastrarExemplar />} />

        {/* ── Administrador ── */}
        <Route
          path="/admin"
          element={<EmConstrucao titulo="Dashboard da Rede" />}
        />
        <Route path="/admin/transferencias" element={<TransferenciasAdmin />} />
        <Route
          path="/admin/catalogo"
          element={<EmConstrucao titulo="Catálogo de Livros" />}
        />

        {/* URL desconhecida volta para o início */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  );
}
