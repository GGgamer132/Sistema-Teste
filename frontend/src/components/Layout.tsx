/** Moldura comum: header azul + área de conteúdo com fundo cinza. */
import type { ReactNode } from "react";
import type { Perfil } from "../types";
import type { Rota } from "../App";
import Header from "./Header";

export default function Layout({
  perfil,
  rotaAtual,
  navegar,
  trocarPerfil,
  children,
}: {
  perfil: Perfil;
  rotaAtual: Rota["nome"];
  navegar: (r: Rota) => void;
  trocarPerfil: (p: Perfil) => void;
  children: ReactNode;
}) {
  return (
    <div className="min-h-screen bg-[#f5f7fa]">
      <Header
        perfil={perfil}
        rotaAtual={rotaAtual}
        navegar={navegar}
        trocarPerfil={trocarPerfil}
      />
      <main className="max-w-[1440px] mx-auto px-10 py-8 flex flex-col gap-7">
        {children}
      </main>
    </div>
  );
}
