# Уведомления & WebSocket — Implementation Plan

## Обзор

Система in-app уведомлений с мгновенной доставкой через STOMP/WebSocket. Уведомления создаются из доменных событий (инвайты, синхронизация, алерты) и push'атся в real-time конкретному пользователю.

## Архитектура

```
Доменное событие (Spring ApplicationEvent)
 → EventListener (InviteNotificationListener / SyncNotificationListener / SellerAlertEpisodeService)
 → NotificationService.createNotification(recipientProfileId, type, title, message, ...)
 → JPA persist NotificationEntity
 → NotificationCreatedEvent (Spring event)
 → NotificationWebSocketDispatcher (@EventListener)
 → SimpMessagingTemplate.convertAndSendToUser(profileId, "/queue/notifications", notification)
 → Клиент (SockJS/STOMP) получает уведомление
```

## Типы уведомлений — `NotificationType`

| Тип | Источник | Описание |
|-----|----------|----------|
| `INVITE_RECEIVED` | `InviteNotificationListener` | Получен инвайт в аккаунт |
| `SYNC_COMPLETED` | `SyncNotificationListener` | Синхронизация успешна |
| `SYNC_FAILED` | `SyncNotificationListener` | Синхронизация завершилась с ошибкой |
| `LOW_STOCK_ALERT` | `SellerAlertEpisodeService` | Товары с нулевым остатком |
| `OUT_OF_STOCK_RISK_ALERT` | Alert system | Риск окончания запасов |
| `RETURN_SPIKE_ALERT` | Alert system | Всплеск возвратов |
| `PENALTY_SPIKE_ALERT` | `SellerAlertEpisodeService` | Новые штрафы |
| `RECONCILIATION_MISMATCH_ALERT` | Alert system | Расхождение в сверке |
| `PRICE_CONTROL_ALERT` | Alert system | Ценовой алерт |
| `PROMO_DEADLINE_APPROACHING` | Promo system | Приближается дедлайн акции |
| `PROMO_NEGATIVE_MARGIN` | Promo system | Отрицательная маржа в акции |
| `PROMO_STOCK_OUT_RISK` | Promo system | Риск out-of-stock в акции |

## Компоненты

### 1. Entity — `NotificationEntity`

**Таблица `notification`:**
| Колонка | Тип | Назначение |
|---------|-----|-----------|
| id | BIGSERIAL | PK (через LongBaseEntity) |
| recipient_profile_id | BIGINT | ID профиля получателя |
| type | VARCHAR | NotificationType enum |
| title | VARCHAR | Заголовок |
| message | VARCHAR | Текст |
| resource_type | VARCHAR | Тип ресурса (INVITE, ACCOUNT, ...) |
| resource_id | VARCHAR | ID ресурса |
| is_read | BOOLEAN | Прочитано |
| read_at | TIMESTAMPTZ | Время прочтения |
| created_at | TIMESTAMPTZ | Время создания (LongBaseEntity) |

### 2. Repository — `NotificationRepository`

Spring Data JPA:
- `findAllByRecipientProfileIdOrderByCreatedAtDesc(profileId, pageable)` → `Page<NotificationEntity>`
- `countByRecipientProfileIdAndReadFalse(profileId)` → int
- `markAllAsRead(profileId, now)` → `@Modifying @Query` bulk update

### 3. Service — `NotificationService`

**`createNotification(recipientProfileId, type, title, message, resourceType, resourceId)`:**
1. Persist `NotificationEntity`
2. Map to `NotificationResponse` DTO
3. Publish `NotificationCreatedEvent(recipientProfileId, response)`

**Read API:**
- `listByRecipient(profileId, pageable)` → `Page<NotificationResponse>`
- `countUnread(profileId)` → int
- `markAsRead(profileId, notificationId)` — с проверкой ownership
- `markAllAsRead(profileId)` → int (affected count)

### 4. Event Listeners

**`InviteNotificationListener`:**
- `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`
- Trigger: `AccountInviteCreatedEvent`
- Поиск `UserProfile` по email инвайта
- Если профиль найден → `createNotification(profileId, INVITE_RECEIVED, ...)`
- i18n через `MessageSource` (locale `ru`)

**`SyncNotificationListener`:**
- `@EventListener` + `@Transactional(REQUIRES_NEW)`
- Trigger: `EtlScenarioTerminalEvent`
- Type: `SYNC_COMPLETED` или `SYNC_FAILED` (по `event.status()`)
- Recipients: все active members аккаунта, fallback → `requestedByProfileId`
- Resource: `ACCOUNT` + accountId

### 5. WebSocket Configuration — `WebSocketConfig`

**`@EnableWebSocketMessageBroker`:**
- Simple broker prefix: `/queue`
- User destination prefix: `/user`
- STOMP endpoint: `/ws/notifications` с SockJS fallback
- `setAllowedOriginPatterns("*")`
- Custom `IamHandshakeHandler`

### 6. Handshake — `IamHandshakeHandler`

**`DefaultHandshakeHandler` override:**
1. Получение `Principal` из request
2. Если `JwtAuthenticationToken` → извлечение `email` claim
3. `userProfileRepository.findByEmailIgnoreCase(email)`
4. Principal name = `String.valueOf(profile.getId())`

Это критично для `convertAndSendToUser()` — Spring маршрутизирует по principal name.

### 7. Dispatcher — `NotificationWebSocketDispatcher`

**`@EventListener` на `NotificationCreatedEvent`:**
```java
messagingTemplate.convertAndSendToUser(
    String.valueOf(event.recipientProfileId()),
    "/queue/notifications",
    event.notification()
);
```

Клиент подписывается на `/user/queue/notifications`.

### 8. REST Controller — `NotificationController`

**`/api/notifications`** (`@PreAuthorize("isAuthenticated()")`):

| Endpoint | Метод | Описание |
|----------|-------|----------|
| `GET /` | list | Пагинированный список |
| `GET /unread-count` | unreadCount | Количество непрочитанных |
| `POST /{id}/read` | markAsRead | Пометить прочитанным |
| `POST /read-all` | markAllAsRead | Пометить все прочитанными |

## Паттерн добавления нового типа уведомления

1. Добавить значение в `NotificationType` enum (datapulse-domain)
2. Создать `@EventListener` в core, слушающий нужное доменное событие
3. Вызвать `NotificationService.createNotification()` с нужным type и payload
4. Real-time dispatch и REST уже работают автоматически

## Ключевые файлы

| Файл | Модуль | Роль |
|------|--------|------|
| `NotificationEntity.java` | core | JPA entity |
| `NotificationRepository.java` | core | Spring Data JPA |
| `NotificationService.java` | core | CRUD + event publish |
| `NotificationCreatedEvent.java` | core | Internal event record |
| `InviteNotificationListener.java` | core | Invite → notification |
| `SyncNotificationListener.java` | core | ETL terminal → notification |
| `WebSocketConfig.java` | application | STOMP/SockJS config |
| `IamHandshakeHandler.java` | application | JWT → profileId principal |
| `NotificationWebSocketDispatcher.java` | application | Event → STOMP push |
| `NotificationController.java` | application | REST API |
| `NotificationType.java` | domain | Enum of types |
