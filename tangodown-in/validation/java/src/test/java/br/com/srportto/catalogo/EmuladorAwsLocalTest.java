package br.com.srportto.catalogo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regra do catálogo: AWS local é emulada pelo Floci; LocalStack não volta a exemplos, skills, agents ou docs. */
class EmuladorAwsLocalTest {
    private static final Pattern LOCALSTACK = Pattern.compile("(?i)localstack");

    static List<Path> arquivos(String raiz, List<String> extensoes) {
        Path base = Catalogo.RAIZ.resolve(raiz);
        if (!Files.isDirectory(base)) return List.of();
        try (Stream<Path> caminhos = Files.walk(base)) {
            return caminhos.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("target"))
                    .filter(p -> extensoes.stream().anyMatch(p.toString()::endsWith))
                    .sorted().toList();
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    @DisplayName("EmuladorAwsLocal: Exemplos executaveis devem usar Floci e nao LocalStack")
    @Test
    void exemplosExecutaveisDevemUsarFlociENaoLocalStack() {
        var erros = new ArrayList<String>();
        for (Path arquivo : arquivos("examples/java", List.of(".java", ".xml", ".md", ".yaml", ".yml", ".properties"))) {
            if (LOCALSTACK.matcher(Catalogo.ler(arquivo)).find()) erros.add(Catalogo.relativo(arquivo));
        }
        String pom = Catalogo.ler(Catalogo.RAIZ.resolve("examples/java/integracao/pom.xml"));
        if (!pom.contains("<artifactId>testcontainers-floci</artifactId>")) erros.add("integracao/pom.xml sem testcontainers-floci");
        assertTrue(erros.isEmpty(), () -> "LocalStack/Floci:\n" + String.join("\n", erros));
    }

    /** Frase de substituição explícita ("em vez de LocalStack") é permitida; uso como ferramenta, não. */
    private static final Pattern SUBSTITUICAO = Pattern.compile("(?i)(em vez de|ao invés de|não use|nunca)\\s+(o\\s+)?localstack");

    @DisplayName("EmuladorAwsLocal: Skills agents e docs do catalogo devem indicar Floci")
    @Test
    void skillsAgentsEDocsDoCatalogoDevemIndicarFloci() {
        var erros = new ArrayList<String>();
        for (String raiz : List.of("skills", "agents", "docs/catalogo")) {
            for (Path arquivo : arquivos(raiz, List.of(".md", ".yaml", ".yml"))) {
                String texto = Catalogo.ler(arquivo);
                texto.lines().filter(linha -> LOCALSTACK.matcher(linha).find())
                        .filter(linha -> !SUBSTITUICAO.matcher(linha).find())
                        .forEach(linha -> erros.add(Catalogo.relativo(arquivo) + ": " + linha.strip()));
            }
        }
        String convencoes = Catalogo.ler(Catalogo.RAIZ.resolve("docs/catalogo/convencoes.md"));
        if (!convencoes.contains("Floci")) erros.add("convencoes.md sem a regra de AWS local (Floci)");
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }
}
