package br.com.srportto.catalogo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Utilitários compartilhados pelos testes do catálogo. */
final class Catalogo {
    static final Path RAIZ = Path.of("../..").toAbsolutePath().normalize();

    /**
     * Ferramentas auxiliares preservadas fora da trilha de exemplos Java (convenções, seção "Java e formatos").
     * A validação de links e de linguagem ignora seu conteúdo; a estrutura do frontmatter continua validada.
     */
    static final Map<String, String> AUXILIARES = Map.of(
            "graphify", "ferramenta importada com scripts próprios",
            "openspec-apply-change", "fluxo OpenSpec 1.4.1 gerado, com ganchos do catálogo (OpenSpecIntegracaoTest)",
            "openspec-archive-change", "fluxo OpenSpec 1.4.1 gerado, com ganchos do catálogo (OpenSpecIntegracaoTest)",
            "openspec-explore", "fluxo OpenSpec 1.4.1 gerado, com ganchos do catálogo (OpenSpecIntegracaoTest)",
            "openspec-propose", "fluxo OpenSpec 1.4.1 gerado, com ganchos do catálogo (OpenSpecIntegracaoTest)",
            "openspec-sync-specs", "fluxo OpenSpec 1.4.1 gerado, com ganchos do catálogo (OpenSpecIntegracaoTest)",
            "python-pro", "skill Python preservada para serviços não Java",
            "remover-imports-nao-usados", "skill multi-linguagem por propósito",
            "terraform-engineer", "IaC; testes Terratest usam Go por exigência da ferramenta");

    private static final Pattern BLOCO = Pattern.compile("(?ms)^\\s*(```|~~~)[^\\n]*\\n.*?^\\s*\\1\\s*$");
    private static final Pattern CODIGO_INLINE = Pattern.compile("`[^`\\n]*`");
    private static final Pattern LINK = Pattern.compile("\\[[^\\]\\n]*\\]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");

    private Catalogo() {}

    static String ler(Path arquivo) {
        try {
            return Files.readString(arquivo).replace("\r\n", "\n");
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    static String relativo(Path arquivo) {
        return RAIZ.relativize(arquivo).toString().replace('\\', '/');
    }

    /** Remove blocos cercados e código inline: exemplos ilustrativos não são dependências reais. */
    static String semCodigo(String texto) {
        return CODIGO_INLINE.matcher(BLOCO.matcher(texto).replaceAll("")).replaceAll("");
    }

    static String semBlocos(String texto) {
        return BLOCO.matcher(texto).replaceAll("");
    }

    static List<String> links(String texto) {
        var encontrados = new ArrayList<String>();
        Matcher m = LINK.matcher(semCodigo(texto));
        while (m.find()) encontrados.add(m.group(1));
        return encontrados;
    }

    static boolean auxiliar(Path arquivo) {
        Path skills = RAIZ.resolve("skills");
        if (!arquivo.startsWith(skills) || arquivo.getParent().equals(skills)) return false;
        return AUXILIARES.containsKey(skills.relativize(arquivo).getName(0).toString());
    }

    /** Markdown versionado da trilha do catálogo: skills, agents, docs/catalogo e README dos exemplos. */
    static List<Path> markdownDaTrilha() {
        var arquivos = new ArrayList<Path>();
        Path indice = RAIZ.resolve("README.md");
        if (Files.isRegularFile(indice)) arquivos.add(indice);
        for (String raiz : List.of("skills", "agents", "docs/catalogo", "examples/java")) {
            Path base = RAIZ.resolve(raiz);
            if (!Files.isDirectory(base)) continue;
            try (Stream<Path> caminhos = Files.walk(base)) {
                caminhos.filter(p -> p.toString().endsWith(".md"))
                        .filter(p -> !p.toString().contains("target"))
                        .filter(p -> !auxiliar(p))
                        .sorted()
                        .forEach(arquivos::add);
            } catch (IOException erro) {
                throw new UncheckedIOException(erro);
            }
        }
        return arquivos;
    }

    static List<Path> diretorios(String relativo) {
        try (Stream<Path> itens = Files.list(RAIZ.resolve(relativo))) {
            return itens.filter(Files::isDirectory).sorted().toList();
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    /** Pastas de skill da trilha (exclui ferramentas auxiliares preservadas). */
    static List<Path> skillsDaTrilha() {
        return diretorios("skills").stream()
                .filter(p -> !AUXILIARES.containsKey(p.getFileName().toString()))
                .toList();
    }

    static List<Path> agents() {
        try (Stream<Path> itens = Files.list(RAIZ.resolve("agents"))) {
            return itens.filter(p -> p.toString().endsWith(".md")).sorted().toList();
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }
}
