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

## Security Rules
These rules come from the 2026-10-15 security review. Each one closes a problem that was reproduced on a running server.
- JWT: every token signed with the access key carries a `typ` claim (`access`, `verify`). Validate the type; never accept one kind of token in place of another. Add a `JwtUtilTest` case for every new token type.
- WebSocket/STOMP: every inbound frame passes `StompAuthChannelInterceptor`. Register each new subscription destination there explicitly. Clients may SEND only to `/app/**`.
- Personal data goes to `/user/queue/**` through `convertAndSendToUser(email, ...)`, never to a `/topic/**` destination keyed by a user ID.
- Shared or broadcast responses (chat, rankings) must not contain email addresses or other personal data. Expose a public ID such as `senderId` and mark internal fields with `@JsonIgnore`.
- Never put access or refresh tokens in URLs (query, fragment, OAuth `state`). Social account linking uses `OAuthLinkStateStore` with POST `/api/auth/link/{provider}/start` and `/api/auth/link/callback`.
- Trust a social provider's email only when the provider marks it verified (Kakao `is_email_verified`, Google `verified_email`). Social login must apply the same lock and soft-delete checks as password login.
- Identity verification (PortOne) must check `code == 0` and `certified == true`. Never treat two empty values as a match, and store the certified name/phone instead of user-submitted values.
- User-facing errors throw `BusinessException` with an HTTP status. `GlobalExceptionHandler` hides every other exception behind a fixed message; never return `e.getMessage()` from library or database exceptions.
- External HTTP calls use the shared `HttpClient` bean with `HttpClientConfig.REQUEST_TIMEOUT`. Check the status code of every external response.
- Endpoints that cost money or send messages (AI, email) need a per-user limit; see `AiUsageLimiter`.
- Validate input with allow-list patterns. Do not add keyword blacklists; SQL injection is prevented by MyBatis `#{}`. Never use `${}` in mappers.
- Every public `permitAll` endpoint must be used by the frontend or documented; delete unused endpoints instead of leaving them open.
- `@Scheduled` jobs run on the dedicated `taskScheduler` (`SchedulingConfig`), not on the WebSocket broker scheduler.
- Test property files contain dummy values only.
- Every security fix ships with a regression test.

## Known Open Items (2026-10-15)
- Login lockout can be triggered by anyone who knows an email, and auth endpoints have no rate limiting (needs infrastructure decision: proxy IP headers, time-based unlock column).
- Signup still collects the resident registration number; decide whether to remove it (PortOne already provides birth date and gender).
- Social users without a registered name/phone pass identity verification with any completed certification.
- `sql/V20261015_unique_social_provider_email.sql` must be applied manually after checking for duplicates.

## Deployment Notes
- Production runs on AWS and is deployed through GitHub Actions after push to `main`.
- After completing backend work, commit and push the changes, then verify the Backend Auto Deploy workflow result.
- Before deployment, verify required environment variables are set on the server.
- Rotate any credential that was previously committed or shared in plain text.

## Latest Verified State
- Date: 2026-05-04
- OpenAI analysis service has explicit connect/request timeouts.
- Price alert scheduler catches active-alert lookup failures and logs them as warnings.
- Required final checks before handoff: `./mvnw test`, `./mvnw clean package -DskipTests`, GitHub Actions deploy success.
