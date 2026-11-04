package br.com.srportto.catalogo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Garante que as tabelas "Resolução das skills" dos agents apontam para references e assets que existem. */
class AgentsReferencesTest {

    private static final Pattern CAMINHO = Pattern.compile("`((?:[a-z0-9-]+/)?(?:references|assets)/[^`\s]+)`");

    @DisplayName("AgentsReferences: caminhos das tabelas de resolução devem existir na skill")
    @Test void caminhosDevemExistir() throws Exception {
        var erros = new ArrayList<String>();
        int verificados = 0;
        for (Path agent : Catalogo.agents()) {
            String nome = agent.getFileName().toString();
            String texto = Catalogo.ler(agent);
            int ini = texto.indexOf("## Resolução das skills");
            if (ini < 0) continue;
            int fim = texto.indexOf("\n## ", ini + 5);
            String secao = texto.substring(ini, fim < 0 ? texto.length() : fim);
            for (String linha : secao.split("\n")) {
                if (!linha.startsWith("|") || linha.startsWith("|---")) continue;
                String[] celulas = linha.split("\\|");
                if (celulas.length < 4) continue;
                String skill = celulas[2].replace("`", "").trim();
                var m = CAMINHO.matcher(celulas[3]);
                while (m.find()) {
                    String caminho = m.group(1);
                    // prefixo "<skill>/" indica referência a outra skill; senão vale a skill da linha
                    String relativo = caminho.matches("[a-z0-9-]+/(references|assets)/.*") ? caminho : skill + "/" + caminho;
                    verificados++;
                    if (!existe(relativo)) erros.add(nome + " → " + caminho + " (skill " + skill + ")");
                }
            }
        }
        assertTrue(verificados > 0, "nenhum caminho verificado nas tabelas dos agents");
        assertTrue(erros.isEmpty(), () -> "Caminhos inexistentes:\n" + String.join("\n", erros));
    }

    /** Aceita glob simples (`*`) exigindo ao menos um arquivo correspondente. */
    private static boolean existe(String relativo) throws Exception {
        int corte = relativo.lastIndexOf('/');
        Path pasta = Catalogo.RAIZ.resolve("skills").resolve(relativo.substring(0, corte));
        String arquivo = relativo.substring(corte + 1);
        if (!arquivo.contains("*")) return Files.exists(pasta.resolve(arquivo));
        if (!Files.isDirectory(pasta)) return false;
        var glob = pasta.getFileSystem().getPathMatcher("glob:" + arquivo);
        try (Stream<Path> filhos = Files.list(pasta)) {
            return filhos.anyMatch(f -> glob.matches(f.getFileName()));
        }
    }
}
