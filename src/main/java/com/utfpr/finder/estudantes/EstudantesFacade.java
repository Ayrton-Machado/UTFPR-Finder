package com.utfpr.finder.estudantes;

import com.utfpr.finder.acesso.RlsContexto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstudantesFacade {
    public static final List<String> CAMPI = List.of("Apucarana", "Campo Mourão", "Cornélio Procópio", "Curitiba",
        "Dois Vizinhos", "Francisco Beltrão", "Guarapuava", "Londrina", "Medianeira", "Pato Branco",
        "Ponta Grossa", "Santa Helena", "Toledo");

    private final JdbcClient jdbc;
    private final RlsContexto rls;
    public EstudantesFacade(JdbcClient jdbc, RlsContexto rls) { this.jdbc = jdbc; this.rls = rls; }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('perfil:editar_proprio')")
    public Optional<Perfil> perfilAtual() {
        var usuario = rls.aplicar();
        return jdbc.sql("""
            select campus,curso,periodo,competencias,resumo,atividades,curriculo_url,linkedin,portfolio
            from estudantes.perfil where usuario_id=?
            """).param(usuario.id()).query((rs, n) -> perfil(rs)).optional();
    }

    @Transactional
    @PreAuthorize("hasAuthority('perfil:editar_proprio')")
    public void salvar(String campus, String curso, String periodo, String competencias, String resumo,
            String atividades, String curriculoUrl, String linkedin, String portfolio) {
        var usuario = rls.aplicar();
        jdbc.sql("""
            update estudantes.perfil set campus=?,curso=?,periodo=?,competencias=?,resumo=?,atividades=?,
                curriculo_url=?,linkedin=?,portfolio=?,atualizado_em=current_timestamp
            where usuario_id=?
            """).param(limpar(campus)).param(limpar(curso)).param(limpar(periodo))
            .param(String.join(",", competencias(competencias))).param(limpar(resumo)).param(limpar(atividades))
            .param(link(curriculoUrl)).param(link(linkedin)).param(link(portfolio)).param(usuario.id()).update();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('estudantes:buscar')")
    public List<Talento> buscar(String busca, String campus) {
        rls.aplicar();
        String termo = limpar(busca);
        String campusFiltro = limpar(campus);
        return jdbc.sql("""
            select u.id,u.nome,p.campus,p.curso,p.periodo,p.competencias,p.resumo,p.atividades,
                   p.curriculo_url,p.linkedin,p.portfolio
            from estudantes.perfil p join acesso.usuario u on u.id=p.usuario_id and u.organizacao_id=p.organizacao_id
            where u.ativo and (?='' or p.campus=?) and (?='' or u.nome ilike '%' || ? || '%'
                or p.curso ilike '%' || ? || '%' or p.competencias ilike '%' || ? || '%'
                or p.atividades ilike '%' || ? || '%')
            order by lower(u.nome)
            """).param(campusFiltro).param(campusFiltro).param(termo).param(termo).param(termo).param(termo).param(termo)
            .query((rs, n) -> new Talento(rs.getLong("id"), rs.getString("nome"), rs.getString("campus"),
                rs.getString("curso"), rs.getString("periodo"), competencias(rs.getString("competencias")),
                rs.getString("resumo"), rs.getString("atividades"), rs.getString("curriculo_url"),
                rs.getString("linkedin"), rs.getString("portfolio"))).list();
    }

    private static String limpar(String valor) { return valor == null ? "" : valor.strip(); }
    private static String link(String valor) {
        String limpo = limpar(valor);
        return limpo.isBlank() || limpo.startsWith("https://") || limpo.startsWith("http://") ? limpo : "";
    }
    public static List<String> competencias(String texto) {
        if (texto == null || texto.isBlank()) return List.of();
        return Arrays.stream(texto.split(",")).map(String::strip).filter(s -> !s.isBlank())
            .map(s -> s.toLowerCase(Locale.ROOT)).distinct().toList();
    }
    private static Perfil perfil(ResultSet rs) throws SQLException {
        return new Perfil(rs.getString("campus"), rs.getString("curso"), rs.getString("periodo"),
            competencias(rs.getString("competencias")), rs.getString("resumo"), rs.getString("atividades"),
            rs.getString("curriculo_url"), rs.getString("linkedin"), rs.getString("portfolio"));
    }

    public record Perfil(String campus, String curso, String periodo, List<String> competencias, String resumo,
            String atividades, String curriculoUrl, String linkedin, String portfolio) {
        public String competenciasTexto() { return String.join(", ", competencias); }
    }
    public record Talento(long id, String nome, String campus, String curso, String periodo,
            List<String> competencias, String resumo, String atividades, String curriculoUrl,
            String linkedin, String portfolio) {}
}
