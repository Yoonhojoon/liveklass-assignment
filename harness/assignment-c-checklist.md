# Assignment C Checklist

| Requirement | Evidence file | Test evidence | Status |
| --- | --- | --- | --- |
| Request API returns acceptance immediately | `src/main/java/com/liveklass/demo/notification/controller/NotificationController.java` | `NotificationControllerIntegrationTest.postNotificationReturnsImmediateAcceptanceAndDuplicateReturnsExistingId` | done |
| Async worker handles actual send | `src/main/java/com/liveklass/demo/notification/worker/NotificationWorker.java` | `NotificationWorkerTest.workerSuccessMovesRequestedToSent` | done |
| Failure does not rollback business transaction | `NotificationController` persists only; `NotificationWorker` catches sender exceptions into state | `NotificationWorkerTest.retryableFailureStoresReasonAndSchedulesNextRetry` | done |
| Failure reason/retry state stored | `NotificationProcessingService.recordFailure`, `NotificationRequest` fields | `NotificationWorkerTest.retryableFailureStoresReasonAndSchedulesNextRetry` | done |
| Duplicate event not sent twice | `NotificationRequestService.create` duplicate policy A | `NotificationRequestServiceTest.duplicateEventReturnsExistingRequest`, `NotificationConcurrentCreateTest.concurrentDuplicateCreateReturnsSameExistingRequest` | done |
| DB unique constraint protects duplicate key | `NotificationRequest` `uk_notification_event` | `NotificationRequestRepositoryTest.uniqueConstraintProtectsDuplicateEventIdentity` | done |
| Status lookup exposes retry/failure/timestamps | `NotificationResponse`, `NotificationController.get` | `NotificationControllerIntegrationTest.statusLookupExposesRetryFailureAndTimestamps` | done |
| User list supports read filter | `NotificationRequestService.listForRecipient` | `NotificationControllerIntegrationTest.userListSupportsReadAndUnreadFilters` | done |
| Read operation idempotent | `NotificationRequestRepository.markReadIfUnread` | `NotificationControllerIntegrationTest.readEndpointIsIdempotentAndPreservesFirstReadAt` | done |
| Retry policy max 3 with backoff | `RetryPolicy` | `RetryPolicyTest.maxRetriesMeansThreeRetryOpportunitiesAfterInitialAttempt`, `RetryPolicyTest.retryBackoffUsesOneFiveFifteenMinutes` | done |
| Stale `PROCESSING` recovery exists | `NotificationRequestRepository.findProcessable`, `claimForProcessing` | `NotificationRequestRepositoryTest.processableRowsIncludeRequestedDueRetryAndStaleProcessingOnly` | done |
| Multi-instance claim uses conditional update | `NotificationRequestRepository.claimForProcessing` | `NotificationRequestRepositoryTest.conditionalClaimAllowsOnlyOneWorker`, `NotificationWorkerTest.unclaimedWorkerDoesNotSend`, `NotificationWorkerTest.staleWorkerCannotOverwriteAfterAnotherWorkerReclaimsLock` | done |
| Package structure follows domain/component split | `harness/package-structure.md` | `./gradlew test` compile check | done |
| README documents run/API/model/design/tests/limits/AI use | `README.md` | README review + `./gradlew test` | done |
