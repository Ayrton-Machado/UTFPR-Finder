package com.utfpr.finder.acesso.internal;

import com.utfpr.finder.acesso.AcessoFacade;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class AutenticacaoProvider implements AuthenticationProvider {
    private final AcessoFacade acesso;
    private final PasswordEncoder encoder;
    private final String hashAusente;
    AutenticacaoProvider(AcessoFacade acesso, PasswordEncoder encoder) {
        this.acesso = acesso; this.encoder = encoder;
        this.hashAusente = encoder.encode(UUID.randomUUID().toString());
    }
    @Override public Authentication authenticate(Authentication tentativa) {
        String senha = tentativa.getCredentials() instanceof String s ? s : "";
        var credenciais = acesso.buscarCredenciais(tentativa.getName());
        boolean confere = senha.getBytes(StandardCharsets.UTF_8).length <= 72
            && encoder.matches(senha, credenciais.map(AcessoFacade.Credenciais::hash).orElse(hashAusente));
        if (!confere || credenciais.isEmpty()) throw new BadCredentialsException("Credenciais inválidas.");
        var usuario = credenciais.get().usuario();
        return UsernamePasswordAuthenticationToken.authenticated(usuario, null, usuario.getAuthorities());
    }
    @Override public boolean supports(Class<?> tipo) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(tipo);
    }
}
