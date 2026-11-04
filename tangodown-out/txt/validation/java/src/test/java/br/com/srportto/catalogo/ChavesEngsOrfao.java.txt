package br.com.srportto.catalogo;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
interface ChavesEngsOrfao {
    boolean ehOrfao(Path arquivoJava) throws IOException;
}