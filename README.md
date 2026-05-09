# Liveklass Demo — 알림 발송 시스템

## 프로젝트 개요

과제 C 요구사항을 구현한 Spring Boot 알림 발송 시스템입니다.

클라이언트는 알림 발송 요청을 `notification_request` 테이블에 저장하고 즉시 접수 응답을 받습니다. 실제 발송은 API 요청 스레드가 아니라 DB polling worker가 처리합니다. 발송 실패는 무시하지 않고 상태, 실패 사유, 재시도 횟수, 다음 재시도 시각으로 저장합니다.

## 기술 스택

- Java 17
- Spring Boot 4.0.6
- Spring WebMVC
- Spring Data JPA
- H2 Database
- Gradle
- JUnit 5 / Spring Boot Test / MockMvc

## 실행 방법

테스트:

```bash
./gradlew test
```

애플리케이션 실행:

```bash
./gradlew bootRun
```

기본 DB는 H2 in-memory입니다.

```properties
spring.datasource.url=jdbc:h2:mem:liveklass;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.jpa.hibernate.ddl-auto=create-drop
notification.worker.enabled=true
notification.worker.poll-delay-ms=5000
```

## API 목록 및 예시

### 1. 알림 발송 요청 등록

```http
POST /api/notifications
Content-Type: application/json
```

요청:

```json
{
  "recipientId": "user-1",
  "notificationType": "PAYMENT_CONFIRMED",
  "channel": "EMAIL",
  "eventId": "payment-1001",
  "title": "결제가 완료되었습니다",
  "message": "결제 확정 알림입니다."
}
```

응답: `202 Accepted`

```json
{
  "id": 1,
  "status": "REQUESTED",
  "duplicated": false
}
```

동일한 `recipientId + notificationType + channel + eventId` 요청이 다시 들어오면 새 row를 만들지 않고 기존 요청을 반환합니다.

```json
{
  "id": 1,
  "status": "REQUESTED",
  "duplicated": true
}
```

### 2. 알림 상태 조회

```http
GET /api/notifications/{id}
```

응답 필드:

```json
{
  "id": 1,
  "recipientId": "user-1",
  "notificationType": "PAYMENT_CONFIRMED",
  "channel": "EMAIL",
  "eventId": "payment-1001",
  "title": "결제가 완료되었습니다",
  "message": "결제 확정 알림입니다.",
  "status": "REQUESTED",
  "retryCount": 0,
  "lastFailureReason": null,
  "nextRetryAt": null,
  "read": false,
  "readAt": null,
  "createdAt": "2026-05-09T12:00:00Z",
  "updatedAt": "2026-05-09T12:00:00Z",
  "processingStartedAt": null,
  "sentAt": null,
  "failedAt": null
}
```

### 3. 사용자 알림 목록 조회

```http
GET /api/users/{recipientId}/notifications
GET /api/users/{recipientId}/notifications?read=true
GET /api/users/{recipientId}/notifications?read=false
```

- `read` 생략: 전체
- `read=true`: 읽음만
- `read=false`: 안읽음만

### 4. 알림 읽음 처리

```http
PATCH /api/notifications/{id}/read
X-User-Id: user-1
```

읽음 처리는 멱등합니다. 이미 읽은 알림을 다시 읽음 처리해도 성공하며 최초 `readAt`을 유지합니다.

## 데이터 모델 설명

테이블: `notification_request`

| 필드 | 설명 |
| --- | --- |
| `id` | 알림 요청 ID |
| `recipient_id` | 수신자 ID |
| `notification_type` | 알림 타입 |
| `channel` | `EMAIL`, `IN_APP` |
| `event_id` | 비즈니스 이벤트 ID |
| `title` | 제목 |
| `message` | 본문 |
| `status` | 처리 상태 |
| `retry_count` | 예약/소진된 재시도 기회 수 |
| `last_failure_reason` | 마지막 실패 사유 |
| `next_retry_at` | 다음 재시도 가능 시각 |
| `locked_by` | worker 점유자 |
| `locked_until` | worker 점유 만료 시각 |
| `read_at` | 읽음 시각 |
| `created_at` | 생성 시각 |
| `updated_at` | 변경 시각 |
| `processing_started_at` | 처리 시작 시각 |
| `sent_at` | 발송 성공 시각 |
| `failed_at` | 최종 실패 시각 |

중복 방지 DB unique key:

```text
recipient_id + notification_type + channel + event_id
```

상태:

```text
REQUESTED -> PROCESSING -> SENT
REQUESTED -> PROCESSING -> RETRY_WAITING -> PROCESSING -> SENT
REQUESTED -> PROCESSING -> RETRY_WAITING -> PROCESSING -> FAILED
```

