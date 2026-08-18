.DEFAULT_GOAL := help

.PHONY: help run cli test build

help:
	@echo "Connect Four backend commands"
	@echo ""
	@echo "  make run     Run the Spring Boot application"
	@echo "  make cli     Run the legacy terminal game"
	@echo "  make test    Run the backend tests"
	@echo "  make build   Test and package the backend"

run:
	./mvnw spring-boot:run

cli:
	./mvnw compile
	java -cp target/classes io.github.blam135.connectfour.legacy.ConnectFour

test:
	./mvnw test

build:
	./mvnw package
