# Enhancement Progress

This document summarizes completed hardening and operations work for the stock dashboard backend/frontend portfolio project.

## Completed
- Backend and frontend GitHub Actions CI workflows.
- Backend and frontend deployment workflow drafts.
- Redis cache configuration with local simple-cache fallback.
- Actuator and Prometheus metrics exposure.
- Prometheus and Grafana infra configuration.
- Backend test coverage for JWT, validation, user service, cache config, and actuator endpoints.
- Codex-oriented project guidance through `AGENTS.md`.

## Local Verification Snapshot
- Backend tests were previously verified with Maven.
- Testcontainers integration tests are gated behind `RUN_INTEGRATION_TESTS=true` for environments where Docker is available.

## Current Notes
- Runtime secrets must be provided through environment variables.
- AI analysis uses OpenAI configuration: `OPENAI_API_KEY` and optional `OPENAI_MODEL`.
- Any credential that was previously exposed in plaintext should be rotated at the provider.
