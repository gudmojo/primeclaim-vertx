package is.gudmundur1.primeclaim;

import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.sql.SQLClient;
import io.vertx.reactivex.ext.sql.SQLConnection;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class UserService {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

  private SQLClient sqlClient;

  public UserService(SQLClient sqlClient) {
    this.sqlClient = sqlClient;
  }

  public void createUser(RoutingContext routingContext) {
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
        WebUtil.fail(routingContext);
      });
  }

  public void getUser(RoutingContext routingContext) {
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

  // If bootstrap api key is set, create or update admin api key
  public void bootstrapAdminApiKey(String bootstrapAdminApiKey) {
    if (bootstrapAdminApiKey != null) {
      sqlClient.rxGetConnection().flatMap(connection ->
        Single.zip(Single.just(connection),
          connection.rxQuery("select apikey as keys from apikey where userid = 0"),
          TupleConnectionAndResultSet::new)).flatMap(tuple -> {
        JsonArray param = new JsonArray();
        param.add(bootstrapAdminApiKey);
        SQLConnection connection = tuple.connection;
        String sql = tuple.resultSet.getRows().isEmpty() ?
          "insert into apikey (apikey, userid) values (?, 0)" :
          "update apikey set apikey = ? where userid = 0";
        return connection.rxUpdateWithParams(sql, param).doAfterTerminate(connection::close);
      }).subscribe(success -> {});
    }
  }

  private class TupleConnectionAndResultSet {
    SQLConnection connection;
    ResultSet resultSet;

    TupleConnectionAndResultSet(SQLConnection connection, ResultSet resultSet) {
      this.connection = connection;
      this.resultSet = resultSet;
    }
  }

}