## 패키지 구조

```text
com.liveklass.demo
  common.response
  notification.config
  notification.controller
  notification.dto
  notification.domain
  notification.repository
  notification.service
  notification.sender
  notification.worker
```

공통 응답 구조는 `common.response`에 두고, notification 도메인은 도메인 내부에서 controller/service/repository 등 구성요소별로 나눕니다.

## 요구사항 해석 및 가정

- V1에서는 `notification_request` 단일 테이블이 요청 기록, DB queue, 상태 조회, 재시도/실패 정보, worker lock, 읽음 상태의 source of truth입니다.
- 실제 메시지 브로커(Kafka/RabbitMQ/SQS)는 설치하지 않습니다.
- 실제 이메일 provider/SMTP도 사용하지 않고 `LogNotificationSender`로 대체합니다.
- API 응답은 발송 성공 결과가 아니라 DB 접수 결과입니다.
- `maxRetries = 3`은 최초 발송 실패 이후 최대 3번의 재시도 기회를 의미합니다. `retryCount`는 예약/소진된 재시도 기회 수이며 3을 넘지 않습니다.
- 알림 실패가 원래 결제/수강신청 트랜잭션을 롤백시키면 안 된다는 요구는 알림 요청 저장과 sender 실행을 worker 경계로 분리하는 방식으로 충족했습니다. 별도 결제/수강신청 도메인은 과제 범위 밖이라 구현하지 않았습니다.

## 설계 결정과 이유

### DB queue 선택

과제는 실제 브로커 설치를 요구하지 않습니다. 그래서 `notification_request`를 DB queue처럼 사용했습니다. 이 방식은 서버 재시작 후에도 상태가 남고, H2/JPA만으로 평가자가 실행할 수 있습니다.

### 조건부 update claim

다중 인스턴스 worker가 같은 row를 동시에 발송하지 않도록 claim은 조건부 update로 처리합니다. update count가 `1`인 worker만 sender를 호출합니다.

처리 대상:

```text
status = REQUESTED
OR status = RETRY_WAITING AND nextRetryAt <= now
OR status = PROCESSING AND lockedUntil < now
```

### 재시도와 최종 실패

일시 실패 시 `RETRY_WAITING`으로 전이하고 실패 사유와 다음 재시도 시각을 저장합니다. 재시도 기회가 이미 3번 소진된 상태에서 다시 실패하면 `FAILED`가 됩니다.

Backoff:

| retryCount | 대기 시간 |
| ---: | --- |
| 1 | 1분 |
| 2 | 5분 |
| 3 | 15분 |

### MQ 전환 가능성

운영에서 MQ를 도입할 경우 REST API와 상태 조회는 유지하고, DB polling worker만 broker consumer로 교체할 수 있습니다. `NotificationSender` 포트도 실제 이메일/푸시 provider adapter로 교체 가능합니다.

## 테스트 실행 방법

```bash
./gradlew test
```

검증 범위:

- 요청 등록 API가 즉시 `REQUESTED` 접수 응답 반환
- 동일 이벤트 duplicate 요청이 기존 row 반환
- DB unique constraint가 중복 key 보호
- 상태 조회 응답에 retry/failure/timestamp 포함
- 사용자 목록 read filter 동작
- 읽음 처리 멱등성
- retry policy 1/5/15분 backoff
- worker 성공: `REQUESTED -> PROCESSING -> SENT`
- worker 실패: `PROCESSING -> RETRY_WAITING`
- 재시도 소진 후 `FAILED`
- conditional claim으로 한 worker만 발송
- due `RETRY_WAITING` 및 stale `PROCESSING` 처리 대상 포함

## 미구현 / 제약사항

- 실제 이메일/SMS/push provider 연동 없음. 로그 sender만 구현했습니다.
- 실제 Kafka/RabbitMQ/SQS 연동 없음. DB polling worker만 구현했습니다.
- 예약 발송, 템플릿 관리, 관리자 수동 재시도 API는 선택 요구사항이라 제외했습니다.
- 별도 결제/수강신청 도메인은 구현하지 않았습니다.
- H2 in-memory DB라 앱 재시작 시 데모 데이터는 사라집니다. 운영에서는 MySQL/PostgreSQL 등 영속 DB로 전환해야 합니다.

## AI 활용 범위

- 요구사항 문서 정리와 구현 계획 수립에 AI를 활용했습니다.
- Spring Boot/JPA/API/worker/test 코드 작성에 AI를 활용했습니다.
- 최종 검증은 `./gradlew test` 실행 결과로 확인했습니다.
