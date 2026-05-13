# Liveklass Demo - Notification System

## 프로젝트 개요

과제 C 알림 발송 시스템 구현입니다. API는 알림 요청을 즉시 접수하고, 실제 발송은 DB 큐를 폴링하는 worker가 비동기로 처리합니다.

## 기술 스택

- Java 17
- Spring Boot 4.0.6
- Spring Data JPA
- Spring Web MVC
- H2 Database
- Gradle
- Lombok
- Micrometer

## 실행 방법

```bash
./gradlew bootRun
```

기본 DB는 파일 기반 H2입니다.

- DB 파일 위치: `./data/liveklass`
- 스키마 전략: `spring.jpa.hibernate.ddl-auto=update`
- `create-drop`을 사용하지 않으므로 서버를 재시작해도 알림 요청, 발송 작업, 사용자 알림함 데이터가 유지됩니다.
- 로컬 데이터를 초기화하려면 애플리케이션을 종료한 뒤 `data` 디렉터리의 H2 파일을 삭제하고 다시 실행합니다.
- H2 콘솔은 기본 설정에서 비활성화되어 있으며, `local` 프로필을 사용하면 `/h2-console`로 확인할 수 있습니다.

테스트:

```bash
./gradlew test
```

## Docker Compose 실행

회사 테스트나 데모처럼 실행 환경을 맞추고 싶을 때는 Docker Compose로 실행할 수 있습니다. 배포 환경의 통일을 위해 Docker를 권장합니다.

```bash
docker compose up --build
```

- 애플리케이션 포트: `http://localhost:8080`
- H2 콘솔: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:file:./data/liveklass`
- 사용자명: `sa`
- 비밀번호: 없음

Compose 실행 시 `docker` 프로필을 사용하며, H2 파일 DB는 Docker named volume `liveklass-h2-data`에 저장합니다. 로컬 `./data` 디렉터리와 분리되므로 테스트하는 사람이 저장소 파일 상태에 영향을 덜 받고, 컨테이너를 내렸다가 다시 올려도 알림 요청, 발송 작업, 사용자 알림함 데이터는 유지됩니다.

호스트 포트를 바꾸고 싶으면 `APP_PORT`를 지정합니다.

```bash
APP_PORT=18080 docker compose up --build
```

중지:

```bash
docker compose down
```

데이터까지 초기화:

```bash
docker compose down -v
```

재시작 복구 정책을 확인하고 싶으면 데이터를 유지한 채 앱 컨테이너만 재시작합니다.

```bash
docker compose restart app
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

성공 응답은 공통 래퍼를 사용합니다.

```json
{
  "success": true,
  "code": "COMMON2020",
  "message": "요청이 접수되었습니다.",
  "data": {
    "id": 1,
    "status": "REQUESTED",
    "duplicated": false
  }
}
```

오류 응답은 RFC 9457 `ProblemDetail` 형식을 사용하고, 애플리케이션 에러 코드는 확장 필드 `code`로 제공합니다.

```json
{
  "type": "about:blank",
  "title": "알림 요청이 올바르지 않습니다.",
  "status": 400,
  "detail": "recipientId is required",
  "instance": "/api/notifications",
  "code": "NOTIFICATION4000"
}
```

### 상태 조회

```http
GET /api/notifications/{id}
```

현재 상태, 재시도 횟수, 마지막 실패 사유, 다음 재시도 시각, lock 정보, 처리/발송/실패 시각을 반환합니다.

```json
{
  "success": true,
  "code": "COMMON2000",
  "message": "요청이 성공했습니다.",
  "data": {
    "id": 1,
    "recipientId": "user-1",
    "notificationType": "PAYMENT_CONFIRMED",
    "channel": "EMAIL",
    "eventId": "payment-1001",
    "title": "결제가 완료되었습니다",
    "message": "결제 확정 알림입니다.",
    "status": "RETRY_WAITING",
    "retryCount": 1,
    "lastFailureReason": "temporary sender failure",
    "nextRetryAt": "2026-05-13T10:01:00Z",
    "lockedBy": null,
    "lockedUntil": null,
    "retryable": true,
    "terminal": false,
    "read": false,
    "readAt": null,
    "createdAt": "2026-05-13T09:59:00Z",
    "updatedAt": "2026-05-13T10:00:00Z",
    "processingStartedAt": "2026-05-13T10:00:00Z",
    "sentAt": null,
    "failedAt": null
  }
}
```

