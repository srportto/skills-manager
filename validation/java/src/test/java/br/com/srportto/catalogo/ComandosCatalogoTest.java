package br.com.srportto.catalogo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Garante que os comandos de fluxo só citam skills e agents que existem no catálogo. */
class ComandosCatalogoTest {

    /** Tokens kebab-case entre crases que não são skill nem agent (nenhum hoje; justifique cada entrada). */
    private static final Set<String> EXCECOES = Set.of();

    @DisplayName("ComandosCatalogo: fluxos EXJ usam o namespace curto e OpenSpec mantém opsx")
    @Test void comandosPublicosUsamNamespaceExj() throws Exception {
        for (var nome : List.of("app", "feat", "ref", "rev", "adr", "av", "val")) {
            assertTrue(Files.isRegularFile(Catalogo.RAIZ.resolve("commands/exj/" + nome + ".md")), nome);
        }
        for (var nome : List.of("apply", "archive", "explore", "propose", "sync")) {
            assertTrue(Files.isRegularFile(Catalogo.RAIZ.resolve("commands/opsx/" + nome + ".md")), nome);
        }
        try (var arquivos = Files.walk(Catalogo.RAIZ.resolve("commands"))) {
            assertTrue(arquivos.filter(Files::isRegularFile).map(Catalogo::relativo)
                    .noneMatch(p -> p.startsWith("commands/java/")
                            || p.startsWith("commands/arq/")
                            || p.startsWith("commands/catalogo/")));
        }
    }

    @DisplayName("ComandosCatalogo: nomes citados em comandos devem existir")
    @Test void nomesCitadosDevemExistir() throws Exception {
        Set<String> skills = Catalogo.diretorios("skills").stream().map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        Set<String> agents = Catalogo.agents().stream().map(p -> p.getFileName().toString().replace(".md", "")).collect(Collectors.toSet());
        var citado = Pattern.compile("`([a-z0-9]+(?:-[a-z0-9]+)+)`");
        var erros = new ArrayList<String>();
        try (var arquivos = Files.walk(Catalogo.RAIZ.resolve("commands"))) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".md")).toList()) {
                var m = citado.matcher(Catalogo.ler(arquivo));
                while (m.find()) {
                    String nome = m.group(1);
                    if (!skills.contains(nome) && !agents.contains(nome) && !EXCECOES.contains(nome)) erros.add(Catalogo.relativo(arquivo) + " → " + nome);
                }
            }
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @DisplayName("ComandosCatalogo: README apresenta as entradas públicas atuais")
    @Test void readmeApresentaEntradasPublicasAtuais() throws Exception {
        var readme = Files.readString(Catalogo.RAIZ.resolve("README.md"));
        assertTrue(readme.contains("$exj"), "README sem a skill de entrada EXJ");
        for (var nome : List.of("app", "feat", "ref", "rev", "adr", "av", "val")) {
            assertTrue(readme.contains("/exj:" + nome), "README sem /exj:" + nome);
        }
        for (var nome : List.of("apply", "archive", "explore", "propose", "sync")) {
            assertTrue(readme.contains("/opsx:" + nome), "README sem /opsx:" + nome);
        }
        for (var antigo : List.of("/java:nova-app", "/java:feature", "/java:refatorar",
                "/java:revisar", "/arq:adr", "/catalogo:avaliar", "/catalogo:validar")) {
            assertTrue(!readme.contains(antigo), "README ainda recomenda " + antigo);
        }
    }
}
