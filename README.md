<div align="center">

<a href="https://hexlet.io/?utm_source=github&utm_medium=link&utm_campaign=paas-java-app">
    <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/Hexlet/brand-assets/master/images/svg/hexlet_wordmark_white_rus.svg">
        <img src="https://raw.githubusercontent.com/Hexlet/brand-assets/master/images/svg/hexlet_wordmark_primary_rus.svg" alt="Хекслет" height="64">
    </picture>
</a>

# Java-приложение для курса «Деплой на PaaS»

[![CI](https://github.com/hexlet-components/paas-java-app/actions/workflows/main.yml/badge.svg)](https://github.com/hexlet-components/paas-java-app/actions/workflows/main.yml)
[![Check](https://github.com/hexlet-components/paas-java-app/actions/workflows/check.yml/badge.svg)](https://github.com/hexlet-components/paas-java-app/actions/workflows/check.yml)

</div>

Компонент создаёт и поддерживает команда [Хекслета](https://hexlet.io/?utm_source=github&utm_medium=link&utm_campaign=paas-java-app), образовательного проекта.

Учебное приложение курса [«Деплой на PaaS»](https://ru.hexlet.io/courses/paas-deploy) для тех, кто проходит курс на Java. Студент делает fork, публикует приложение на Render и учится на нём конфигурации, обновлениям и диагностике. Такие же приложения есть для других стеков курса, у всех одинаковое поведение по HTTP. Изменение маршрутов, ответов или формата лога ломает шаги курса, поэтому такие правки сначала сверяются с уроками.

## Поведение

| Запрос | Ответ |
| --- | --- |
| `GET /` | `200`, заголовок «Привет от Хекслета!», значение `SERVER_MESSAGE`, с `DATABASE_URL` ещё счётчик посещений |
| `GET /error` | `500`, событие уходит в Sentry, процесс продолжает работать |
| другой путь | `404` |

Сервер слушает `0.0.0.0` на порту из `PORT` (по умолчанию `3000`) и печатает строку `GET / 200` на каждый запрос. Sentry SDK включается переменной `SENTRY_DSN`, без неё события не отправляются.

## Публикация на Render

У Render нет готовой среды Java, поэтому приложение собирается по `Dockerfile` в корне.

| Поле | Значение |
| --- | --- |
| Language | Docker |
| Dockerfile Path | `./Dockerfile` |
| Docker Command | пусто, запускает `CMD` из Dockerfile |
| Environment | `PORT=3000`, `SERVER_MESSAGE`, по желанию `SENTRY_DSN` и `DATABASE_URL` |

Версию JDK задают образы `eclipse-temurin` в `Dockerfile`: сборка идёт на JDK, запуск на JRE.

## База данных

С переменной `DATABASE_URL` главная страница считает посещения в таблице `page_visits` и показывает строку «Посещений: N». Адрес имеет вид `postgresql://user:password@host:port/name`, порт необязателен. На Render это Internal Database URL базы из того же региона. Без переменной приложение работает без базы, и страница остаётся прежней.

Миграции лежат в `src/main/resources/db/migration` и попадают в сборку. При запуске приложение сначала применяет новые миграции через [Flyway](https://documentation.red-gate.com/fd) и только потом открывает порт. Flyway перед применением берёт advisory-блокировку PostgreSQL, поэтому из двух экземпляров, запущенных разом, миграцию применяет один, а второй ждёт и видит её уже применённой. Новая миграция это следующий файл `V<номер>__<описание>.sql` в том же каталоге.

Тесты с базой идут, когда задан `TEST_DATABASE_URL`, и очищают эту базу. Без переменной они пропускаются. В CI базу даёт сервис PostgreSQL из `main.yml`.

## Запуск

```sh
mise run setup
mise run test
PORT=3000 SERVER_MESSAGE="Первая публикация" mise run start
```

`make build` повторяет стадию сборки `Dockerfile`, `make start` запускает приложение командой из его `CMD` и перед этим сам выполняет `build`. Эти две цели работают без mise, нужен только JDK 27, его установка описана в [инструкции Хекслета](https://github.com/Hexlet/ru-instructions/blob/main/java.md).

`make <задача>` вызывает ту же задачу mise.

## Проверки

`mise run check` гоняет тот же набор, что CI: Spotless, Error Prone, Hadolint, OSV-Scanner, разметку, опечатки, ссылки, workflows и секреты.
