ALTER TABLE acesso.usuario DROP CONSTRAINT IF EXISTS usuario_papel_check;
UPDATE acesso.usuario SET papel = 'EMPRESA' WHERE papel = 'PUBLICADOR';
ALTER TABLE acesso.usuario ADD CONSTRAINT usuario_papel_check
    CHECK (papel IN ('ADMIN', 'PROFESSOR', 'EMPRESA', 'ESTUDANTE'));

INSERT INTO acesso.permissao(recurso, acao) VALUES
    ('oportunidades', 'aprovar'),
    ('estudantes', 'buscar'),
    ('perfil', 'editar_empresa')
ON CONFLICT DO NOTHING;

ALTER TABLE estudantes.perfil
    ADD COLUMN resumo TEXT NOT NULL DEFAULT '',
    ADD COLUMN atividades TEXT NOT NULL DEFAULT '',
    ADD COLUMN curriculo_url VARCHAR(300) NOT NULL DEFAULT '',
    ADD COLUMN linkedin VARCHAR(300) NOT NULL DEFAULT '',
    ADD COLUMN portfolio VARCHAR(300) NOT NULL DEFAULT '';

ALTER TABLE oportunidades.oportunidade DROP CONSTRAINT IF EXISTS oportunidade_status_check;
UPDATE oportunidades.oportunidade SET status = 'APROVADA' WHERE status = 'PUBLICADA';
ALTER TABLE oportunidades.oportunidade
    ADD COLUMN campus VARCHAR(100) NOT NULL DEFAULT 'Campo Mourão',
    ADD COLUMN revisado_por BIGINT,
    ADD COLUMN revisado_em TIMESTAMPTZ,
    ADD CONSTRAINT oportunidade_status_check
        CHECK (status IN ('RASCUNHO', 'PENDENTE', 'APROVADA', 'REJEITADA', 'ENCERRADA')),
    ADD CONSTRAINT oportunidade_revisor_fk
        FOREIGN KEY (organizacao_id, revisado_por) REFERENCES acesso.usuario(organizacao_id, id);
UPDATE oportunidades.oportunidade SET campus = 'Curitiba' WHERE localidade ILIKE '%curitiba%';
CREATE INDEX oportunidade_campus_feed
    ON oportunidades.oportunidade(organizacao_id, campus, status, criado_em DESC);

CREATE SCHEMA empresas;
CREATE TABLE empresas.perfil (
    organizacao_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    nome_fantasia VARCHAR(120) NOT NULL DEFAULT '',
    descricao TEXT NOT NULL DEFAULT '',
    areas_interesse TEXT NOT NULL DEFAULT '',
    competencias_buscadas TEXT NOT NULL DEFAULT '',
    site VARCHAR(300) NOT NULL DEFAULT '',
    linkedin VARCHAR(300) NOT NULL DEFAULT '',
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    PRIMARY KEY (organizacao_id, usuario_id),
    FOREIGN KEY (organizacao_id, usuario_id) REFERENCES acesso.usuario(organizacao_id, id)
);
INSERT INTO empresas.perfil(organizacao_id, usuario_id, nome_fantasia)
SELECT organizacao_id, id, nome FROM acesso.usuario WHERE papel = 'EMPRESA'
ON CONFLICT DO NOTHING;

GRANT USAGE ON SCHEMA empresas TO finder_app;
GRANT SELECT, INSERT, UPDATE ON empresas.perfil TO finder_app;
ALTER TABLE empresas.perfil ENABLE ROW LEVEL SECURITY;
CREATE POLICY empresa_isolada ON empresas.perfil TO finder_app
    USING (organizacao_id = autenticacao.organizacao_atual())
    WITH CHECK (organizacao_id = autenticacao.organizacao_atual()
        AND usuario_id = autenticacao.usuario_atual());

INSERT INTO acesso.usuario_permissao(organizacao_id, usuario_id, permissao_id)
SELECT u.organizacao_id, u.id, p.id
FROM acesso.usuario u
CROSS JOIN acesso.permissao p
WHERE (u.papel = 'ADMIN' AND (p.recurso, p.acao) IN (
        ('usuarios','visualizar'), ('usuarios','criar'),
        ('oportunidades','visualizar'), ('oportunidades','criar'), ('oportunidades','editar'), ('oportunidades','aprovar'),
        ('candidaturas','visualizar_organizacao'), ('candidaturas','alterar_status'), ('estudantes','buscar')))
   OR (u.papel = 'EMPRESA' AND (p.recurso, p.acao) IN (
        ('oportunidades','visualizar'), ('oportunidades','criar'), ('oportunidades','editar'),
        ('candidaturas','visualizar_organizacao'), ('candidaturas','alterar_status'),
        ('estudantes','buscar'), ('perfil','editar_empresa')))
ON CONFLICT DO NOTHING;
