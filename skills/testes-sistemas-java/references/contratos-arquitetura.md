# Contratos, arquitetura e catálogo

Contrato HTTP: métodos, códigos, payload, validação, erros, idempotência, 429/503 e headers. Prefira cliente HTTP Java contra servidor em porta efêmera quando testar o comportamento de borda.

Compatibilidade: campo opcional/aditivo, significado do enum, schema antigo/novo e leitor antigo. Alteração de banco usa expand/contract com janela de convivência e rollback documentado. Ordem de eventos e dados stale fazem parte do contrato.

Arquitetura: domínio puro não depende de Spring/JPA/adapters; dependências atravessam portas. Use ArchUnit quando existir estrutura de camadas; não aplique regra hexagonal a todo exemplo JDK.

Catálogo: parser YAML para identificadores, parser Markdown para links reais (ignorar blocos de exemplo), existência de referências e inventário. Testes não julgam redação por frases exatas. Casos realistas aplicados por outro agente medem qualidade de instruções; registrar saída bruta e rubrica separadamente.

[Validação Java do catálogo](../../../validation/java/pom.xml) não requer containers nem os insumos locais ignorados em .docs.

