package is.gudmundur1.primeclaim;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)

@Testcontainers
public class TestMainVerticle {

  public static final int HTTP_PORT = 8889;
  public static final String HOST = "localhost";
  public static final String PG_DATABASE = "test";
  public static final String PG_USERNAME = "test";
  public static final String PG_PASSWORD = "test";
  public static final String PG_HOSTNAME = "localhost";

  @Container
  GenericContainer postgresContainer = new PostgreSQLContainer()
    .withTmpFs(Collections.singletonMap("/var/lib/pgsql/data", "rw"));

  @BeforeEach
  void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
    Integer pgPort = postgresContainer.getMappedPort(5432);
    String pgUrl = "jdbc:postgresql://" + PG_HOSTNAME + ":" + pgPort + "/" + PG_DATABASE;
    DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject()
        .put("http.port", HTTP_PORT)
        .put("postgres-host", "localhost")
        .put("postgres-port", pgPort)
        .put("postgres-database", PG_DATABASE)
        .put("postgres-username", PG_USERNAME)
        .put("postgres-password", PG_PASSWORD)
      );
    Flyway flyway = Flyway.configure().dataSource(
      pgUrl,
      PG_USERNAME,
      PG_PASSWORD).load();
    flyway.migrate();
    vertx.deployVerticle(new MainVerticle(), options, testContext.succeeding(id -> testContext.completeNow()));
  }

  @Test
  @DisplayName("Should start a Web Server on port")
  @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
  void start_http_server(Vertx vertx, VertxTestContext testContext) throws Throwable {
    WebClient client = WebClient.create(vertx);
    client.get(HTTP_PORT, "localhost", "/").rxSend().subscribe(response ->
      testContext.verify(() -> {
        assertTrue(response.statusCode() == 200);
        assertTrue(response.body().toString().contains("hello"));
        testContext.completeNow();
      }));
  }

  @Test
  @DisplayName("Should respond to GET /ping")
  @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
  void get_ping(Vertx vertx, VertxTestContext testContext) throws Throwable {
    WebClient client = WebClient.create(vertx);
    client.get(HTTP_PORT, "localhost", "/ping").rxSend().subscribe(response ->
      testContext.verify(() -> {
        assertTrue(response.statusCode() == 200);
        assertTrue(response.body().toString().contains("pong"));
        testContext.completeNow();
      }));
  }

  @Test
  @DisplayName("create user")
  @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
  void create_user(Vertx vertx, VertxTestContext testContext) throws Throwable {
    WebClient client = WebClient.create(vertx);
    JsonObject newUser = new JsonObject();
    String username = "myname";
    newUser.put("username", username);
    newUser.put("isadmin", false);
    client.post(HTTP_PORT, HOST, "/user").rxSendJsonObject(newUser).subscribe(postResult -> {
      testContext.verify(() -> assertEquals(200, postResult.statusCode()));
      client.get(HTTP_PORT, HOST, "/user/" + username).rxSend().subscribe(result ->
        testContext.verify(() -> {
          assertEquals(200, result.statusCode());
          JsonObject json = result.bodyAsJsonObject();
          assertEquals(username, json.getString("username"));
          assertEquals(false, json.getBoolean("isadmin"));
          assertTrue(json.getString("apikey").matches("[A-Za-z0-9]+"));
          testContext.completeNow();
        }));
    });
  }

  @Test
  @DisplayName("claim prime")
  @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
  void claim_prime(Vertx vertx, VertxTestContext testContext) throws Throwable {
    testContext.failNow(new Exception("test not implemented"));
  }

  @Test
  @DisplayName("list claims")
  @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
  void list_claims(Vertx vertx, VertxTestContext testContext) throws Throwable {
    testContext.failNow(new Exception("test not implemented"));
  }
}
