# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn ./.mvn
RUN chmod +x mvnw

RUN ./mvnw dependency:go-offline -B -q

COPY src ./src
RUN ./mvnw package -DskipTests -B -q

# ── Stage 2: Run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runner
WORKDIR /app

RUN groupadd --system appgroup && useradd --system --gid appgroup appuser
USER appuser

COPY --from=builder /app/target/*.war app.war

EXPOSE 8443

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.war"]