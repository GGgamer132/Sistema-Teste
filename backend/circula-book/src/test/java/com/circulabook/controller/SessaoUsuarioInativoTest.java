package com.circulabook.controller;

import com.circulabook.model.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Token emitido antes da desativação deixa de valer na hora (não espera as 8 h). */
class SessaoUsuarioInativoTest extends ApoioApiTest {

    @Test
    @DisplayName("Usuário desativado depois do login: o token passa a dar 401")
    void tokenDeUsuarioDesativado() throws Exception {
        Usuario ana = usuario("Ana Souza", "COMUM", null);
        String token = login(ana);
        chamar(HttpMethod.GET, "/api/auth/me", token, null).andExpect(status().isOk());

        ana.setAtivo(false);
        usuarioRepository.saveAndFlush(ana);
        chamar(HttpMethod.GET, "/api/auth/me", token, null).andExpect(status().isUnauthorized());
        chamar(HttpMethod.GET, "/api/livros", token, null).andExpect(status().isUnauthorized());

        ana.setAtivo(true);
        usuarioRepository.saveAndFlush(ana);
        chamar(HttpMethod.GET, "/api/auth/me", token, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Usuário removido do banco: o token dá 401")
    void tokenDeUsuarioInexistente() throws Exception {
        Usuario bruno = usuario("Bruno Alves", "COMUM", null);
        String token = login(bruno);
        usuarioRepository.delete(bruno);
        usuarioRepository.flush();
        chamar(HttpMethod.GET, "/api/auth/me", token, null).andExpect(status().isUnauthorized());
    }
}
