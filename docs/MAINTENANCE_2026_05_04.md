# Maintenance Log - 2026-05-04

## Scope
- Complete the Claude/Anthropic removal follow-up work.
- Stabilize backend production behavior after OpenAI migration.
- Keep the GitHub Actions deployment flow as the source of truth for production rollout.

## Backend Changes
- `AiAnalysisService` uses OpenAI Responses API with environment-driven configuration.
- OpenAI calls now have explicit timeouts:
  - Connect timeout: 10 seconds
  - Request timeout: 60 seconds
- `PriceAlertService` now catches active-alert lookup failures before scheduled execution can surface noisy task errors.
- Price alert and big-move scheduler logs were normalized to readable ASCII messages.

## Required Environment Variables
- `OPENAI_API_KEY`
- `OPENAI_MODEL`, optional, defaults to `gpt-5.4-mini`
- Database, OAuth, JWT, AWS S3, SSL, PortOne, Resend, and data API values must stay outside tracked files.

## Verification
- `./mvnw test`
- `./mvnw clean package -DskipTests`
- Backend Auto Deploy must complete successfully after pushing to `main`.

## Production Notes
- Runtime secrets are managed on the EC2/systemd side, not in source control.
- GitHub push protection should remain enabled.
- If any provider key was exposed before this migration, rotate it in the provider console and update the server environment only.
