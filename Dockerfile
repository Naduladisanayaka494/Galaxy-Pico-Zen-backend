# Build with JDK 17 even though the POM targets 1.8 bytecode — Spring Boot
# 2.7 and the toolchain are happy either way, and 17 is what the README asks
# developers to build with, so the container matches local builds.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Maven attempts each download exactly once by default, so one truncated
# response ("Premature end of Content-Length delimited message body") kills the
# whole build. Retry instead.
ENV MAVEN_OPTS="-Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.rto=120000"

# Warms the dependency cache in its own layer, keyed only on the POM, so source
# edits rebuild without re-resolving the tree. This is purely an optimisation —
# the package step below resolves anything it misses — so a flaky mirror here
# must not fail the build. That is what the `|| true` is for, and it is the
# only reason it is acceptable.
COPY pom.xml ./
RUN mvn -B dependency:go-offline || true

COPY src ./src

# maven.test.skip rather than skipTests: it also skips *compiling* the tests, so
# test-only dependencies are never resolved at all. The suite is CI's job — the
# one test here is a @SpringBootTest context load that needs a live database
# this build has no access to.
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Unprivileged runtime user. Nothing in the app writes to disk (uploads go to
# S3, not the container filesystem), so it needs no writable paths of its own.
RUN groupadd --system galaxy && useradd --system --gid galaxy --no-create-home galaxy

# Left owned by root and world-readable: the app only ever reads it, and a
# chown here would duplicate the whole 50MB jar into a second layer.
COPY --from=build /build/target/galaxy-*.jar app.jar
USER galaxy

EXPOSE 8080

# No actuator on the classpath, so health is a plain TCP accept check on the
# server port — enough for the gateway's depends_on to wait for a real boot
# (Flyway migrations included) rather than just container start.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1

# Container memory, not the host's, is what the heap should be sized against.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
