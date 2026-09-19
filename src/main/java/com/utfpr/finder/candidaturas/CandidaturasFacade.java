package com.utfpr.finder.candidaturas;

import com.utfpr.finder.acesso.RlsContexto;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidaturasFacade {
    private static final List<String> STATUS = List.of("ENVIADA", "EM_ANALISE", "ACEITA", "RECUSADA");
    private final JdbcClient jdbc;
    private final RlsContexto rls;
    public CandidaturasFacade(JdbcClient jdbc, RlsContexto rls) { this.jdbc = jdbc; this.rls = rls; }

    @Transactional
    @PreAuthorize("hasAuthority('candidaturas:criar')")
    public void candidatar(long oportunidadeId) {
        var usuario = rls.aplicar();
        boolean disponivel = jdbc.sql("""
            select exists(
                select 1 from oportunidades.oportunidade o
                join estudantes.perfil p on p.organizacao_id=o.organizacao_id and p.usuario_id=?
                where o.id=? and o.status='APROVADA' and o.campus=p.campus
                  and (o.prazo is null or o.prazo >= current_date))
            """).param(usuario.id()).param(oportunidadeId).query(Boolean.class).single();
        if (!disponivel) throw new IllegalArgumentException("Oportunidade indisponível para o seu campus.");
        jdbc.sql("insert into candidaturas.candidatura(organizacao_id,oportunidade_id,estudante_id) values (?,?,?)")
            .param(usuario.organizacaoId()).param(oportunidadeId).param(usuario.id()).update();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('candidaturas:visualizar_proprias')")
    public List<Resumo> minhas() {
        var usuario = rls.aplicar();
        return consultar("where c.estudante_id=? order by c.criada_em desc", usuario.id());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('candidaturas:visualizar_organizacao')")
    public List<Resumo> daOrganizacao() {
        var usuario = rls.aplicar();
        return usuario.papel().equals("EMPRESA")
            ? consultar("where o.publicador_id=? order by c.criada_em desc", usuario.id())
            : consultar("order by c.criada_em desc");
    }

    @Transactional
    @PreAuthorize("hasAuthority('candidaturas:alterar_status')")
    public void alterarStatus(long id, String status) {
        var usuario = rls.aplicar();
        if (!STATUS.contains(status)) throw new IllegalArgumentException("Status inválido.");
        int alteradas = usuario.papel().equals("EMPRESA")
            ? jdbc.sql("""
                update candidaturas.candidatura c set status=?,atualizada_em=current_timestamp
                where c.id=? and exists(select 1 from oportunidades.oportunidade o
                    where o.id=c.oportunidade_id and o.organizacao_id=c.organizacao_id and o.publicador_id=?)
                """).param(status).param(id).param(usuario.id()).update()
            : jdbc.sql("update candidaturas.candidatura set status=?,atualizada_em=current_timestamp where id=?")
                .param(status).param(id).update();
        if (alteradas == 0) throw new IllegalArgumentException("Candidatura não encontrada.");
    }

    private List<Resumo> consultar(String complemento, Object... parametros) {
        var consulta = jdbc.sql("""
            select c.id,c.oportunidade_id,o.titulo,c.estudante_id,u.nome as estudante,
                   p.curso,p.campus,p.competencias,c.status,c.criada_em
            from candidaturas.candidatura c
            join oportunidades.oportunidade o on o.organizacao_id=c.organizacao_id and o.id=c.oportunidade_id
            join acesso.usuario u on u.organizacao_id=c.organizacao_id and u.id=c.estudante_id
            left join estudantes.perfil p on p.organizacao_id=c.organizacao_id and p.usuario_id=c.estudante_id
            """ + complemento);
        for (Object parametro : parametros) consulta = consulta.param(parametro);
        return consulta.query((rs, n) -> new Resumo(rs.getLong("id"), rs.getLong("oportunidade_id"),
            rs.getString("titulo"), rs.getLong("estudante_id"), rs.getString("estudante"),
            rs.getString("curso"), rs.getString("campus"), rs.getString("status"),
            rs.getObject("criada_em", OffsetDateTime.class))).list();
    }

    public record Resumo(long id, long oportunidadeId, String oportunidade, long estudanteId,
            String estudante, String curso, String campus, String status, OffsetDateTime criadaEm) {}
}
