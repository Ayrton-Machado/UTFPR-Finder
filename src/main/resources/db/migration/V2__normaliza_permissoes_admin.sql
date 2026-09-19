DELETE FROM acesso.usuario_permissao up
USING acesso.usuario u, acesso.permissao p
WHERE u.organizacao_id = up.organizacao_id
  AND u.id = up.usuario_id
  AND p.id = up.permissao_id
  AND u.papel = 'ADMIN'
  AND (p.recurso, p.acao) IN (
      ('candidaturas', 'criar'),
      ('candidaturas', 'visualizar_proprias'),
      ('perfil', 'editar_proprio')
  );
