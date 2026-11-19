# build и start повторяют команды публикации на Render и работают без mise:
# студенту курса нужен только рантайм стека. Остальные задачи make проксирует
# в mise run, они живут в mise.toml.
build:
	./gradlew installDist

start: build
	build/install/paas-java-app/bin/paas-java-app

.PHONY: build start

# Тонкий прокси: make <задача> запускает mise run <задача>. Задачи живут в mise.toml.
%: FORCE
	@mise run $@

# Без этого правила make пытается собрать сам Makefile через прокси.
Makefile: ;
FORCE: ;
