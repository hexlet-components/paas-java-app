package io.hexlet.paas;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;

/** Подключение к PostgreSQL по DATABASE_URL, миграции и счётчик посещений главной страницы. */
final class Database {
  // Один запрос и создаёт строку счётчика, и увеличивает её, поэтому одновременные запросы
  // не теряют посещения.
  private static final String COUNT_VISIT =
      "INSERT INTO page_visits (path, total) VALUES ('/', 1) "
          + "ON CONFLICT (path) DO UPDATE SET total = page_visits.total + 1 "
          + "RETURNING total";

  private Database() {}

  /**
   * Переводит адрес вида postgresql://user:password@host:port/name в настройки драйвера JDBC. Порт
   * в адресе необязателен, Render его не указывает.
   */
  static DataSource dataSource(String url) {
    URI uri = URI.create(url);
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setServerNames(new String[] {uri.getHost()});
    if (uri.getPort() != -1) {
      dataSource.setPortNumbers(new int[] {uri.getPort()});
    }
    dataSource.setDatabaseName(uri.getPath().substring(1));
    String userInfo = uri.getRawUserInfo();
    if (userInfo != null) {
      String[] parts = userInfo.split(":", 2);
      dataSource.setUser(decode(parts[0]));
      if (parts.length == 2) {
        dataSource.setPassword(decode(parts[1]));
      }
    }
    return dataSource;
  }

  private static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  /**
   * Применяет новые миграции из src/main/resources/db/migration. Перед применением Flyway берёт
   * блокировку в базе, поэтому второй экземпляр приложения ждёт, пока первый закончит.
   *
   * <p>В базе уже могут быть чужие таблицы, например учебные. Без истории миграций Flyway на такой
   * базе останавливается, поэтому он отмечает её версией 0 и затем применяет все миграции.
   */
  static void migrate(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .load()
        .migrate();
  }

  /** Увеличивает счётчик главной страницы и возвращает новое значение. */
  static long countVisit(DataSource dataSource) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(COUNT_VISIT);
        ResultSet result = statement.executeQuery()) {
      result.next();
      return result.getLong(1);
    }
  }
}
