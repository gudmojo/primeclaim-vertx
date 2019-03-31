package is.gudmundur1.primeclaim;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.PostgreSQLClient;
import io.vertx.ext.sql.SQLClient;
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
      sqlClient.getConnection(getConnection -> {
        if (getConnection.failed()) {
          LOGGER.error("Exception when getting connection", getConnection.cause());
          fail(routingContext);
          return;
        }
        getConnection.result().query("select 'hello' as col1", query -> {
          if (query.failed()) {
            LOGGER.error("Query failed", query.cause());
            fail(routingContext);
            return;
          }
          String fromDatabase = query.result().getRows().get(0).getString("col1");
          routingContext.response()
            .putHeader("content-type", "text/plain")
            .end(fromDatabase);
          });
      });
    });
    // TODO add pagination
    // TODO add filter by owner
    router.route(HttpMethod.GET, "/claims").handler(routingContext -> {
      HttpServerResponse response = routingContext.response();
      response.putHeader("content-type", "text/plain").setChunked(true);

      sqlClient.getConnection(getConnection -> {
        if (getConnection.failed()) {
          LOGGER.error("Exception when getting connection", getConnection.cause());
          fail(routingContext);
          return;
        }
        getConnection.result().query("select prime, owner from claim" , query -> {
          if (query.failed()) {
            LOGGER.error("Exception in get claims", query.cause());
            fail(routingContext);
            return;
          }
          query.result().getRows().forEach(x -> {
            response.write(x.toString());
          });
          response.end();
        });
      });

    });
    router.route(HttpMethod.GET, "/createtestclaims").handler(routingContext -> {
      sqlClient.getConnection(getConnection -> {
        if (getConnection.failed()) {
          LOGGER.error("Exception when getting connection", getConnection.cause());
          fail(routingContext);
          return;
        }
        getConnection.result().execute("insert into claim (prime, owner) values (1, 'bob')", execute -> {
          if (execute.failed()) {
            LOGGER.error("Exception in create test claim", execute.cause());
            fail(routingContext);
            return;
          }
          routingContext.response()
            .putHeader("content-type", "text/plain")
            .end("ok");
        });
      });
    });
    router.route(HttpMethod.GET, "/ping").handler(routingContext -> {
      sqlClient.getConnection(getConnection -> {
        if (getConnection.failed()) {
          LOGGER.error("Exception when getting connection", getConnection.cause());
          fail(routingContext);
          return;
        }
        getConnection.result().query("select 'pong' as col1" , event -> {
          if (event.failed()) {
            LOGGER.error("Exception executing query", event.cause());
            fail(routingContext);
            return;
          }
          String fromDatabase = event.result().getRows().get(0).getString("col1");
          routingContext.response()
            .putHeader("content-type", "text/plain")
            .end(fromDatabase);
        });
      });
    });


    vertx.createHttpServer().requestHandler(router).listen(8888, http -> {
      if (http.failed()) {
        startFuture.fail(http.cause());
        return;
      }
      startFuture.complete();
      LOGGER.info("HTTP server started on port 8888");
    });
  }

  private void fail(RoutingContext routingContext) {
    routingContext.response()
      .putHeader("content-type", "text/plain")
      .setStatusCode(500)
      .end("failure");
  }

}
