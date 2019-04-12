package is.gudmundur1.primeclaim;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.sql.SQLClient;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClaimService {

  private static Logger LOGGER = LoggerFactory.getLogger(ClaimService.class);

  private SQLClient sqlClient;

  public ClaimService(SQLClient sqlClient) {
    this.sqlClient = sqlClient;
  }

  public void createClaim(RoutingContext routingContext) {
    JsonObject bodyAsJson = routingContext.getBodyAsJson();
    Integer prime = bodyAsJson.getInteger("prime");
    String username = bodyAsJson.getString("username"); // TODO authenticate
    if (!PrimeUtil.isPrime(prime)) {
      routingContext.response().setStatusCode(400).end();
    }
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
        WebUtil.fail(routingContext);
      });
  }

  // TODO add pagination
  // TODO add filter by owner
  public void getClaims(RoutingContext routingContext) {
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
        WebUtil.fail(routingContext);
      });
  }

}
