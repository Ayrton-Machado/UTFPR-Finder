# UTFPR Finder

MVP para conectar estudantes, universidade e mercado com oportunidades organizadas por campus. Empresas publicam vagas e encontram talentos; professores fazem a curadoria; estudantes recebem somente oportunidades aprovadas para o próprio campus.

## Fluxo do MVP

1. o administrador cria acessos de estudante, empresa ou professor;
2. o estudante completa campus, curso, atividades, competências e links profissionais;
3. a empresa personaliza seu perfil, publica uma oportunidade para um campus e pesquisa talentos;
4. a oportunidade fica `PENDENTE` até a análise de um professor ou administrador;
5. quando aprovada, ela aparece apenas para estudantes do campus selecionado;
6. o estudante se candidata e acompanha o processo;
7. a empresa acompanha as candidaturas das próprias oportunidades.

O escopo continua propositalmente simples. Não há chat, e-mail, notificações, upload de arquivos, recuperação automática de senha, IA, aplicativo mobile, contratos ou pagamentos. Currículo, LinkedIn e portfólio são informados como links.

## Papéis e permissões

- `ADMIN`: gerencia acessos, oportunidades, aprovações e talentos da organização.
- `PROFESSOR`: representa a curadoria da universidade, analisa oportunidades, consulta estudantes e acompanha candidaturas.
- `EMPRESA`: mantém o perfil da empresa, publica oportunidades, pesquisa estudantes e acompanha candidaturas das próprias vagas.
- `ESTUDANTE`: mantém o próprio perfil, consulta oportunidades aprovadas do próprio campus e acompanha as próprias candidaturas.

As permissões são separadas dos papéis e armazenadas por usuário, recurso e ação:

| Recurso | Ações |
| --- | --- |
| `usuarios` | `visualizar`, `criar` |
| `oportunidades` | `visualizar`, `criar`, `editar`, `aprovar` |
| `estudantes` | `buscar` |
| `candidaturas` | `criar`, `visualizar_proprias`, `visualizar_organizacao`, `alterar_status` |
| `perfil` | `editar_proprio`, `editar_empresa` |

Cada papel recebe um conjunto padrão mínimo. Não existe uma tela de edição granular de permissões nesta versão.

## Regras principais

### Campus

- O campus do perfil do estudante define sua fronteira de visualização.
- Uma oportunidade pertence a exatamente um campus, mesmo quando a modalidade é remota.
- Estudantes só visualizam oportunidades `APROVADA` cujo campus seja igual ao do perfil.
- A regra é aplicada na aplicação e novamente no PostgreSQL por RLS.
- Professores e empresas podem filtrar oportunidades e talentos por campus.

### Oportunidades e aprovação

- Tipos: `ESTAGIO`, `EMPREGO`, `PROJETO`, `EVENTO`, `DESAFIO` e `CAPACITACAO`.
- Estados: `RASCUNHO`, `PENDENTE`, `APROVADA`, `REJEITADA` e `ENCERRADA`.
- Uma oportunidade enviada pela empresa entra como `PENDENTE`.
- Somente professor ou administrador pode aprovar ou rejeitar.
- A empresa administra somente oportunidades e candidaturas publicadas por seu usuário.

### Perfis e talentos

- O estudante informa campus, curso, período, resumo, atividades, competências, currículo, LinkedIn e portfólio.
- A empresa informa apresentação, áreas de interesse, competências buscadas, site e LinkedIn.
- Empresas, professores e administradores podem pesquisar estudantes por nome, campus, curso, competências e atividades.
- A busca é textual e explicável; não usa IA.

### Compatibilidade e candidaturas

A compatibilidade compara curso e competências do perfil com os requisitos da oportunidade. Cada critério atendido vale um ponto. Uma oportunidade sem critérios específicos é compatível com todos.

- Um estudante pode se candidatar uma única vez por oportunidade.
- A candidatura só é aceita se a oportunidade estiver aprovada, dentro do prazo e no campus do estudante.
- Estados: `ENVIADA`, `EM_ANALISE`, `ACEITA` e `RECUSADA`.

## Banco de dados e segurança

PostgreSQL único com schemas separados por domínio:

```text
organizacoes.organizacao
acesso.usuario
acesso.permissao
acesso.usuario_permissao
estudantes.perfil
empresas.perfil
oportunidades.oportunidade
candidaturas.candidatura
```

Todas as tabelas de negócio carregam `organizacao_id`. Chaves estrangeiras compostas impedem relações entre organizações diferentes.

O processo web usa um papel PostgreSQL restrito, sem `SUPERUSER` e sem `BYPASSRLS`. A aplicação define `organizacao_id` e `usuario_id` em cada transação. As políticas RLS garantem:

- isolamento entre organizações;
- estudante limitado ao próprio perfil;
- estudante limitado a oportunidades aprovadas do próprio campus;
- empresa limitada ao próprio perfil;
- candidaturas próprias ou organizacionais conforme a permissão.

As autorizações do Spring Security continuam sendo a primeira barreira; a RLS protege contra consultas incorretas e vazamentos acidentais.

## Arquitetura

Monólito modular orientado a domínios, com interface server-side:

```text
com.utfpr.finder
├── acesso
├── estudantes
├── empresas
├── oportunidades
└── candidaturas
```

Controllers chamam fachadas transacionais; as fachadas aplicam autorização, contexto RLS, regras e persistência. Não há SPA nem API separada no MVP.

## Stack

- Java 21 e Spring Boot 4.1.1.
- Spring Modulith 2.1.1.
- Spring MVC, Thymeleaf e CSS local.
- Spring Security com sessão HTTP e CSRF.
- PostgreSQL 18, JdbcClient e Flyway.
- Testcontainers com PostgreSQL para os testes.
- Docker Compose para o banco local.

## Execução local

Pré-requisitos: Java 21, Maven e Docker com Compose.

```bash
cp .env.example .env
docker compose up -d --wait
mvn -Dspring-boot.run.profiles=bootstrap spring-boot:run
mvn -Dspring-boot.run.profiles=seed spring-boot:run
mvn spring-boot:run
```

A aplicação fica disponível em `http://localhost:8070`.

O bootstrap e o seeder são idempotentes. O administrador local inicial é:

- login: `admin`
- senha: `admin-local-123`

O seeder adiciona os seguintes cenários, todos com a senha `demo-local-123`:

| Papel | Login | Campus/contexto |
| --- | --- | --- |
| Empresa | `empresa.demo` | publica vagas e pesquisa talentos |
| Professor | `professor.demo` | aprova ou rejeita oportunidades |
| Estudante | `ana.demo` | Campo Mourão |
| Estudante | `lucas.demo` | Francisco Beltrão |

Execute os testes com:

```bash
mvn clean test
```

## Critério de conclusão

O MVP está demonstrável quando uma empresa envia uma oportunidade para um campus, um professor a aprova, apenas estudantes daquele campus conseguem visualizá-la e se candidatar, e a empresa encontra talentos e acompanha as candidaturas recebidas.
