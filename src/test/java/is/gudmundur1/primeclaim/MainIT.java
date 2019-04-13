package is.gudmundur1.primeclaim;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

import static is.gudmundur1.primeclaim.IntegrationTestUtil.ADMIN_API_KEY;
import static is.gudmundur1.primeclaim.IntegrationTestUtil.HOST;
import static is.gudmundur1.primeclaim.IntegrationTestUtil.HTTP_PORT;
import static is.gudmundur1.primeclaim.TestUtil.assertEquals;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)

public class MainIT {

  @BeforeEach
  void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
    IntegrationTestUtil.deploy(vertx, testContext);
  }

  @Test
  @DisplayName("Should start a Web Server on port")
  @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
  void start_http_server(Vertx vertx, VertxTestContext testContext) {
    WebClient client = WebClient.create(vertx);
    client.get(HTTP_PORT, "localhost", "/").rxSend().subscribe(getRoot ->
      testContext.verify(() -> {
        assertTrue(getRoot.statusCode() == HTTP_OK);
        assertTrue(getRoot.body().toString().contains("hello"));
        testContext.completeNow();
      }));
  }

  @Test
  @DisplayName("create user")
  @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
  void create_user(Vertx vertx, VertxTestContext testContext) {
    WebClient client = WebClient.create(vertx);
    JsonObject newUser = new JsonObject();
    String username = "myname";
    newUser.put("username", username);
    newUser.put("isadmin", false);
    client.post(HTTP_PORT, HOST, "/user?apikey=" + ADMIN_API_KEY).rxSendJsonObject(newUser).flatMap(postUser -> {
      assertEquals(testContext, HTTP_OK, postUser.statusCode());
      return client.get(HTTP_PORT, HOST, "/user/" + username + "?apikey=" + ADMIN_API_KEY).rxSend();
    }).subscribe(getUser ->
      testContext.verify(() -> {
        assertEquals(testContext, HTTP_OK, getUser.statusCode());
        JsonObject json = getUser.bodyAsJsonObject();
        assertEquals(testContext, username, json.getString("username"));
        assertEquals(testContext, false, json.getBoolean("isadmin"));
        assertTrue(json.getString("apikey").matches("[A-Za-z0-9]+"));
        testContext.completeNow();
      }));
  }
}
