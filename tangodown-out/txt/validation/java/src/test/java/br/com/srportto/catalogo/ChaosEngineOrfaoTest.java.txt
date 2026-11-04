package br.com.srportto.catalogo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChaosEngineOrfaoTest {
    private static final Pattern DECLARACAO_RECURSO = Pattern.compile(
            "\\b([\\w$]+)\\s*=\\s*(?:new\\s+([\\w.$]*(?:Container|JedisPooled|ToxiproxyClient))"
                    + "\\s*(?:<[^>]*>)?\\s*\\(|RedisExternoSuporte\\.container\\s*\\(|"
                    + "Network\\.newNetwork\\s*\\()");
    private static final Pattern METODO = Pattern.compile("(?:void|static\\s+void)\\s+(\\w+)\\s*\\([^)]*\\)");

    private record Diagnostico(String arquivo, String classe, String metodoCriacao, String reversao) {
        @Override public String toString() {
            return classe + "#" + metodoCriacao + " em " + arquivo + " sem reversão correspondente: " + reversao;
        }
    }

    private static final class Detector implements ChavesEngsOrfao {
        @Override
        public boolean ehOrfao(Path arquivoJava) throws IOException {
            return diagnostico(arquivoJava) != null;
        }

        Diagnostico diagnostico(Path arquivoJava) throws IOException {
            String fonte = Files.readString(arquivoJava);
            Matcher declaracoes = DECLARACAO_RECURSO.matcher(fonte);
            Set<String> recursos = new LinkedHashSet<>();
            Set<String> recursosQuePrecisamDeReversao = new LinkedHashSet<>();
            Set<String> gerenciadosPeloJUnit = new LinkedHashSet<>();
            while (declaracoes.find()) {
                String recurso = declaracoes.group(1);
                recursos.add(recurso);
                String tipo = declaracoes.group(2);
                if (declaracoes.group().contains("Network.newNetwork")
                        || declaracoes.group().contains("RedisExternoSuporte.container")
                        || (tipo != null && (tipo.endsWith("Container") || tipo.endsWith("JedisPooled")))) {
                    recursosQuePrecisamDeReversao.add(recurso);
                }
                int limiteDeclaracao = Math.max(fonte.lastIndexOf(';', declaracoes.start()),
                        fonte.lastIndexOf('}', declaracoes.start()));
                if (fonte.substring(limiteDeclaracao + 1, declaracoes.start()).contains("@Container")) {
                    gerenciadosPeloJUnit.add(recurso);
                }
            }

            for (String recurso : recursos) {
                if (!recursosQuePrecisamDeReversao.contains(recurso)
                        && !chamada(fonte, recurso, "start")) continue;
                boolean temReversao = chamada(fonte, recurso, "stop|close|shutdown|remove");
                boolean tryComRecurso = Pattern.compile("try\\s*\\([^)]*\\b" + Pattern.quote(recurso)
                        + "\\b[^)]*\\)").matcher(fonte).find();
                boolean gerenciado = gerenciadosPeloJUnit.contains(recurso);
                if (!temReversao && !tryComRecurso && !gerenciado) {
                    return new Diagnostico(arquivoJava.getFileName().toString(), nomeClasse(fonte),
                            metodoDeInicio(fonte, recurso), "ausente para " + recurso);
                }
            }
            return null;
        }

        private boolean chamada(String fonte, String recurso, String metodos) {
            return Pattern.compile("\\b" + Pattern.quote(recurso) + "\\s*\\.\\s*(?:" + metodos + ")\\s*\\(")
                    .matcher(fonte).find();
        }

        private String metodoDeInicio(String fonte, String recurso) {
            Matcher metodo = METODO.matcher(fonte);
            String atual = "inicialização";
            while (metodo.find()) {
                atual = metodo.group(1);
                int inicioCorpo = fonte.indexOf('{', metodo.end());
                int próximoMetodo = fonte.indexOf("\n    @", inicioCorpo);
                int fim = próximoMetodo < 0 ? fonte.length() : próximoMetodo;
                if (chamada(fonte.substring(inicioCorpo, fim), recurso, "start")) return atual;
            }
            return atual;
        }

        private String nomeClasse(String fonte) {
            Matcher classe = Pattern.compile("\\bclass\\s+(\\w+)").matcher(fonte);
            return classe.find() ? classe.group(1) : "classe desconhecida";
        }
    }

    @DisplayName("ChaosEngineOrfao Testes com recursos externos devem ter dono ereversao quando o cenário é exercitado")
    @Test
    void testesComRecursosExternosDevemTerDonoEReversao() throws IOException {
        var detector = new Detector();
        var orfaos = new ArrayList<String>();
        Path exemplos = Catalogo.RAIZ.resolve("examples/java");
        try (Stream<Path> arquivos = Files.walk(exemplos)) {
            for (Path arquivo : arquivos.filter(Files::isRegularFile).filter(ChaosEngineOrfaoTest::testeExterno).toList()) {
                Diagnostico diagnostico = detector.diagnostico(arquivo);
                if (diagnostico != null) orfaos.add(diagnostico.toString());
            }
        }
        assertTrue(orfaos.isEmpty(), () -> String.join("\n", orfaos));
    }

    @DisplayName("ChaosEngineOrfao Deve ignorar teste sem recurso externo quando o cenário é exercitado")
    @Test
    void deveIgnorarTesteSemRecursoExterno() throws IOException {
        Path teste = Files.createTempFile("sem-recurso-", "IT.java");
        try {
            Files.writeString(teste, "class SemRecursoIT { void executar() {} }");
            assertFalse(new Detector().ehOrfao(teste));
        } finally {
            Files.deleteIfExists(teste);
        }
    }

    @DisplayName("ChaosEngineOrfao Deve detectar container iniciado sem reversao quando o cenário é exercitado")
    @Test
    void deveDetectarContainerIniciadoSemReversao() throws IOException {
        Path teste = Files.createTempFile("container-orfa", "IT.java");
        try {
            Files.writeString(teste, "class ContainerOrfaIT { GenericContainer<?> valkey = new GenericContainer<>(); "
                    + "void iniciar() { valkey.start(); } }");
            var diagnostico = new Detector().diagnostico(teste);
            assertTrue(diagnostico != null);
            assertTrue(diagnostico.toString().contains("ContainerOrfaIT#iniciar"));
            assertTrue(diagnostico.toString().contains("sem reversão"));
        } finally {
            Files.deleteIfExists(teste);
        }
    }

    @DisplayName("ChaosEngineOrfao Deve aceitar reversao em finally quando o cenário é exercitado")
    @Test
    void deveAceitarReversaoEmFinally() throws IOException {
        Path teste = Files.createTempFile("container-finally", "IT.java");
        try {
            Files.writeString(teste, "class ContainerFinallyIT { GenericContainer<?> valkey = new GenericContainer<>(); "
                    + "void iniciar() { valkey.start(); try { executar(); } finally { valkey.stop(); } } }");
            assertFalse(new Detector().ehOrfao(teste));
        } finally {
            Files.deleteIfExists(teste);
        }
    }

    @DisplayName("ChaosEngineOrfao Deve sinalizar cada recurso sem sua própria reversão quando o cenário é exercitado")
    @Test
    void deveDetectarRecursoSemReversaoCorrespondente() throws IOException {
        Path teste = Files.createTempFile("dois-recursos-orfaos", "IT.java");
        try {
            Files.writeString(teste, "class DoisRecursosIT { "
                    + "GenericContainer<?> cache = new GenericContainer<>(); "
                    + "GenericContainer<?> banco = new GenericContainer<>(); "
                    + "void iniciar() { cache.start(); banco.start(); } "
                    + "void encerrar() { cache.stop(); } }");
            var diagnostico = new Detector().diagnostico(teste);
            assertTrue(diagnostico != null);
            assertTrue(diagnostico.toString().contains("banco"));
        } finally {
            Files.deleteIfExists(teste);
        }
    }

    @DisplayName("ChaosEngineOrfao Deve reconhecer caminhos de teste usando componentes multiplataforma")
    @Test
    void deveReconhecerDiretorioDeTesteExternoEmQualquerSistema() {
        Path externo = Path.of("examples", "java", "integracao", "src", "test", "java", "TesteExternoIT.java");
        Path principal = Path.of("examples", "java", "integracao", "src", "main", "java", "Teste.java");

        assertTrue(testeExterno(externo));
        assertFalse(testeExterno(principal));
    }

    private static boolean testeExterno(Path arquivo) {
        String nome = arquivo.getFileName().toString();
        var partes = arquivo.normalize().iterator();
        String anterior = "";
        String atual = "";
        String seguinte = "";
        while (partes.hasNext()) {
            seguinte = partes.next().toString();
            if (anterior.equals("src") && atual.equals("test") && seguinte.equals("java")) {
                return nome.endsWith("IT.java") || nome.endsWith("CargaIT.java");
            }
            anterior = atual;
            atual = seguinte;
        }
        return false;
    }
}
