DROP POLICY oportunidade_isolada ON oportunidades.oportunidade;
CREATE POLICY oportunidade_leitura ON oportunidades.oportunidade FOR SELECT TO finder_app
    USING (organizacao_id = autenticacao.organizacao_atual() AND (
        NOT EXISTS (
            SELECT 1 FROM acesso.usuario u
            WHERE u.id = autenticacao.usuario_atual()
              AND u.organizacao_id = autenticacao.organizacao_atual()
              AND u.papel = 'ESTUDANTE')
        OR (status = 'APROVADA' AND campus = (
            SELECT p.campus FROM estudantes.perfil p
            WHERE p.usuario_id = autenticacao.usuario_atual()
              AND p.organizacao_id = autenticacao.organizacao_atual()))
    ));
CREATE POLICY oportunidade_insercao ON oportunidades.oportunidade FOR INSERT TO finder_app
    WITH CHECK (organizacao_id = autenticacao.organizacao_atual());
CREATE POLICY oportunidade_atualizacao ON oportunidades.oportunidade FOR UPDATE TO finder_app
    USING (organizacao_id = autenticacao.organizacao_atual())
    WITH CHECK (organizacao_id = autenticacao.organizacao_atual());

DROP POLICY perfil_isolado ON estudantes.perfil;
CREATE POLICY perfil_leitura ON estudantes.perfil FOR SELECT TO finder_app
    USING (organizacao_id = autenticacao.organizacao_atual() AND (
        usuario_id = autenticacao.usuario_atual()
        OR EXISTS (
            SELECT 1 FROM acesso.usuario_permissao up
            JOIN acesso.permissao p ON p.id = up.permissao_id
            WHERE up.organizacao_id = autenticacao.organizacao_atual()
              AND up.usuario_id = autenticacao.usuario_atual()
              AND p.recurso = 'estudantes' AND p.acao = 'buscar')));
CREATE POLICY perfil_insercao ON estudantes.perfil FOR INSERT TO finder_app
    WITH CHECK (organizacao_id = autenticacao.organizacao_atual());
CREATE POLICY perfil_atualizacao ON estudantes.perfil FOR UPDATE TO finder_app
    USING (organizacao_id = autenticacao.organizacao_atual() AND usuario_id = autenticacao.usuario_atual())
    WITH CHECK (organizacao_id = autenticacao.organizacao_atual() AND usuario_id = autenticacao.usuario_atual());

DROP POLICY empresa_isolada ON empresas.perfil;
CREATE POLICY empresa_leitura ON empresas.perfil FOR SELECT TO finder_app
    USING (organizacao_id = autenticacao.organizacao_atual() AND usuario_id = autenticacao.usuario_atual());
CREATE POLICY empresa_insercao ON empresas.perfil FOR INSERT TO finder_app
    WITH CHECK (organizacao_id = autenticacao.organizacao_atual());
CREATE POLICY empresa_atualizacao ON empresas.perfil FOR UPDATE TO finder_app
    USING (organizacao_id = autenticacao.organizacao_atual() AND usuario_id = autenticacao.usuario_atual())
    WITH CHECK (organizacao_id = autenticacao.organizacao_atual() AND usuario_id = autenticacao.usuario_atual());

DELETE FROM candidaturas.candidatura c
USING oportunidades.oportunidade o, estudantes.perfil p
WHERE o.organizacao_id = c.organizacao_id AND o.id = c.oportunidade_id
  AND p.organizacao_id = c.organizacao_id AND p.usuario_id = c.estudante_id
  AND o.campus <> p.campus;
