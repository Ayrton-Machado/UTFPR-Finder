package com.utfpr.finder.acesso.internal;

import java.nio.charset.StandardCharsets;
import java.util.List;
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
@Profile("bootstrap")
class BootstrapInicial implements ApplicationRunner {
    private final JdbcClient jdbc;
    private final Environment env;
    private final PasswordEncoder encoder;
    BootstrapInicial(JdbcClient jdbc, Environment env, PasswordEncoder encoder) {
        this.jdbc = jdbc; this.env = env; this.encoder = encoder;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        String papelBanco = env.getProperty("APP_DB_USER", "finder_runtime");
        String senhaBanco = obrigatorio("APP_DB_PASSWORD");
        if (!papelBanco.matches("[a-z][a-z0-9_]{0,62}") || papelBanco.equals("finder_app"))
            throw new IllegalStateException("APP_DB_USER inválido.");
        boolean existe = jdbc.sql("select exists(select 1 from pg_roles where rolname=?)")
            .param(papelBanco).query(Boolean.class).single();
        if (existe) {
            boolean inseguro = jdbc.sql("""
                select rolsuper or rolbypassrls or rolcreaterole or rolcreatedb or not rolcanlogin
                from pg_roles where rolname=?
                """).param(papelBanco).query(Boolean.class).single();
            if (inseguro) throw new IllegalStateException("APP_DB_USER deve ser um papel dedicado e restrito.");
        } else {
            String comando = jdbc.sql("select format('create role %I login nosuperuser nocreatedb nocreaterole nobypassrls password %L', cast(? as text), cast(? as text))")
                .param(papelBanco).param(senhaBanco).query(String.class).single();
            jdbc.sql(comando).update();
        }
        String grant = jdbc.sql("select format('grant finder_app to %I', cast(? as text))")
            .param(papelBanco).query(String.class).single();
        jdbc.sql(grant).update();

        String nomeOrganizacao = obrigatorio("BOOTSTRAP_ORGANIZACAO_NOME").strip();
        String tipo = env.getProperty("BOOTSTRAP_ORGANIZACAO_TIPO", "UNIVERSIDADE");
        if (!List.of("UNIVERSIDADE", "EMPRESA", "INSTITUICAO").contains(tipo))
            throw new IllegalStateException("BOOTSTRAP_ORGANIZACAO_TIPO inválido.");
        long organizacaoId = jdbc.sql("""
            insert into organizacoes.organizacao(nome,tipo) values (?,?)
            on conflict ((lower(btrim(nome)))) do update set nome=excluded.nome
            returning id
            """).param(nomeOrganizacao).param(tipo).query(Long.class).single();

        String login = obrigatorio("BOOTSTRAP_ADMIN_LOGIN").strip().toLowerCase();
        String senha = obrigatorio("BOOTSTRAP_ADMIN_SENHA");
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException("BOOTSTRAP_ADMIN_SENHA deve ter no máximo 72 bytes.");
        boolean adminExiste = jdbc.sql("select exists(select 1 from acesso.usuario where login=?)")
            .param(login).query(Boolean.class).single();
        if (!adminExiste) {
            long adminId = jdbc.sql("insert into acesso.usuario(organizacao_id,nome,login,senha_hash,papel) values (?,?,?,?, 'ADMIN') returning id")
                .param(organizacaoId).param(obrigatorio("BOOTSTRAP_ADMIN_NOME").strip()).param(login)
                .param(encoder.encode(senha)).query(Long.class).single();
            jdbc.sql("""
                insert into acesso.usuario_permissao(organizacao_id,usuario_id,permissao_id)
                select ?,?,id from acesso.permissao
                where (recurso,acao) not in (('candidaturas','criar'),
                    ('candidaturas','visualizar_proprias'),('perfil','editar_proprio'))
                """)
                .param(organizacaoId).param(adminId).update();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void encerrarAoConcluir(ApplicationReadyEvent evento) {
        evento.getApplicationContext().close();
    }

    private String obrigatorio(String chave) {
        String valor = env.getProperty(chave);
        if (valor == null || valor.isBlank()) throw new IllegalStateException("Configure " + chave + ".");
        return valor;
    }
}
