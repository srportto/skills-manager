package br.com.srportto.exemplos;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.JedisPooled;

/** Valkey real para os testes ExternoIT de cache, limite distribuído e streams. */
final class RedisExternoSuporte {
    static final DockerImageName VALKEY = DockerImageName.parse("valkey/valkey:8");

    private RedisExternoSuporte() {}

    static GenericContainer<?> container() {
        return new GenericContainer<>(VALKEY).withExposedPorts(6379);
    }

    static JedisPooled cliente(GenericContainer<?> container) {
        return new JedisPooled(container.getHost(), container.getMappedPort(6379));
    }
}
