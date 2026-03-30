# Business Features — Workflow

## Назначение

Папка `features/` содержит описания бизнес-фич — от идеи до готовности к реализации. Каждая фича проходит через стандартный workflow, на выходе которого формируется Technical Breakdown Document (TBD) — план реализации.

## Workflow

```
1. DRAFT        → Описание бизнес-потребности, user story, acceptance criteria
2. DESIGNING    → Архитектурная проработка: какие модули затронуты, какие изменения нужны
3. ARCH_UPDATED → Архитектурные документы (modules/*.md, data-model.md) обновлены/созданы
4. TBD_READY    → Technical Breakdown сформирован — фича готова к реализации
5. IMPLEMENTING → В процессе реализации
6. DONE         → Реализовано и верифицировано
```

### Переходы между статусами

| Переход | Что происходит |
|---------|----------------|
| DRAFT → DESIGNING | Начинается архитектурное обсуждение: анализ impact на модули, выбор подходов |
| DESIGNING → ARCH_UPDATED | Архитектурные документы обновлены: добавлены/изменены секции в `modules/`, `data-model.md`, `non-functional-architecture.md` |
| ARCH_UPDATED → TBD_READY | В feature-документе заполнена секция TBD: конкретные задачи, порядок, зависимости |
| TBD_READY → IMPLEMENTING | Начата реализация по TBD |
| IMPLEMENTING → DONE | Код написан, тесты пройдены, фича верифицирована |

## Как создать новую фичу

1. Скопировать `_TEMPLATE.md` → `YYYY-MM-DD-feature-name.md`
2. Заполнить секции Business Context, User Stories, Acceptance Criteria
3. Установить статус `DRAFT`
4. Начать архитектурную проработку

## Правила

- **Один файл — одна бизнес-фича.** Не объединять несколько независимых фич.
- **Feature file — живой документ.** Обновляется по мере проработки.
- **Архитектурные изменения фиксируются в module docs**, не в feature файле. Feature файл ссылается на обновлённые модули.
- **TBD заполняется в feature файле** — это plan of record для реализации.
- **Naming convention:** `YYYY-MM-DD-short-name.md` (дата создания + краткое имя).

## Связь с архитектурными документами

```
docs/features/2026-04-01-inventory-alerts.md    ← бизнес-фича
  ├── обновляет → docs/modules/analytics-pnl.md  ← добавлена секция "Inventory alerts"
  ├── обновляет → docs/modules/seller-operations.md ← добавлена alert visualization
  └── содержит → TBD (technical breakdown)        ← план реализации
```

Если фича требует **нового модуля** (не покрываемого существующими) — создаётся новый `modules/new-module.md` и обновляется `data-model.md`.
