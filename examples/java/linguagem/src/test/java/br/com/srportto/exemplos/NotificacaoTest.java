package br.com.srportto.exemplos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificacaoTest {
    /** Possível só porque Webhook é non-sealed: a hierarquia reabre a partir dele. */
    static final class WebhookAssinado extends Notificacao.Webhook {
        WebhookAssinado(URI url, String mensagem) { super(url, mensagem); }
    }

    @DisplayName("Notificacao: Classe selada deve declarar subclasses final e non-sealed")
    @Test
    void classeSeladaDeveDeclararSubclassesFinalENonSealed() {
        assertTrue(Notificacao.class.isSealed());
        assertEquals(Set.of(Notificacao.Email.class, Notificacao.Webhook.class),
                Set.of(Notificacao.class.getPermittedSubclasses()));
        assertFalse(Notificacao.Webhook.class.isSealed());
    }

    @DisplayName("Notificacao: Switch exaustivo deve cobrir subclasses de tipo non-sealed")
    @Test
    void switchExaustivoDeveCobrirSubclassesDeTipoNonSealed() {
        assertEquals("email:ana@exemplo.com", Notificacao.destino(new Notificacao.Email("ana@exemplo.com", "oi")));
        assertEquals("webhook:https://exemplo.com/h",
                Notificacao.destino(new WebhookAssinado(URI.create("https://exemplo.com/h"), "oi")));
    }
}
