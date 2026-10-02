FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY spring-monitor/ ./spring-monitor/
WORKDIR /workspace/spring-monitor
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/* \
	&& groupadd --system --gid 10001 monitor \
	&& useradd --system --uid 10001 --gid monitor --home-dir /app --shell /usr/sbin/nologin monitor
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/spring-monitor/target/spring-monitor-0.0.1-SNAPSHOT.jar /app/spring-monitor.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70.0
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/spring-monitor.jar"]