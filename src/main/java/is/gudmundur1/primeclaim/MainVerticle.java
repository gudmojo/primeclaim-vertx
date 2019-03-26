package is.gudmundur1.primeclaim;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.PostgreSQLClient;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

  public static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public void start(Future<Void> startFuture) {
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
          fail(routingContext);
        }
      });

    });
    // TODO add pagination
    // TODO add filter by owner
    router.route(HttpMethod.GET, "/claims").handler(routingContext -> {
      HttpServerResponse response = routingContext.response();
      response.putHeader("content-type", "text/plain").setChunked(true);

      sqlClient.getConnection(res -> {
        if (res.succeeded()) {

          SQLConnection connection = res.result();

          // Got a connection
          connection.query("select prime, owner from claim" , event -> {
            if (event.succeeded()) {
              event.result().getRows().forEach(x -> {
                response.write(x.toString());
              });
              response.end();
            } else {
              LOGGER.error("Exception in get claims", event.cause());
              fail(routingContext);
            }
          });

        } else {
          // Failed to get connection - deal with it
          LOGGER.error("Exception when getting connection", res.cause());
          fail(routingContext);
        }
      });

    });
    router.route(HttpMethod.GET, "/createtestclaims").handler(routingContext -> {
      sqlClient.getConnection(res -> {
        if (res.succeeded()) {

          SQLConnection connection = res.result();

          // Got a connection
          connection.execute("insert into claim (prime, owner) values (1, 'bob')", event -> {
            if (event.succeeded()) {
              routingContext.response()
                .putHeader("content-type", "text/plain")
                .end("ok");
            } else {
              LOGGER.error("Exception in create test claim", event.cause());
              fail(routingContext);
            }
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
            if (event.succeeded()) {
              String fromDatabase = event.result().getRows().get(0).getString("col1");
              routingContext.response()
                .putHeader("content-type", "text/plain")
                .end(fromDatabase);
            } else {
              LOGGER.error("Exception executing query", event.cause());
              fail(routingContext);
            }
          });

        } else {
          // Failed to get connection - deal with it
          LOGGER.error("Exception when getting connection", res.cause());
          fail(routingContext);
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

  private void fail(RoutingContext routingContext) {
    routingContext.response()
      .putHeader("content-type", "text/plain")
      .setStatusCode(500)
      .end("failure");
  }

}
