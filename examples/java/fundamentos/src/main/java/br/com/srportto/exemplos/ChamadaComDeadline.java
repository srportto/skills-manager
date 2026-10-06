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
        // Não inicia I/O sem tempo disponível.
        orcamento.exigirDisponivel();
        var requisicao = HttpRequest.newBuilder(uri).timeout(orcamento.restante()).GET().build();
        try {
            return cliente.send(requisicao, HttpResponse.BodyHandlers.ofString()).body();
        } catch (HttpTimeoutException erro) {
            var timeout = new TimeoutException("Deadline esgotado aguardando " + uri);
            timeout.initCause(erro);
            throw timeout;
        }
    }
}
