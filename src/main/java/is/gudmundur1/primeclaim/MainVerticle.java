package is.gudmundur1.primeclaim;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.PostgreSQLClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

  public static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public void start(Future<Void> startFuture) throws Exception {

    JsonObject postgreSQLClientConfig = new JsonObject()
      .put("host", "localhost")
      .put("port", 5432)
      .put("username", "postgres")
      .put("password", "mysecretpassword")
      .put("database", "postgres");
    SQLClient sqlClient = PostgreSQLClient.createShared(vertx, postgreSQLClientConfig);

    Router router = Router.router(vertx);
    router.route(HttpMethod.GET, "/").handler(routingContext -> {
      sqlClient.getConnection(res -> {
        if (res.succeeded()) {

          SQLConnection connection = res.result();

          // Got a connection
          connection.query("select 'hello' as col1" , event -> {
            String fromDatabase = event.result().getRows().get(0).getString("col1");
            routingContext.response()
              .putHeader("content-type", "text/plain")
              .end(fromDatabase);
          });

        } else {
          // Failed to get connection - deal with it
          LOGGER.error("Exception when getting connection", res.cause());
        }
      });

    });
    router.route(HttpMethod.GET, "/ping").handler(routingContext -> {
      sqlClient.getConnection(res -> {
        if (res.succeeded()) {

          SQLConnection connection = res.result();

          // Got a connection
          connection.query("select 'pong' as col1" , event -> {
            String fromDatabase = event.result().getRows().get(0).getString("col1");
            routingContext.response()
              .putHeader("content-type", "text/plain")
              .end(fromDatabase);
          });

        } else {
          // Failed to get connection - deal with it
          LOGGER.error("Exception when getting connection", res.cause());
        }
      });

    });


    vertx.createHttpServer().requestHandler(router).listen(8888, http -> {
      if (http.succeeded()) {
        startFuture.complete();
        LOGGER.info("HTTP server started on port 8888");
      } else {
        startFuture.fail(http.cause());
      }
    });
  }

}
