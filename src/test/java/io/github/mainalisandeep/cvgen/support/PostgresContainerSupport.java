package io.github.mainalisandeep.cvgen.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Base class for every test that needs a database.
 *
 * <p>Tests used to run on H2 with {@code MODE=PostgreSQL}. That shim is not PostgreSQL: it cannot
 * parse the partial unique index in changeset 001, which had to be fenced behind
 * {@code dbms="postgresql"}. Dialect-specific DDL was therefore skipped outright in tests rather
 * than exercised — the same fate awaiting {@code jsonb}, {@code CHECK} constraints and
 * {@code gen_random_uuid()} (changeset 002) as the schema grows.
 *
 * <p>The container is a JVM-wide singleton started once in a static initialiser rather than a
 * Spring {@code @Bean}. Spring caches a separate ApplicationContext per distinct test
 * configuration (for example {@code @AutoConfigureMockMvc} produces its own), and a container bean
 * would be created — and stopped — once per context. A static instance is started once per JVM and
 * shared by every context.
 *
 * <p>Deliberately not reused between runs ({@code withReuse} is off). A reused container keeps its
 * {@code DATABASECHANGELOG} table, so editing an in-development changeset would fail with a
 * checksum error that a fresh container never sees. It is never stopped explicitly either — Ryuk,
 * the Testcontainers sidecar, removes it when the JVM exits.
 */
public abstract class PostgresContainerSupport {

    /** Pinned to the major version of the local and deployed server. Keep these in step. */
    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("cvgen_test")
            .withUsername("cvgen")
            .withPassword("cvgen");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private DataSource dataSource;

    /**
     * Refuses to run if the context resolved to any database other than the container.
     *
     * <p>Not paranoia. {@code application-test.yaml} no longer declares a datasource, so an
     * unresolved {@code @DynamicPropertySource} would fall through to {@code application.yaml}'s
     * {@code ${DATABASE_URL}} — the developer's real database, loaded from {@code .env} by
     * {@link io.github.mainalisandeep.cvgen.config.DotenvEnvironmentPostProcessor}. Some tests here
     * call {@code deleteAll()}, so a silent fallback would destroy real data instead of failing.
     *
     * <p>Runs before any subclass {@code @BeforeEach}, which is where that cleanup lives.
     */
    @BeforeEach
    void rejectNonContainerDatabase() throws SQLException {
        String expectedUrl = POSTGRES.getJdbcUrl();
        try (Connection connection = dataSource.getConnection()) {
            String actualUrl = connection.getMetaData().getURL();
            if (!expectedUrl.equals(actualUrl)) {
                throw new IllegalStateException(
                        "Refusing to run: tests are connected to " + actualUrl
                                + " but the Testcontainers instance is " + expectedUrl
                                + ". Check that @DynamicPropertySource still overrides application.yaml.");
            }
        }
    }
}
