package com.utfpr.finder.acesso.internal;

import com.utfpr.finder.acesso.AcessoFacade;
import com.utfpr.finder.acesso.UsuarioAutenticado;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
class SegurancaConfig {
    @Bean PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }

    @Bean
    @Profile("!bootstrap & !seed")
    SecurityFilterChain filtros(HttpSecurity http, AutenticacaoProvider provider, AcessoFacade acesso) throws Exception {
        http.authenticationProvider(provider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/error").permitAll()
                .anyRequest().authenticated())
            .formLogin(login -> login.loginPage("/login").loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true).failureUrl("/login?erro").permitAll())
            .logout(logout -> logout.logoutUrl("/logout").invalidateHttpSession(true)
                .deleteCookies("JSESSIONID").logoutSuccessUrl("/login?logout"))
            .requestCache(cache -> cache.disable())
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; style-src 'self'; img-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
                .frameOptions(frame -> frame.deny()));
        http.addFilterBefore(new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado usuario) {
                    if (!acesso.sessaoValida(usuario)) {
                        request.getSession().invalidate(); response.sendRedirect("/login?sessao"); return;
                    }
                    MDC.put("usuario_id", Long.toString(usuario.id()));
                    MDC.put("organizacao_id", Long.toString(usuario.organizacaoId()));
                }
                try { chain.doFilter(request, response); }
                finally { MDC.remove("usuario_id"); MDC.remove("organizacao_id"); }
            }
        }, AuthorizationFilter.class);
        return http.build();
    }
}
