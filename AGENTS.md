# Codex Guide - Backend

## Project
- Spring Boot 3.3.10 backend for the stock dashboard.
- Java 21, Maven, MyBatis, MySQL, WebSocket/STOMP, JWT, OAuth, PortOne, Resend, AWS S3, OpenAI.
- Main package: `com.stock.dashboard`.

## Local Commands
- Run tests: `./mvnw test`
- Run app: `./mvnw spring-boot:run`
- Build package: `./mvnw clean package`

## Configuration
- Keep real credentials out of tracked files.
- Use `src/main/resources/application.properties.example` as the environment variable checklist.
- Local `application.properties` may exist for development, but all secrets must come from environment variables.
- AI analysis uses OpenAI Responses API with `OPENAI_API_KEY` and optional `OPENAI_MODEL`.

## Coding Rules
- Use constructor injection with `@RequiredArgsConstructor` and `private final` dependencies.
- Controllers should return `ResponseEntity<T>` and keep business logic in services.
- Prefer request/response DTOs for new APIs instead of raw maps when the shape is stable.
- MyBatis mapper SQL must keep soft-delete and ownership filters explicit.
- Do not log tokens, API keys, passwords, OAuth secrets, identity verification data, or payment secrets.

## Deployment Notes
- Production runs on AWS and is deployed manually from MobaXterm.
- Before deployment, verify required environment variables are set on the server.
- Rotate any credential that was previously committed or shared in plain text.
