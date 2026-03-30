# Seller Alerts — Implementation Plan

## Обзор

Edge-triggered алерты при критических изменениях в данных продавца. Уведомление отправляется при входе в состояние «условие выполняется» и не повторяется, пока условие не сбросится и не возникнет снова.

## Архитектура

```
Scheduled reconciliation job
 → SellerAlertEpisodeService.reconcile*(currentAccountIds)
   → SellerAlertFiringStateRepository (firing state check)
   → NotificationService.createNotification (if edge: false→true)
   → NotificationWebSocketDispatcher (real-time push)
```

## Edge-triggered vs Level-triggered

**Level-triggered:** уведомление на каждый проверочный цикл, пока условие истинно → спам.

**Edge-triggered (используется):** уведомление только при переходе `false → true`. Пока `firing=true`, новые уведомления не создаются. При `true → false` (recovery) → следующий `false → true` создаёт новое уведомление.

## Компоненты

### 1. SellerAlertEpisodeService

**Методы:**
- `reconcileLowStock(Set<Long> currentAccountIds)` — товары с quantity_available = 0
- `reconcilePenalties(Set<Long> currentAccountIds)` — новые штрафы за последние сутки

**Алгоритм `reconcile(alertType, currentAccountIds, title, message)`:**

**Для каждого accountId в currentAccountIds (условие выполняется):**
1. `firingStateRepository.findByAccountIdAndAlertType(accountId, alertType)` — текущее состояние
2. Если `firing == false` (новый эпизод):
   a. `notifyActiveAccountMembers(accountId, ...)` — уведомление всем active members
   b. Если отправлено 0 уведомлений → skip (не arm без получателей)
3. Set `firing = true`, save

**Recovery (условие перестало выполняться):**
1. `findAllFiringByAlertType(alertType)` — все с `firing = true`
2. Для каждого, чей accountId НЕ в `currentAccountIds`:
   - Set `firing = false`, save
   - Log: "condition no longer holds"

### 2. Firing State Entity — `SellerAlertFiringStateEntity`

**Таблица `seller_alert_firing_state`:**
| Колонка | Тип | Назначение |
|---------|-----|-----------|
| id | BIGSERIAL | PK |
| account_id | BIGINT | Аккаунт |
| alert_type | VARCHAR | NotificationType enum |
| firing | BOOLEAN | Текущее состояние |

### 3. Notification Recipients

Получатели — все активные участники аккаунта (не только OWNER):
```java
accountMemberRepository
    .findAllByAccountIdWithUserOrderByIdAsc(accountId)
    .stream()
    .filter(member -> member.getStatus() == AccountMemberStatus.ACTIVE)
    .map(member -> member.getUser().getId())
    .distinct()
```

### 4. Поддерживаемые алерты

| Alert Type | Condition | Title |
|------------|-----------|-------|
| `LOW_STOCK_ALERT` | `quantity_available = 0` в `fact_inventory_snapshot` | "Товары с нулевым остатком" |
| `PENALTY_SPIKE_ALERT` | Новые записи в `fact_penalties` за последние сутки | "Новые штрафы" |

### 5. Scheduled Jobs

Reconciliation jobs вызывают `SellerAlertEpisodeService`:
- Query витрин для определения `currentAccountIds` (аккаунты с выполненным условием)
- Передача set в `reconcile*()`
- Периодичность настраивается через `@Scheduled` cron в YAML

## Дизайн-решения

**Почему edge-triggered:**
- Алерты проверяются после каждой ETL-синхронизации
- Level-triggered создавал бы duplicate уведомления каждый цикл
- Edge-triggered: одно уведомление при возникновении проблемы, без спама

**Почему guard на 0 получателей:**
- Если в аккаунте нет active members → firing state не переключается в true
- Предотвращает «залипание» firing без отправленных уведомлений
- При появлении участников алерт сработает корректно

## Ключевые файлы

| Файл | Модуль | Роль |
|------|--------|------|
| `SellerAlertEpisodeService.java` | core | Edge-triggered reconciliation |
| `SellerAlertFiringStateEntity.java` | core | Firing state JPA entity |
| `SellerAlertFiringStateRepository.java` | core | State persistence |
| `NotificationService.java` | core | Notification creation |
| `NotificationType.java` | domain | Alert type values |
