package cz.kula.killteamdiscordbot.pairing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.util.List;

@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
@TestConfiguration(proxyBeanMethods = false)
public class DumpInitTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        var container = new PostgreSQLContainer(DockerImageName.parse("postgres:16.2-alpine"))
                .withDatabaseName("mydatabase")
                .withUsername("myuser")
                .withPassword("secret")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("local/dump.sql"),
                        "/docker-entrypoint-initdb.d/dump.sql"
                );
        container.setPortBindings(List.of("5432:5432"));
        return container;

    }

}
