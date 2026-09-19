package com.utfpr.finder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DatabaseRlsTests {
    @Autowired JdbcClient jdbc;
    @Autowired PostgreSQLContainer postgres;
    long organizacaoPermitida;
    long organizacaoBloqueada;

    @BeforeTransaction
    void prepararOrganizacoes() throws Exception {
        String sufixo = UUID.randomUUID().toString();
        try (var conexao = conexaoProprietaria();
             var comando = conexao.prepareStatement(
                "insert into organizacoes.organizacao(nome,tipo) values (?, 'UNIVERSIDADE') returning id")) {
            comando.setString(1, "Organização permitida " + sufixo);
            try (var resultado = comando.executeQuery()) { resultado.next(); organizacaoPermitida = resultado.getLong(1); }
            comando.setString(1, "Organização bloqueada " + sufixo);
            try (var resultado = comando.executeQuery()) { resultado.next(); organizacaoBloqueada = resultado.getLong(1); }
        }
    }

    @Test
    void rlsIsolaOrganizacoes() {
        jdbc.sql("select set_config('app.organizacao_id', ?, true), set_config('app.usuario_id', '1', true)")
            .param(Long.toString(organizacaoPermitida)).query().singleRow();

        assertThat(jdbc.sql("select nome from organizacoes.organizacao").query(String.class).list())
            .singleElement().asString().startsWith("Organização permitida");
        assertThatThrownBy(() -> jdbc.sql("""
            insert into acesso.usuario(organizacao_id,nome,login,senha_hash,papel)
            values (?, 'Intruso', 'intruso', 'hash', 'ESTUDANTE')
            """).param(organizacaoBloqueada).update()).isInstanceOf(DataAccessException.class);
    }

    @Test
    void rlsLimitaEstudanteAoProprioCampus() throws Exception {
        long estudante;
        long empresa;
        String sufixo = UUID.randomUUID().toString();
        try (var conexao = conexaoProprietaria()) {
            try (var comando = conexao.prepareStatement("""
                    insert into acesso.usuario(organizacao_id,nome,login,senha_hash,papel)
                    values (?,'Estudante',?,'hash','ESTUDANTE') returning id
                    """)) {
                comando.setLong(1, organizacaoPermitida); comando.setString(2, "estudante-" + sufixo);
                try (var resultado = comando.executeQuery()) { resultado.next(); estudante = resultado.getLong(1); }
            }
            try (var comando = conexao.prepareStatement("""
                    insert into acesso.usuario(organizacao_id,nome,login,senha_hash,papel)
                    values (?,'Empresa',?,'hash','EMPRESA') returning id
                    """)) {
                comando.setLong(1, organizacaoPermitida); comando.setString(2, "empresa-" + sufixo);
                try (var resultado = comando.executeQuery()) { resultado.next(); empresa = resultado.getLong(1); }
            }
            try (var comando = conexao.prepareStatement(
                    "insert into estudantes.perfil(organizacao_id,usuario_id,campus) values (?,?,'Campo Mourão')")) {
                comando.setLong(1, organizacaoPermitida); comando.setLong(2, estudante); comando.executeUpdate();
            }
            try (var comando = conexao.prepareStatement("""
                    insert into oportunidades.oportunidade
                        (organizacao_id,publicador_id,titulo,responsavel,tipo,modalidade,campus,descricao,status)
                    values (?,?,?,'Empresa','ESTAGIO','REMOTO',?,?,'APROVADA')
                    """)) {
                comando.setLong(1, organizacaoPermitida); comando.setLong(2, empresa);
                comando.setString(3, "Vaga Campo Mourão"); comando.setString(4, "Campo Mourão"); comando.setString(5, "Visível");
                comando.executeUpdate();
                comando.setString(3, "Vaga Francisco Beltrão"); comando.setString(4, "Francisco Beltrão"); comando.setString(5, "Oculta");
                comando.executeUpdate();
            }
        }

        jdbc.sql("select set_config('app.organizacao_id', ?, true), set_config('app.usuario_id', ?, true)")
            .param(Long.toString(organizacaoPermitida)).param(Long.toString(estudante)).query().singleRow();

        assertThat(jdbc.sql("select titulo from oportunidades.oportunidade order by titulo")
            .query(String.class).list()).containsExactly("Vaga Campo Mourão");
    }

    private Connection conexaoProprietaria() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
