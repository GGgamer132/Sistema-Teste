/**
 * Barra azul do topo, presente em todas as telas.
 * O menu muda conforme o perfil do usuário logado e o item da página atual
 * fica em destaque.
 */
import { Link, NavLink, useLocation } from "react-router-dom";
import type { Perfil } from "../types";
import { NOME_PERFIL, TELA_INICIAL, useAuth, useUsuarioLogado } from "../auth/contexto";

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
    { rotulo: "Empréstimos", to: "/biblioteca" },
    { rotulo: "Registrar Empréstimo", to: "/biblioteca/emprestimo" },
    { rotulo: "Registrar Devolução", to: "/biblioteca/devolucao" },
    { rotulo: "Cadastrar Exemplares", to: "/biblioteca/exemplares" },
    { rotulo: "Acervo", to: "/biblioteca/acervo" },
    { rotulo: "Transferências", to: "/biblioteca/transferencias" },
    { rotulo: "Reservas", to: "/biblioteca/reservas" },
  ],
  ADMIN: [
    { rotulo: "Transferências", to: "/admin/transferencias" },
    { rotulo: "Catálogo", to: "/admin/catalogo" },
    { rotulo: "Categorias", to: "/admin/categorias" },
    { rotulo: "Bibliotecas", to: "/admin/bibliotecas" },
    { rotulo: "Bibliotecários", to: "/admin/bibliotecarios" },
  ],
};

const ICONE: Record<Perfil, string> = {
  COMUM: "👤",
  BIBLIOTECARIO: "🧑‍💼",
  ADMIN: "🛡️",
};

export default function Header() {
  const { pathname } = useLocation();
  const { sair } = useAuth();
  const usuario = useUsuarioLogado();
  const perfil = usuario.perfil;

  return (
    <header className="bg-[#1976d2] text-white">
      <div className="h-[76px] px-10 flex items-center justify-between gap-6">
        <div className="flex items-center gap-9 shrink-0">
          <Link to={TELA_INICIAL[perfil]} className="text-[20px] font-bold shrink-0">
            📚 Circula Book
          </Link>

          <nav className="hidden md:flex items-center gap-6">
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

        {/* A identificação encolhe (com reticências) para nunca cobrir o menu */}
        <div className="flex items-center justify-end gap-4 min-w-0 flex-1">
          <span
            className="hidden lg:block min-w-0 truncate text-[13px] font-medium"
            data-testid="identificacao"
            title={`${usuario.nome} · ${NOME_PERFIL[perfil]}${usuario.bibliotecaNome ? ` — ${usuario.bibliotecaNome}` : ""}`}
          >
            {ICONE[perfil]} {usuario.nome} · {NOME_PERFIL[perfil]}
            {usuario.bibliotecaNome ? ` — ${usuario.bibliotecaNome}` : ""}
          </span>

          <button
            type="button"
            onClick={sair}
            className="bg-white/15 border border-white/40 rounded-[8px] px-3 py-[6px]
                       text-[13px] text-white hover:bg-white/25"
          >
            Sair
          </button>
        </div>
      </div>
    </header>
  );
}
