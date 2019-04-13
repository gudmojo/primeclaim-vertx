package is.gudmundur1.primeclaim;

import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Predicate;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;

public class AuthService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

  private final UserRepo userRepo;

  public AuthService(UserRepo userRepo) {
    this.userRepo = userRepo;
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
    List<String> apikeyList = routingContext.queryParam("apikey");
    if (apikeyList.isEmpty()) {
      LOGGER.warn("No api key");
      routingContext.response().setStatusCode(HTTP_FORBIDDEN).end();
      return;
    }

    userRepo.getUserByApiKey(apikeyList).subscribe(queryResult -> {
      List<JsonObject> rows = queryResult.getRows();
      if (rows.isEmpty()) {
        LOGGER.warn("Invalid api key");
        routingContext.response().setStatusCode(HTTP_FORBIDDEN).end();
      } else if (!predicate.test(new AppUser(rows.get(0)))) {
        LOGGER.warn("Bad user");
        routingContext.response().setStatusCode(HTTP_FORBIDDEN).end();
      } else {
        fn.run();
      }
    }, err -> {
      LOGGER.error("Error in loggedInOnly", err);
      routingContext.response().setStatusCode(HTTP_FORBIDDEN).end();
    });
  }

  private class AppUser {
    final String username;
    final boolean isAdmin;

    AppUser(JsonObject json) {
      this.username = json.getString("username");
      this.isAdmin = json.getBoolean("isadmin");
    }
  }
}
