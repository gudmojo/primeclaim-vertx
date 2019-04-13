package is.gudmundur1.primeclaim;

import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class UserService {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

  private final UserRepo userRepo;

  public UserService(UserRepo userRepo) {
    this.userRepo = userRepo;
  }

  public void createUser(RoutingContext routingContext) {
    JsonObject bodyAsJson = routingContext.getBodyAsJson();
    String username = bodyAsJson.getString("username");
    Boolean isadmin = bodyAsJson.getBoolean("isadmin");
    String newApiKey = ApiKeyUtil.generateApiKey();
    // TODO: verify username is valid and not null
    userRepo.insertUser(username, isadmin, newApiKey)
      .subscribe(result -> {
        LOGGER.info("Success: Create user");
        routingContext.response().end();
      }, err -> {
        LOGGER.error("Exception executing insert", err);
        WebUtil.fail(routingContext);
      });
  }

  public void getUser(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    String username = routingContext.request().getParam("username");
    userRepo.getUser(username).subscribe(result -> {
        List<JsonObject> rows = result.getRows();
        if (rows.isEmpty()) {
          response.setStatusCode(404).end();
        } else {
          response.end(rows.get(0).encode());
        }
      }, err -> {
        LOGGER.error("Exception in get user", err);
        WebUtil.fail(routingContext);
      });
  }
}
