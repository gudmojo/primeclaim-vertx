package is.gudmundur1.primeclaim;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClaimService {

  private static Logger LOGGER = LoggerFactory.getLogger(ClaimService.class);

  private ClaimRepo claimRepo;

  public ClaimService(ClaimRepo claimRepo) {
    this.claimRepo = claimRepo;
  }

  public void createClaim(RoutingContext routingContext) {
    JsonObject bodyAsJson = routingContext.getBodyAsJson();
    Integer prime = bodyAsJson.getInteger("prime");
    String username = bodyAsJson.getString("username"); // TODO user the current user
    if (!PrimeUtil.isPrime(prime)) {
      routingContext.response().setStatusCode(400).end();
    }
    claimRepo.insertClaim(prime, username)
      .subscribe(result -> {
        LOGGER.info("Success: Create claim");
        routingContext.response().end();
      }, err -> {
        Throwable mappedError = claimRepo.mapErrors(err);
        if (mappedError instanceof UniqueViolationException) {
          LOGGER.info("Tried to post an already claimed prime");
          WebUtil.fail(routingContext);
        } else {
          LOGGER.error("Exception executing insert", mappedError);
          WebUtil.fail(routingContext);
        }
      });
  }

  // TODO add pagination
  // TODO add filter by owner
  public void getClaims(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    response.putHeader("content-type", "text/plain").setChunked(true);

    JsonArray collection = new JsonArray();
      claimRepo.getClaims().subscribe(resultSet -> {
        resultSet.getRows().forEach(collection::add);
        response.end(collection.encode());
      }, err -> {
        LOGGER.error("Query failed", err);
        WebUtil.fail(routingContext);
      });
  }

}
