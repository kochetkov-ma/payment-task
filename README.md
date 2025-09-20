# Платежная система

Микросервисная система для обработки платежей с асинхронным взаимодействием через Kafka.

## Модули проекта

- **payment-backend** (порт 8080) - API для регистрации пользователей и создания платежей
- **payment-balance-service** (порт 8081) - Управление балансами пользователей, комиссия захардкожена в коде (1%)

## Запуск проекта

```bash
docker-compose up -d
```

Сервисы:
- Postgres: порт 5432
- Kafka: порт 9092
- Wiremock: порт 8082
- Payment Backend: порт 8080
- Payment Balance Service: порт 8081

## Flow пользователя

**Краткая схема:**
```
Регистрация → Настройка баланса → Авторизация → Создание платежа →
Kafka (hold-request) → Balance проверка → Kafka (hold-response) →
Обновление статуса → HTTP callback → Результат
```

### 1. Регистрация пользователя
- **payment-backend** сохраняет пользователя в БД
- **Тип:** HTTP → DB
- **URL:** POST /api/auth/register
- **Таблица:** `payment_db.users` (сохранение пользователя)

### 2. Предварительная настройка баланса
- Можно пополнить/изменить баланс пользователя
- **Комиссия:** захардкожена в коде - 1% (`COMMISSION_RATE = BigDecimal("0.01")`)
- **Расчет:** `commission = amount * 0.01` (в BalanceService.calculateCommission())
- **Тип:** DB
- **Таблица:** `balance_db.balances`
- **Операция:** INSERT/UPDATE balance для пополнения баланса (начальный баланс = 0.00)

### 3. Авторизация
- Клиент авторизуется и получает JWT токен для дальнейших запросов
- **Использование токена:** добавлять в заголовок `Authorization: Bearer <token>`
- **Тип:** HTTP → DB
- **URL:** POST /api/auth/login
- **Таблица:** `payment_db.users` (проверка учетных данных)

### 4. Создание платежа
- **payment-backend** создает запись в БД со статусом `CREATED`
- Автоматически отправляет HoldRequest в Kafka
- Статус платежа обновляется на `PROCESSING`
- **Тип:** HTTP → DB → KAFKA → DB
- **URL:** POST /api/payments
- **Таблица:** `payment_db.payments` (статус CREATED → PROCESSING)
- **Топик:** `payment-hold-request` (отправка HoldRequest)

### 5. Обработка в Balance Service
- **payment-balance-service** получает сообщение из Kafka
- Рассчитывает комиссию (1% от суммы)
- Проверяет баланс: если достаточно → **сразу списывает** сумму + комиссия
- Создает запись транзакции
- Отправляет HoldResponse с рассчитанной комиссией
- **Тип:** KAFKA → DB → DB → KAFKA
- **Топик входящий:** `payment-hold-request`
- **Таблицы:** `balance_db.balances` (проверка/списание), `balance_db.transactions` (запись транзакции)
- **Топик исходящий:** `payment-hold-response`

### 6. Финализация платежа
- **payment-backend** получает HoldResponse из Kafka
- Обновляет статус платежа: `COMPLETED` если success=true, `FAILED` если success=false
- Отправляет HTTP callback на указанный URL
- **Тип:** KAFKA → DB → HTTP
- **Топик:** `payment-hold-response` (получение ответа)
- **Таблица:** `payment_db.payments` (обновление статуса COMPLETED/FAILED)
- **HTTP:** callback на указанный URL

**Итоговое состояние БД:**
- `payment_db.payments` - статус COMPLETED/FAILED
- `balance_db.balances` - обновленный баланс (уменьшен на сумму + комиссия)
- `balance_db.transactions` - запись о списании

**Пример расчета комиссии:**
- Платеж: 100.00
- Комиссия: 100.00 × 1% = 1.00 (захардкожена в коде BalanceService)
- Итого списано: 100.00 + 1.00 = 101.00

## Статусы платежей и переходы

**Все статусы:**
- `CREATED` - платеж создан
- `PROCESSING` - отправлен на обработку в balance-service
- `COMPLETED` - успешно завершен
- `FAILED` - ошибка обработки

**Переходы между статусами:**
```
CREATED → PROCESSING → COMPLETED
   ↓           ↓
   ↓           ↓
   ↓      → FAILED
   ↓
   → FAILED (при ошибке отправки в Kafka)
```

**Когда происходят переходы:**
1. `CREATED → PROCESSING` - сразу после отправки HoldRequest в Kafka
2. `PROCESSING → COMPLETED` - получен HoldResponse с success=true
3. `PROCESSING → FAILED` - получен HoldResponse с success=false
4. `CREATED → FAILED` - ошибка отправки в Kafka или внутренняя ошибка

