package is.gudmundur1.primeclaim;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.Vertx;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Collections;

public class IntegrationTestUtil {

  public static final int HTTP_PORT = 8889;
  public static final String HOST = "localhost";
  public static final String PG_DATABASE = "test";
  public static final String PG_USERNAME = "test";
  public static final String PG_PASSWORD = "test";
  public static final String PG_HOSTNAME = "localhost";
  public static final String ADMIN_API_KEY = "bCmdRcFneSFjNL9u";

  public static void deploy(Vertx vertx, VertxTestContext testContext) {
    GenericContainer postgresContainer = new PostgreSQLContainer()
      .withTmpFs(Collections.singletonMap("/var/lib/pgsql/data", "rw"));
    postgresContainer.start();
    Integer pgPort = postgresContainer.getMappedPort(5432);
    String pgUrl = "jdbc:postgresql://" + PG_HOSTNAME + ":" + pgPort + "/" + PG_DATABASE;
    DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject()
        .put(ConfigKey.LISTEN_PORT, HTTP_PORT)
        .put(ConfigKey.POSTGRES_HOST, PG_HOSTNAME)
        .put(ConfigKey.POSTGRES_PORT, pgPort)
        .put(ConfigKey.POSTGRES_DATABASE, PG_DATABASE)
        .put(ConfigKey.POSTGRES_USER, PG_USERNAME)
        .put(ConfigKey.POSTGRES_PASSWORD, PG_PASSWORD)
        .put(ConfigKey.BOOTSTRAP_ADMIN_API_KEY, ADMIN_API_KEY)
      );
    Flyway flyway = Flyway.configure().dataSource(
      pgUrl,
      PG_USERNAME,
      PG_PASSWORD).load();
    flyway.migrate();
    vertx.deployVerticle(new MainVerticle(), options, testContext.succeeding(id -> testContext.completeNow()));

  }
}
