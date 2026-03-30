# Datapulse — Политика работы с API маркетплейсов

## Назначение

Обязательные правила для всех marketplace-адаптеров системы. Применяется к: capability interfaces, provider DTO, auth flows, rate limit handling, sync endpoints, write endpoints, reconciliation reads.

## Главное правило

Все marketplace-адаптеры используют **только текущие, официальные, публично документированные** API маркетплейсов.

## Допустимые источники

Только официальные источники маркетплейсов:

- Официальная документация для разработчиков.
- Официальный API reference / OpenAPI / Swagger.
- Официальные release notes / changelogs / API updates.
- Официальная документация по авторизации / токенам / правам.
- Официальная документация по rate limits и error semantics.

## Запрещённые источники

Не использовать как source of truth:

- Blog posts, Habr, Medium.
- Stack Overflow.
- Неофициальные SDK.
- GitHub-примеры третьих сторон.
- Postman-коллекции из интернета.
- Telegram-чаты, community assumptions.
- Устаревшие внутренние сниппеты без верификации по текущим official docs.

## Обязательная верификация перед реализацией

Перед реализацией или изменением любого адаптера проверить в official docs:

- Endpoint path и HTTP method.
- Request/response schema.
- Auth method и required scopes.
- Pagination semantics.
- Rate limits.
- Retry semantics.
- Idempotency semantics (если применимо).
- Error codes и их семантика.
- Deprecations / replacements.
- Versioning / migration notes.

## Правило актуальности документации

Документация маркетплейсов — time-sensitive. При реализации или изменении endpoints, payloads, auth, limits, status semantics, financial/report semantics, pricing/promo behavior — **текущие official docs перепроверяются перед кодингом**.

## Поведение при неясности документации

Если official docs неоднозначны или неполны:

1. Не изобретать поведение.
2. Не полагаться на неофициальные источники.
3. Изолировать неопределённость на уровне adapter boundary.
4. Добавить explicit TODO/FIXME с ссылкой на official doc.
5. Сохранить стабильность domain/application контрактов.
6. Предпочитать read-only или no-op-safe поведение вместо рискованных write-операций.

## Anti-corruption boundary

Provider DTO и provider-specific response semantics остаются **внутри adapter boundaries**. Domain и application layers зависят от контрактов Datapulse, не от provider transport shapes.

Протекание provider shapes в domain/application — architectural violation.

## Rate limiting

### Принципы

- Каждый адаптер реализует rate limiting в соответствии с документированными или эмпирически определёнными лимитами провайдера.
- Rate limiter — token-bucket на уровне адаптера.
- При получении HTTP 429 — backoff и retry; не прерывать sync целиком.
- Лимиты конфигурируются через `@ConfigurationProperties`.

### При отсутствии документированных лимитов

- Начать с conservative defaults.
- Мониторить 429 responses.
- Корректировать лимиты эмпирически.
- Зафиксировать определённые значения в конфигурации.

## Retry при ошибках провайдера

| Тип ошибки | Поведение |
|------------|-----------|
| HTTP 429 (rate limit) | Backoff + retry |
| HTTP 5xx (transient) | Backoff + retry |
| HTTP 4xx (кроме 429) | Не retry; зафиксировать ошибку, перейти к следующему item |
| Connection timeout | Backoff + retry |
| Неизвестный payload / parse error | Зафиксировать ошибку, не retry; расследовать изменение API |

## Credential handling

- API-ключи маркетплейсов хранятся в HashiCorp Vault.
- Metadata о credentials (account ID, тип, scope) хранится в PostgreSQL.
- Маскирование credentials в логах обязательно.
- Валидация credentials выполняется перед каждой sync-сессией.
- Все попытки доступа к credentials аудируются.

## Версионирование API

- Адаптер привязан к конкретной версии endpoint.
- При deprecation провайдером — migration plan документируется.
- Deprecated endpoints заменяются до их отключения провайдером.
- `@JsonIgnoreProperties(ignoreUnknown = true)` на provider DTO — защита от minor additions.

## Observability для provider calls

Каждый вызов провайдера обязан содержать:

- `correlation_id`.
- `account_id`.
- Provider и capability.
- HTTP method, endpoint, status code.
- Timing (duration_ms).
- Retry count.
- Error details (если применимо).

## Устойчивость к частичной деградации

- Сбой одного маркетплейса не блокирует обработку другого (lane isolation).
- Сбой одного data domain в рамках маркетплейса не блокирует другие domains (event-level isolation внутри lane).
- Partial failure при загрузке batch: зафиксировать ошибочные items, продолжить с остальными.

## Review checklist для marketplace changes

Каждое изменение marketplace-адаптера обязано подтвердить:

- [ ] Official doc links reviewed.
- [ ] Endpoint/version verified.
- [ ] Auth verified.
- [ ] Deprecation check performed.
- [ ] DTO changes aligned with current docs.
- [ ] Rate-limit/retry semantics reviewed.
- [ ] Tests updated for current documented behavior.

## Связанные документы

- [Архитектура данных](data-architecture.md) — инварианты, anti-corruption boundary
- [Матрица возможностей провайдеров](provider-capability-matrix.md) — покрытие, rate limits, auth
- [Архитектура данных](data-architecture.md) — sign conventions, join keys
