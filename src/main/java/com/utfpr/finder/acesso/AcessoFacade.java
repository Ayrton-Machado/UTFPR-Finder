package com.utfpr.finder.acesso;

import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcessoFacade {
    private final JdbcClient jdbc;
    private final RlsContexto rls;
    private final PasswordEncoder encoder;

    public AcessoFacade(JdbcClient jdbc, RlsContexto rls, PasswordEncoder encoder) {
        this.jdbc = jdbc; this.rls = rls; this.encoder = encoder;
    }

    public Optional<Credenciais> buscarCredenciais(String login) {
        return jdbc.sql("select * from autenticacao.buscar_credenciais(?)").param(login == null ? "" : login)
            .query((rs, n) -> {
                Array array = rs.getArray("permissoes");
                String[] permissoes = array == null ? new String[0] : (String[]) array.getArray();
                var usuario = new UsuarioAutenticado(rs.getLong("id"), rs.getLong("organizacao_id"),
                    rs.getString("nome"), rs.getString("login"), rs.getString("papel"),
                    rs.getLong("versao_acesso"), Arrays.asList(permissoes));
                return new Credenciais(usuario, rs.getString("senha_hash"));
            }).optional();
    }

    public boolean sessaoValida(UsuarioAutenticado usuario) {
        return jdbc.sql("select autenticacao.versao_sessao(?)").param(usuario.id()).query(Long.class)
            .optional().map(v -> v == usuario.versaoAcesso()).orElse(false);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('usuarios:visualizar')")
    public List<UsuarioResumo> listarUsuarios() {
        rls.aplicar();
        return jdbc.sql("select id,nome,login,papel from acesso.usuario where ativo order by lower(nome)")
            .query(UsuarioResumo.class).list();
    }

    @Transactional
    @PreAuthorize("hasAuthority('usuarios:criar')")
    public long criarUsuario(String nome, String login, String senha, String papel) {
        var atual = rls.aplicar();
        nome = texto(nome, "Nome");
        login = texto(login, "Login").toLowerCase();
        senha = texto(senha, "Senha");
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("A senha deve ter no máximo 72 bytes.");
        if (!List.of("ADMIN", "PROFESSOR", "EMPRESA", "ESTUDANTE").contains(papel))
            throw new IllegalArgumentException("Papel inválido.");
        long id = jdbc.sql("insert into acesso.usuario(organizacao_id,nome,login,senha_hash,papel) values (?,?,?,?,?) returning id")
            .param(atual.organizacaoId()).param(nome).param(login).param(encoder.encode(senha)).param(papel)
            .query(Long.class).single();
        concederPermissoes(atual.organizacaoId(), id, papel);
        if (papel.equals("ESTUDANTE"))
            jdbc.sql("insert into estudantes.perfil(organizacao_id,usuario_id) values (?,?)")
                .param(atual.organizacaoId()).param(id).update();
        if (papel.equals("EMPRESA"))
            jdbc.sql("insert into empresas.perfil(organizacao_id,usuario_id,nome_fantasia) values (?,?,?)")
                .param(atual.organizacaoId()).param(id).param(nome).update();
        return id;
    }

    private void concederPermissoes(long organizacaoId, long usuarioId, String papel) {
        jdbc.sql("""
            insert into acesso.usuario_permissao(organizacao_id,usuario_id,permissao_id)
            select ?,?,p.id from acesso.permissao p where
                (? = 'ADMIN' and (p.recurso,p.acao) in (
                    ('usuarios','visualizar'),('usuarios','criar'),
                    ('oportunidades','visualizar'),('oportunidades','criar'),('oportunidades','editar'),('oportunidades','aprovar'),
                    ('candidaturas','visualizar_organizacao'),('candidaturas','alterar_status'),('estudantes','buscar')))
                or (? = 'PROFESSOR' and (p.recurso,p.acao) in (
                    ('oportunidades','visualizar'),('oportunidades','criar'),('oportunidades','editar'),('oportunidades','aprovar'),
                    ('candidaturas','visualizar_organizacao'),('candidaturas','alterar_status'),('estudantes','buscar')))
                or (? = 'EMPRESA' and (p.recurso,p.acao) in (
                    ('oportunidades','visualizar'),('oportunidades','criar'),('oportunidades','editar'),
                    ('candidaturas','visualizar_organizacao'),('candidaturas','alterar_status'),
                    ('estudantes','buscar'),('perfil','editar_empresa')))
                or (? = 'ESTUDANTE' and (p.recurso,p.acao) in (
                    ('oportunidades','visualizar'),('candidaturas','criar'),
                    ('candidaturas','visualizar_proprias'),('perfil','editar_proprio')))
            """).param(organizacaoId).param(usuarioId).param(papel).param(papel).param(papel).param(papel).update();
    }

    private String texto(String valor, String campo) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(campo + " é obrigatório.");
        return valor.strip();
    }

    public record Credenciais(UsuarioAutenticado usuario, String hash) {}
    public record UsuarioResumo(long id, String nome, String login, String papel) {}
}
