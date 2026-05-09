# Verification and Control

## Required gates per slice

- Requirement link identified before code change.
- Target test or verification command chosen before implementation.
- Code changed only inside slice scope.
- Relevant tests pass or blocker is documented.
- README/docs updated if API/model/behavior/package layout changed.
- Slice committed after verification.
- Slice completion claim includes `git log -1 --oneline` evidence for the slice commit.
- Any remaining `git status --short` output is explained as next-slice or intentionally untracked work.

## Minimum verification commands

```bash
./gradlew test
```

Use targeted tests first when available, then full test suite before final submission.

## Review checklist

- API request thread does not perform actual send.
- Failure exception becomes stored status data.
- Duplicate key is `recipientId + notificationType + channel + eventId`.
- DB unique constraint exists for duplicate protection.
- Duplicate request returns existing row.
- Worker claim uses conditional update, not select-only ownership.
- Processable statuses are `REQUESTED`, due `RETRY_WAITING`, stale `PROCESSING`.
- `SENT` and `FAILED` are terminal.
- Read operation is idempotent.
- Common responses stay under `common.response`.
- Notification code stays under domain/component packages listed in `harness/package-structure.md`.
- No MQ/outbox overbuild in v1.
- No secrets, personal paths, tokens, or account data recorded.

## Sensitive information check

Before commit, inspect changed text for:

```text
password, secret, token, apiKey, api_key, Authorization, Bearer, /Users/, C:\\Users\\, /home/<personal>
```

Do not commit real secrets or personal environment details.

## GitHub workflow gates

Before implementation:

- GitHub issue exists using `.github/ISSUE_TEMPLATE/bug_report.md` or `.github/ISSUE_TEMPLATE/feature_request.md`.
- Branch is created from `master` and matches the issue scope.
- Issue body records the planned branch name when the template provides that field.

Before PR:

- Relevant tests pass and command output is summarized.
- Commits are small and use concise Korean 1-2 line messages.
- PR targets `master` and uses `.github/PULL_REQUEST_TEMPLATE.md`.
- PR links the issue.

Before merge:

- PR checks pass or the absence of configured checks is noted.
- Review requirements are satisfied.
- No unresolved scope, test, secret, or harness checklist blockers remain.
