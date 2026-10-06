package br.com.srportto.exemplos;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Fallback semanticamente seguro para leitura não crítica (ex.: recomendações): devolve o último valor
 * conhecido com idade visível, dentro de um frescor máximo. Nunca inventa valor para chave desconhecida.
 * Não use este padrão para confirmar pagamento, estoque ou autorização.
 */
public final class FallbackDegradado<T> {
    @FunctionalInterface
    public interface Fonte<T> { T obter(String chave) throws Exception; }

    public enum Origem { PRIMARIA, ULTIMO_VALOR_CONHECIDO }

    public record Resposta<T>(T valor, Origem origem, Duration idade) {}

    public static final class Indisponivel extends Exception {
        Indisponivel(String mensagem, Throwable causa) { super(mensagem, causa); }
    }

    private record Entrada<T>(T valor, long gravadoEm) {}

    private final Fonte<T> primaria;
    private final long frescorMaximoNanos;
    private final LongSupplier relogio;
    private final Map<String, Entrada<T>> ultimos;

    public FallbackDegradado(Fonte<T> primaria, Duration frescorMaximo, int maximoEntradas, LongSupplier relogio) {
        if (maximoEntradas <= 0 || frescorMaximo.isNegative()) throw new IllegalArgumentException("Limites inválidos");
        this.primaria = Objects.requireNonNull(primaria);
        this.frescorMaximoNanos = frescorMaximo.toNanos();
        this.relogio = Objects.requireNonNull(relogio);
        // LRU limitado: o fallback não pode crescer sem limite de memória.
        this.ultimos = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entrada<T>> maisAntiga) {
                return size() > maximoEntradas;
            }
        };
    }

    public Resposta<T> obter(String chave) throws Indisponivel {
        Objects.requireNonNull(chave);
        try {
            T valor = primaria.obter(chave);
            synchronized (ultimos) {
                ultimos.put(chave, new Entrada<>(valor, relogio.getAsLong()));
            }
            return new Resposta<>(valor, Origem.PRIMARIA, Duration.ZERO);
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            throw new Indisponivel("Interrompido", erro);
        } catch (Exception falhaPrimaria) {
            return degradar(chave, falhaPrimaria);
        }
    }

    private Resposta<T> degradar(String chave, Exception causa) throws Indisponivel {
        Entrada<T> entrada;
        synchronized (ultimos) {
            entrada = ultimos.get(chave);
        }
        if (entrada == null) throw new Indisponivel("Sem valor conhecido para " + chave, causa);
        long idade = relogio.getAsLong() - entrada.gravadoEm();
        if (idade > frescorMaximoNanos) throw new Indisponivel("Valor conhecido excede o frescor máximo", causa);
        return new Resposta<>(entrada.valor(), Origem.ULTIMO_VALOR_CONHECIDO, Duration.ofNanos(idade));
    }

    public int entradas() {
        synchronized (ultimos) {
            return ultimos.size();
        }
    }
}
