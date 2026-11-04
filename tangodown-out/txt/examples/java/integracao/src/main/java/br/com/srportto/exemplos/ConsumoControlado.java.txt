package br.com.srportto.exemplos;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Ponto central de decisão do consumo (equivalente ao handler central de erros HTTP): aplica o efeito,
 * classifica a falha e decide entre confirmar ou reentregar. Mensagem com falha permanente só é confirmada
 * depois de copiada de forma durável para a quarentena (DLQ/DLT); sem essa cópia, confirmar seria perder dado.
 */
public final class ConsumoControlado<M> {
    @FunctionalInterface
    public interface Efeito<M> { void aplicar(M mensagem) throws Exception; }

    @FunctionalInterface
    public interface Quarentena<M> { void enviar(M mensagem, Exception causa) throws Exception; }

    public enum Decisao { CONFIRMAR, REENTREGAR }

    public record Resultado(Decisao decisao, Exception erro) {
        static Resultado confirmar() { return new Resultado(Decisao.CONFIRMAR, null); }
        static Resultado reentregar(Exception erro) { return new Resultado(Decisao.REENTREGAR, erro); }
    }

    private final Efeito<M> efeito;
    private final Predicate<Exception> permanente;
    private final Quarentena<M> quarentena;

    public ConsumoControlado(Efeito<M> efeito, Predicate<Exception> permanente, Quarentena<M> quarentena) {
        this.efeito = Objects.requireNonNull(efeito);
        this.permanente = Objects.requireNonNull(permanente);
        this.quarentena = Objects.requireNonNull(quarentena);
    }

    public Resultado consumir(M mensagem) {
        try {
            efeito.aplicar(mensagem);
            return Resultado.confirmar();
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            return Resultado.reentregar(erro);
        } catch (Exception erro) {
            // Falha desconhecida é tratada como transitória: repetir é mais seguro que descartar.
            return permanente.test(erro) ? quarentenar(mensagem, erro) : Resultado.reentregar(erro);
        }
    }

    /** Usado também quando as tentativas de uma falha transitória se esgotam. */
    public Resultado quarentenar(M mensagem, Exception causa) {
        try {
            quarentena.enviar(mensagem, causa);
            return Resultado.confirmar();
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            return Resultado.reentregar(erro);
        } catch (Exception falhaQuarentena) {
            falhaQuarentena.addSuppressed(causa);
            return Resultado.reentregar(falhaQuarentena);
        }
    }
}
