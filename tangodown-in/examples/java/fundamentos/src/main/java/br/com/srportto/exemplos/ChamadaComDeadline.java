package br.com.srportto.exemplos;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Cliente HTTP do JDK com timeout de conexão fixo e timeout de requisição derivado do orçamento restante.
 * Esgotar o prazo devolve o controle ao chamador, mas não cancela o trabalho já iniciado no servidor.
 */
public final class ChamadaComDeadline {
    private final HttpClient cliente;

    public ChamadaComDeadline(Duration timeoutConexao) {
        cliente = HttpClient.newBuilder().connectTimeout(timeoutConexao).build();
    }

    public String obter(URI uri, OrcamentoTempo orcamento) throws IOException, InterruptedException, TimeoutException {
        // Uma única leitura: checar e usar o mesmo valor evita prazo zerado entre a checagem e a requisição.
        Duration restante = orcamento.restante();
        if (restante.isZero()) throw new TimeoutException("Deadline esgotado antes de chamar " + uri);
        var requisicao = HttpRequest.newBuilder(uri).timeout(restante).GET().build();
        try {
            return cliente.send(requisicao, HttpResponse.BodyHandlers.ofString()).body();
        } catch (HttpTimeoutException erro) {
            var timeout = new TimeoutException("Deadline esgotado aguardando " + uri);
            timeout.initCause(erro);
            throw timeout;
        }
    }
}
