package com.utfpr.finder.acesso;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record UsuarioAutenticado(long id, long organizacaoId, String nome, String login,
        String papel, long versaoAcesso, List<String> permissoes) implements UserDetails, Serializable {

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        var autoridades = new java.util.ArrayList<GrantedAuthority>();
        autoridades.add(new SimpleGrantedAuthority("ROLE_" + papel));
        permissoes.forEach(p -> autoridades.add(new SimpleGrantedAuthority(p)));
        return List.copyOf(autoridades);
    }
    @Override public String getPassword() { return ""; }
    @Override public String getUsername() { return login; }
}
