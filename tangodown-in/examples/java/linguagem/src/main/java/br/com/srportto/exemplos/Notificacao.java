package br.com.srportto.exemplos;

import java.net.URI;
import java.util.Objects;

/** Classe abstrata selada: cada subclasse escolhe fechar ({@code final}) ou reabrir ({@code non-sealed}). */
public abstract sealed class Notificacao permits Notificacao.Email, Notificacao.Webhook {
    private final String mensagem;

    protected Notificacao(String mensagem) {
        this.mensagem = Objects.requireNonNull(mensagem);
    }

    public String mensagem() {
        return mensagem;
    }

    public static final class Email extends Notificacao {
        private final String endereco;

        public Email(String endereco, String mensagem) {
            super(mensagem);
            this.endereco = Objects.requireNonNull(endereco);
        }

        public String endereco() {
            return endereco;
        }
    }

    /** non-sealed: integrações podem estender (ex.: webhook assinado) sem alterar esta hierarquia. */
    public static non-sealed class Webhook extends Notificacao {
        private final URI url;

        public Webhook(URI url, String mensagem) {
            super(mensagem);
            this.url = Objects.requireNonNull(url);
        }

        public URI url() {
            return url;
        }
    }

    /** Exaustivo sem default: o caso Webhook cobre também as subclasses dele. */
    public static String destino(Notificacao notificacao) {
        return switch (notificacao) {
            case Email email -> "email:" + email.endereco();
            case Webhook webhook -> "webhook:" + webhook.url();
        };
    }
}
