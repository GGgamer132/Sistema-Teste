/**
 * Roteador do aplicativo (react-router-dom).
 * Cada tela tem uma URL própria: o F5 mantém a tela e o botão voltar do
 * navegador funciona. Cada grupo de rotas exige o perfil correspondente.
 */
import { Navigate, Route, Routes } from "react-router-dom";
import RotaProtegida from "./auth/RotaProtegida";
import { TELA_INICIAL, useAuth } from "./auth/contexto";

import Login from "./pages/Login";
import Cadastro from "./pages/Cadastro";

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

export default function App() {
  const { usuario } = useAuth();

  return (
    <Routes>
      {/* ── Acesso (público) ── */}
      <Route path="/login" element={<Login />} />
      <Route path="/cadastro" element={<Cadastro />} />

      {/* ── Usuário da comunidade ── */}
      <Route element={<RotaProtegida perfis={["COMUM"]} />}>
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
      </Route>

      {/* ── Bibliotecário ── */}
      <Route element={<RotaProtegida perfis={["BIBLIOTECARIO"]} />}>
        <Route path="/biblioteca" element={<EmprestimosBiblioteca />} />
        <Route
          path="/biblioteca/emprestimo"
          element={<RegistrarEmprestimo />}
        />
        <Route path="/biblioteca/devolucao" element={<RegistrarDevolucao />} />
        <Route path="/biblioteca/exemplares" element={<CadastrarExemplar />} />
      </Route>

      {/* ── Administrador ── */}
      <Route element={<RotaProtegida perfis={["ADMIN"]} />}>
        <Route
          path="/admin"
          element={<EmConstrucao titulo="Dashboard da Rede" />}
        />
        <Route path="/admin/transferencias" element={<TransferenciasAdmin />} />
        <Route
          path="/admin/catalogo"
          element={<EmConstrucao titulo="Catálogo de Livros" />}
        />
      </Route>

      {/* URL desconhecida volta para o início do perfil (ou para o login) */}
      <Route
        path="*"
        element={
          <Navigate to={usuario ? TELA_INICIAL[usuario.perfil] : "/login"} replace />
        }
      />
    </Routes>
  );
}
