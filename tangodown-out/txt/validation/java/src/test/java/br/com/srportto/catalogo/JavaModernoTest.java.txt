package br.com.srportto.catalogo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** A skill java-moderno cobre sealed e switch moderno e aponta para a fonte executável do módulo linguagem. */
class JavaModernoTest {
    @DisplayName("JavaModerno: Referencia deve cobrir sealed e switch com fonte executavel")
    @Test
    void referenciaDeveCobrirSealedESwitchComFonteExecutavel() {
        String texto = Catalogo.ler(Catalogo.RAIZ.resolve("skills/java-moderno/references/sealed-e-switch.md"));
        var erros = new ArrayList<String>();
        for (String termo : List.of("permits", "non-sealed", "final", "getPermittedSubclasses", "case null",
                "when", "yield", "case APP, WEB", "_", "domina", "exaustiv", "JEP 507", "preview")) {
            if (!texto.contains(termo)) erros.add("sem '" + termo + "'");
        }
        for (String classe : List.of("Pagamento", "Tarifacao", "ResultadoCobranca", "Notificacao")) {
            if (!texto.contains("examples/java/linguagem/src/main/java/br/com/srportto/exemplos/" + classe + ".java")) {
                erros.add("sem link para " + classe);
            }
        }
        String skill = Catalogo.ler(Catalogo.RAIZ.resolve("skills/java-moderno/SKILL.md"));
        if (!skill.contains("references/sealed-e-switch.md")) erros.add("SKILL.md não aponta a referência");
        if (skill.contains("4.0.4")) erros.add("SKILL.md ainda cita Spring Boot 4.0.4");
        // Um único modelo de Pagamento em toda a skill: o do módulo linguagem (trechos combináveis compilam).
        for (String modeloAntigo : List.of("numeroMascarado", "permits Pix, Cartao {", "case Cartao c ->", "(Pix) pagamento")) {
            if (skill.contains(modeloAntigo)) erros.add("SKILL.md mistura outro modelo de Pagamento: '" + modeloAntigo + "'");
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }
}
