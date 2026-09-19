package com.utfpr.finder.seed;

import java.time.LocalDate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("seed")
class DemoSeeder implements ApplicationRunner {
    private static final String SENHA_DEMO = "demo-local-123";
    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final Environment env;

    DemoSeeder(JdbcClient jdbc, PasswordEncoder encoder, Environment env) {
        this.jdbc = jdbc; this.encoder = encoder; this.env = env;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String nomeOrganizacao = env.getProperty("BOOTSTRAP_ORGANIZACAO_NOME", "UTFPR");
        long organizacaoId = jdbc.sql("select id from organizacoes.organizacao where lower(nome)=lower(?)")
            .param(nomeOrganizacao).query(Long.class).optional()
            .orElseThrow(() -> new IllegalStateException("Execute o bootstrap antes do seeder."));

        long empresa = usuario(organizacaoId, "Vertice Tecnologia", "empresa.demo", "EMPRESA");
        long professor = usuario(organizacaoId, "Prof. Ricardo Almeida", "professor.demo", "PROFESSOR");
        long ana = usuario(organizacaoId, "Ana Souza", "ana.demo", "ESTUDANTE");
        long lucas = usuario(organizacaoId, "Lucas Martins", "lucas.demo", "ESTUDANTE");

        perfilEmpresa(organizacaoId, empresa, "Vertice Tecnologia",
            "Empresa de produtos digitais que aproxima tecnologia, dados e impacto regional.",
            "desenvolvimento de software,dados,produto", "java,sql,comunicação,git",
            "https://example.com", "https://www.linkedin.com");
        perfilEstudante(organizacaoId, ana, "Campo Mourão", "Engenharia de Software", "6º período",
            "java,sql,git,comunicação", "Desenvolvedora em formação interessada em produtos digitais.",
            "Empresa Júnior, projeto de extensão em acessibilidade e monitoria de programação.",
            "https://example.com/curriculo-ana", "https://www.linkedin.com", "https://github.com");
        perfilEstudante(organizacaoId, lucas, "Francisco Beltrão", "Administração", "4º período",
            "excel,gestão,comunicação,power bi", "Estudante interessado em operações, dados e inovação.",
            "Centro Acadêmico, organização de eventos e projeto de análise de indicadores.",
            "https://example.com/curriculo-lucas", "https://www.linkedin.com", "");

        long estagioCm = oportunidade(organizacaoId, empresa, "Estágio em Desenvolvimento Java", "Vertice Tecnologia",
            "ESTAGIO", "HIBRIDO", "Campo Mourão, PR", "Campo Mourão",
            "Atue com o time de produto na evolução de serviços web, com mentoria técnica e contato com todo o ciclo de desenvolvimento.",
            "Engenharia de Software", "java,sql,git", LocalDate.now().plusDays(30), "APROVADA", professor);
        oportunidade(organizacaoId, empresa, "Estágio em Qualidade e Processos", "Cooperativa Sudoeste",
            "ESTAGIO", "PRESENCIAL", "Francisco Beltrão, PR", "Francisco Beltrão",
            "Apoie o mapeamento de processos, indicadores e iniciativas de melhoria contínua.",
            "Administração", "excel,gestão,comunicação", LocalDate.now().plusDays(35), "APROVADA", professor);
        oportunidade(organizacaoId, empresa, "Programa de Talentos em Dados", "Vertice Tecnologia",
            "EMPREGO", "REMOTO", "Brasil", "Campo Mourão",
            "Programa para estudantes que desejam transformar dados em decisões. Experiência prévia não é obrigatória.",
            "", "sql,power bi,comunicação", LocalDate.now().plusDays(45), "APROVADA", professor);
        oportunidade(organizacaoId, empresa, "Projeto Smart Campus", "UTFPR Inovação",
            "PROJETO", "PRESENCIAL", "Francisco Beltrão, PR", "Francisco Beltrão",
            "Forme uma equipe e proponha uma solução simples para melhorar a experiência no campus.",
            "", "criatividade,comunicação", LocalDate.now().plusDays(20), "APROVADA", professor);
        oportunidade(organizacaoId, empresa, "Estágio em Produto Digital", "Vertice Tecnologia",
            "ESTAGIO", "HIBRIDO", "Campo Mourão, PR", "Campo Mourão",
            "Oportunidade para colaborar com pesquisa, prototipação e acompanhamento de métricas de produto.",
            "", "comunicação,produto,pesquisa", LocalDate.now().plusDays(40), "PENDENTE", null);

        candidatura(organizacaoId, estagioCm, ana, "EM_ANALISE");
    }

    private long usuario(long organizacaoId, String nome, String login, String papel) {
        long id = jdbc.sql("select id from acesso.usuario where login=?").param(login).query(Long.class)
            .optional().orElseGet(() -> jdbc.sql("""
                insert into acesso.usuario(organizacao_id,nome,login,senha_hash,papel)
                values (?,?,?,?,?) returning id
                """).param(organizacaoId).param(nome).param(login).param(encoder.encode(SENHA_DEMO)).param(papel)
                .query(Long.class).single());
        jdbc.sql("update acesso.usuario set nome=?,papel=?,ativo=true where id=?")
            .param(nome).param(papel).param(id).update();
        concederPermissoes(organizacaoId, id, papel);
        return id;
    }

