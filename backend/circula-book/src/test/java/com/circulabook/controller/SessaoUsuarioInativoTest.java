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
        Usuario u1 = usuario("UsuÃ¡rio 1", "COMUM", null);
        String token = login(u1);
        chamar(HttpMethod.GET, "/api/auth/me", token, null).andExpect(status().isOk());

        u1.setAtivo(false);
        usuarioRepository.saveAndFlush(u1);
        chamar(HttpMethod.GET, "/api/auth/me", token, null).andExpect(status().isUnauthorized());
        chamar(HttpMethod.GET, "/api/livros", token, null).andExpect(status().isUnauthorized());

        u1.setAtivo(true);
        usuarioRepository.saveAndFlush(u1);
        chamar(HttpMethod.GET, "/api/auth/me", token, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Usuário removido do banco: o token dá 401")
    void tokenDeUsuarioInexistente() throws Exception {
        Usuario u2 = usuario("UsuÃ¡rio 2", "COMUM", null);
        String token = login(u2);
        usuarioRepository.delete(u2);
        usuarioRepository.flush();
        chamar(HttpMethod.GET, "/api/auth/me", token, null).andExpect(status().isUnauthorized());
    }
}
