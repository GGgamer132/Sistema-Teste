# Circula Book 📚

Sistema de Gestão de Rede de Bibliotecas Comunitárias desenvolvido como
projeto da disciplina de Projeto e Construção de Sistemas — CEFET/RJ 2026.2.

## Sobre o sistema

O Circula Book resolve um problema real de bibliotecas comunitárias, pontos
de leitura e centros culturais: o acervo de cada unidade é pequeno e
independente, sem visão integrada da rede. Um livro pode estar disponível
em uma biblioteca enquanto outra, a poucos quilômetros, não o possui.

A plataforma conecta diferentes unidades, permitindo que usuários encontrem,
reservem e solicitem empréstimos de livros disponíveis em qualquer ponto
da rede — sem precisar ir de biblioteca em biblioteca fisicamente.

A especificação funcional completa está em `CONTEXTO_SISTEMA_CIRCULA_BOOK_v2.md`.

## Funcionalidades principais

- Busca de livros em todo o acervo da rede
- Reserva de exemplares em qualquer biblioteca participante
- Solicitação de transferência de exemplares entre unidades
- Gestão de empréstimos e devoluções
- Acompanhamento do histórico de circulação de cada exemplar
- Registro de demandas da comunidade para aquisição de novos títulos
- Relatórios para bibliotecários e administradores da rede

## Perfis de usuário

| Perfil | Descrição |
|--------|-----------|
| **Usuário Comum** | Busca livros, faz reservas, solicita transferências, acompanha empréstimos, registra interesse em livros |
| **Bibliotecário/Voluntário** | Gerencia o acervo da sua unidade, registra empréstimos e devoluções |
| **Administrador da Rede** | Gerencia todas as unidades, aprova transferências, analisa demandas de aquisição |

## Tecnologias utilizadas

- **Backend**: Java 17 + Spring Boot 4
- **Banco de dados**: PostgreSQL 15
- **Frontend**: React com TypeScript e Vite (Tailwind CSS, React Router)
- **Build**: Maven (backend) e npm (frontend)
- **Testes**: JUnit (backend) e Playwright (ponta a ponta)
- **Docker**: previsto (ainda não configurado)

## Pré-requisitos

- Java 17 ou superior
- Maven 3.6.0 ou superior
- PostgreSQL 15 ou superior
- Node.js com npm (frontend)

## Como rodar

Requer PostgreSQL local com o banco `circula_book_db` (usuário `circula_user`, senha `circula_senha_123`).

```
cd backend/circula-book && mvn spring-boot:run     # http://localhost:8080
cd frontend && npm install && npm run dev          # http://localhost:5173
```

O backend recria o banco a cada início e carrega o seed de demonstração (`data.sql`). Tudo o que for feito pela interface se perde ao reiniciar.

## Credenciais de demonstração

Senha de todos: `senha123`.

| Perfil        | Nome                      | E-mail                                    |
| ------------- | ------------------------- | ----------------------------------------- |
| Administrador | Admin                     | `admin@circulabook.com`                   |
| Bibliotecário | Bibliotecário Central     | `bibliotecariocentral@circulabook.com`    |
| Bibliotecário | Bibliotecário Vila Isabel | `bibliotecariovilaisabel@circulabook.com` |
| Bibliotecário | Bibliotecário Tijuca      | `bibliotecariotijuca@circulabook.com`     |
| Usuário       | Usuário 1 (sem histórico) | `usuario1@circulabook.com`                |
| Usuário       | Usuário 2 (3/3, atrasado) | `usuario2@circulabook.com`                |
| Usuário       | Usuário 3 (com reservas)  | `usuario3@circulabook.com`                |

## Gravação do vídeo de demonstração

1. **Reinicie o backend antes de gravar** (o roteiro parte do seed limpo): `cd frontend && npm run backend:reiniciar`.
2. Siga o roteiro da seção 9.0 da especificação, na ordem.
3. Para ensaiar vendo o navegador, com pausas: `DEMO_LENTA=1 npm run test:e2e:demo` (PowerShell: `$env:DEMO_LENTA=1; npm run test:e2e:demo`).

## Testes

```
cd backend/circula-book && mvn test     # inclui o seed real no PostgreSQL (schema seed_teste)
cd frontend && npm run test:e2e         # cada spec roda num backend recém-reiniciado
```

## Equipe

| Nome | Matrícula |
|------|-----------|
| João Kongevold   | 2311996BCC |
| Milena Soares    | 2312314BCC |
| Otávio Medeiros  | 2417852BCC |
| Sarah Campos     | 2311893BCC |

## Repositório

https://github.com/marceloareas/CirculaBook
