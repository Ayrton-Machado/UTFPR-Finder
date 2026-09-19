Siga KISS e YAGNI como fundamentos para uma boa implementação.
Não crie validações em excesso
Não crie testes em excesso, eles podem acabar virando um segundo sistema e não há nada mais improdutivo que debugar testes
Não crie complexidade em coisas simples, seja eficiente e objetivo
Não deduza regras ou advinhe nada, se não souber ou não tiver certeza PERGUNTE.
Não tente ser independente, somos parceiros e a coordenação pertence a mim
Não realize alterações sem permissão explicita
Não Execute comandos externos sem autorização explicita (ex.: consulta no banco através do docker)
Em respostas e conclusões, seja objetivo.

Para esta base, seguir o escopo e as decisões do README.md.
Usar TDD em cenários relevantes, sem testes redundantes. Persistência é testada em PostgreSQL temporário com Testcontainers, nunca no banco de desenvolvimento.
Toda operação respeita permissões no servidor e contexto transacional de organização; não confiar em IDs de organização recebidos do formulário.
Não registrar dados pessoais ou segredos. Eventos de alteração somente após commit. Não editar migrations já aplicadas em ambiente compartilhado.
