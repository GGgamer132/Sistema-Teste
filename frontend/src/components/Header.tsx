/**
 * Barra azul do topo, presente nas 8 telas.
 *
 * O menu muda conforme o perfil ativo, exatamente como no Figma:
 *  - COMUM         -> Buscar Livros / Meus Empréstimos / Minhas Reservas / Meu Histórico
 *  - BIBLIOTECARIO -> Painel / Registrar Empréstimo / Devolução / Transferência / Cadastrar
 *  - ADMIN         -> Dashboard / Solicitações / Catálogo / Bibliotecas / Relatórios
 */
import type { Perfil } from "../types";
import type { Rota } from "../App";

interface ItemMenu {
  rotulo: string;
  rota: Rota["nome"];
}

const MENUS: Record<Perfil, ItemMenu[]> = {
  COMUM: [
    { rotulo: "Buscar Livros", rota: "busca" },
    { rotulo: "Meus Empréstimos", rota: "busca" },
    { rotulo: "Minhas Reservas", rota: "busca" },
    { rotulo: "Meu Histórico", rota: "busca" },
  ],
  BIBLIOTECARIO: [
    { rotulo: "Painel da Biblioteca", rota: "busca" },
    { rotulo: "Registrar Empréstimo", rota: "emprestimo" },
    { rotulo: "Registrar Devolução", rota: "devolucao" },
    { rotulo: "Cadastrar Exemplar", rota: "cadastrarExemplar" },
  ],
  ADMIN: [
    { rotulo: "Dashboard", rota: "busca" },
    { rotulo: "Solicitações de Transferência", rota: "transferencias" },
    { rotulo: "Catálogo de Livros", rota: "busca" },
  ],
};

const IDENTIFICACAO: Record<Perfil, string> = {
  COMUM: "👤 Ana Souza · Usuário da Comunidade",
  BIBLIOTECARIO: "🧑‍💼 Carlos Lima · Bibliotecário — Biblioteca Vila Isabel",
  ADMIN: "🛡️ Roberto Dias · Administrador da Rede",
};

export default function Header({
  perfil,
  rotaAtual,
  navegar,
  trocarPerfil,
}: {
  perfil: Perfil;
  rotaAtual: Rota["nome"];
  navegar: (r: Rota) => void;
  trocarPerfil: (p: Perfil) => void;
}) {
  return (
    <header className="bg-[#1976d2] text-white">
      <div className="h-[76px] px-10 flex items-center justify-between gap-6">
        <div className="flex items-center gap-9 min-w-0">
          <button
            onClick={() => navegar({ nome: "busca" })}
            className="text-[20px] font-bold shrink-0"
          >
            📚 Circula Book
          </button>

          <nav className="hidden md:flex items-center gap-7 min-w-0">
            {MENUS[perfil].map((item) => (
              <button
                key={item.rotulo}
                onClick={() => navegar({ nome: item.rota } as Rota)}
                className={`text-[14px] whitespace-nowrap ${
                  item.rota === rotaAtual ? "font-semibold" : "font-normal opacity-90"
                }`}
              >
                {item.rotulo}
              </button>
            ))}
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
            <option className="text-[#2c3e50]" value="COMUM">Usuário</option>
            <option className="text-[#2c3e50]" value="BIBLIOTECARIO">Bibliotecário</option>
            <option className="text-[#2c3e50]" value="ADMIN">Admin</option>
          </select>
        </div>
      </div>
    </header>
  );
}
