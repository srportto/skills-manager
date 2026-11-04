package br.com.srportto.catalogo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integração do fluxo OpenSpec (comandos /opsx e skills openspec-*) com as skills e os agents do catálogo. */
class OpenSpecIntegracaoTest {
    private static final Path SKILL = Catalogo.RAIZ.resolve("skills/openspec-catalogo-java/SKILL.md");
    private static final Path CONFIG = Catalogo.RAIZ.resolve("skills/openspec-catalogo-java/references/config.yaml");
    /** Artefatos do schema spec-driven (OpenSpec 1.4.1); outras chaves em rules são ignoradas pelo CLI. */
    private static final Set<String> ARTEFATOS = Set.of("proposal", "specs", "design", "tasks");
    /** Nomes de skill/agent entre crases: minúsculas com ao menos um hífen (ex.: `java-revisor`). */
    private static final Pattern NOME = Pattern.compile("`([a-z0-9]+(?:-[a-z0-9]+)+)`");

    private static boolean existeSkillOuAgent(String nome) {
        return Files.exists(Catalogo.RAIZ.resolve("skills/" + nome + "/SKILL.md"))
                || Files.exists(Catalogo.RAIZ.resolve("agents/" + nome + ".md"));
    }

    private static String secao(String texto, String titulo) {
        int inicio = texto.indexOf(titulo);
        assertTrue(inicio >= 0, "seção ausente: " + titulo);
        int fim = texto.indexOf("\n## ", inicio + titulo.length());
        return texto.substring(inicio, fim < 0 ? texto.length() : fim);
    }

    @DisplayName("OpenSpecIntegracao: Mapa de fases deve citar apenas skills e agents existentes")
    @Test
    void mapaDeFasesDeveCitarApenasSkillsEAgentsExistentes() {
        String mapa = secao(Catalogo.ler(SKILL), "## Mapa de fases");
        var erros = new ArrayList<String>();
        for (String fase : List.of("explore", "propose", "apply", "archive")) {
            if (!mapa.contains("`" + fase + "`")) erros.add("fase sem linha no mapa: " + fase);
        }
        mapa.lines().filter(linha -> linha.startsWith("|")).forEach(linha -> {
            var m = NOME.matcher(linha);
            while (m.find()) if (!existeSkillOuAgent(m.group(1))) erros.add("mapa cita inexistente: " + m.group(1));
        });
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @DisplayName("OpenSpecIntegracao: Config modelo deve usar apenas artefatos do schema e skills existentes")
    @Test
    void configModeloDeveUsarApenasArtefatosDoSchemaESkillsExistentes() {
        Map<String, Object> config = new Yaml(new SafeConstructor(new LoaderOptions())).load(Catalogo.ler(CONFIG));
        assertEquals("spec-driven", config.get("schema"));
        assertTrue(String.valueOf(config.get("context")).contains("openspec-catalogo-java"),
                "context deve apontar a skill de integração");
        var regras = (Map<?, ?>) config.get("rules");
        var erros = new ArrayList<String>();
        for (String artefato : ARTEFATOS) if (!regras.containsKey(artefato)) erros.add("rules sem " + artefato);
        for (var entrada : regras.entrySet()) {
            if (!ARTEFATOS.contains(String.valueOf(entrada.getKey()))) {
                erros.add("rules." + entrada.getKey() + " não é artefato do schema (o CLI ignora)");
            }
            for (Object regra : (List<?>) entrada.getValue()) {
                var m = NOME.matcher(String.valueOf(regra));
                while (m.find()) {
                    if (!existeSkillOuAgent(m.group(1))) erros.add("rules." + entrada.getKey() + " cita inexistente: " + m.group(1));
                }
            }
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @DisplayName("OpenSpecIntegracao: Comandos opsx devem delegar para a skill openspec correspondente")
    @Test
    void comandosOpsxDevemDelegarParaASkillOpenspecCorrespondente() {
        var mapa = Map.of("explore", "openspec-explore", "propose", "openspec-propose",
                "apply", "openspec-apply-change", "archive", "openspec-archive-change", "sync", "openspec-sync-specs");
        var erros = new ArrayList<String>();
        mapa.forEach((comando, skill) -> {
            String texto = Catalogo.ler(Catalogo.RAIZ.resolve("commands/opsx/" + comando + ".md"));
            String corpo = texto.substring(texto.indexOf("\n---", 4) + 4);
            if (!corpo.contains("`" + skill + "`")) erros.add(comando + " não delega para " + skill);
            if (!corpo.contains("$ARGUMENTS")) erros.add(comando + " não repassa $ARGUMENTS");
            // Fonte única: o comando não pode voltar a duplicar os passos da skill.
            if (corpo.lines().count() > 30) erros.add(comando + " duplica a skill (" + corpo.lines().count() + " linhas)");
        });
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @DisplayName("OpenSpecIntegracao: Skills openspec devem carregar a integracao com o catalogo")
    @Test
    void skillsOpenspecDevemCarregarAIntegracaoComOCatalogo() {
        var erros = new ArrayList<String>();
        var exigidos = Map.of(
                "openspec-explore", List.of("openspec-catalogo-java", "arquiteto-sistemas"),
                "openspec-propose", List.of("openspec-catalogo-java", "openspec instructions"),
                "openspec-apply-change", List.of("openspec-catalogo-java", "java-construtor", "-DskipTests"),
                // Portão condicional: só change que altera código Java exige o veredicto.
                "openspec-archive-change", List.of("openspec-catalogo-java", "java-revisor", "APROVADO", "alterou código Java"));
        exigidos.forEach((skill, trechos) -> {
            String texto = Catalogo.ler(Catalogo.RAIZ.resolve("skills/" + skill + "/SKILL.md"));
            for (String trecho : trechos) if (!texto.contains(trecho)) erros.add(skill + " sem '" + trecho + "'");
        });
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    /** Texto único do portão de archive: as duas skills não podem divergir na semântica. */
    static final List<String> PORTAO_ARCHIVE = List.of(
            "A change alterou código Java quando suas tarefas ou o diff tocam arquivos `.java`, `pom.xml` ou `build.gradle`.",
            "Veredicto ausente conta como PENDENTE. PENDENTE ou REPROVADO bloqueia o archive, a menos que o usuário assuma o risco explicitamente; registre essa decisão no resumo do archive.");

    @DisplayName("OpenSpecIntegracao: Portao de archive deve ter a mesma semantica nas duas skills")
    @Test
    void portaoDeArchiveDeveTerAMesmaSemanticaNasDuasSkills() {
        var erros = new ArrayList<String>();
        for (String skill : List.of("openspec-catalogo-java", "openspec-archive-change")) {
            // Junta as quebras de linha do Markdown para comparar frases inteiras.
            String texto = Catalogo.ler(Catalogo.RAIZ.resolve("skills/" + skill + "/SKILL.md")).replaceAll("\\s*\\n\\s*", " ");
            for (String frase : PORTAO_ARCHIVE) if (!texto.contains(frase)) erros.add(skill + " sem: " + frase);
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }
}
