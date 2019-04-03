package is.gudmundur1.primeclaim;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.PostgreSQLClient;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

public class MainVerticle extends AbstractVerticle {

  public static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public void start(Future<Void> startFuture) {
    JsonObject postgreSQLClientConfig = new JsonObject()
      .put("host", config().getString("postgres-host"))
      .put("port", config().getInteger("postgres-port"))
      .put("username", config().getString("postgres-username"))
      .put("password", config().getString("postgres-password"))
      .put("database", config().getString("postgres-database"));
    SQLClient sqlClient = PostgreSQLClient.createShared(vertx, postgreSQLClientConfig);

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
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
    router.route(HttpMethod.POST, "/user").handler(routingContext -> {
      JsonObject bodyAsJson = routingContext.getBodyAsJson();
      String username = bodyAsJson.getString("username");
      // TODO: verify username is valid and not null
      sqlClient.getConnection(getConnection -> {
        if (getConnection.failed()) {
          LOGGER.error("Exception when getting connection", getConnection.cause());
          fail(routingContext);
          return;
        }
        JsonArray params = new JsonArray();
        params.add(username);
        params.add(bodyAsJson.getBoolean("isadmin"));
        params.add(ApiKeyUtil.generateApiKey());
        String sql =
          " with ins1 as ( " +
          "   insert into appuser (username, isadmin) values (?, ?) " +
          "   returning id as user_id " +
          " )" +
          " insert into apikey (apikey, userid) " +
          " select ?, user_id from ins1;";
        getConnection.result().updateWithParams(sql, params, execute -> {
          if (execute.failed()) {
            LOGGER.error("Exception executing insert", execute.cause());
            fail(routingContext);
            return;
          }
          LOGGER.info("Insert success");
          routingContext.response().end();
        });
      });
    });
    router.route(HttpMethod.GET, "/user/:username").handler(routingContext -> {
      HttpServerResponse response = routingContext.response();

      sqlClient.getConnection(getConnection -> {
        if (getConnection.failed()) {
          LOGGER.error("Exception when getting connection", getConnection.cause());
          fail(routingContext);
          return;
        }
        String sql = "select username, isadmin, apikey from appuser u left join apikey on u.id = userid where username = ?";
        JsonArray params = new JsonArray();
        String username = routingContext.request().getParam("username");
        params.add(username);
        getConnection.result().queryWithParams(sql, params, query -> {
          if (query.failed()) {
            LOGGER.error("Exception in get user", query.cause());
            fail(routingContext);
            return;
          }
          List<JsonObject> rows = query.result().getRows();
          String responseText = rows.get(0).encode();
          response.end(responseText);
        });
      });
    });


    Integer httpPort = config().getInteger("http.port");
    vertx.createHttpServer().requestHandler(router).listen(httpPort, http -> {
      if (http.failed()) {
        startFuture.fail(http.cause());
        return;
      }
      startFuture.complete();
      LOGGER.info("HTTP server started on port " + httpPort);
    });
  }

  private void fail(RoutingContext routingContext) {
    routingContext.response()
      .putHeader("content-type", "text/plain")
      .setStatusCode(500)
      .end("failure");
  }

}
