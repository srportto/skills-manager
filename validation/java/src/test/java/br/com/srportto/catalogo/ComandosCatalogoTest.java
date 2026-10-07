package br.com.srportto.catalogo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Garante que os comandos de fluxo só citam skills e agents que existem no catálogo. */
class ComandosCatalogoTest {

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
                    boolean pareceCatalogo = nome.endsWith("-java") || nome.startsWith("openspec-") || nome.contains("revisor")
                            || nome.contains("construtor") || nome.contains("arquiteto") || nome.contains("refatorador");
                    if (pareceCatalogo && !skills.contains(nome) && !agents.contains(nome)) erros.add(Catalogo.relativo(arquivo) + " → " + nome);
                }
            }
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }
}
