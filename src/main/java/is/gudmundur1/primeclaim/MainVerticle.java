package is.gudmundur1.primeclaim;

import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.asyncsql.PostgreSQLClient;
import io.vertx.reactivex.ext.sql.SQLClient;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

  private void exceptionGuard(RoutingContext routingContext, Runnable fn) {
    try {
      fn.run();
    } catch (Throwable t) {
      LOGGER.error("Exception in web guard: ", t);
      WebUtil.fail(routingContext);
    }
  }

  @Override
  public void start(Future<Void> startFuture) {
    JsonObject postgreSQLClientConfig = new JsonObject()
      .put("host", config().getString(ConfigKey.POSTGRES_HOST))
      .put("port", config().getInteger(ConfigKey.POSTGRES_PORT))
      .put("username", config().getString(ConfigKey.POSTGRES_USER))
      .put("password", config().getString(ConfigKey.POSTGRES_PASSWORD))
      .put("database", config().getString(ConfigKey.POSTGRES_DATABASE));
    SQLClient sqlClient = PostgreSQLClient.createShared(vertx, postgreSQLClientConfig);

    UserRepo userRepo = new UserRepo(sqlClient);
    AuthService authService = new AuthService(userRepo);
    ClaimRepo claimRepo = new ClaimRepo(sqlClient);
    ClaimService claimService = new ClaimService(claimRepo);
    UserService userService = new UserService(sqlClient);

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.route(HttpMethod.GET, "/").handler(routingContext ->
      exceptionGuard(routingContext, () ->
        getRoot(routingContext)));

    router.route(HttpMethod.POST, "/claims").handler(routingContext ->
      exceptionGuard(routingContext, () ->
        authService.loggedInOnly(routingContext, () ->
          claimService.createClaim(routingContext))));

    router.route(HttpMethod.GET, "/claims").handler(routingContext ->
      exceptionGuard(routingContext, () ->
        authService.loggedInOnly(routingContext, () ->
          claimService.getClaims(routingContext))));

    router.route(HttpMethod.POST, "/user").handler(routingContext ->
      exceptionGuard(routingContext, () ->
        authService.adminOnly(routingContext, () ->
          userService.createUser(routingContext))));

    router.route(HttpMethod.GET, "/user/:username").handler(routingContext ->
      exceptionGuard(routingContext, () ->
        authService.adminOnly(routingContext, () ->
          userService.getUser(routingContext))));

    userService.bootstrapAdminApiKey(config().getString(ConfigKey.BOOTSTRAP_ADMIN_API_KEY));

    Integer httpPort = config().getInteger(ConfigKey.LISTEN_PORT);
    vertx.createHttpServer().requestHandler(router).listen(httpPort, http -> {
      if (http.failed()) {
        startFuture.fail(http.cause());
        return;
      }
      startFuture.complete();
      LOGGER.info("HTTP server started on port " + httpPort);
    });
  }

  private void getRoot(RoutingContext routingContext) {
    routingContext.response()
      .putHeader("content-type", "text/plain")
      .end("hello");
  }
}
