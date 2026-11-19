# Сборка на JDK, запуск на JRE: в итоговом образе нет Gradle и исходников.
FROM eclipse-temurin:27-jdk AS build

WORKDIR /app

# Wrapper и описание сборки идут отдельным слоем, чтобы зависимости
# кешировались, пока build.gradle.kts не меняется.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.lockfile ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null

COPY src ./src
RUN ./gradlew --no-daemon installDist -x test

FROM eclipse-temurin:27-jre

WORKDIR /app

COPY --from=build /app/build/install/paas-java-app ./

EXPOSE 3000

CMD ["bin/paas-java-app"]
