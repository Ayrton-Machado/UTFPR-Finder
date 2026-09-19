package com.utfpr.finder.empresas;

import com.utfpr.finder.acesso.RlsContexto;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmpresasFacade {
    private final JdbcClient jdbc;
    private final RlsContexto rls;

    public EmpresasFacade(JdbcClient jdbc, RlsContexto rls) { this.jdbc = jdbc; this.rls = rls; }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('perfil:editar_empresa')")
    public Optional<Perfil> perfilAtual() {
        var usuario = rls.aplicar();
        return jdbc.sql("""
            select nome_fantasia,descricao,areas_interesse,competencias_buscadas,site,linkedin
            from empresas.perfil where usuario_id=?
            """).param(usuario.id()).query((rs, n) -> new Perfil(rs.getString("nome_fantasia"),
                rs.getString("descricao"), itens(rs.getString("areas_interesse")),
                itens(rs.getString("competencias_buscadas")), rs.getString("site"), rs.getString("linkedin")))
            .optional();
    }

    @Transactional
    @PreAuthorize("hasAuthority('perfil:editar_empresa')")
    public void salvar(String nomeFantasia, String descricao, String areas, String competencias,
            String site, String linkedin) {
        var usuario = rls.aplicar();
        jdbc.sql("""
            update empresas.perfil set nome_fantasia=?,descricao=?,areas_interesse=?,competencias_buscadas=?,
                site=?,linkedin=?,atualizado_em=current_timestamp where usuario_id=?
            """).param(texto(nomeFantasia)).param(texto(descricao)).param(String.join(",", itens(areas)))
            .param(String.join(",", itens(competencias))).param(link(site)).param(link(linkedin))
            .param(usuario.id()).update();
    }

    private static String texto(String valor) { return valor == null ? "" : valor.strip(); }
    private static String link(String valor) {
        String limpo = texto(valor);
        return limpo.isBlank() || limpo.startsWith("https://") || limpo.startsWith("http://") ? limpo : "";
    }
    private static List<String> itens(String texto) {
        if (texto == null || texto.isBlank()) return List.of();
        return Arrays.stream(texto.split(",")).map(String::strip).filter(s -> !s.isBlank()).distinct().toList();
    }

    public record Perfil(String nomeFantasia, String descricao, List<String> areasInteresse,
            List<String> competenciasBuscadas, String site, String linkedin) {
        public String areasTexto() { return String.join(", ", areasInteresse); }
        public String competenciasTexto() { return String.join(", ", competenciasBuscadas); }
    }
}
