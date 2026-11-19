plugins {
    application
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.javalin)
    implementation(libs.sentry)
    runtimeOnly(libs.slf4jSimple)

    errorprone(libs.errorprone)

    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testImplementation(libs.assertjCore)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

// Версии зависимостей фиксирует gradle.lockfile: его читает OSV-Scanner.
dependencyLocking {
    lockAllConfigurations()
}

application {
    mainClass = "io.hexlet.paas.App"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 27
    // Предупреждения компилятора и Error Prone ломают сборку.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat()
    }
}
