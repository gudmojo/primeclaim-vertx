package is.gudmundur1.primeclaim;

import io.vertx.reactivex.ext.web.RoutingContext;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;

public class WebUtil {

  public static void fail(RoutingContext routingContext) {
    routingContext.response()
      .putHeader("content-type", "text/plain")
      .setStatusCode(HTTP_INTERNAL_ERROR)
      .end("failure");
  }

}
