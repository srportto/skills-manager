# A10 — Server-Side Request Forgery (SSRF)

Leia este arquivo quando o backend fizer request HTTP para uma URL fornecida pelo cliente (webhook, import por URL, avatar, preview de link) ou ao revisar clients HTTP que seguem redirects.

Quando o backend faz request a uma URL fornecida pelo client (webhook, import por URL, avatar):

```java
// ERRADO - aceita qualquer URL; atacante aponta para http://169.254.169.254/... (metadata service)
String url = request.getUrl();
HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

// CORRETO - resolve o host e bloqueia ranges privados/loopback antes de conectar
URI uri = new URI(url);
InetAddress addr = InetAddress.getByName(uri.getHost());
if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isAnyLocalAddress()) {
    throw new BusinessException("URL nao permitida");
}
HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
```

## Versão mais robusta: allowlist + parsing de URI + todos os IPs resolvidos

Bloquear só ranges privados é uma denylist e tem furos (link-local `169.254.x.x`, IPv6, DNS que
resolve para vários IPs, redirect para interno). Prefira **allowlist** de destinos conhecidos e,
quando o destino for livre, valide **todos** os IPs resolvidos e desligue redirects.

```java
// ANTES - valida so o primeiro IP, nao restringe esquema nem porta e segue redirect para o interno
InetAddress addr = InetAddress.getByName(uri.getHost());
if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()) throw new BusinessException("URL nao permitida");

// DEPOIS - esquema https, host na allowlist, todos os IPs publicos, sem redirect
public final class DestinoWebhook {

    private static final Set<String> HOSTS_PERMITIDOS = Set.of("hooks.parceiro.com", "api.pagamentos.com");

    /** Valida a URL informada pelo cliente e devolve a URI segura para chamada. */
    public static URI validar(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new BusinessException("URL invalida");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new BusinessException("URL nao permitida");           // so https, sem user:pass@
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!HOSTS_PERMITIDOS.contains(host)) {
            throw new BusinessException("Host nao permitido");          // allowlist
        }
        try {
            for (InetAddress ip : InetAddress.getAllByName(host)) {      // TODOS os registros A/AAAA
                if (ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress()
                        || ip.isAnyLocalAddress() || ip.isMulticastAddress()) {
                    throw new BusinessException("Destino resolve para rede interna");
                }
            }
        } catch (UnknownHostException e) {
            throw new BusinessException("Host nao resolvido");
        }
        return uri;
    }
}

// cliente sem redirect automatico (um 302 para http://169.254.169.254 contornaria a validacao) e com timeout
HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(3))
        .build();
HttpRequest req = HttpRequest.newBuilder(DestinoWebhook.validar(url))
        .timeout(Duration.ofSeconds(5))
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();
```

Limite conhecido: entre a validação e a conexão o DNS pode mudar (DNS rebinding). Em ambiente
sensível, conecte no IP já validado (resolver customizado) ou isole a saída por egress firewall /
proxy de saída — controle de rede está fora do escopo desta skill (`cloud-architect`).
Em nuvem, exija IMDSv2 para reduzir o impacto de SSRF contra o metadata service.
