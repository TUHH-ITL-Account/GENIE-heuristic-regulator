package core;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.MariaDbPoolDataSource;

public class MariaDBTest {

  @Disabled
  public void testControllerRunning() throws InterruptedException, IOException, SQLException {
    MariaDbPoolDataSource pool = new MariaDbPoolDataSource(
        "jdbc:mariadb://localhost:3306/test?pipe=MariaDB&user=root&password=admin&maxPoolSize=10");

    try (Connection connection = pool.getConnection()) {
      try (Statement stmt = connection.createStatement()) {
        ResultSet rs = stmt.executeQuery("SELECT * FROM testtable");
        rs.next();
        System.out.println(rs.getString(1)); //4489
      }
    }

    pool.close();
  }

  @Test
  public void testJavaSys() {
    System.out.println(System.getProperty("os.name"));
    System.out.println(System.getProperty("os.arch"));
  }

}
