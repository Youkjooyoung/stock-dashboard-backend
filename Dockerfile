# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

# Maven 래퍼 + 의존성 정의 먼저 복사 → 레이어 캐시 활용
COPY mvnw pom.xml ./
COPY .mvn ./.mvn
RUN chmod +x mvnw

# 의존성 선다운로드 (소스 변경 시 재사용)
RUN ./mvnw dependency:go-offline -B -q

# 소스 복사 후 빌드 (테스트 제외)
COPY src ./src
RUN ./mvnw package -DskipTests -B -q

# ── Stage 2: Run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy AS runner
WORKDIR /app

# 보안: root가 아닌 별도 사용자 실행
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser
USER appuser

COPY --from=builder /app/target/*.war app.war

EXPOSE 8443

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.war"]
