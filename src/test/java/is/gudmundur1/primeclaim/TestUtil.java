package is.gudmundur1.primeclaim;

import io.vertx.junit5.VertxTestContext;

public class TestUtil {
  public static void assertEquals(VertxTestContext testContext, Object a, Object b) {
    testContext.verify(() -> org.junit.jupiter.api.Assertions.assertEquals(a, b));
  }

}
