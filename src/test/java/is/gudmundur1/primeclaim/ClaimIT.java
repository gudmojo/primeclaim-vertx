package is.gudmundur1.primeclaim;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static is.gudmundur1.primeclaim.IntegrationTestUtil.ADMIN_API_KEY;
import static is.gudmundur1.primeclaim.IntegrationTestUtil.HOST;
import static is.gudmundur1.primeclaim.IntegrationTestUtil.HTTP_PORT;
import static is.gudmundur1.primeclaim.TestUtil.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)

public class ClaimIT {

  @BeforeEach
  void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
    IntegrationTestUtil.deploy(vertx, testContext);
  }

  @Test
  @DisplayName("POST claim should fail if not authenticated")
  @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
  void post_claim_should_fail_if_not_authenticated(Vertx vertx, VertxTestContext testContext) {
    WebClient client = WebClient.create(vertx);
    JsonObject claim = new JsonObject();
    claim.put("username", "johnny");
    claim.put("prime", 3);
    client.post(HTTP_PORT, "localhost", "/claims").rxSendJsonObject(claim).subscribe(postClaim ->
      testContext.verify(() -> {
        assertTrue(postClaim.statusCode() == 403);
        assertTrue(postClaim.body() == null);
        testContext.completeNow();
      }));
  }

  @Test
  @DisplayName("GET claim should fail if not authenticated")
  @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
  void get_claim_should_fail_if_not_authenticated(Vertx vertx, VertxTestContext testContext) {
    WebClient client = WebClient.create(vertx);
    JsonObject claim = new JsonObject();
    claim.put("username", "johnny");
    claim.put("prime", 3);
    client.get(HTTP_PORT, "localhost", "/claims").rxSend().subscribe(getClaims -> {
      assertEquals(testContext, 403, getClaims.statusCode());
      testContext.verify(() -> assertTrue(getClaims.body() == null));
      testContext.completeNow();
    });
  }

  @Test
  @DisplayName("claim 5 primes and list them")
  @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
  void claim_prime(Vertx vertx, VertxTestContext testContext) {
    JsonObject newUser = new JsonObject();
    String username = "johnny";
    newUser.put("username", username);
    newUser.put("isadmin", false);
    WebClient client = WebClient.create(vertx, new WebClientOptions().setLogActivity(true));
    client.post(HTTP_PORT, HOST, "/user?apikey=" + ADMIN_API_KEY).rxSendJsonObject(newUser)
      .flatMap(createUser -> {
        assertEquals(testContext, 200, createUser.statusCode());
        return client.get(HTTP_PORT, HOST, "/user/johnny?apikey=" + ADMIN_API_KEY).rxSend();
      })
      .flatMap(getUser -> {
        assertEquals(testContext, 200, getUser.statusCode());
        String apikey = getUser.bodyAsJsonObject().getString("apikey");
        return Single.zip(Single.just(apikey), Observable.fromArray(new Integer[]{1, 2, 3, 5, 7})
          .flatMapSingle(prime -> {
            JsonObject req = new JsonObject();
            req.put("username", "johnny");
            req.put("prime", prime);
            return client.post(HTTP_PORT, HOST,
            "/claims?apikey=" + apikey).rxSendJsonObject(req);
          }).toList(), TupleApiKeyPostAllClaims::new);
      })
      .flatMap(tuple -> {
        tuple.postAllClaims.forEach(postClaim -> assertEquals(testContext, 200, postClaim.statusCode()));
        return client.get(HTTP_PORT, HOST, "/claims?apikey=" + tuple.apiKey).rxSend();
      }).subscribe(getClaims -> {
          JsonArray list = getClaims.bodyAsJsonArray();
          assertEquals(testContext, 5, list.size());
          List<Integer> intList = new ArrayList<>(list.size());
          for (int i = 0; i < list.size(); i++) {
            intList.add(list.getJsonObject(i).getInteger("prime"));
          }
          Collections.sort(intList);
          assertEquals(testContext, "[1, 2, 3, 5, 7]", intList.toString());
          testContext.completeNow();
        });
  }

  private class TupleApiKeyPostAllClaims {
    String apiKey;
    List<HttpResponse<Buffer>> postAllClaims;

    TupleApiKeyPostAllClaims(String apiKey, List<HttpResponse<Buffer>> postAllClaims) {
      this.apiKey = apiKey;
      this.postAllClaims = postAllClaims;
    }
  }
}