    private void concederPermissoes(long organizacaoId, long usuarioId, String papel) {
        jdbc.sql("delete from acesso.usuario_permissao where organizacao_id=? and usuario_id=?")
            .param(organizacaoId).param(usuarioId).update();
        jdbc.sql("""
            insert into acesso.usuario_permissao(organizacao_id,usuario_id,permissao_id)
            select ?,?,p.id from acesso.permissao p where
                (? = 'PROFESSOR' and (p.recurso,p.acao) in (
                    ('oportunidades','visualizar'),('oportunidades','criar'),('oportunidades','editar'),('oportunidades','aprovar'),
                    ('candidaturas','visualizar_organizacao'),('candidaturas','alterar_status'),('estudantes','buscar')))
                or (? = 'EMPRESA' and (p.recurso,p.acao) in (
                    ('oportunidades','visualizar'),('oportunidades','criar'),('oportunidades','editar'),
                    ('candidaturas','visualizar_organizacao'),('candidaturas','alterar_status'),
                    ('estudantes','buscar'),('perfil','editar_empresa')))
                or (? = 'ESTUDANTE' and (p.recurso,p.acao) in (
                    ('oportunidades','visualizar'),('candidaturas','criar'),
                    ('candidaturas','visualizar_proprias'),('perfil','editar_proprio')))
            """).param(organizacaoId).param(usuarioId).param(papel).param(papel).param(papel).update();
    }

    private void perfilEstudante(long organizacaoId, long usuarioId, String campus, String curso, String periodo,
            String competencias, String resumo, String atividades, String curriculo, String linkedin, String portfolio) {
        jdbc.sql("""
            insert into estudantes.perfil(organizacao_id,usuario_id,campus,curso,periodo,competencias,resumo,
                atividades,curriculo_url,linkedin,portfolio)
            values (?,?,?,?,?,?,?,?,?,?,?)
            on conflict (organizacao_id,usuario_id) do update set campus=excluded.campus,curso=excluded.curso,
                periodo=excluded.periodo,competencias=excluded.competencias,resumo=excluded.resumo,
                atividades=excluded.atividades,curriculo_url=excluded.curriculo_url,linkedin=excluded.linkedin,
                portfolio=excluded.portfolio,atualizado_em=current_timestamp
            """).param(organizacaoId).param(usuarioId).param(campus).param(curso).param(periodo).param(competencias)
            .param(resumo).param(atividades).param(curriculo).param(linkedin).param(portfolio).update();
    }

    private void perfilEmpresa(long organizacaoId, long usuarioId, String nome, String descricao, String areas,
            String competencias, String site, String linkedin) {
        jdbc.sql("""
            insert into empresas.perfil(organizacao_id,usuario_id,nome_fantasia,descricao,areas_interesse,
                competencias_buscadas,site,linkedin) values (?,?,?,?,?,?,?,?)
            on conflict (organizacao_id,usuario_id) do update set nome_fantasia=excluded.nome_fantasia,
                descricao=excluded.descricao,areas_interesse=excluded.areas_interesse,
                competencias_buscadas=excluded.competencias_buscadas,site=excluded.site,
                linkedin=excluded.linkedin,atualizado_em=current_timestamp
            """).param(organizacaoId).param(usuarioId).param(nome).param(descricao).param(areas)
            .param(competencias).param(site).param(linkedin).update();
    }

    private long oportunidade(long organizacaoId, long publicadorId, String titulo, String responsavel,
            String tipo, String modalidade, String localidade, String campus, String descricao, String curso,
            String competencias, LocalDate prazo, String status, Long revisor) {
        Long id = jdbc.sql("select id from oportunidades.oportunidade where organizacao_id=? and titulo=?")
            .param(organizacaoId).param(titulo).query(Long.class).optional().orElse(null);
        if (id == null) return jdbc.sql("""
            insert into oportunidades.oportunidade(organizacao_id,publicador_id,titulo,responsavel,tipo,modalidade,
                localidade,campus,descricao,curso,competencias,prazo,status,revisado_por,revisado_em)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,case when cast(? as bigint) is null then null else current_timestamp end) returning id
            """).param(organizacaoId).param(publicadorId).param(titulo).param(responsavel).param(tipo)
            .param(modalidade).param(localidade).param(campus).param(descricao).param(curso).param(competencias)
            .param(prazo).param(status).param(revisor).param(revisor).query(Long.class).single();
        jdbc.sql("""
            update oportunidades.oportunidade set publicador_id=?,responsavel=?,tipo=?,modalidade=?,localidade=?,
                campus=?,descricao=?,curso=?,competencias=?,prazo=?,status=?,revisado_por=?,
                revisado_em=case when cast(? as bigint) is null then null else current_timestamp end,atualizado_em=current_timestamp
            where id=?
            """).param(publicadorId).param(responsavel).param(tipo).param(modalidade).param(localidade).param(campus)
            .param(descricao).param(curso).param(competencias).param(prazo).param(status).param(revisor).param(revisor)
            .param(id).update();
        return id;
    }

    private void candidatura(long organizacaoId, long oportunidadeId, long estudanteId, String status) {
        jdbc.sql("""
            insert into candidaturas.candidatura(organizacao_id,oportunidade_id,estudante_id,status)
            values (?,?,?,?) on conflict (organizacao_id,oportunidade_id,estudante_id)
            do update set status=excluded.status,atualizada_em=current_timestamp
            """).param(organizacaoId).param(oportunidadeId).param(estudanteId).param(status).update();
    }

    @EventListener(ApplicationReadyEvent.class)
    void encerrarAoConcluir(ApplicationReadyEvent evento) { evento.getApplicationContext().close(); }
}
