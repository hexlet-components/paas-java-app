package io.hexlet.paas;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.sentry.Sentry;
import java.io.PrintStream;

/** Учебное приложение курса «Деплой на PaaS»: HTTP-контракт общий для всех стеков. */
public final class App {
  private static final String HEADING = "Привет от Хекслета!";
  private static final String DEFAULT_MESSAGE = "Hello, PaaS!";
  private static final String ERROR_TEXT = "Oops! Something went wrong!";
  private static final String HTML = "text/html; charset=utf-8";
  private static final String PLAIN = "text/plain; charset=utf-8";

  private App() {}

  /** Собирает приложение Javalin: маршруты, обработку ошибок и строку лога на каждый запрос. */
  static Javalin create(String rawMessage, PrintStream log) {
    String message = rawMessage == null || rawMessage.isEmpty() ? DEFAULT_MESSAGE : rawMessage;
    return Javalin.create(config -> {
      config.startup.showJavalinBanner = false;
      config.requestLogger.http(
          (ctx, ms) -> log.printf("%s %s %d%n", ctx.method(), ctx.path(), ctx.status().getCode()));
      config.routes.get(
          "/",
          ctx ->
              reply(
                  ctx, 200, HTML, "<h1>" + HEADING + "</h1><p>" + escapeHtml(message) + "</p>\n"));
      config.routes.get("/error", ctx -> fail());
      config.routes.exception(
          IllegalStateException.class,
          (e, ctx) -> {
            Sentry.captureException(e);
            reply(ctx, 500, PLAIN, ERROR_TEXT + "\n");
          });
      config.routes.error(404, ctx -> reply(ctx, 404, PLAIN, "not found\n"));
    });
  }

  // Jetty пишет заголовок как text/html;charset=utf-8, без пробела: тип и кодировка те же.
  private static void reply(Context ctx, int status, String contentType, String body) {
    ctx.status(status).contentType(contentType).result(body);
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

  public static void main(String[] args) {
    String dsn = System.getenv("SENTRY_DSN");
    if (dsn != null && !dsn.isEmpty()) {
      Sentry.init(options -> options.setDsn(dsn));
    }
    int port = Integer.parseInt(env("PORT", "3000"));
    create(System.getenv("SERVER_MESSAGE"), System.out).start("0.0.0.0", port);
    System.out.println("Listening on http://0.0.0.0:" + port);
  }
}
