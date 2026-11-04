# Decisões de execução — OPSX, exemplos, Floci e Java moderno (2026-10-06)

Registro versionado das decisões tomadas durante a execução do [plano](2026-10-06-opsx-floci-java-moderno.md). Cada
decisão traz o motivo e o custo se estiver errada. Versões e resultados estão em
[compatibilidade](../../catalogo/compatibilidade.md).

## Decisões gerais

| Decisão | Motivo | Custo se errada |
|---|---|---|
| Nenhum commit durante a execução | O usuário aprovou a implementação, mas não autorizou commits | Usuário agrupa e commita o lote |
| Revisão final feita pelo próprio executor | Subagents só com pedido explícito; execução escolhida foi a nativa | Revisão mais fraca que um revisor independente |
| `openspec-*` e `terraform-engineer` alterados | Pedidos explícitos (itens 1 e 3), apesar de serem auxiliares | Nenhum; a validação cobre os ganchos |

## Por tarefa

### T01–T02 — OpenSpec

- Integração em três camadas: `config.yaml` (rules injetadas pelo CLI), skill `openspec-catalogo-java` (mapa de
  fases) e ganchos curtos nas skills geradas. Verificado com o CLI 1.4.1: `rules.apply` é ignorado, por isso a fase
  `apply` é guiada pela skill.
- `openspec init --tools none` cria só `openspec/` (sem `.claude/`); as rules de `design` chegam em
  `openspec instructions design --json`.
- Frontmatter das `openspec-*` mantido como gerado (inclusive o `---` duplicado) para facilitar comparar com o
  upstream. *Custo: aviso visual no Markdown.*
- Comandos `/opsx:*` reduzidos a delegação; o texto duplicado das skills (≈690 linhas) saiu.
- Execução de `/opsx:propose` de ponta a ponta não feita: consome uso pago e não foi autorizada. A prova disponível
  é a do CLI acima e `OpenSpecIntegracaoTest`.

### T03–T06 — exemplos

- `ChamadaComDeadline`: uma leitura do orçamento (antes, prazo zerado entre a checagem e a requisição virava
  `IllegalArgumentException`).
- `CacheProtegido`: o 5º parâmetro mantém a posição, mas muda de semântica — de "quantidade de faixas de lock" para
  "limite de chaves recomputando ao mesmo tempo". Chaves diferentes não se bloqueiam mais por colisão de hash.
  *Custo: chamador externo que passava muitas faixas passa a permitir mais recomputações simultâneas (ainda
  limitadas pelo banco).*
- `ConsumidorSqsLimitado`: falha numa renovação de visibilidade não cancela as seguintes; a renovação é cancelada
  antes de publicar a conclusão.
- `MetricasProtecao`: séries pré-registradas no construtor. O teste de cardinalidade passou de "2 séries" para
  "o total não cresce com valores dinâmicos"; dois testes passaram a filtrar também por operação/dependência.

### T07–T08 — Floci

- Motivo original da troca: `localstack/localstack:latest` (2026.7) passou a exigir licença ("License activation
  failed", exit 55), o que tinha obrigado a fixar a 4.4.
- `io.floci:testcontainers-floci:2.16.1` usa o mesmo Testcontainers 2.0.5 do repositório; imagem `floci/floci:2.2.0`
  subiu em 1,6 s e o `ConsumidorSqsLimitadoExternoIT` (DLQ por `RedrivePolicy`) passou sem mudança de asserção.
- Guarda `EmuladorAwsLocalTest` aceita apenas frases de substituição explícita ("em vez de LocalStack").

### T09–T10 — Java moderno

- Módulo novo `examples/java/linguagem` (somente JDK). `PagamentoTest` compila em memória os erros de
  exaustividade e dominância com `--release 25`.
- `void main()` do JDK 25 verificado com Spring Boot 4.0.7: o `repackage` falha com "Unable to find main class". A
  nota da skill foi mantida, com versão e data.

### T11 — consolidação

- Suíte (após a revisão): validação 28; exemplos 113; `-Pintegracao` 113 + 7 `ExternoIT` (com o intermitente abaixo); `-Pcarga` 1 `CargaIT` (rodado porque o
  checkout usa `MetricasProtecao`).

## Revisão final

Revisor independente (subagent, contexto novo): nenhum Critical, 3 Important corrigidos com teste RED→GREEN.

- **Corrigido:** em `CacheProtegido`, perdedores da chave quente pegavam vaga antes de descobrir a recomputação em
  andamento e faziam outras chaves serem rejeitadas na tempestade. Agora saem antes de disputar vaga
  (`rajadaNaChaveQuenteNaoDeveConsumirVagasDeOutrasChaves`).
- **Corrigido:** `java-moderno` misturava dois modelos de `Pagamento`; as seções 2 e 3 usam agora só o do módulo
  `linguagem` (`JavaModernoTest` barra o modelo antigo).
- **Corrigido:** o portão de archive tinha semânticas diferentes nas duas skills (bloqueia × pergunta). Texto único:
  veredicto ausente = PENDENTE; PENDENTE/REPROVADO bloqueia salvo risco assumido e registrado; "alterou código
  Java" = tarefas ou diff com `.java`, `pom.xml` ou `build.gradle` (`portaoDeArchiveDeveTerAMesmaSemanticaNasDuasSkills`).

**Melhorias menores adiadas** (decisão do usuário):

- `ConsumidorSqsLimitado`: `cancel(false)` não espera renovação já em voo (pode atrasar reentrega uma janela);
  e `consumir` que lança vaza capacidade de `emVoo` (preexistente).
- `EmuladorAwsLocalTest` filtra "target" no caminho absoluto (checkout em pasta com "target" no nome esvazia a guarda).
- `JavaModernoTest` tem termos triviais (`_`, `final`).
- `sealed-e-switch.md`: exemplo de constante de enum qualificada sem fonte executável; frase "enums são final"
  imprecisa (enum com corpo é implicitamente sealed).
- `terraform-engineer/providers.md`: endpoints do Floci no mesmo bloco de provider de produção; faltam flags `skip_*`.
- Tag `-compat` do Floci exige editar `ServicosExternos` (sem propriedade de sistema).
- `openspec-sync-specs` descrito como "com ganchos" sem ter gancho; cabeçalho "preservadas sem reescrita" desatualizado.
- `MetricasProtecao`: pré-registro com histograma de percentis em todas as séries aumenta séries no Prometheus.
- `java-moderno` (preexistente): `var` é do Java 10, não 11; "21 finaliza o Project Loom" é impreciso.

## Pendências

- `ExperimentoCoordenadorLentoExternoIT` intermitente (preexistente, código não tocado neste lote): 2 falhas em 5
  rodadas com 7 permitidos contra limite 6. Detalhe em [compatibilidade](../../catalogo/compatibilidade.md).

- Execução de ponta a ponta do `/opsx:propose` com o catálogo instalado (requer autorização de gasto).
- Cópias instaladas em `~/.claude/` não foram atualizadas: reinstale a partir desta fonte.
- Avaliação dos agents (A05, A07, A08 com nota 1; demais casos pendentes) e primeira execução do workflow no GitHub.
