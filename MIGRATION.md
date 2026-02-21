# DataPulse ETL RFC migration

## What was changed
- Reworked ETL orchestration into Rabbit-backed TASKS -> EXECUTION flow with Spring Integration DSL entry points.
- Added RFC state tables support (`etl_execution`, `etl_source_state`, `etl_outbox`) and JDBC state repositories.
- Refactored worker path to:
  - validate payload,
  - run state CAS transitions,
  - reset RAW by `request_id`,
  - invoke typed snapshot ingestion flow,
  - schedule retries only via outbox records.
- Added scheduled outbox publisher that routes WAIT messages to DLX with per-message TTL.
- Simplified scenario endpoint to publish independent event tasks.

## What was removed
- Orchestration-level aggregation completion mechanism.
- Execution outcome DTO aggregation chain and event completion cache/listeners.
- In-process retry wireTap WAIT publisher logic.
- Scenario step executor with dependency waiting.

## What remains intentionally
- Snapshot ingest stays in Spring Integration DSL with split -> iterator -> aggregate -> save batch semantics.
- Existing EventSource registry and dynamic RAW table schema support are kept.
- Existing ETL HTTP endpoints are preserved (`/api/etl/run`, `/api/etl/scenario/run`) with 202 semantics.
