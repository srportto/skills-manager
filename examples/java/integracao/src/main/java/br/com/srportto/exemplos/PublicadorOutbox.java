package br.com.srportto.exemplos;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;
import java.util.function.*;

public final class PublicadorOutbox {
    private final DataSource banco;
    public PublicadorOutbox(DataSource banco) { this.banco = Objects.requireNonNull(banco); }

    /**
     * Publica um lote respeitando a quota disponível.
     *
     * <p>Este relay é at-least-once: entre {@code destino.accept(id)} e o
     * {@code UPDATE outbox SET publicado=true}, uma queda do processo provoca reenvio na próxima execução.
     * A deduplicação fica no consumidor, via {@code Idempotency-Key} ou chave de negócio equivalente; o relay
     * não conhece o efeito. Veja {@link ProcessadorIdempotente} como exemplo de consumidor idempotente.</p>
     *
     * @param limite quantidade máxima de eventos consultados, entre 1 e 1000
     * @param quota sinaliza se ainda há capacidade para publicar
     * @param destino publica o identificador no destino externo
     * @return quantidade marcada como publicada
     * @throws SQLException se a consulta ou atualização do outbox falhar
     */
    public int publicar(int limite, BooleanSupplier quota, Consumer<String> destino) throws SQLException {
        if (limite < 1 || limite > 1000) throw new IllegalArgumentException("Lote entre 1 e 1000");
        var ids = new ArrayList<String>();
        try (var conexao = banco.getConnection();
             var consulta = conexao.prepareStatement("SELECT id FROM outbox WHERE publicado=false ORDER BY seq LIMIT ?")) {
            consulta.setInt(1, limite);
            try (var resultado = consulta.executeQuery()) { while (resultado.next()) ids.add(resultado.getString(1)); }
        }
        int enviados = 0;
        for (String id : ids) {
            if (!quota.getAsBoolean()) break;
            // Nenhuma conexão fica retida durante I/O externo.
            destino.accept(id);
            try (var conexao = banco.getConnection();
                 var comando = conexao.prepareStatement("UPDATE outbox SET publicado=true WHERE id=?")) {
                comando.setString(1, id); comando.executeUpdate();
            }
            enviados++;
        }
        return enviados;
    }
}

