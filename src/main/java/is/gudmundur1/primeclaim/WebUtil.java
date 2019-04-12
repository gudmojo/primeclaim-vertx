package is.gudmundur1.primeclaim;

import io.vertx.reactivex.ext.web.RoutingContext;

public class WebUtil {

  public static void fail(RoutingContext routingContext) {
    routingContext.response()
      .putHeader("content-type", "text/plain")
      .setStatusCode(500)
      .end("failure");
  }

}
