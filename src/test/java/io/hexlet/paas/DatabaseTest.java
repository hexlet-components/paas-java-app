package io.hexlet.paas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.javalin.Javalin;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Тесты с базой идут, когда задан TEST_DATABASE_URL, например
 * postgresql://postgres@localhost:5432/paas_test. База очищается.
 */
class DatabaseTest {
  private DataSource database;

  @BeforeEach
  void setUp() throws Exception {
    String url = System.getenv("TEST_DATABASE_URL");
    assumeTrue(url != null && !url.isEmpty(), "TEST_DATABASE_URL не задан");
    database = Database.dataSource(url);
    try (Connection connection = database.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS page_visits, flyway_schema_history");
    }
  }

  @Test
  void concurrentMigrationsApplyOnce() throws Exception {
    String url = System.getenv("TEST_DATABASE_URL");
    List<Future<?>> starts = new ArrayList<>();
    try (ExecutorService executor = Executors.newFixedThreadPool(5)) {
      for (int i = 0; i < 5; i++) {
        // У каждого экземпляра свой источник подключений, как у отдельных процессов.
        starts.add(executor.submit(() -> Database.migrate(Database.dataSource(url))));
      }
      for (Future<?> start : starts) {
        start.get();
      }
    }
    try (Connection connection = database.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success")) {
      result.next();
      assertThat(result.getInt(1)).isEqualTo(1);
    }
  }

  @Test
  void homeCountsVisits() throws Exception {
    Database.migrate(database);
    PrintStream log = new PrintStream(OutputStream.nullOutputStream());
    Javalin server = App.create("Первая публикация", log, database).start(0);
    try {
      HttpClient client = HttpClient.newHttpClient();
      URI uri = URI.create("http://127.0.0.1:" + server.port() + "/");
      for (int visit = 1; visit <= 2; visit++) {
        HttpResponse<String> response =
            client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.body())
            .isEqualTo(
                "<h1>Привет от Хекслета!</h1><p>Первая публикация</p><p>Посещений: "
                    + visit
                    + "</p>\n");
      }
    } finally {
      server.stop();
    }
  }
}