**Триггеры callback'ов:**
- Callback отправляется только при переходе в `COMPLETED` или `FAILED`
- Никогда не отправляется для статусов `CREATED` и `PROCESSING`

## Взаимодействие сервисов

```
payment-backend → Kafka (hold-request) → payment-balance-service
payment-balance-service → Kafka (hold-response) → payment-backend
payment-backend → HTTP callback → Wiremock/внешний сервис
```

**Инфраструктура:**
- Postgres: отдельные БД payment_db и balance_db
- Kafka: топики payment-hold-request, payment-hold-response

## Схема баз данных

### payment_db (PostgreSQL)

**Таблица: users**

| Колонка | Тип | Описание |
|---------|-----|----------|
| id | UUID (PK) | Уникальный идентификатор пользователя |
| username | VARCHAR (UNIQUE) | Имя пользователя (3-50 символов) |
| password | VARCHAR | Хешированный пароль (6-100 символов) |
| email | VARCHAR (UNIQUE) | Email адрес пользователя |
| enabled | BOOLEAN | Активен ли аккаунт (по умолчанию true) |
| created_at | TIMESTAMP | Дата создания |
| updated_at | TIMESTAMP | Дата последнего обновления |
| created_by | VARCHAR | Кто создал запись |
| updated_by | VARCHAR | Кто обновил запись |

**Таблица: payments**

| Колонка | Тип | Описание |
|---------|-----|----------|
| id | UUID (PK) | Уникальный идентификатор платежа |
| amount | DECIMAL(19,2) | Сумма платежа |
| status | VARCHAR (ENUM) | Статус: CREATED, PROCESSING, COMPLETED, FAILED |
| callback_url | VARCHAR | URL для callback уведомлений |
| user_id | UUID (FK) | Ссылка на пользователя |
| external_id | VARCHAR (UNIQUE) | Внешний идентификатор |
| created_at | TIMESTAMP | Дата создания |
| updated_at | TIMESTAMP | Дата последнего обновления |
| created_by | VARCHAR | Кто создал запись |
| updated_by | VARCHAR | Кто обновил запись |

**Таблица: auth_tokens**

| Колонка | Тип | Описание |
|---------|-----|----------|
| id | UUID (PK) | Уникальный идентификатор токена |
| token | VARCHAR (UNIQUE) | JWT токен |
| user_id | UUID (FK) | Ссылка на пользователя |
| expires_at | TIMESTAMP | Время истечения токена |
| created_at | TIMESTAMP | Дата создания |

### balance_db (PostgreSQL)

**Таблица: balances**

| Колонка | Тип | Описание |
|---------|-----|----------|
| id | UUID (PK) | Уникальный идентификатор баланса |
| username | VARCHAR (UNIQUE) | Имя пользователя |
| balance | DECIMAL(19,2) | Доступный баланс |
| reserved_amount | DECIMAL(19,2) | Зарезервированная сумма |
| created_at | TIMESTAMP | Дата создания |
| updated_at | TIMESTAMP | Дата последнего обновления |
| created_by | VARCHAR | Кто создал запись |
| updated_by | VARCHAR | Кто обновил запись |

**Таблица: transactions**

| Колонка | Тип | Описание |
|---------|-----|----------|
| id | UUID (PK) | Уникальный идентификатор транзакции |
| balance_id | UUID (FK) | Ссылка на баланс |
| amount | DECIMAL(19,2) | Сумма транзакции |
| type | VARCHAR (ENUM) | Тип: DEPOSIT, WITHDRAWAL, HOLD, RELEASE |
| payment_id | UUID | Ссылка на платеж (может быть null) |
| status | VARCHAR (ENUM) | Статус: PENDING, COMPLETED, FAILED |
| created_at | TIMESTAMP | Дата создания |
| updated_at | TIMESTAMP | Дата последнего обновления |

## API сервисов

### Payment Backend (порт 8080)

**Регистрация:**
```json
POST /api/auth/register
{
  "username": "user1",
  "password": "password123"
}
```

**Авторизация:**
```json
POST /api/auth/login
{
  "username": "user1",
  "password": "password123"
}
```

**Создание платежа:**
```json
POST /api/payments
Authorization: Bearer <token>
{
  "amount": 100.50,
  "callbackUrl": "http://wiremock:8080/payment/callback"
}
```

**Получение платежа:**
```
GET /api/payments/{id}
```

**Callback уведомления:**
```json
POST {callbackUrl}
Content-Type: application/json

{
  "paymentId": "uuid",
  "status": "COMPLETED", // или "FAILED"
  "amount": 100.50,
  "commission": 1.00, // только при success (1% от суммы)
  "timestamp": "2025-09-20T13:50:00",
  "message": "Успешно обработан" // или описание ошибки
}
```
- **callbackUrl** берется из первоначального запроса на создание платежа
- Отправляется только при статусах `COMPLETED` или `FAILED`
- Timeout: 30 секунд
- Retry: 3 попытки с интервалом 5 секунд
- Ожидается ответ 200-299 для подтверждения получения

