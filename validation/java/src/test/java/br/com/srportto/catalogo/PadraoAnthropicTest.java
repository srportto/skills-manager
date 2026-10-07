package br.com.srportto.catalogo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regras estruturais do padrão de skill da Anthropic (skill-creator: "Anatomy of a Skill"). */
class PadraoAnthropicTest {

    // Violações conhecidas no início do plano; cada task remove as suas. Só pode encolher.
    static final Set<String> PENDENCIAS = Set.of();

    static final int MAX_LINHAS_SKILL = 500;
    static final int LINHAS_EXIGE_SUMARIO = 300;

    private static List<Path> arquivos(Path pasta) {
        if (!Files.isDirectory(pasta)) return List.of();
        try (Stream<Path> itens = Files.list(pasta)) {
            return itens.sorted().toList();
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    private static void registrar(List<String> erros, String chave, String mensagem) {
        if (!PENDENCIAS.contains(chave)) erros.add(chave + " → " + mensagem);
    }

    @DisplayName("PadraoAnthropic: SKILL.md deve ter no maximo 500 linhas")
    @Test void skillDeveTerNoMaximo500Linhas() {
        var erros = new ArrayList<String>();
        for (Path pasta : Catalogo.skillsDaTrilha()) {
            long linhas = Catalogo.ler(pasta.resolve("SKILL.md")).lines().count();
            if (linhas > MAX_LINHAS_SKILL) {
                registrar(erros, pasta.getFileName() + ":tamanho", linhas + " linhas; mova seções para references/");
            }
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @DisplayName("PadraoAnthropic: name e description devem respeitar os limites")
    @Test void nameEDescriptionDevemRespeitarLimites() throws Exception {
        var erros = new ArrayList<String>();
        for (Path pasta : Catalogo.skillsDaTrilha()) {
            Map<String, Object> dados = CatalogoEstruturaTest.metadados(pasta.resolve("SKILL.md"));
            String nome = String.valueOf(dados.get("name"));
            String descricao = String.valueOf(dados.get("description"));
            if (!nome.matches("[a-z0-9-]{1,64}")) erros.add(nome + " → name fora de ^[a-z0-9-]{1,64}$");
            if (descricao.length() > 1024) erros.add(nome + " → description com " + descricao.length() + " caracteres");
            if (descricao.contains("<") || descricao.contains(">")) erros.add(nome + " → description com '<' ou '>'");
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @DisplayName("PadraoAnthropic: references devem ter um nivel e ser linkadas pelo SKILL.md")
    @Test void referencesDevemSerLinkadasPeloSkill() {
        var erros = new ArrayList<String>();
        for (Path pasta : Catalogo.skillsDaTrilha()) {
            String skill = Catalogo.ler(pasta.resolve("SKILL.md"));
            for (Path ref : arquivos(pasta.resolve("references"))) {
                String rel = "references/" + ref.getFileName();
                if (Files.isDirectory(ref)) erros.add(pasta.getFileName() + "/" + rel + " → subpasta; mantenha um nível");
                else if (!skill.contains(rel)) erros.add(pasta.getFileName() + "/" + rel + " → órfã; cite no SKILL.md com 'quando ler'");
            }
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @DisplayName("PadraoAnthropic: references longas devem abrir com sumario")
    @Test void referencesLongasDevemTerSumario() {
        var erros = new ArrayList<String>();
        for (Path pasta : Catalogo.skillsDaTrilha()) {
            for (Path ref : arquivos(pasta.resolve("references"))) {
                if (!ref.toString().endsWith(".md")) continue;
                String texto = Catalogo.ler(ref);
                if (texto.lines().count() > LINHAS_EXIGE_SUMARIO && !texto.contains("## Sumário")) {
                    registrar(erros, pasta.getFileName() + "/references/" + ref.getFileName() + ":sumario", "sem '## Sumário'");
                }
            }
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @DisplayName("PadraoAnthropic: assets devem ser citados pelo SKILL.md ou por uma reference")
    @Test void assetsDevemSerCitados() {
        var erros = new ArrayList<String>();
        for (Path pasta : Catalogo.skillsDaTrilha()) {
            var textos = new StringBuilder(Catalogo.ler(pasta.resolve("SKILL.md")));
            arquivos(pasta.resolve("references")).forEach(r -> textos.append(Catalogo.ler(r)));
            for (Path asset : arquivos(pasta.resolve("assets"))) {
                if (!textos.toString().contains("assets/" + asset.getFileName())) {
                    erros.add(pasta.getFileName() + "/assets/" + asset.getFileName() + " → não citado");
                }
            }
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @DisplayName("PadraoAnthropic: toda skill da trilha deve ter evals com disparo positivo e negativo")
    @Test @SuppressWarnings("unchecked")
    void skillsDevemTerEvals() throws IOException {
        var erros = new ArrayList<String>();
        for (Path pasta : Catalogo.skillsDaTrilha()) {
            String nome = pasta.getFileName().toString();
            Path arquivo = pasta.resolve("evals/evals.json");
            if (!Files.exists(arquivo)) { erros.add(nome + " → sem evals/evals.json"); continue; }
            Map<String, Object> raiz;
            try {
                // JSON é YAML válido; SafeConstructor evita instanciar tipos arbitrários
                raiz = new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()))
                        .load(Files.readString(arquivo));
            } catch (RuntimeException e) { erros.add(nome + " → evals.json inválido: " + e.getMessage()); continue; }
            if (!nome.equals(raiz.get("skill_name"))) erros.add(nome + " → skill_name divergente");
            var evals = raiz.get("evals") instanceof List<?> l ? (List<Object>) l : List.<Object>of();
            if (evals.size() < 2) erros.add(nome + " → menos de 2 casos");
            for (Object o : evals) {
                var caso = o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.<String, Object>of();
                if (!naoVazio(caso.get("prompt")) || !naoVazio(caso.get("expected_output")) || !naoVazio(caso.get("expectations")))
                    erros.add(nome + " → caso " + caso.get("id") + " sem prompt, expected_output ou expectations");
            }
            var trigger = raiz.get("trigger") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.<String, Object>of();
            if (!naoVazio(trigger.get("should_trigger")) || !naoVazio(trigger.get("should_not_trigger")))
                erros.add(nome + " → trigger sem caso positivo e negativo");
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    private static boolean naoVazio(Object valor) {
        if (valor instanceof String s) return !s.isBlank();
        if (valor instanceof List<?> l) return !l.isEmpty();
        return false;
    }
}
