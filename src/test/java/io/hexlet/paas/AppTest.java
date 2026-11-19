package io.hexlet.paas;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppTest {
  private final HttpClient client = HttpClient.newHttpClient();
  private final ByteArrayOutputStream logBuffer = new ByteArrayOutputStream();
  private final List<SentryEvent> events = new CopyOnWriteArrayList<>();
  private HttpServer server;

  @BeforeEach
  void setUp() throws IOException {
    // beforeSend запоминает событие и возвращает null: в сеть ничего не уходит.
    Sentry.init(options -> {
      options.setDsn("http://public@127.0.0.1:1/1");
      options.setBeforeSend((event, hint) -> {
        events.add(event);
        return null;
      });
    });
    start("<b>&\"'");
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
    Sentry.close();
  }

  private void start(String message) throws IOException {
    if (server != null) {
      server.stop(0);
    }
    PrintStream log = new PrintStream(logBuffer, true, StandardCharsets.UTF_8);
    server = App.start(0, message, log);
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    return client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
  }

  private String log() {
    return logBuffer.toString(StandardCharsets.UTF_8);
  }

  @Test
  void rootEscapesMessage() throws Exception {
    HttpResponse<String> response = get("/");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("text/html; charset=utf-8");
    assertThat(response.body())
        .isEqualTo("<h1>Привет от Хекслета!</h1><p>&lt;b&gt;&amp;&quot;&#39;</p>\n");
  }

  @Test
  void rootUsesDefaultMessage() throws Exception {
    start("");

    assertThat(get("/").body()).isEqualTo("<h1>Привет от Хекслета!</h1><p>Hello, PaaS!</p>\n");
  }

  @Test
  void errorReportsToSentryAndServerKeepsWorking() throws Exception {
    HttpResponse<String> response = get("/error");

    assertThat(response.statusCode()).isEqualTo(500);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("text/plain; charset=utf-8");
    assertThat(response.body()).isEqualTo("Oops! Something went wrong!\n");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getThrowable()).hasMessage("Oops! Something went wrong!");
    assertThat(get("/").statusCode()).isEqualTo(200);
  }

  @Test
  void unknownPathIsNotFound() throws Exception {
    HttpResponse<String> response = get("/missing");

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("text/plain; charset=utf-8");
    assertThat(response.body()).isEqualTo("not found\n");
  }

  @Test
  void logsEveryRequest() throws Exception {
    get("/");
    get("/error");
    get("/missing");

    assertThat(log().lines()).containsExactly("GET / 200", "GET /error 500", "GET /missing 404");
  }

  @Test
  void listensOnAllInterfaces() {
    assertThat(server.getAddress().getAddress().isAnyLocalAddress()).isTrue();
  }
}
