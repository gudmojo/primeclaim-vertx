package is.gudmundur1.primeclaim;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.sql.SQLClient;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Predicate;

public class AuthService {

  private static Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

  private SQLClient sqlClient;

  public AuthService(SQLClient sqlClient) {
    this.sqlClient = sqlClient;
  }

  public void loggedInOnly(RoutingContext routingContext, Runnable fn) {
    loggedIn(routingContext, fn, this::userExists);
  }

  private boolean userExists(AppUser appUser) {
    return appUser != null;
  }

  public void adminOnly(RoutingContext routingContext, Runnable fn) {
    loggedIn(routingContext, fn, this::userIsAdmin);
  }

  private boolean userIsAdmin(AppUser appUser) {
    return appUser.isAdmin;
  }

  private void loggedIn(RoutingContext routingContext, Runnable fn, Predicate<AppUser> predicate) {
    sqlClient.rxGetConnection().flatMap(connection -> {
      List<String> apikeyList = routingContext.queryParam("apikey");
      if (apikeyList.isEmpty()) {
        LOGGER.warn("No api key");
        throw new RuntimeException("No api key");
      }
      String sql = "select username, isadmin from apikey a join appuser u on a.userid = u.id where apikey = ?";
      JsonArray params = new JsonArray();
      params.add(apikeyList.get(0));
      return connection.rxQueryWithParams(sql, params).doAfterTerminate(connection::close);
    }).subscribe(queryResult -> {
      List<JsonObject> rows = queryResult.getRows();
      if (rows.isEmpty()) {
        LOGGER.warn("Invalid api key");
        routingContext.response().setStatusCode(403).end();
      } else if (!predicate.test(new AppUser(rows.get(0)))) {
        LOGGER.warn("Bad user");
        routingContext.response().setStatusCode(403).end();
      } else {
        fn.run();
      }
    }, err -> {
      LOGGER.error("Error in loggedInOnly", err);
      routingContext.response().setStatusCode(403).end();
    });
  }

  private class AppUser {
    String username;
    boolean isAdmin;

    AppUser(JsonObject json) {
      this.username = json.getString("username");
      this.isAdmin = json.getBoolean("isadmin");
    }
  }
}
