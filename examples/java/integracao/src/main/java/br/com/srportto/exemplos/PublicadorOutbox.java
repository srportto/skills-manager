package br.com.srportto.exemplos;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;
import java.util.function.*;

public final class PublicadorOutbox {
    private final DataSource banco;
    public PublicadorOutbox(DataSource banco) { this.banco = Objects.requireNonNull(banco); }

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

