package br.com.exemplo.esqueleto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Prova que o esqueleto sobe sem infra externa e responde smoke test e probes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EsqueletoApplicationTest {

    @LocalServerPort
    int porta;

    private HttpResponse<String> get(String caminho) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).GET().build();
        return HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("GET /disponibilidade responde 200 com o nome da aplicação e status DISPONIVEL")
    void disponibilidade() throws Exception {
        var resposta = get("/disponibilidade");
        assertEquals(200, resposta.statusCode());
        assertTrue(resposta.body().contains("\"aplicacao\":\"esqueleto-aplicacao\""), resposta.body());
        assertTrue(resposta.body().contains("\"status\":\"DISPONIVEL\""), resposta.body());
    }

    @Test
    @DisplayName("Probes liveness e readiness do Actuator respondem 200")
    void probes() throws Exception {
        assertEquals(200, get("/actuator/health/liveness").statusCode());
        assertEquals(200, get("/actuator/health/readiness").statusCode());
    }
}
