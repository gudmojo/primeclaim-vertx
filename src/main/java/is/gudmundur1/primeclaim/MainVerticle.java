package is.gudmundur1.primeclaim;

import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.asyncsql.PostgreSQLClient;
import io.vertx.reactivex.ext.sql.SQLClient;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MainVerticle extends AbstractVerticle {

  public static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

  private SQLClient sqlClient;

  private void exceptionGuard(RoutingContext routingContext, Runnable fn) {
    try {
      fn.run();
    } catch (Throwable t) {
      LOGGER.error("Exception in web guard: ", t);
      fail(routingContext);
    }
  }

  private void loggedInOnly(RoutingContext routingContext, Runnable fn) {
    sqlClient.rxGetConnection().flatMap(connection -> {
      String sql = "select username from apikey a join appuser u on a.userid = u.id where apikey = ?";
      JsonArray params = new JsonArray();
      List<String> apikeyList = routingContext.queryParam("apikey");
      if (apikeyList.isEmpty()) {
        LOGGER.warn("No api key");
        throw new RuntimeException("No api key");
      }
      params.add(apikeyList.get(0));
      return connection.rxQueryWithParams(sql, params).doAfterTerminate(connection::close);
    }).subscribe(queryResult -> {
        if (queryResult.getRows().isEmpty()) {
          LOGGER.warn("Invalid api key");
          routingContext.response().setStatusCode(403).end();
        } else {
          fn.run();
        }
      }, err -> {
        LOGGER.error("Error in loggedInOnly", err);
        routingContext.response().setStatusCode(403).end();
      });
  }

  @Override
  public void start(Future<Void> startFuture) {
    JsonObject postgreSQLClientConfig = new JsonObject()
      .put("host", config().getString("postgres-host"))
      .put("port", config().getInteger("postgres-port"))
      .put("username", config().getString("postgres-username"))
      .put("password", config().getString("postgres-password"))
      .put("database", config().getString("postgres-database"));
    sqlClient = PostgreSQLClient.createShared(vertx, postgreSQLClientConfig);

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.route(HttpMethod.GET, "/").handler(routingContext ->
      exceptionGuard(routingContext, () ->
        sqlClient.rxGetConnection()
          .flatMap(connection -> connection.rxQuery("select 'hello' as col1").doAfterTerminate(connection::close))
          .subscribe(resultSet -> {
            routingContext.response()
              .putHeader("content-type", "text/plain")
              .end(resultSet.getRows().get(0).getString("col1"));
            }, t -> {
              LOGGER.error("Query failed", t);
              fail(routingContext);
            }
            )));

    router.route(HttpMethod.POST, "/claims").handler(routingContext -> {
      exceptionGuard(routingContext, () -> {
        loggedInOnly(routingContext, () -> {
          JsonObject bodyAsJson = routingContext.getBodyAsJson();
          Integer prime = bodyAsJson.getInteger("prime");
          String username = bodyAsJson.getString("username"); // TODO authenticate
          JsonArray params = new JsonArray();
          params.add(prime);
          params.add(username);
          String sql =
            " insert into claim (prime, owner) " +
              " select ?, id from appuser where username = ?";
          sqlClient.rxGetConnection().flatMap(connection ->
            connection.rxUpdateWithParams(sql, params).doAfterTerminate(connection::close))
            .subscribe(result -> {
              LOGGER.info("Success: Create claim");
              routingContext.response().end();
            }, err -> {
              LOGGER.error("Exception executing insert", err);
              fail(routingContext);
            });
        });
      });
    });

    // TODO add pagination
    // TODO add filter by owner
    router.route(HttpMethod.GET, "/claims").handler(routingContext -> {
      exceptionGuard(routingContext, () -> {
        HttpServerResponse response = routingContext.response();
        response.putHeader("content-type", "text/plain").setChunked(true);

        JsonArray collection = new JsonArray();
        sqlClient.rxGetConnection().flatMap(connection -> {
          String sql = "select prime, username as owner from claim c left join appuser u on c.owner = u.id";
          return connection.rxQuery(sql).doAfterTerminate(connection::close);
        })
          .subscribe(resultSet -> {
            resultSet.getRows().forEach(collection::add);
            response.end(collection.encode());
          }, err -> {
            LOGGER.error("Query failed", err);
            fail(routingContext);
          });
        });
      });


    router.route(HttpMethod.GET, "/ping").handler(routingContext ->
      exceptionGuard(routingContext, () -> {
        loggedInOnly(routingContext, () -> {
          sqlClient.rxGetConnection().flatMap(connection ->
            connection.rxQuery("select 'pong' as col1").doAfterTerminate(connection::close))
            .subscribe(result -> {
              routingContext.response()
                .putHeader("content-type", "text/plain")
                .end(result.getRows().get(0).getString("col1"));
            }, err -> {
              LOGGER.error("Exception executing query", err);
              fail(routingContext);
            });
          });
      }));

    router.route(HttpMethod.POST, "/user").handler(routingContext -> {
      exceptionGuard(routingContext, () -> {
        JsonObject bodyAsJson = routingContext.getBodyAsJson();
        String username = bodyAsJson.getString("username");
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
        // TODO: verify username is valid and not null
        sqlClient.rxGetConnection().flatMap(connection ->
          connection.rxUpdateWithParams(sql, params).doAfterTerminate(connection::close))
          .subscribe(result -> {
              LOGGER.info("Success: Create user");
              routingContext.response().end();
            }, err -> {
              LOGGER.error("Exception executing insert", err);
              fail(routingContext);
            });
        });
      });

    router.route(HttpMethod.GET, "/user/:username").handler(routingContext -> {
      exceptionGuard(routingContext, () -> {
        HttpServerResponse response = routingContext.response();
        String sql =
          "select username, isadmin, apikey " +
            " from appuser u left join apikey on u.id = userid " +
            " where username = ?";
        JsonArray params = new JsonArray();
        String username = routingContext.request().getParam("username");
        params.add(username);

        sqlClient.rxGetConnection().flatMap(connection ->
          connection.rxQueryWithParams(sql, params).doAfterTerminate(connection::close))
          .subscribe(result -> {
            response.end(result.getRows().get(0).encode());
          }, err -> {
            LOGGER.error("Exception in get user", err);
            fail(routingContext);
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