### 사용자 알림 목록

```http
GET /api/users/{recipientId}/notifications?read=false
```

`read` 파라미터는 생략, `true`, `false`를 지원하며 응답은 공통 래퍼의 `data` 배열로 반환됩니다.

### 읽음 처리

```http
PATCH /api/notifications/{id}/read
X-User-Id: user-1
```

읽음 처리는 멱등이며 최초 `readAt`만 보존합니다. 응답은 상태 조회와 같은 `NotificationResponse`를 공통 래퍼의 `data`로 반환합니다.

### 지원 enum

- `notificationType`: `COURSE_ENROLLED`, `PAYMENT_CONFIRMED`, `COURSE_START_D_MINUS_1`, `COURSE_CANCELED`
- `channel`: `EMAIL`, `IN_APP`
- `status`: `REQUESTED`, `PROCESSING`, `SENT`, `RETRY_WAITING`, `FAILED`

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

## 운영 설정

알림 worker와 retry 정책은 외부 설정으로 조정합니다.


| 설정                                | 기본값      | 설명                            |
| ----------------------------------- | ----------- | ------------------------------- |
| `notification.worker.enabled`       | `true`      | worker 실행 여부                |
| `notification.worker.poll-delay-ms` | `5000`      | poll 간격                       |
| `notification.worker.batch-size`    | `20`        | 한 번에 조회할 처리 후보 수     |
| `notification.worker.lock-ttl`      | `10m`       | `PROCESSING` 점유 만료 시간     |
| `notification.retry.max-retries`    | `3`         | 최초 시도 이후 재시도 가능 횟수 |
| `notification.retry.backoffs`       | `1m,5m,15m` | 재시도 횟수별 대기 시간         |

worker는 성공, 재시도 예약, 최종 실패, claim skip을 로그와 Micrometer counter로 남깁니다.

- `notification.worker.sent`
- `notification.worker.retry_scheduled`
- `notification.worker.failed`
- `notification.worker.claim_skipped`

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
- 실제 운영 환경으로 전환이 가능해야 하기 때문에, 추후 로그 검색을 위한 로그 분류가 필요하다고 가정했습니다.

## 설계 결정과 이유

- API 요청 스레드는 발송하지 않고 요청/job/inbox row만 저장합니다.
- 실제 메시지 브로커 없이 구현하되, 실제 운영 환경으로 전환 가능한 구조여야 하기 때문에 큐를 사용하지 않되, 이에 대비한 인터페이스 구조로 해야한다고 판단했습니다.
- worker 실패는 예외로 끝내지 않고 delivery job의 상태, 실패 사유, 재시도 시각으로 저장합니다.
- 다중 인스턴스 중복 처리는 조건부 update claim으로 방지합니다.
- `locked_by`, `locked_until`은 delivery job에만 두어 request 원장을 worker 세부 구현에서 분리했습니다.
- `read_at`은 inbox에만 두어 사용자 보관 상태와 worker 큐 상태를 분리했습니다.
- 성공 응답은 `common.response.ApiResponse`로 통일하고, 오류 응답은 Spring의 `ProblemDetail` 기반으로 분리했습니다.

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

## 재시작 복구 정책

서버 재시작 후 worker는 파일 DB에 남아 있는 `notification_delivery_job` 중 아래 대상을 다시 처리합니다.

- `REQUESTED`
- `RETRY_WAITING` 중 `nextRetryAt`이 현재 시각보다 과거인 작업
- `PROCESSING` 중 `lockedUntil`이 현재 시각보다 과거인 오래 점유된 작업

`SENT`, `FAILED`는 terminal 상태로 보고 자동 재처리하지 않습니다.

## 미구현 / 제약사항

- 실제 이메일 발송은 로그 sender로 대체합니다.
- 실제 메시지 브로커는 사용하지 않습니다.
- 수동 재시도 운영 API는 구현하지 않았습니다.
- delivery job 보관/정리 정책은 별도 운영 정책으로 남겨두었습니다.

## AI 활용 범위

요구사항 분석, 데이터 모델 분리 설계, 코드 작성, 테스트 보강, 검증 실행에 AI를 활용했습니다.
