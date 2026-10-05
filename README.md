# Circula Book 📚

Sistema de Gestão de Rede de Bibliotecas Comunitárias desenvolvido como
projeto da disciplina de Projeto e Construção de Sistemas — CEFET/RJ 2026.2.

## Sobre esta versão

Esta é a **versão reduzida** do Circula Book, preparada para avaliação. Ela contém
apenas estes fluxos:

1. **Login** com os usuários já cadastrados na base;
2. **Busca de livros** em todo o acervo da rede;
3. **Detalhes do livro**, com a disponibilidade em cada biblioteca;
4. **Reserva** (fila de espera por biblioteca, com retirada na mesma biblioteca);
5. **Minhas reservas**, onde o usuário acompanha e cancela as próprias reservas;
6. **Empréstimo** registrado pelo bibliotecário;
7. **Devolução** registrada pelo bibliotecário, que atende automaticamente o 1º da fila.

## Documentação

- [Manual do Usuário](MANUAL_DO_USUARIO.md): como usar cada tela, regras e passo a passo com o seed.
- [Manual do Desenvolvedor](MANUAL_DO_DESENVOLVEDOR.md): arquitetura, instalação, modelo de dados e API.

## Perfis de usuário

| Perfil            | O que faz                                                      |
| ----------------- | -------------------------------------------------------------- |
| **Usuário comum** | Busca livros, vê os detalhes, reserva quando não há exemplar livre e acompanha/cancela as próprias reservas |
| **Bibliotecário** | Registra empréstimos e devoluções na própria biblioteca        |

## Tecnologias utilizadas

- **Backend**: Java 17 + Spring Boot 4.1 (Spring Security com JWT, Spring Data JPA)
- **Banco de dados**: PostgreSQL
- **Frontend**: React 19 com TypeScript e Vite (Tailwind CSS, React Router)
- **Build**: Maven (backend) e npm (frontend)

## Pré-requisitos

- Java 17 ou superior
- Maven 3.6.3 ou superior
- PostgreSQL 15 ou superior
- Node.js 20.19+ (ou 22.12+) com npm

## Como rodar

Requer PostgreSQL local com o banco `circula_book_db` (usuário `circula_user`, senha `circula_senha_123`).

```
cd backend/circula-book && mvn spring-boot:run     # http://localhost:8080
cd frontend && npm install && npm run dev          # http://localhost:5173
```

O backend recria o banco a cada início e carrega o seed de demonstração (`data.sql`). Tudo o que for feito pela interface se perde ao reiniciar.

## Credenciais de demonstração

Senha de todos: `senha123`.

| Perfil        | Nome                                 | E-mail                                    |
| ------------- | ------------------------------------ | ----------------------------------------- |
| Bibliotecário | Bibliotecário Central                | `bibliotecariocentral@circulabook.com`    |
| Bibliotecário | Bibliotecário Vila Isabel            | `bibliotecariovilaisabel@circulabook.com` |
| Usuário       | Usuário 1 (sem nenhum registro)      | `usuario1@circulabook.com`                |
| Usuário       | Usuário 2 (3 empréstimos, 1 atrasado) | `usuario2@circulabook.com`               |
| Usuário       | Usuário 3 (reserva pronta e reserva na fila) | `usuario3@circulabook.com`        |

## Equipe

| Nome            | Matrícula  |
| --------------- | ---------- |
| João Kongevold  | 2311996BCC |
| Milena Soares   | 2312314BCC |
| Otavio Medeiros | 2417852BCC |
| Sarah Campos    | 2311893BCC |

## Repositório

https://github.com/marceloareas/CirculaBook
