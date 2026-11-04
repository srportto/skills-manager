package br.com.exemplo.esqueleto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Classe principal. RENOMEIE para {@code <Nome>Application} ao copiar o esqueleto. */
@SpringBootApplication
public class EsqueletoApplication {

    // main clássico: o plugin do Spring Boot 4.0.7 não reconhece o "void main()" instance-main do JDK 25.
    public static void main(String[] args) {
        SpringApplication.run(EsqueletoApplication.class, args);
    }
}
