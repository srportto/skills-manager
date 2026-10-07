# Roteamento, DNS, balanceamento e borda

Desenhe caminho cliente → DNS → CDN/WAF → LB/gateway → aplicação Java → dependências. Em cada salto marque TLS, autenticação, timeout, retry, cache, limite e responsabilidade.

DNS: TTL e caches do cliente/resolver tornam failover não instantâneo. Health check de DNS, roteamento geográfico e balanceamento regional não resolvem consistência dos dados. Meça tempo de detecção, propagação e recuperação.

L4 decide por conexão/transporte; L7 inspeciona protocolo e permite roteamento por host/path. Round-robin pressupõe custo semelhante; least-connections usa conexões como aproximação de carga; hashing ajuda afinidade, mas pode concentrar hot keys. Sessão stateful exige estratégia explícita de replicação/afinidade e falha.

Gateway/proxy: autenticação de borda não substitui autorização no serviço. Aceite headers encaminhados apenas de proxies confiáveis; IP não é identidade universal para quota. Configure body/header limits, buffering, deadline e dono do retry para não multiplicar tentativas.

CDN: cache key inclui dimensões relevantes (idioma/versão/tenant quando aplicável); conteúdo autenticado exige política explícita contra vazamento entre usuários. Defina TTL, invalidação, stale e proteção de origin. Edge reduz latência de leitura, não elimina sincronização/consistência de escrita.

No Java, configure timeout de conexão e de requisição no HttpClient ou cliente gerenciado pelo Spring; use pooling e orçamento restante. WebSocket/gRPC de longa duração exigem limites por conexão, reconexão com jitter e drenagem no deploy.

Prova: failover DNS com caches; LB remove instância indisponível; overload resulta em rejeição previsível; headers não burlam limite; CDN não mistura conteúdo privado. Configuração cloud pertence à skill `cloud-architect` (agent `arquiteto-cloud`); contrato HTTP a api-rest-design.

