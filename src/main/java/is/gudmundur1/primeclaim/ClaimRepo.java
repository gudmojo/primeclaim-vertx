package is.gudmundur1.primeclaim;

import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.reactivex.ext.sql.SQLClient;
import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException;
import scala.collection.immutable.Map;

public class ClaimRepo {

  private final SQLClient sqlClient;

  public ClaimRepo(SQLClient sqlClient) {
    this.sqlClient = sqlClient;
  }

  public Single<UpdateResult> insertClaim(Integer prime, String username) {
    JsonArray params = new JsonArray();
    params.add(prime);
    params.add(username);
    String sql =
      " insert into claim (prime, owner) " +
        " select ?, id from appuser where username = ?";
    return sqlClient.rxGetConnection().flatMap(connection ->
      connection.rxUpdateWithParams(sql, params).doAfterTerminate(connection::close));
  }

  private String getSqlState(Map<Object, String> fields) {
    char key = 'C'; // Scala library weirdness. Figured out via debugger
    return fields.get(key).get();
  }

  public Single<ResultSet> getClaims() {
    return sqlClient.rxGetConnection().flatMap(connection -> {
      String sql = "select prime, username as owner from claim c left join appuser u on c.owner = u.id";
      return connection.rxQuery(sql).doAfterTerminate(connection::close);
    });

  }

  public Throwable mapErrors(Throwable throwable) {
    if (throwable instanceof GenericDatabaseException) {
      GenericDatabaseException ex = (GenericDatabaseException) throwable;
      Map<Object, String> fields = ex.errorMessage().fields();
      String sqlstate = getSqlState(fields);
      if ("23505".equals(sqlstate)) {
        return new UniqueViolationException();
      }
    }
    return throwable;
  }
}
