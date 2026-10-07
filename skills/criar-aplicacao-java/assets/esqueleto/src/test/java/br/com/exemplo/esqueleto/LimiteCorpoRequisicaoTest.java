package br.com.exemplo.esqueleto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Prova que o limite do corpo vale para JSON: com Content-Length e sem ele (chunked), acima do limite → 413. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.http.limite-corpo=1KB")
@Import(LimiteCorpoRequisicaoTest.EcoController.class)
class LimiteCorpoRequisicaoTest {

    /** Endpoint de teste que lê o corpo JSON (o esqueleto não tem POST de negócio). */
    @RestController
    static class EcoController {
        @PostMapping("/eco")
        Map<String, Object> eco(@RequestBody Map<String, Object> corpo) {
            return corpo;
        }
    }

    @LocalServerPort
    int porta;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private static String json(int tamanhoTexto) {
        return "{\"texto\":\"" + "a".repeat(tamanhoTexto) + "\"}";
    }

    private HttpResponse<String> postar(BodyPublisher corpo) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/eco"))
                .header("Content-Type", "application/json")
                .POST(corpo)
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /** Sem tamanho conhecido de antemão, o HttpClient envia o corpo chunked, sem Content-Length. */
    private static BodyPublisher chunked(String corpo) {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        return BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes));
    }

    @Test
    @DisplayName("Corpo JSON dentro do limite chega ao controller, com e sem Content-Length")
    void dentroDoLimite() throws Exception {
        assertEquals(200, postar(BodyPublishers.ofString(json(100))).statusCode());
        assertEquals(200, postar(chunked(json(100))).statusCode());
    }

    @Test
    @DisplayName("Content-Length acima do limite devolve 413 em Problem Details antes do controller")
    void contentLengthAcimaDoLimite() throws Exception {
        var resposta = postar(BodyPublishers.ofString(json(2_000)));
        assertEquals(413, resposta.statusCode());
        assertTrue(resposta.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        assertTrue(resposta.body().contains("\"status\":413"), resposta.body());
    }

    @Test
    @DisplayName("Corpo chunked acima do limite é interrompido na leitura e devolve 413")
    void chunkedAcimaDoLimite() throws Exception {
        var resposta = postar(chunked(json(2_000)));
        assertEquals(413, resposta.statusCode());
        assertTrue(resposta.body().contains("\"status\":413"), resposta.body());
    }
}
