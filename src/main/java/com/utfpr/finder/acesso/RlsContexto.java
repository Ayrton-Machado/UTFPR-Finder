package com.utfpr.finder.acesso;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class RlsContexto {
    private final JdbcClient jdbc;
    public RlsContexto(JdbcClient jdbc) { this.jdbc = jdbc; }

    public UsuarioAutenticado aplicar() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario))
            throw new IllegalStateException("Usuário não autenticado.");
        jdbc.sql("select set_config('app.organizacao_id', ?, true), set_config('app.usuario_id', ?, true)")
            .param(Long.toString(usuario.organizacaoId())).param(Long.toString(usuario.id())).query().singleRow();
        return usuario;
    }
}
