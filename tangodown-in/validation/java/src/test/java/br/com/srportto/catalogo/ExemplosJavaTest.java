package br.com.srportto.catalogo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExemplosJavaTest {
    /** Linguagens de programação que não podem aparecer como exemplo da trilha Java. */
    private static final Set<String> PROIBIDAS = Set.of(
            "python", "py", "javascript", "js", "typescript", "ts", "go", "golang",
            "kotlin", "kt", "groovy", "scala", "ruby", "rb", "csharp", "cs", "rust");

    /**
     * Baseline de conteúdo importado ainda não reescrito. Cada entrada deve continuar necessária:
     * ao converter o arquivo para Java, o teste exige remover a exceção.
     */
    private static final Map<String, String> EXCECOES_LEGADO = Map.of();

    /** Avaliações EXJ registram literalmente exemplos fora da trilha Java para testar o roteamento. */
    private static final Set<String> EXCECOES_EVAL_EXJ = Set.of(
            "docs/catalogo/avaliacoes/2026-10-08/exj-05-a.md",
            "docs/catalogo/avaliacoes/2026-10-08/exj-05-b.md");

    private static final Pattern ABERTURA = Pattern.compile("(?m)^\\s*(?:```|~~~)\\s*([A-Za-z0-9_+#-]+)");
    private static final Set<String> EXTENSOES_PROIBIDAS = Set.of(
            ".py", ".js", ".mjs", ".ts", ".go", ".kt", ".kts", ".groovy", ".scala", ".rb", ".cs", ".rs");

    private static List<String> linguagensProibidas(Path arquivo) {
        var achadas = new ArrayList<String>();
        var m = ABERTURA.matcher(Catalogo.ler(arquivo));
        while (m.find()) {
            String linguagem = m.group(1).toLowerCase();
            if (PROIBIDAS.contains(linguagem)) achadas.add(linguagem);
        }
        return achadas;
    }

    @DisplayName("ExemplosJava: Blocos de codigo da trilha devem ser java ou configuracao")
    @Test
    void blocosDeCodigoDaTrilhaDevemSerJavaOuConfiguracao() {
        var erros = new ArrayList<String>();
        for (Path arquivo : Catalogo.markdownDaTrilha()) {
            String nome = Catalogo.relativo(arquivo);
            var achadas = linguagensProibidas(arquivo);
            if (!achadas.isEmpty() && !EXCECOES_LEGADO.containsKey(nome) && !EXCECOES_EVAL_EXJ.contains(nome)) {
                erros.add(nome + " → exemplo em linguagem não Java: " + achadas);
            }
        }
        assertTrue(erros.isEmpty(), () -> erros.size() + " problema(s):\n" + String.join("\n", erros));
    }

    @DisplayName("ExemplosJava: Excecoes de legado devem continuar necessarias")
    @Test
    void excecoesDeLegadoDevemContinuarNecessarias() {
        var erros = new ArrayList<String>();
        EXCECOES_LEGADO.forEach((nome, motivo) -> {
            Path arquivo = Catalogo.RAIZ.resolve(nome);
            if (!Files.exists(arquivo) || linguagensProibidas(arquivo).isEmpty()) {
                erros.add(nome + " já não precisa da exceção (" + motivo + ")");
            }
        });
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @DisplayName("ExemplosJava: Avaliacoes EXJ fora do escopo devem continuar registrando Python")
    @Test
    void excecoesDeEvalExjDevemContinuarNecessarias() {
        var erros = new ArrayList<String>();
        EXCECOES_EVAL_EXJ.forEach(nome -> {
            Path arquivo = Catalogo.RAIZ.resolve(nome);
            if (!Files.exists(arquivo) || !linguagensProibidas(arquivo).contains("python")) {
                erros.add(nome + " não registra mais o exemplo Python esperado da avaliação EXJ");
            }
        });
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @DisplayName("ExemplosJava: Modulos executaveis devem conter somente fontes java")
    @Test
    void modulosExecutaveisDevemConterSomenteFontesJava() throws IOException {
        var erros = new ArrayList<String>();
        for (String raiz : List.of("examples", "validation")) {
            try (Stream<Path> caminhos = Files.walk(Catalogo.RAIZ.resolve(raiz))) {
                caminhos.filter(Files::isRegularFile)
                        .filter(p -> !p.toString().contains("target"))
                        .filter(p -> EXTENSOES_PROIBIDAS.stream().anyMatch(p.toString()::endsWith))
                        .forEach(p -> erros.add(Catalogo.relativo(p) + " → fonte em linguagem não Java"));
            }
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @DisplayName("ExemplosJava: Cada exemplo deve ter prova com mesmo nome")
    @Test
    void cadaExemploDeveTerProvaComMesmoNome() throws IOException {
        var erros = new ArrayList<String>();
        for (Path modulo : Catalogo.diretorios("examples/java")) {
            Path principal = modulo.resolve("src/main/java");
            if (!Files.isDirectory(principal)) continue;
            Path testes = modulo.resolve("src/test/java");
            try (Stream<Path> fontes = Files.walk(principal)) {
                for (Path fonte : fontes.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String classe = fonte.getFileName().toString().replace(".java", "");
                    Path pacote = principal.relativize(fonte.getParent());
                    boolean provada = Stream.of("Test", "IT", "ExternoIT", "CargaIT")
                            .anyMatch(sufixo -> Files.exists(testes.resolve(pacote).resolve(classe + sufixo + ".java")));
                    if (!provada) erros.add(Catalogo.relativo(fonte) + " → sem " + classe + "Test/IT correspondente");
                }
            }
        }
        assertTrue(erros.isEmpty(), () -> erros.size() + " problema(s):\n" + String.join("\n", erros));
    }
}
