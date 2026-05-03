# Stock Dashboard Backend

Spring Boot backend for the stock dashboard portfolio project.

## Stack
- Java 21
- Spring Boot 3.3.10
- Maven
- MyBatis
- MySQL
- Spring Security / JWT
- WebSocket / STOMP
- OpenAI Responses API for AI analysis
- AWS S3, PortOne, Resend, OAuth integrations

## Local Setup
1. Prepare MySQL and create the `stock_dashboard` database.
2. Copy the environment variable checklist from `src/main/resources/application.properties.example`.
3. Set the required environment variables in your shell, IDE run configuration, or deployment environment.
4. Run the backend:

```bash
./mvnw spring-boot:run
```

The default local port is `8443`.

## Test
```bash
./mvnw test
```

## AI Analysis
The `/api/ai/analyze` endpoint uses OpenAI Responses API.

Required:
- `OPENAI_API_KEY`

Optional:
- `OPENAI_MODEL` defaults to `gpt-5.4-mini`

The response shape is kept stable:

```json
{
  "analysis": "..."
}
```

## Security
Do not commit real credentials. Any credential that was previously exposed in source files or chat logs should be rotated in the provider console.

Sensitive values include DB passwords, OAuth client secrets, PortOne secrets, Resend keys, JWT secrets, AWS keys, SSL keystore passwords, and OpenAI API keys.
