package br.com.srportto.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Valida os manifestos do plugin e do marketplace do Claude Code (JSON é subconjunto de YAML). */
class PluginManifestoTest {
    private static final Path DIR = Catalogo.RAIZ.resolve(".claude-plugin");
    private static final Path DIR_CODEX = Catalogo.RAIZ.resolve(".codex-plugin");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ler(String arquivo) throws IOException {
        var caminho = DIR.resolve(arquivo);
        assertTrue(Files.isRegularFile(caminho), "manifesto ausente: " + caminho);
        var yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        return (Map<String, Object>) yaml.load(Files.readString(caminho));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> lerCodex(String arquivo) throws IOException {
        var caminho = DIR_CODEX.resolve(arquivo);
        assertTrue(Files.isRegularFile(caminho), "manifesto ausente: " + caminho);
        var yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        return (Map<String, Object>) yaml.load(Files.readString(caminho));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> primeiroPlugin(Map<String, Object> marketplace) {
        var plugins = (List<Map<String, Object>>) marketplace.get("plugins");
        assertNotNull(plugins, "marketplace.json sem 'plugins'");
        assertTrue(!plugins.isEmpty(), "marketplace.json com 'plugins' vazio");
        return plugins.get(0);
    }

    @Test
    void nomeDoPluginEhIdentificadorValidoEIgualAoDoMarketplace() throws IOException {
        var plugin = ler("plugin.json");
        var nome = (String) plugin.get("name");
        assertNotNull(nome, "plugin.json sem 'name'");
        assertEquals("exj", nome);
        assertTrue(nome.matches("^[a-z0-9-]+$"), "name deve ser kebab-case: " + nome);
        assertEquals(nome, primeiroPlugin(ler("marketplace.json")).get("name"));
    }

    @Test
    void versaoSegueSemver() throws IOException {
        var versao = String.valueOf(ler("plugin.json").get("version"));
        assertTrue(versao.matches("\\d+\\.\\d+\\.\\d+"), "version fora de semver: " + versao);
    }

    @Test
    void marketplaceTemNomeDonoEFonteExistente() throws IOException {
        var marketplace = ler("marketplace.json");
        assertEquals("srportto-exj", marketplace.get("name"));
        assertNotNull(marketplace.get("name"), "marketplace sem 'name'");
        assertNotNull(marketplace.get("owner"), "marketplace sem 'owner'");
        var fonte = (String) primeiroPlugin(marketplace).get("source");
        assertNotNull(fonte, "plugin sem 'source'");
        assertTrue(Files.isDirectory(Catalogo.RAIZ.resolve(fonte).normalize()), "source inexistente: " + fonte);
    }

    @Test
    void manifestoCodexDeclaraAsSkillsDoCatalogo() throws IOException {
        var manifesto = lerCodex("plugin.json");
        assertEquals("exj", manifesto.get("name"));
        assertEquals("./skills/", manifesto.get("skills"));
        assertTrue(Files.isDirectory(Catalogo.RAIZ.resolve("skills")));
    }
}
