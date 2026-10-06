package br.com.srportto.catalogo;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogoEstruturaTest {
    static final Path RAIZ = Path.of("../..").toAbsolutePath().normalize();

    static Map<String, Object> metadados(Path arquivo) throws Exception {
        String texto = Files.readString(arquivo).replace("\r\n", "\n");
        assertTrue(texto.startsWith("---\n"), arquivo.toString());
        int fim = texto.indexOf("\n---", 4);
        assertTrue(fim > 4, "Frontmatter incompleto: " + arquivo);
        var opcoes = new LoaderOptions();
        opcoes.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(opcoes)).load(texto.substring(4, fim));
    }

    static Map<String, Path> skills() throws Exception {
        var encontrados = new TreeMap<String, Path>();
        try (var diretorios = Files.list(RAIZ.resolve("skills"))) {
            for (Path diretorio : diretorios.filter(Files::isDirectory).toList()) {
                Path arquivo = diretorio.resolve("SKILL.md");
                var dados = metadados(arquivo);
                String nome = (String) dados.get("name");
                assertEquals(diretorio.getFileName().toString(), nome, arquivo.toString());
                assertNull(encontrados.put(nome, arquivo), "Identificador duplicado: " + nome);
                assertFalse(Objects.toString(dados.get("description"), "").isBlank(), arquivo.toString());
            }
        }
        return encontrados;
    }

    @Test void deveResolverAsSkillsDeclaradasPelosAgents() throws Exception {
        var existentes = skills();
        try (var arquivos = Files.list(RAIZ.resolve("agents"))) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".md")).toList()) {
                var dados = metadados(arquivo);
                assertEquals(arquivo.getFileName().toString().replace(".md", ""), dados.get("name"));
                for (Object nome : (List<?>) dados.getOrDefault("skills", List.of())) {
                    assertTrue(existentes.containsKey(nome), arquivo + " referencia skill inexistente: " + nome);
                }
            }
        }
    }

    @Test void relatedSkillsDevemReferenciarIdentificadoresExistentes() throws Exception {
        var existentes = skills();
        var erros = new ArrayList<String>();
        for (var entrada : existentes.entrySet()) {
            if (!(metadados(entrada.getValue()).get("metadata") instanceof Map<?, ?> meta)) continue;
            Object relacionadas = meta.get("related-skills");
            // Aceita lista YAML ou texto separado por vírgulas (convenções do catálogo).
            List<?> nomes = relacionadas instanceof List<?> lista ? lista
                    : relacionadas == null ? List.of() : Arrays.stream(relacionadas.toString().split(",")).map(String::trim).toList();
            for (Object nome : nomes) {
                if (!existentes.containsKey(nome.toString())) erros.add(entrada.getKey() + " → related-skill inexistente: " + nome);
            }
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @Test void agentsDevemSeguirOContratoDeEntradasEntregasEFronteiras() throws Exception {
        var erros = new ArrayList<String>();
        for (Path arquivo : Catalogo.agents()) {
            String texto = Files.readString(arquivo).replace("\r\n", "\n");
            for (String secao : List.of("## Entradas", "## Entregas e evidências", "## Fronteiras e encaminhamentos")) {
                if (!texto.contains(secao)) erros.add(arquivo.getFileName() + " → seção ausente: " + secao);
            }
            // Contrato do catálogo: teste pulado nunca é evidência de aprovação.
            if (texto.contains("-DskipTests") && !texto.contains("nunca")) {
                erros.add(arquivo.getFileName() + " → menciona -DskipTests sem proibi-lo");
            }
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @Test void frontmatterNaoDeveSerSeguidoDeDelimitadorDuplicado() throws Exception {
        var erros = new ArrayList<String>();
        for (Path arquivo : skills().values()) {
            if (Catalogo.auxiliar(arquivo)) continue; // ferramentas auxiliares não sofrem alteração incidental
            String texto = Files.readString(arquivo).replace("\r\n", "\n");
            int fim = texto.indexOf("\n---", 4);
            // Um segundo "---" logo após o fechamento vira régua horizontal e indica frontmatter mal formado.
            if (texto.startsWith("\n---", fim + 4)) erros.add(RAIZ.relativize(arquivo) + " → '---' duplicado após o frontmatter");
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @Test void deveDisponibilizarAsReferenciasTransversaisDoPlano() throws Exception {
        var existentes = skills();
        assertTrue(existentes.containsKey("resiliencia-controle-fluxo-java"), "Referência de resiliência ausente");
        assertTrue(existentes.containsKey("testes-sistemas-java"), "Referência de testes ausente");
    }
}
