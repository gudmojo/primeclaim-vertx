package is.gudmundur1.primeclaim;

import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.reactivex.ext.sql.SQLClient;
import io.vertx.reactivex.ext.sql.SQLConnection;

import java.util.List;

public class UserRepo {

  private SQLClient sqlClient;

  public UserRepo(SQLClient sqlClient) {
    this.sqlClient = sqlClient;
  }

  public Single<ResultSet> getUser(String username) {
    String sql =
      "select username, isadmin, apikey " +
        " from appuser u left join apikey on u.id = userid " +
        " where username = ?";
    JsonArray params = new JsonArray();
    params.add(username);

    return sqlClient.rxGetConnection().flatMap(connection ->
      connection.rxQueryWithParams(sql, params).doAfterTerminate(connection::close));
  }

  public Single<ResultSet> getUserByApiKey(List<String> apikeyList) {
    return sqlClient.rxGetConnection().flatMap(connection -> {
      String sql = "select username, isadmin from apikey a join appuser u on a.userid = u.id where apikey = ?";
      JsonArray params = new JsonArray();
      params.add(apikeyList.get(0));
      return connection.rxQueryWithParams(sql, params).doAfterTerminate(connection::close);
    });
  }

  Single<UpdateResult> insertUser(String username, boolean isadmin, String newApiKey) {
    JsonArray params = new JsonArray();
    params.add(username);
    params.add(isadmin);
    params.add(newApiKey);
    String sql =
      " with ins1 as ( " +
        "   insert into appuser (username, isadmin) values (?, ?) " +
        "   returning id as user_id " +
        " )" +
        " insert into apikey (apikey, userid) " +
        " select ?, user_id from ins1;";
    return sqlClient.rxGetConnection().flatMap(connection ->
      connection.rxUpdateWithParams(sql, params).doAfterTerminate(connection::close));
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
