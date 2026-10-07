package br.com.exemplo.esqueleto.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite real do tamanho do corpo da requisição, JSON incluso.
 *
 * <p>O Tomcat do Spring Boot 4 não tem propriedade que limite corpo JSON: {@code server.tomcat.max-http-form-post-size}
 * vale só para formulário ({@code application/x-www-form-urlencoded}) e {@code server.tomcat.max-swallow-size} só
 * para o descarte do corpo de upload abortado. Por isso o limite fica aqui:
 * <ul>
 *   <li>{@code Content-Length} acima do limite → 413 em Problem Details, sem ler o corpo nem chegar ao controller;</li>
 *   <li>corpo sem tamanho declarado (chunked) → bytes contados na leitura; ao passar do limite,
 *       {@link CorpoExcedidoException} vira 413 no {@link ApiExceptionHandler}.</li>
 * </ul>
 */
public class LimiteCorpoRequisicaoFilter extends OncePerRequestFilter {

    private final long limiteBytes;

    public LimiteCorpoRequisicaoFilter(DataSize limite) {
        if (limite.toBytes() <= 0) {
            throw new IllegalArgumentException("Limite do corpo deve ser positivo");
        }
        this.limiteBytes = limite.toBytes();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        long declarado = requisicao.getContentLengthLong();
        if (declarado > limiteBytes) {
            recusar(resposta);
            return;
        }
        // Com Content-Length o servidor não entrega mais que o declarado; sem ele, o corpo é contado na leitura.
        cadeia.doFilter(declarado >= 0 ? requisicao : new CorpoLimitado(requisicao, limiteBytes), resposta);
    }

    private void recusar(HttpServletResponse resposta) throws IOException {
        resposta.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resposta.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Content Too Large\",\"status\":413,"
                + "\"detail\":\"Corpo da requisição acima de " + limiteBytes + " bytes\"}");
    }

    /** Requisição sem Content-Length: o corpo passa por um contador que interrompe a leitura acima do limite. */
    private static final class CorpoLimitado extends HttpServletRequestWrapper {
        private final long limiteBytes;
        private ServletInputStream corpo;

        CorpoLimitado(HttpServletRequest requisicao, long limiteBytes) {
            super(requisicao);
            this.limiteBytes = limiteBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (corpo == null) {
                corpo = new EntradaLimitada(super.getInputStream(), limiteBytes);
            }
            return corpo;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String codificacao = getCharacterEncoding();
            Charset charset = codificacao != null ? Charset.forName(codificacao) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class EntradaLimitada extends ServletInputStream {
        private final ServletInputStream origem;
        private final long limiteBytes;
        private long lidos;

        EntradaLimitada(ServletInputStream origem, long limiteBytes) {
            this.origem = origem;
            this.limiteBytes = limiteBytes;
        }

        @Override
        public int read() throws IOException {
            int lido = origem.read();
            if (lido >= 0) {
                contar(1);
            }
            return lido;
        }

        @Override
        public int read(byte[] destino, int inicio, int tamanho) throws IOException {
            int lidosAgora = origem.read(destino, inicio, tamanho);
            if (lidosAgora > 0) {
                contar(lidosAgora);
            }
            return lidosAgora;
        }

        private void contar(int quantidade) {
            lidos += quantidade;
            if (lidos > limiteBytes) {
                throw new CorpoExcedidoException(limiteBytes);
            }
        }

        @Override
        public boolean isFinished() {
            return origem.isFinished();
        }

        @Override
        public boolean isReady() {
            return origem.isReady();
        }

        @Override
        public void setReadListener(ReadListener ouvinte) {
            origem.setReadListener(ouvinte);
        }
    }
}
