// Espelha os DTOs e entidades devolvidos pelo backend Spring Boot.

export type Perfil = "COMUM" | "BIBLIOTECARIO" | "ADMIN";

export interface Usuario {
  id: number;
  nome: string;
  email: string;
  tipo: Perfil;
  biblioteca?: Biblioteca | null;
  ativo: boolean;
  bloqueadoAte?: string | null;
}

export interface Biblioteca {
  id: number;
  nome: string;
  endereco?: string;
  email?: string;
  telefone?: string;
  ativa?: boolean;
}

export interface Categoria {
  id: number;
  nome: string;
  descricao?: string;
}

export interface Livro {
  id: number;
  titulo: string;
  autor: string;
  isbn?: string;
  editora?: string;
  anoPublicacao?: number;
  sinopse?: string;
  categoria?: Categoria | null;
}

export interface Exemplar {
  id: number;
  livro: Livro;
  biblioteca: Biblioteca;
  codigoBarras?: string;
  status:
    | "DISPONIVEL"
    | "EMPRESTADO"
    | "RESERVADO"
    | "EM_TRANSFERENCIA"
    | "INDISPONIVEL";
  estadoConservacao?: string;
}

export interface Disponibilidade {
  bibliotecaId: number;
  bibliotecaNome: string;
  endereco?: string;
  totalExemplares: number;
  disponiveis: number;
}

/** DTO da busca (telas 2 e 3). */
export interface LivroResumo {
  id: number;
  titulo: string;
  autor: string;
  editora?: string;
  isbn?: string;
  anoPublicacao?: number;
  categoria?: string;
  sinopse?: string;
  totalExemplares: number;
  disponiveis: number;
  bibliotecasComDisponivel: number;
  naFilaDeEspera: number;
  situacao: "DISPONIVEL" | "AGUARDANDO" | "INDISPONIVEL";
  disponibilidade?: Disponibilidade[];
}

export interface Emprestimo {
  id: number;
  exemplar: Exemplar;
  usuario: Usuario;
  biblioteca: Biblioteca;
  dataEmprestimo: string;
  dataPrevDevolucao: string;
  dataDevolucao?: string | null;
  status: "ATIVO" | "DEVOLVIDO" | "ATRASADO";
}

export interface Reserva {
  id: number;
  livro: Livro;
  usuario: Usuario;
  /** Biblioteca onde o usuário está na fila (de onde o exemplar sai). */
  bibliotecaFila: Biblioteca;
  /** Biblioteca onde o usuário vai retirar (igual à da fila = sem transferência). */
  bibliotecaDestino: Biblioteca;
  /** Exemplar separado quando o 1º da fila é atendido. */
  exemplar?: Exemplar | null;
  dataReserva: string;
  dataExpiracao: string;
  status:
    | "PENDENTE"
    | "AGUARDANDO_TRANSFERENCIA"
    | "DISPONIVEL"
    | "RETIRADA"
    | "CANCELADA"
    | "EXPIRADA";
}

export interface SolicitacaoTransferencia {
  id: number;
  /** Título sempre disponível, mesmo sem exemplar vinculado. */
  livro: Livro;
  /** Nulo enquanto a transferência "aguarda exemplar". */
  exemplar: Exemplar | null;
  bibliotecaOrigem: Biblioteca;
  bibliotecaDestino: Biblioteca;
  solicitante: Usuario;
  aprovador?: Usuario | null;
  reserva?: Reserva | null;
  status:
    | "PENDENTE"
    | "APROVADA"
    | "EM_TRANSITO"
    | "CONCLUIDA"
    | "REJEITADA"
    | "CANCELADA";
  dataSolicitacao: string;
  dataConclusao?: string | null;
  observacoes?: string;
}

/** Resposta de /api/emprestimos/situacao/{id} — alimenta os alertas da tela 4. */
export interface SituacaoUsuario {
  usuarioId: number;
  nome: string;
  email: string;
  emprestimosAtivos: number;
  limite: number;
  bloqueado: boolean;
  bloqueadoAte?: string | null;
  apto: boolean;
  motivo: string;
}

/** Resposta de GET /api/transferencias/resumo. */
export interface ResumoTransferencias {
  pendentes: number;
  aguardandoExemplar: number;
  emTransito: number;
  concluidas: number;
  rejeitadas: number;
}
