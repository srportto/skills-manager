package br.com.srportto.exemplos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagamentoTest {
    @TempDir Path destino;

    /** Compila em memória com --release 25 (sem preview) e devolve as mensagens de erro. */
    private List<String> compilar(String fonte) {
        var diagnosticos = new DiagnosticCollector<JavaFileObject>();
        var arquivo = new SimpleJavaFileObject(URI.create("string:///Uso.java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignorarErros) { return fonte; }
        };
        ToolProvider.getSystemJavaCompiler()
                .getTask(null, null, diagnosticos, List.of("--release", "25", "-d", destino.toString()), null, List.of(arquivo))
                .call();
        return diagnosticos.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(Locale.ROOT)).toList();
    }

    private static String hierarquiaCom(String casos) {
        return """
                sealed interface Meio permits Pix, Cartao, Boleto {}
                record Pix() implements Meio {}
                record Cartao() implements Meio {}
                record Boleto() implements Meio {}
                class Uso {
                    int taxa(Meio meio) {
                        return switch (meio) {
                %s
                        };
                    }
                }
                """.formatted(casos);
    }

    @DisplayName("Pagamento: Hierarquia selada deve permitir somente os tipos declarados")
    @Test
    void hierarquiaSeladaDevePermitirSomenteOsTiposDeclarados() {
        assertTrue(Pagamento.class.isSealed());
        assertEquals(Set.of(Pagamento.Pix.class, Pagamento.Cartao.class, Pagamento.Boleto.class),
                Set.of(Pagamento.class.getPermittedSubclasses()));
    }

    @DisplayName("Pagamento: Valor nao positivo deve ser rejeitado na construcao")
    @Test
    void valorNaoPositivoDeveSerRejeitadoNaConstrucao() {
        assertThrows(IllegalArgumentException.class, () -> new Pagamento.Pix("chave", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new Pagamento.Cartao(Pagamento.Bandeira.ELO, 13, BigDecimal.TEN));
    }

    @DisplayName("Pagamento: Switch sem default deve quebrar a compilacao quando falta um subtipo")
    @Test
    void switchSemDefaultDeveQuebrarACompilacaoQuandoFaltaUmSubtipo() {
        // Controle positivo: todos os subtipos tratados compila sem erro.
        assertEquals(List.of(), compilar(hierarquiaCom("case Pix p -> 0; case Cartao c -> 3; case Boleto b -> 2;")));
        List<String> erros = compilar(hierarquiaCom("case Pix p -> 0; case Cartao c -> 3;"));
        assertTrue(erros.stream().anyMatch(e -> e.contains("does not cover all possible input values")), erros::toString);
    }

    @DisplayName("Pagamento: Caso geral antes do especifico deve ser erro de dominancia")
    @Test
    void casoGeralAntesDoEspecificoDeveSerErroDeDominancia() {
        List<String> erros = compilar(hierarquiaCom("case Meio m -> 1; case Pix p -> 0;"));
        assertTrue(erros.stream().anyMatch(e -> e.contains("dominated")), erros::toString);
    }
}