### Kafka сообщения

**Топик: payment-hold-request**
**Hold Request:**
```json
{
  "paymentId": "uuid",
  "userId": "username",
  "amount": 100.50
}
```

**Топик: payment-hold-response**
**Hold Response:**
```json
{
  "paymentId": "uuid",
  "success": true,
  "message": "Успешно списано",
  "commission": 1.00
}
```

## Подключение к сервисам

**Внешние подключения (с хоста):**

**HTTP API сервисов:**
- Payment Backend: `http://localhost:8080`
- Payment Balance Service: `http://localhost:8081` (внутренний, нет публичного API)
- Wiremock: `http://localhost:8082`

**База данных:**
```bash
psql -h localhost -p 5432 -U postgres -d postgres
# Затем подключиться к конкретным БД:
\c payment_db;
\c balance_db;
```

**Kafka (с хоста):**
```bash
# Подключение к Kafka через localhost:9092
kafka-console-consumer --bootstrap-server localhost:9092 --topic payment-hold-request --from-beginning
kafka-console-producer --bootstrap-server localhost:9092 --topic payment-hold-request
```

**Kafka:**
```bash
# Просмотр топиков
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list

# Чтение сообщений
docker exec -it kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic payment-hold-request --from-beginning
```

**Wiremock Admin API:**
```bash
# Просмотр всех заглушек
GET http://localhost:8082/__admin/mappings

# Просмотр запросов (для проверки callback'ов)
GET http://localhost:8082/__admin/requests

# Поиск запросов по URL
GET http://localhost:8082/__admin/requests?url=/payment/callback

# Очистка истории запросов
DELETE http://localhost:8082/__admin/requests

# Проверка конкретного запроса
GET http://localhost:8082/__admin/requests/{request-id}
```

**HTTP API для проверки запросов (из Python/любого языка):**
```bash
# Получить все запросы
GET http://localhost:8082/__admin/requests

# Подсчет запросов по критериям
POST http://localhost:8082/__admin/requests/count
{
  "method": "POST",
  "url": "/payment/callback"
}

# Поиск запросов по критериям
POST http://localhost:8082/__admin/requests/find
{
  "method": "POST",
  "urlPath": "/payment/callback",
  "headers": {
    "Content-Type": {"equalTo": "application/json"}
  }
}

# Неотловленные запросы
GET http://localhost:8082/__admin/requests/unmatched
```

## Wiremock заглушки

**Health check:**
- `GET /health` → статус UP

**Успешный callback:**
- `POST /payment/callback` → 200 OK, status: SUCCESS

**Неуспешный callback:**
- `POST /payment/callback/failure` → 200 OK, status: FAILED, errorCode: INSUFFICIENT_FUNDS

**Проверка полученных callback'ов:**
```bash
# Просмотр всех запросов к Wiremock
curl http://localhost:8082/__admin/requests | jq '.requests[]'

# Поиск callback'ов по URL
curl "http://localhost:8082/__admin/requests?url=/payment/callback" | jq '.requests[].request.body'
```

Динамические поля в ответах: timestamp, transactionId из запроса, processingTime (100-5000ms).

**📖 Документация WireMock Admin API:**
https://wiremock.org/docs/verifying/ - верификация запросов

## Ошибки

**401 (Unauthorized):**
- Отсутствие заголовка `Authorization`
- Неверный формат токена (`Bearer <token>`)
- Истекший или недействительный JWT токен
- Неверные учетные данные при авторизации (POST /api/auth/login)
- Пользователь не найден в системе после валидного токена

**400 (Bad Request):**

*Правила валидации для регистрации (POST /api/auth/register):*
- `username`: обязательное поле, длина 3-50 символов
- `password`: обязательное поле, длина 6-100 символов
- `email`: обязательное поле, должен быть валидный email формат

*Правила валидации для авторизации (POST /api/auth/login):*
- `username`: обязательное поле
- `password`: обязательное поле

*Правила валидации для создания платежа (POST /api/payments):*
- `amount`: обязательное поле, минимум 0.01 (больше нуля)
- `callbackUrl`: обязательное поле, не пустая строка

*Другие причины 400:*
- Неверный JSON формат в теле запроса
- `IllegalArgumentException` из бизнес-логики (дублирование email/username при регистрации)
- Отсутствующие обязательные поля

**404 (Not Found):**
- Платеж с указанным ID не найден (GET /api/payments/{id})

**500 (Internal Server Error):**
- Необработанные исключения в контроллерах
- Ошибки соединения с БД или Kafka
- Прочие системные ошибки