package com.utfpr.finder.oportunidades;

import com.utfpr.finder.acesso.RlsContexto;
import com.utfpr.finder.estudantes.EstudantesFacade;
import com.utfpr.finder.estudantes.EstudantesFacade.Perfil;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OportunidadesFacade {
    private static final List<String> TIPOS = List.of("ESTAGIO", "EMPREGO", "PROJETO", "EVENTO", "DESAFIO", "CAPACITACAO");
    private static final List<String> MODALIDADES = List.of("PRESENCIAL", "HIBRIDO", "REMOTO");
    private final JdbcClient jdbc;
    private final RlsContexto rls;
    private final EstudantesFacade estudantes;
    public OportunidadesFacade(JdbcClient jdbc, RlsContexto rls, EstudantesFacade estudantes) {
        this.jdbc = jdbc; this.rls = rls; this.estudantes = estudantes;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('oportunidades:visualizar')")
    public List<Resumo> listar(String busca, String aba, String campus) {
        var usuario = rls.aplicar();
        boolean estudante = usuario.papel().equals("ESTUDANTE");
        boolean empresa = usuario.papel().equals("EMPRESA");
        Optional<Perfil> perfil = estudante ? estudantes.perfilAtual() : Optional.empty();
        String campusVisivel = estudante ? perfil.map(Perfil::campus).orElse("") : texto(campus);
        String termo = texto(busca);
        String status = "pendentes".equals(aba) ? "PENDENTE" : "";

        var oportunidades = jdbc.sql("""
            select id,publicador_id,titulo,responsavel,tipo,modalidade,localidade,campus,curso,competencias,prazo,status
            from oportunidades.oportunidade
            where (? = false or (status='APROVADA' and campus=?))
              and (? = false or publicador_id=? or status='APROVADA')
              and (?='' or status=?) and (?='' or campus=?)
              and (?='' or titulo ilike '%' || ? || '%' or responsavel ilike '%' || ? || '%'
                   or curso ilike '%' || ? || '%' or competencias ilike '%' || ? || '%')
            order by case status when 'PENDENTE' then 0 when 'APROVADA' then 1 else 2 end, criado_em desc
            """).param(estudante).param(campusVisivel).param(empresa).param(usuario.id())
            .param(status).param(status).param(estudante ? "" : campusVisivel).param(estudante ? "" : campusVisivel)
            .param(termo).param(termo).param(termo).param(termo).param(termo)
            .query((rs, n) -> {
                String curso = rs.getString("curso");
                List<String> competencias = EstudantesFacade.competencias(rs.getString("competencias"));
                Integer compatibilidade = perfil.map(p -> compatibilidade(p, curso, competencias)).orElse(null);
                return new Resumo(rs.getLong("id"), rs.getLong("publicador_id"), rs.getString("titulo"),
                    rs.getString("responsavel"), rs.getString("tipo"), rs.getString("modalidade"),
                    rs.getString("localidade"), rs.getString("campus"), curso, competencias,
                    rs.getObject("prazo", LocalDate.class), rs.getString("status"), compatibilidade);
            }).list();
        if (estudante && "para-mim".equals(aba))
            oportunidades.sort(Comparator.comparing(Resumo::compatibilidade,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return oportunidades;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('oportunidades:visualizar')")
    public Detalhe buscar(long id) {
        var usuario = rls.aplicar();
        var oportunidade = jdbc.sql("""
            select id,publicador_id,titulo,responsavel,tipo,modalidade,localidade,campus,descricao,curso,
                   competencias,prazo,status
            from oportunidades.oportunidade where id=?
            """).param(id).query((rs, n) -> new Detalhe(rs.getLong("id"), rs.getLong("publicador_id"),
                rs.getString("titulo"), rs.getString("responsavel"), rs.getString("tipo"),
                rs.getString("modalidade"), rs.getString("localidade"), rs.getString("campus"),
                rs.getString("descricao"), rs.getString("curso"),
                EstudantesFacade.competencias(rs.getString("competencias")),
                rs.getObject("prazo", LocalDate.class), rs.getString("status"))).optional().orElseThrow();
        if (usuario.papel().equals("ESTUDANTE")) {
            String campus = estudantes.perfilAtual().map(Perfil::campus).orElse("");
            if (!oportunidade.status().equals("APROVADA") || !oportunidade.campus().equals(campus))
                throw new IllegalArgumentException("Oportunidade indisponível para o seu campus.");
        }
        if (usuario.papel().equals("EMPRESA") && oportunidade.publicadorId() != usuario.id()
                && !oportunidade.status().equals("APROVADA"))
            throw new IllegalArgumentException("Oportunidade indisponível.");
        return oportunidade;
    }

    @Transactional
    @PreAuthorize("hasAuthority('oportunidades:criar')")
    public long criar(Nova nova) {
        var usuario = rls.aplicar();
        validar(nova);
        String status = "RASCUNHO".equals(nova.status()) ? "RASCUNHO" : "PENDENTE";
        return jdbc.sql("""
            insert into oportunidades.oportunidade
                (organizacao_id,publicador_id,titulo,responsavel,tipo,modalidade,localidade,campus,
                 descricao,curso,competencias,prazo,status)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?) returning id
            """).param(usuario.organizacaoId()).param(usuario.id()).param(nova.titulo().strip())
            .param(nova.responsavel().strip()).param(nova.tipo()).param(nova.modalidade())
            .param(texto(nova.localidade())).param(nova.campus().strip()).param(nova.descricao().strip())
            .param(texto(nova.curso())).param(String.join(",", EstudantesFacade.competencias(nova.competencias())))
            .param(nova.prazo()).param(status).query(Long.class).single();
    }

    @Transactional
    @PreAuthorize("hasAuthority('oportunidades:editar')")
    public void enviarOuEncerrar(long id, String status) {
        var usuario = rls.aplicar();
        if (!List.of("PENDENTE", "ENCERRADA").contains(status)) throw new IllegalArgumentException("Status inválido.");
        String complemento = usuario.papel().equals("EMPRESA") ? " and publicador_id=?" : "";
        var consulta = jdbc.sql("update oportunidades.oportunidade set status=?,atualizado_em=current_timestamp where id=?" + complemento)
            .param(status).param(id);
        if (usuario.papel().equals("EMPRESA")) consulta = consulta.param(usuario.id());
        if (consulta.update() == 0) throw new IllegalArgumentException("Oportunidade não encontrada.");
    }

    @Transactional
    @PreAuthorize("hasAuthority('oportunidades:aprovar')")
    public void revisar(long id, String status) {
        var usuario = rls.aplicar();
        if (!List.of("APROVADA", "REJEITADA").contains(status)) throw new IllegalArgumentException("Decisão inválida.");
        if (jdbc.sql("""
            update oportunidades.oportunidade set status=?,revisado_por=?,revisado_em=current_timestamp,
                atualizado_em=current_timestamp where id=? and status='PENDENTE'
            """).param(status).param(usuario.id()).param(id).update() == 0)
            throw new IllegalArgumentException("A oportunidade não está pendente de análise.");
    }

    private void validar(Nova nova) {
        if (nova.titulo() == null || nova.titulo().isBlank() || nova.responsavel() == null || nova.responsavel().isBlank()
                || nova.descricao() == null || nova.descricao().isBlank() || nova.campus() == null || nova.campus().isBlank())
            throw new IllegalArgumentException("Título, responsável, campus e descrição são obrigatórios.");
        if (!TIPOS.contains(nova.tipo()) || !MODALIDADES.contains(nova.modalidade()))
            throw new IllegalArgumentException("Tipo ou modalidade inválida.");
    }
    private static String texto(String s) { return s == null ? "" : s.strip(); }
    static int compatibilidade(Perfil perfil, String curso, List<String> exigidas) {
        int total = exigidas.size() + (curso == null || curso.isBlank() ? 0 : 1);
        if (total == 0) return 100;
        int pontos = 0;
        if (curso != null && !curso.isBlank() && curso.equalsIgnoreCase(perfil.curso())) pontos++;
        var doEstudante = perfil.competencias().stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
        pontos += exigidas.stream().map(s -> s.toLowerCase(Locale.ROOT)).filter(doEstudante::contains).count();
        return (int) Math.round(pontos * 100.0 / total);
    }

    public record Nova(String titulo, String responsavel, String tipo, String modalidade, String localidade,
            String campus, String descricao, String curso, String competencias, LocalDate prazo, String status) {}
    public record Resumo(long id, long publicadorId, String titulo, String responsavel, String tipo,
            String modalidade, String localidade, String campus, String curso, List<String> competencias,
            LocalDate prazo, String status, Integer compatibilidade) {}
    public record Detalhe(long id, long publicadorId, String titulo, String responsavel, String tipo,
            String modalidade, String localidade, String campus, String descricao, String curso,
            List<String> competencias, LocalDate prazo, String status) {
        public String competenciasTexto() { return String.join(", ", competencias); }
    }
}
