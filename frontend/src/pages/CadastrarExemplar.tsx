/**
 * TELA 7 — Cadastrar Livro/Exemplar (UC11 / UC20).
 * Dois modos: vincular a um título existente ou criar um título novo.
 * RN10 (só bibliotecário da casa) e RN11 (exemplar único) são validadas no backend.
 */
import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { Biblioteca, Categoria, Exemplar, Livro } from "../types";
import {
  AreaTexto, Botao, Campo, CardResumo, Callout, DuasColunas, Entrada, Erro,
  GrupoRadio, LinhaResumo, SectionCard, Selecao, Sucesso, TituloPagina, Trilha,
} from "../components/ui";

type Modo = "EXISTENTE" | "NOVO";
type Estado = "NOVO" | "BOM" | "USADO";

// Sem login, o bibliotecário é fixo: Carlos Lima (id 2), da Vila Isabel (id 1).
const BIBLIOTECARIO_LOGADO = 2;
const BIBLIOTECA_LOGADA = 1;

export default function CadastrarExemplar() {
  const [modo, setModo] = useState<Modo>("NOVO");
  const [livros, setLivros] = useState<Livro[]>([]);
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [bibliotecas, setBibliotecas] = useState<Biblioteca[]>([]);

  const [livroId, setLivroId] = useState<number | "">("");
  const [titulo, setTitulo] = useState("");
  const [autor, setAutor] = useState("");
  const [editora, setEditora] = useState("");
  const [isbn, setIsbn] = useState("");
  const [ano, setAno] = useState("");
  const [categoriaId, setCategoriaId] = useState<number | "">("");
  const [sinopse, setSinopse] = useState("");

  const [codigo, setCodigo] = useState("");
  const [estado, setEstado] = useState<Estado>("NOVO");

  const [erro, setErro] = useState("");
  const [sucesso, setSucesso] = useState("");
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    Promise.all([
      api.get<Livro[]>("/livros"),
      api.get<Categoria[]>("/categorias"),
      api.get<Biblioteca[]>("/bibliotecas"),
    ])
      .then(([l, c, b]) => {
        setLivros(l);
        setCategorias(c);
        setBibliotecas(b);
      })
      .catch((e) => setErro(e.message));
  }, []);

  const livroSelecionado = livros.find((l) => l.id === livroId);
  const nomeBiblioteca =
    bibliotecas.find((b) => b.id === BIBLIOTECA_LOGADA)?.nome ?? "Biblioteca Vila Isabel";
  const nomeCategoria =
    modo === "EXISTENTE"
      ? livroSelecionado?.categoria?.nome ?? "—"
      : categorias.find((c) => c.id === categoriaId)?.nome ?? "—";

  async function cadastrar() {
    setEnviando(true);
    setErro("");
    setSucesso("");
    try {
      const corpo =
        modo === "EXISTENTE"
          ? { livroId, codigoBarras: codigo, bibliotecaId: BIBLIOTECA_LOGADA, estadoConservacao: estado }
          : {
              titulo, autor, editora, isbn,
              anoPublicacao: ano ? Number(ano) : null,
              categoriaId: categoriaId || null,
              sinopse,
              codigoBarras: codigo,
              bibliotecaId: BIBLIOTECA_LOGADA,
              estadoConservacao: estado,
            };

      const ex = await api.post<Exemplar>(
        `/exemplares?bibliotecarioId=${BIBLIOTECARIO_LOGADO}`,
        corpo,
      );

      setSucesso(
        `Exemplar ${ex.codigoBarras ?? `#${ex.id}`} de "${ex.livro.titulo}" cadastrado em ` +
          `${ex.biblioteca.nome} com status ${ex.status}.` +
          (ex.status === "INDISPONIVEL"
            ? " Por ser o único exemplar do título na rede, ele nasce indisponível para empréstimo (RN11)."
            : ""),
      );

      // Limpa o formulário e recarrega a lista de títulos
      setCodigo("");
      setTitulo(""); setAutor(""); setEditora(""); setIsbn(""); setAno("");
      setCategoriaId(""); setSinopse(""); setLivroId("");
      api.get<Livro[]>("/livros").then(setLivros).catch(() => {});
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  const podeCadastrar =
    !enviando &&
    (modo === "EXISTENTE" ? !!livroId : titulo.trim() !== "" && autor.trim() !== "");

  return (
    <>
      <Trilha itens={[{ rotulo: "Painel da Biblioteca" }, { rotulo: "Cadastrar Exemplar" }]} />
      <TituloPagina
        titulo="Cadastrar Novo Exemplar"
        subtitulo={`${nomeBiblioteca} · UC11/UC20 - Cadastro de livro e exemplar`}
      />

      {erro && <Erro mensagem={erro} />}
      {sucesso && <Sucesso mensagem={sucesso} />}

      <DuasColunas
        esquerda={
          <>
            <SectionCard titulo="1. Selecionar ou cadastrar livro">
              <GrupoRadio<Modo>
                valor={modo}
                onChange={setModo}
                opcoes={[
                  { valor: "EXISTENTE", rotulo: "Livro já cadastrado" },
                  { valor: "NOVO", rotulo: "Novo título" },
                ]}
              />

              {modo === "EXISTENTE" && (
                <div className="mt-4">
                  <Campo label="Escolha o título já existente no catálogo">
                    <Selecao
                      value={livroId}
                      onChange={(e) => setLivroId(Number(e.target.value))}
                    >
                      <option value="">Selecione um livro...</option>
                      {livros.map((l) => (
                        <option key={l.id} value={l.id}>
                          {l.titulo} — {l.autor}
                        </option>
                      ))}
                    </Selecao>
                  </Campo>
                </div>
              )}
            </SectionCard>

            {modo === "NOVO" && (
              <SectionCard titulo="2. Dados do novo título">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                  <Campo label="Título">
                    <Entrada value={titulo} onChange={(e) => setTitulo(e.target.value)}
                             placeholder="Ex: Iracema" />
                  </Campo>
                  <Campo label="Autor">
                    <Entrada value={autor} onChange={(e) => setAutor(e.target.value)}
                             placeholder="Ex: José de Alencar" />
                  </Campo>
                  <Campo label="Editora">
                    <Entrada value={editora} onChange={(e) => setEditora(e.target.value)}
                             placeholder="Ex: Ática" />
                  </Campo>
                  <Campo label="ISBN">
                    <Entrada value={isbn} onChange={(e) => setIsbn(e.target.value)}
                             placeholder="Ex: 978-85-08-..." />
                  </Campo>
                  <Campo label="Ano de publicação">
                    <Entrada type="number" value={ano} onChange={(e) => setAno(e.target.value)}
                             placeholder="Ex: 1865" />
                  </Campo>
                  <Campo label="Categoria">
                    <Selecao value={categoriaId}
                             onChange={(e) => setCategoriaId(Number(e.target.value))}>
                      <option value="">Selecione...</option>
                      {categorias.map((c) => (
                        <option key={c.id} value={c.id}>{c.nome}</option>
                      ))}
                    </Selecao>
                  </Campo>
                </div>
                <div className="mt-5">
                  <Campo label="Sinopse">
                    <AreaTexto value={sinopse} onChange={(e) => setSinopse(e.target.value)}
                               placeholder="Breve resumo da obra..." />
                  </Campo>
                </div>
              </SectionCard>
            )}

            <SectionCard titulo={modo === "NOVO" ? "3. Dados do exemplar" : "2. Dados do exemplar"}>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                <Campo label="Código do exemplar (tombo)">
                  <Entrada value={codigo} onChange={(e) => setCodigo(e.target.value)}
                           placeholder="Ex: EX-01045" />
                </Campo>
                <Campo label="Biblioteca">
                  <Entrada value={`${nomeBiblioteca} (fixo)`} readOnly />
                </Campo>
              </div>
              <div className="mt-5">
                <p className="text-[13px] font-medium text-[#66707d] mb-2">
                  Estado de conservação
                </p>
                <GrupoRadio<Estado>
                  valor={estado}
                  onChange={setEstado}
                  opcoes={[
                    { valor: "NOVO", rotulo: "Novo" },
                    { valor: "BOM", rotulo: "Bom" },
                    { valor: "USADO", rotulo: "Usado" },
                  ]}
                />
              </div>
            </SectionCard>
          </>
        }
        direita={
          <>
            <Callout tipo="info" titulo="Cadastro de exemplar (RN10)">
              Um exemplar só pode ser cadastrado por um bibliotecário da biblioteca à qual
              pertencerá. O livro correspondente deve estar previamente cadastrado no sistema.
            </Callout>

            <Callout tipo="aviso" titulo="Exemplar único (RN11)">
              Se este for o único exemplar do título na rede, ele ficará com status
              indisponível para empréstimo imediato.
            </Callout>

            <CardResumo
              titulo="Resumo do cadastro"
              rodape={
                <Botao onClick={cadastrar} disabled={!podeCadastrar} className="w-full">
                  {enviando ? "Cadastrando..." : "📚 Cadastrar Exemplar"}
                </Botao>
              }
            >
              <LinhaResumo
                rotulo="Título"
                valor={modo === "EXISTENTE" ? livroSelecionado?.titulo ?? "—" : titulo || "—"}
              />
              <LinhaResumo rotulo="Categoria" valor={nomeCategoria} />
              <LinhaResumo rotulo="Biblioteca" valor={nomeBiblioteca} />
              <LinhaResumo rotulo="Código" valor={codigo || "—"} />
            </CardResumo>
          </>
        }
      />
    </>
  );
}
