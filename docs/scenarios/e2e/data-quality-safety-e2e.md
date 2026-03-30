# Data Quality & Safety E2E Scenarios

## Бизнес-контекст

Сценарии, обеспечивающие безопасность автоматизации: обнаружение проблем с данными, блокировка опасных действий, и расследование аномалий. Проходят через Analytics → Audit & Alerting → Pricing → Seller Operations.

## Сценарии

### E2E-DQ-01: Stale data → automation block → resolution

- **Business goal:** Устаревшие данные автоматически блокируют pricing для предотвращения ошибочных решений.
- **Участвующие модули:** ETL (sync state) → Analytics → Audit & Alerting → Pricing → Seller Ops.
- **Основной поток:**
  1. **ETL:** Sync fails или provider delayed → data becomes stale.
  2. **Audit & Alerting:** Scheduled stale_data checker → data freshness below threshold → create alert_event (STALE_DATA, blocks_automation=true).
  3. **Pricing:** Next pricing run → check active blocking alerts → alert found → skip pricing for this connection → log "automation_blocked".
  4. **Seller Ops:** Alert visible in UI. Operator investigates. Grid shows stale indicator.
  5. **Resolution path A:** Sync recovers → next successful sync → stale checker: data now fresh → alert AUTO_RESOLVED → pricing unblocked.
  6. **Resolution path B:** Manual: operator acknowledges → investigates → resolves alert → pricing unblocked.
- **Ключевые зависимости:** Stale data threshold configured. alert_rule with blocks_automation=true. Pricing checks alert state.
- **Failure paths:**
  - Alert not created (no rule configured) → pricing runs on stale data → dangerous.
  - Alert stuck (never resolved) → pricing permanently blocked → manual intervention needed.
  - Auto-resolution flapping (data alternates fresh/stale) → repeated block/unblock.
- **Почему обязательный:** Primary safety net. Предотвращает financial damage от pricing на устаревших данных.

### E2E-DQ-02: Reconciliation residual anomaly → investigation

- **Business goal:** Расхождение P&L с фактическим payout маркетплейса обнаружено и расследовано.
- **Участвующие модули:** Analytics (P&L, residual) → Audit & Alerting → Seller Ops.
- **Основной поток:**
  1. **Analytics:** P&L computed → reconciliation residual > threshold.
  2. **Audit & Alerting:** residual_anomaly checker → create alert_event (RESIDUAL_ANOMALY).
  3. **Seller Ops:** Analyst sees alert → drills down to affected SKUs/periods.
  4. **Investigation:** Data provenance trace: mart → fact → canonical → raw → provider response. Identify root cause (missing fee type, misclassified entry, provider-side error).
  5. **Resolution:** Fix mapping/categorization → re-materialize → residual normalized → alert resolved.
- **Ключевые зависимости:** Reconciliation residual computation. Data provenance chain intact. Raw data preserved.
- **Failure paths:**
  - Root cause is provider-side (undocumented fee) → cannot fix mapping, must accept residual → document as known limitation.
  - Raw data expired (retention) → investigation chain broken.
  - Residual within tolerance but systemic → not alerted → gradual accuracy degradation.
- **Почему обязательный:** P&L accuracy validation. Единственный способ обнаружить «скрытые» удержания маркетплейса.

### E2E-DQ-03: Mismatch detection → alert → correction

- **Business goal:** Расхождение между expected price (наше решение) и actual price (маркетплейс) обнаружено и исправлено.
- **Участвующие модули:** ETL (price sync) → Seller Ops (mismatch monitor) → Audit & Alerting → Execution (optional re-action).
- **Основной поток:**
  1. **ETL:** PRICES_SYNC → canonical_price_snapshot updated with current marketplace price.
  2. **Seller Ops:** Mismatch monitor compares last successful price_action.target_price vs canonical_price_snapshot.current_price → |diff| > threshold → mismatch record.
  3. **Audit & Alerting:** MISMATCH_DETECTED alert → WebSocket notification.
  4. **Seller Ops:** Operator reviews mismatch → three options:
     - a. Accept (marketplace price is correct, our data was stale).
     - b. Re-price (trigger manual pricing run).
     - c. Investigate (check price journal, execution log).
  5. **Execution (optional):** New pricing decision → new action → provider write → correction.
- **Ключевые зависимости:** Price sync freshness. Mismatch threshold. Recent successful price actions to compare against.
- **Failure paths:**
  - Mismatch due to marketplace processing delay (temporary) → false positive. Mitigation: delay check after action.
  - External price change (another tool, manual marketplace UI) → genuine mismatch, but not our error.
  - Re-pricing creates loop (our price → marketplace adjusts → mismatch → re-price → ...).
- **Почему обязательный:** Закрывает feedback loop: confirms that actions had intended effect.

### E2E-DQ-04: Missing sync detection → alert → recovery

- **Business goal:** Обнаружение и восстановление пропущенных синхронизаций.
- **Участвующие модули:** Integration (sync state) → Audit & Alerting → ETL (re-sync).
- **Основной поток:**
  1. **Audit & Alerting:** Scheduled missing_sync checker: elapsed time since `marketplace_sync_state.last_success_at` exceeds configured interval → create alert_event (MISSING_SYNC).
  2. **Integration:** Alert visible → admin investigates connection health.
  3. **Recovery path A:** Connection ACTIVE, temporary issue → manual sync trigger → recovery.
  4. **Recovery path B:** Connection AUTH_FAILED → credential update → re-validation → resume syncs.
  5. **Recovery path C:** Provider outage → wait → monitor → alert auto-resolves when provider recovers.
- **Ключевые зависимости:** Expected sync interval configured per domain. Connection health monitoring.
- **Failure paths:**
  - Alert fatigue: too many missing_sync alerts during provider maintenance.
  - No recovery action taken → data staleness compounds → downstream pricing blocked.
- **Почему обязательный:** Prevents silent data staleness. Without this, sync failures could go unnoticed.
