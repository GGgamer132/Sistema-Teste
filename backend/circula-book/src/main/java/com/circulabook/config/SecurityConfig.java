package com.circulabook.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.circulabook.repository.UsuarioRepository;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security stateless + JWT HS256.
 * As regras abaixo aplicam a matriz de autorização (seção 2.3 do CONTEXTO)
 * aos endpoints existentes. Recortes por dono/biblioteca ficam nos controllers.
 */
@Configuration
public class SecurityConfig {

    private static final String COMUM = "COMUM";
    private static final String BIBLIOTECARIO = "BIBLIOTECARIO";
    private static final String ADMIN = "ADMIN";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/cadastro").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()

                // Catálogo: leitura para todos os perfis; criar/editar/desativar só o Admin (RN18)
                .requestMatchers(HttpMethod.GET, "/api/bibliotecas/todas").hasRole(ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/livros/**", "/api/bibliotecas/**",
                                 "/api/categorias/**", "/api/exemplares/**").authenticated()
                .requestMatchers("/api/livros/**", "/api/categorias/**", "/api/bibliotecas/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/exemplares/**").hasRole(BIBLIOTECARIO)
                .requestMatchers(HttpMethod.PATCH, "/api/exemplares/*/indisponivel",
                                 "/api/exemplares/*/reativar").hasRole(BIBLIOTECARIO)

                // Reservas: criar/cancelar/ver as próprias é do usuário comum
                .requestMatchers(HttpMethod.GET, "/api/reservas/posicao/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/reservas/usuario/**").hasRole(COMUM)
                .requestMatchers(HttpMethod.POST, "/api/reservas").hasRole(COMUM)
                .requestMatchers(HttpMethod.PATCH, "/api/reservas/*/cancelar").hasRole(COMUM)
                .requestMatchers("/api/reservas/**").hasRole(ADMIN)

                // Empréstimos: registrar/devolver só no balcão; listagens recortadas no controller
                .requestMatchers(HttpMethod.POST, "/api/emprestimos/registrar",
                                 "/api/emprestimos/devolver").hasRole(BIBLIOTECARIO)
                .requestMatchers(HttpMethod.GET, "/api/emprestimos/ativos").hasAnyRole(BIBLIOTECARIO, ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/emprestimos/situacao/**").hasAnyRole(COMUM, BIBLIOTECARIO)
                .requestMatchers(HttpMethod.GET, "/api/emprestimos", "/api/emprestimos/usuario/**").authenticated()
                .requestMatchers("/api/emprestimos/**").denyAll()

                // Usuários: busca do balcão (bibliotecário vê só COMUM); o resto é do Admin
                .requestMatchers(HttpMethod.GET, "/api/usuarios/busca", "/api/usuarios/tipo/**")
                    .hasAnyRole(BIBLIOTECARIO, ADMIN)
                .requestMatchers("/api/usuarios/**").hasRole(ADMIN)

                // Transferências: chegada é do bibliotecário do destino; decisão e avulsa do Admin
                .requestMatchers(HttpMethod.PATCH, "/api/transferencias/*/confirmar-chegada").hasRole(BIBLIOTECARIO)
                .requestMatchers(HttpMethod.GET, "/api/transferencias").hasAnyRole(COMUM, ADMIN)
                .requestMatchers("/api/transferencias/**").hasRole(ADMIN)

                .requestMatchers("/api/historico/**").hasRole(ADMIN)
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o
                .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(naoAutenticado())
                .accessDeniedHandler(acessoNegado()))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(naoAutenticado())
                .accessDeniedHandler(acessoNegado()));
        return http.build();
    }

    /** Claim "perfil" vira a role (ROLE_COMUM, ROLE_BIBLIOTECARIO, ROLE_ADMIN). */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter perfis = new JwtGrantedAuthoritiesConverter();
        perfis.setAuthoritiesClaimName("perfil");
        perfis.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(perfis);
        return conv;
    }

    private AuthenticationEntryPoint naoAutenticado() {
        return (req, resp, ex) -> escrever(resp, HttpServletResponse.SC_UNAUTHORIZED,
            "Sessão inválida ou expirada. Faça login novamente.");
    }

    private AccessDeniedHandler acessoNegado() {
        return (req, resp, ex) -> escrever(resp, HttpServletResponse.SC_FORBIDDEN,
            "Acesso negado para o seu perfil.");
    }

    private static void escrever(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        resp.setContentType("text/plain;charset=UTF-8");
        resp.getWriter().write(msg);
    }

    @Bean
    public SecretKey jwtSecretKey(@Value("${circulabook.jwt.secret}") String segredo) {
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 caracteres.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /**
     * Além da assinatura e da validade, o token só vale se o usuário ainda existe e
     * está ativo: desativar alguém derruba a sessão na hora, sem esperar as 8 h do token.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, UsuarioRepository usuarioRepository) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
            .macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> usuarioAtivo = jwt -> {
            boolean ativo;
            try {
                ativo = usuarioRepository.findById(Long.valueOf(jwt.getSubject()))
                    .map(u -> Boolean.TRUE.equals(u.getAtivo()))
                    .orElse(false);
            } catch (NumberFormatException e) {
                ativo = false;
            }
            return ativo
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                    "Usuário inativo ou inexistente.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), usuarioAtivo));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
