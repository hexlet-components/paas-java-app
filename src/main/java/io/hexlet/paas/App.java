package io.hexlet.paas;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.sentry.Sentry;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Учебное приложение курса «Деплой на PaaS»: HTTP-контракт общий для всех стеков. */
public final class App {
  private static final String HEADING = "Привет от Хекслета!";
  private static final String DEFAULT_MESSAGE = "Hello, PaaS!";
  private static final String ERROR_TEXT = "Oops! Something went wrong!";
  private static final String HTML = "text/html; charset=utf-8";
  private static final String PLAIN = "text/plain; charset=utf-8";

  private final String message;
  private final PrintStream log;

  App(String message, PrintStream log) {
    this.message = message == null || message.isEmpty() ? DEFAULT_MESSAGE : message;
    this.log = log;
  }

  /** Запускает сервер на 0.0.0.0, порт 0 выбирает свободный. */
  static HttpServer start(int port, String message, PrintStream log) throws IOException {
    App app = new App(message, log);
    HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
    server.createContext("/", app::handle);
    server.start();
    return server;
  }

  static String escapeHtml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private static String env(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isEmpty() ? fallback : value;
  }

  private static void fail() {
    throw new IllegalStateException(ERROR_TEXT);
  }

  private void handle(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    int status = 200;
    String contentType = PLAIN;
    String body;
    if (path.equals("/")) {
      contentType = HTML;
      body = "<h1>" + HEADING + "</h1><p>" + escapeHtml(message) + "</p>";
    } else if (path.equals("/error")) {
      status = 500;
      body = ERROR_TEXT;
      try {
        fail();
      } catch (IllegalStateException e) {
        Sentry.captureException(e);
      }
    } else {
      status = 404;
      body = "not found";
    }
    byte[] payload = (body + "\n").getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(status, payload.length);
    try (var output = exchange.getResponseBody()) {
      output.write(payload);
    }
    log.printf("%s %s %d%n", exchange.getRequestMethod(), path, status);
  }

  public static void main(String[] args) throws IOException {
    String dsn = System.getenv("SENTRY_DSN");
    if (dsn != null && !dsn.isEmpty()) {
      Sentry.init(options -> options.setDsn(dsn));
    }
    int port = Integer.parseInt(env("PORT", "3000"));
    start(port, System.getenv("SERVER_MESSAGE"), System.out);
    System.out.println("Listening on http://0.0.0.0:" + port);
  }
}
