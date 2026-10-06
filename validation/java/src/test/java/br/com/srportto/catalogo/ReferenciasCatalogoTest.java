package br.com.srportto.catalogo;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenciasCatalogoTest {
    // Caminho de skill como fonte (skills/x) ou instalação (.claude/skills/x); placeholders como <nome> não casam.
    private static final Pattern CAMINHO_SKILL = Pattern.compile("(?<![\\w-])(?:\\.claude/|\\.codex/)?skills/([a-z0-9][a-z0-9-]*)(?=[/`)\\s.,;:]|$)");
    private static final Pattern CONTAGEM = Pattern.compile("\\b(\\d+) (skills|agents)\\b");

    private static String falhas(List<String> erros) {
        return erros.size() + " problema(s):\n" + String.join("\n", erros);
    }

    @Test
    void linksLocaisDevemResolverNaFonteDoCatalogo() {
        var erros = new ArrayList<String>();
        for (Path arquivo : Catalogo.markdownDaTrilha()) {
            for (String alvo : Catalogo.links(Catalogo.ler(arquivo))) {
                if (alvo.matches("^[a-z][a-z0-9+.-]*:.*") || alvo.startsWith("#")) continue;
                String semAncora = alvo.replaceFirst("#.*$", "");
                if (semAncora.isEmpty()) continue;
                Path destino = arquivo.getParent().resolve(semAncora).normalize();
                if (!Files.exists(destino)) {
                    erros.add(Catalogo.relativo(arquivo) + " → link quebrado: " + alvo);
                }
            }
        }
        assertTrue(erros.isEmpty(), () -> falhas(erros));
    }

    @Test
    void requisitosNaoDevemDependerDaPastaDocsIgnorada() {
        var erros = new ArrayList<String>();
        for (Path arquivo : Catalogo.markdownDaTrilha()) {
            for (String alvo : Catalogo.links(Catalogo.ler(arquivo))) {
                if (alvo.startsWith(".docs") || alvo.contains("/.docs/")) {
                    erros.add(Catalogo.relativo(arquivo) + " → depende de insumo ignorado: " + alvo);
                }
            }
        }
        assertTrue(erros.isEmpty(), () -> falhas(erros));
    }

    @Test
    void caminhosDeSkillCitadosDevemExistir() {
        Set<String> existentes = Catalogo.diretorios("skills").stream()
                .map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        var erros = new ArrayList<String>();
        for (Path arquivo : Catalogo.markdownDaTrilha()) {
            // URLs externas (ex.: documentação de origem) não são caminhos do catálogo.
            String texto = Catalogo.semBlocos(Catalogo.ler(arquivo)).replaceAll("https?://[^\\s)>]+", "");
            var m = CAMINHO_SKILL.matcher(texto);
            while (m.find()) {
                if (!existentes.contains(m.group(1))) {
                    erros.add(Catalogo.relativo(arquivo) + " → skill inexistente: " + m.group());
                }
            }
        }
        assertTrue(erros.isEmpty(), () -> falhas(erros));
    }

    @Test
    void indiceDeveListarInventarioSemContagemDesatualizada() {
        Path indice = Catalogo.RAIZ.resolve("skills/README.md");
        String texto = Catalogo.ler(indice);
        var erros = new ArrayList<String>();
        var skills = Catalogo.diretorios("skills");
        var agents = Catalogo.agents();
        for (Path skill : skills) {
            String nome = skill.getFileName().toString();
            if (!texto.contains("`" + nome + "`") && !texto.contains(nome + "/")) {
                erros.add("skills/README.md não cita a skill " + nome);
            }
        }
        for (Path agent : agents) {
            String nome = agent.getFileName().toString().replace(".md", "");
            if (!texto.contains("`" + nome + "`") && !texto.contains(nome + ".md")) {
                erros.add("skills/README.md não cita o agent " + nome);
            }
        }
        var m = CONTAGEM.matcher(texto);
        while (m.find()) {
            int esperado = m.group(2).equals("skills") ? skills.size() : agents.size();
            if (Integer.parseInt(m.group(1)) != esperado) {
                erros.add("skills/README.md contagem desatualizada: '" + m.group() + "' (inventário: " + esperado + ")");
            }
        }
        assertTrue(erros.isEmpty(), () -> falhas(erros));
    }

    @Test
    void matrizDeCoberturaDeveRastrearTodosOsModulos() {
        Path matriz = Catalogo.RAIZ.resolve("docs/catalogo/matriz-cobertura.md");
        assertTrue(Files.exists(matriz), "docs/catalogo/matriz-cobertura.md ausente");
        String texto = Catalogo.ler(matriz);
        var erros = new ArrayList<String>();
        for (String id : List.of("M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9", "M10", "B1", "B2", "ENG")) {
            if (!Pattern.compile("\\|\\s*" + id + "\\s*\\|").matcher(texto).find()) {
                erros.add("matriz sem linha para " + id);
            }
        }
        assertTrue(erros.isEmpty(), () -> falhas(erros));
    }
}
