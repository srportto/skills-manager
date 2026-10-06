package br.com.srportto.exemplos;

import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

/** Imagens fixas dos testes ExternoIT; um teste falha (não é ignorado) quando o Docker não está disponível. */
final class ServicosExternos {
    static final DockerImageName POSTGRES = DockerImageName.parse("postgres:18-alpine");
    static final DockerImageName KAFKA = DockerImageName.parse("confluentinc/cp-kafka:7.7.1");
    static final DockerImageName LOCALSTACK = DockerImageName.parse("localstack/localstack:4.4");

    private ServicosExternos() {}

    static DataSource dataSource(PostgreSQLContainer postgres) {
        var ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        return ds;
    }
}
