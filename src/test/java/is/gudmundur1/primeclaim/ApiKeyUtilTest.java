package is.gudmundur1.primeclaim;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import rx.Observable;
import rx.Single;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;


class ApiKeyUtilTest {

  @Test
  void generate() {
    String generated = ApiKeyUtil.generateApiKey();
    assertEquals(16, generated.length());
    assertTrue(generated.matches("[A-Z0-9]+"));
  }

  private ExecutorService executor
    = Executors.newFixedThreadPool(3);

  public Future<Integer> calculate(Integer input) {
    return executor.submit(() -> {
      Thread.sleep(1000);
      System.out.println(input);
      return input * input;
    });
  }

  private Single<Integer> single(int i) {
    return Single.from(calculate(i));
  }

  @Test
  void sequence() {
    Observable.from(new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}).map(i -> {
      return single(i);
    }).toList().subscribe(item -> {
      single(0).subscribe(response -> {
        System.out.println("geeet");
      });
    });
  }
}
