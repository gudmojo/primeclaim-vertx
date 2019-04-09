package is.gudmundur1.primeclaim;

import io.reactivex.Observable;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)

public class TestMainVerticle {

  public static final int HTTP_PORT = 8889;
  public static final String HOST = "localhost";
  public static final String PG_DATABASE = "test";
  public static final String PG_USERNAME = "test";
  public static final String PG_PASSWORD = "test";
  public static final String PG_HOSTNAME = "localhost";

  private void assertEquals(VertxTestContext testContext, Object a, Object b) {
    testContext.verify(() -> org.junit.jupiter.api.Assertions.assertEquals(a, b));
  }

  @BeforeEach
  void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
    GenericContainer postgresContainer = new PostgreSQLContainer()
      .withTmpFs(Collections.singletonMap("/var/lib/pgsql/data", "rw"));
    postgresContainer.start();
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
    client.get(HTTP_PORT, "localhost", "/").rxSend().subscribe(getRoot ->
      testContext.verify(() -> {
        assertTrue(getRoot.statusCode() == 200);
        assertTrue(getRoot.body().toString().contains("hello"));
        testContext.completeNow();
      }));
  }

  @Test
  @DisplayName("Should respond to GET /ping")
  @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
  void get_ping(Vertx vertx, VertxTestContext testContext) throws Throwable {
    WebClient client = WebClient.create(vertx);
    JsonObject newUser = new JsonObject();
    String username = "myname";
    newUser.put("username", username);
    newUser.put("isadmin", false);
    client.post(HTTP_PORT, HOST, "/user").rxSendJsonObject(newUser).flatMap(postResult ->
      client.get(HTTP_PORT, HOST, "/user/myname").rxSend())
      .flatMap(getUser ->
        client.get(HTTP_PORT, "localhost", "/ping?apikey=" + getUser.bodyAsJsonObject().getString("apikey")).rxSend())
      .subscribe(getPing ->
        testContext.verify(() -> {
          assertTrue(getPing.statusCode() == 200);
          assertTrue(getPing.body().toString().contains("pong"));
          testContext.completeNow();
        }));
  }

  @Test
  @DisplayName("Ping should fail if not authenticated")
  @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
  void ping_should_fail_if_not_authenticated(Vertx vertx, VertxTestContext testContext) throws Throwable {
    WebClient client = WebClient.create(vertx);
    client.get(HTTP_PORT, "localhost", "/ping").rxSend().subscribe(getPing ->
      testContext.verify(() -> {
        assertTrue(getPing.statusCode() == 403);
        assertTrue(getPing.body() == null);
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
    client.post(HTTP_PORT, HOST, "/user").rxSendJsonObject(newUser).flatMap(postUser -> {
      assertEquals(testContext, 200, postUser.statusCode());
      return client.get(HTTP_PORT, HOST, "/user/" + username).rxSend();
    }).subscribe(getUser ->
      testContext.verify(() -> {
        assertEquals(testContext, 200, getUser.statusCode());
        JsonObject json = getUser.bodyAsJsonObject();
        assertEquals(testContext, username, json.getString("username"));
        assertEquals(testContext, false, json.getBoolean("isadmin"));
        assertTrue(json.getString("apikey").matches("[A-Za-z0-9]+"));
        testContext.completeNow();
      }));
  }

  @Test
  @DisplayName("claim 10 primes and list them")
  @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
  void claim_prime(Vertx vertx, VertxTestContext testContext) throws Throwable {
    JsonObject newUser = new JsonObject();
    String username = "johnny";
    newUser.put("username", username);
    newUser.put("isadmin", false);
    WebClient client = WebClient.create(vertx, new WebClientOptions().setLogActivity(true));
    client.post(HTTP_PORT, HOST, "/user").rxSendJsonObject(newUser).flatMap(createUser -> {
      assertEquals(testContext, 200, createUser.statusCode());
      return Observable.fromArray(new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
        .flatMapSingle(prime -> {
          JsonObject req = new JsonObject();
          req.put("username", "johnny");
          req.put("prime", prime);
          return client.post(HTTP_PORT, HOST, "/claims").rxSendJsonObject(req);
        }).toList();
    })
      .flatMap(postAllClaims -> client.get(HTTP_PORT, HOST, "/claims").rxSend())
      .subscribe(getClaims -> {
        JsonArray list = getClaims.bodyAsJsonArray();
        assertEquals(testContext, 10, list.size());
        List<Integer> intList = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
          intList.add(list.getJsonObject(i).getInteger("prime"));
        }
        Collections.sort(intList);
        assertEquals(testContext, "[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]", intList.toString());
        testContext.completeNow();
      });
  }
}
