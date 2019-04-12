package is.gudmundur1.primeclaim;

import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import io.vertx.reactivex.ext.sql.SQLClient;

import java.util.List;

public class UserRepo {

  private SQLClient sqlClient;

  public UserRepo(SQLClient sqlClient) {
    this.sqlClient = sqlClient;
  }

  public Single<ResultSet> getUserByApiKey(List<String> apikeyList) {
    return sqlClient.rxGetConnection().flatMap(connection -> {
      String sql = "select username, isadmin from apikey a join appuser u on a.userid = u.id where apikey = ?";
      JsonArray params = new JsonArray();
      params.add(apikeyList.get(0));
      return connection.rxQueryWithParams(sql, params).doAfterTerminate(connection::close);
    });
  }

}
