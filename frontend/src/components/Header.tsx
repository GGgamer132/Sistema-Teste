/**
 * Barra azul do topo, presente em todas as telas.
 * O menu muda conforme o perfil e o item da página atual fica em destaque.
 */
import { Link, NavLink, useLocation } from "react-router-dom";
import type { Perfil } from "../types";

interface ItemMenu {
  rotulo: string;
  to: string;
  /** Prefixos de URL que também devem marcar este item como ativo. */
  tambem?: string[];
}

const MENUS: Record<Perfil, ItemMenu[]> = {
  COMUM: [
    { rotulo: "Buscar Livros", to: "/", tambem: ["/resultados", "/livro"] },
    { rotulo: "Meus Empréstimos", to: "/meus-emprestimos" },
    { rotulo: "Minhas Reservas", to: "/minhas-reservas" },
    { rotulo: "Meu Histórico", to: "/historico" },
  ],
  BIBLIOTECARIO: [
    { rotulo: "Painel da Biblioteca", to: "/biblioteca" },
    { rotulo: "Registrar Empréstimo", to: "/biblioteca/emprestimo" },
    { rotulo: "Registrar Devolução", to: "/biblioteca/devolucao" },
    { rotulo: "Cadastrar Exemplar", to: "/biblioteca/exemplares" },
  ],
  ADMIN: [
    { rotulo: "Dashboard", to: "/admin" },
    { rotulo: "Solicitações de Transferência", to: "/admin/transferencias" },
    { rotulo: "Catálogo de Livros", to: "/admin/catalogo" },
  ],
};

const IDENTIFICACAO: Record<Perfil, string> = {
  COMUM: "👤 Ana Souza · Usuário da Comunidade",
  BIBLIOTECARIO: "🧑‍💼 Carlos Lima · Bibliotecário — Biblioteca Vila Isabel",
  ADMIN: "🛡️ Roberto Dias · Administrador da Rede",
};

export default function Header({
  perfil,
  trocarPerfil,
}: {
  perfil: Perfil;
  trocarPerfil: (p: Perfil) => void;
}) {
  const { pathname } = useLocation();

  return (
    <header className="bg-[#1976d2] text-white">
      <div className="h-[76px] px-10 flex items-center justify-between gap-6">
        <div className="flex items-center gap-9 min-w-0">
          <Link to="/" className="text-[20px] font-bold shrink-0">
            📚 Circula Book
          </Link>

          <nav className="hidden md:flex items-center gap-7 min-w-0">
            {MENUS[perfil].map((item) => {
              const ativoExtra = item.tambem?.some((p) =>
                pathname.startsWith(p),
              );
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end
                  className={({ isActive }) =>
                    `text-[14px] whitespace-nowrap pb-1 border-b-2 ${
                      isActive || ativoExtra
                        ? "font-semibold border-white"
                        : "font-normal opacity-90 border-transparent hover:opacity-100"
                    }`
                  }
                >
                  {item.rotulo}
                </NavLink>
              );
            })}
          </nav>
        </div>

        <div className="flex items-center gap-4 shrink-0">
          <span className="hidden lg:inline text-[13px] font-medium">
            {IDENTIFICACAO[perfil]}
          </span>

          {/*
            Trocador de perfil: substitui o login, que ainda não existe.
            Permite demonstrar as telas dos três atores sem autenticação.
          */}
          <select
            aria-label="Trocar perfil de demonstração"
            value={perfil}
            onChange={(e) => trocarPerfil(e.target.value as Perfil)}
            className="bg-white/15 border border-white/40 rounded-[8px] px-3 py-[6px]
                       text-[13px] text-white"
          >
            <option className="text-[#2c3e50]" value="COMUM">
              Usuário
            </option>
            <option className="text-[#2c3e50]" value="BIBLIOTECARIO">
              Bibliotecário
            </option>
            <option className="text-[#2c3e50]" value="ADMIN">
              Admin
            </option>
          </select>
        </div>
      </div>
    </header>
  );
}
