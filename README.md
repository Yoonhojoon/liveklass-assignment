# Liveklass Demo - Notification System

## 프로젝트 개요

과제 C 알림 발송 시스템 구현입니다. API는 알림 요청을 즉시 접수하고, 실제 발송은 DB 큐를 폴링하는 worker가 비동기로 처리합니다.

## 기술 스택

- Java 17
- Spring Boot
- Spring Data JPA
- H2 Database
- Gradle
- Lombok

## 실행 방법

```bash
./gradlew bootRun
```

테스트:

```bash
./gradlew test
```

## API 목록 및 예시

### 알림 요청 등록

```http
POST /api/notifications
Content-Type: application/json

{
  "recipientId": "user-1",
  "notificationType": "PAYMENT_CONFIRMED",
  "channel": "EMAIL",
  "eventId": "payment-1001",
  "title": "결제가 완료되었습니다",
  "message": "결제 확정 알림입니다."
}
```

응답은 즉시 `202 Accepted`를 반환하며 실제 발송 성공 여부를 확정하지 않습니다.

### 상태 조회

```http
GET /api/notifications/{id}
```

현재 상태, 재시도 횟수, 마지막 실패 사유, 처리/발송/실패 시각을 반환합니다.

### 사용자 알림 목록

```http
GET /api/users/{recipientId}/notifications?read=false
```

`read` 파라미터는 생략, `true`, `false`를 지원합니다.

### 읽음 처리

```http
PATCH /api/notifications/{id}/read
X-User-Id: user-1
```

읽음 처리는 멱등이며 최초 `readAt`만 보존합니다.

## 데이터 모델 설명

알림 요청, worker 큐 상태, 사용자 알림함을 분리했습니다.

### `notification_request`

알림 요청 원장과 중복 방지 기준을 저장합니다.

- `id`
- `recipient_id`
- `notification_type`
- `channel`
- `event_id`
- `title`
- `message`
- `created_at`, `updated_at`
- unique: `recipient_id + notification_type + channel + event_id`

### `notification_delivery_job`

worker가 폴링/점유/재시도하는 DB 큐입니다.

- `request_id`
- `status`
- `retry_count`
- `last_failure_reason`
- `next_retry_at`
- `locked_by`, `locked_until`
- `processing_started_at`
- `sent_at`, `failed_at`
- `created_at`, `updated_at`

### `notification_inbox`

사용자 알림 목록과 읽음 상태를 저장합니다.

- `request_id`
- `recipient_id`
- `read_at`
- `created_at`, `updated_at`

## 요구사항 해석 및 가정

- 동일 이벤트 기준은 `recipientId + notificationType + channel + eventId`입니다.
- 중복 요청은 새 row를 만들지 않고 기존 요청을 반환합니다.
- DB를 큐처럼 사용하지만, 실제 MQ로 교체 가능하도록 worker 처리 상태는 `notification_delivery_job`에 격리했습니다.
- 사용자 목록 API 보존을 위해 EMAIL/IN_APP 모두 `notification_inbox` row를 생성합니다.

## 설계 결정과 이유

- API 요청 스레드는 발송하지 않고 요청/job/inbox row만 저장합니다.
- worker 실패는 예외로 끝내지 않고 delivery job의 상태, 실패 사유, 재시도 시각으로 저장합니다.
- 다중 인스턴스 중복 처리는 조건부 update claim으로 방지합니다.
- `locked_by`, `locked_until`은 delivery job에만 두어 request 원장을 worker 세부 구현에서 분리했습니다.
- `read_at`은 inbox에만 두어 사용자 보관 상태와 worker 큐 상태를 분리했습니다.

## 테스트 실행 방법

```bash
./gradlew test
```

주요 검증:

- 중복 요청 멱등 처리
- DB unique constraint
- worker conditional claim
- 재시도/최종 실패 상태 전이
- stale `PROCESSING` 재처리
- 사용자 목록 read/unread 필터
- 읽음 처리 멱등성

## 미구현 / 제약사항

- 실제 이메일 발송은 로그 sender로 대체합니다.
- 실제 메시지 브로커는 사용하지 않습니다.
- 수동 재시도 운영 API는 구현하지 않았습니다.
- delivery job 보관/정리 정책은 별도 운영 정책으로 남겨두었습니다.

## AI 활용 범위

요구사항 분석, 데이터 모델 분리 설계, 코드 작성, 테스트 보강, 검증 실행에 AI를 활용했습니다.
