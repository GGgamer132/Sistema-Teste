# Circula Book

Gestão de uma rede de bibliotecas comunitárias (CEFET/RJ). O leitor encontra um livro em qualquer unidade, entra na fila de uma biblioteca e pode retirá-lo em outra por transferência. A especificação completa está em `CONTEXTO_SISTEMA_CIRCULA_BOOK_v2.md`.

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

## Gravar o vídeo de demonstração

1. **Reinicie o backend antes de gravar** (o roteiro parte do seed limpo): `cd frontend && npm run backend:reiniciar`.
2. Siga o roteiro da seção 9.0 da especificação, na ordem.
3. Para ensaiar vendo o navegador, com pausas: `DEMO_LENTA=1 npm run test:e2e:demo` (PowerShell: `$env:DEMO_LENTA=1; npm run test:e2e:demo`).

## Testes

```
cd backend/circula-book && mvn test     # inclui o seed real no PostgreSQL (schema seed_teste)
cd frontend && npm run test:e2e         # cada spec roda num backend recém-reiniciado
```
