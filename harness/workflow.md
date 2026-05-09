# Recommended Workflow

## Default loop

```text
1. Read requirements
2. Pick one small slice
3. Write or update tests for that slice
4. Implement minimal code
5. Run targeted tests
6. Fix until green
7. Update README/docs when behavior, API, model, or package layout changes
8. git add changed files
9. git commit with a concise Korean 1-2 line message
10. Verify the slice commit exists with `git log -1 --oneline` and confirm the next slice starts from a clean or intentionally documented `git status`.
```

## Slice order

1. Domain enums and request model
2. `notification_request` entity, repository, unique constraint
3. Request registration service with duplicate policy A
4. Status/list/read APIs
5. Retry policy
6. Worker claim via conditional update
7. Sender port and mock/log sender
8. Worker success/failure/retry/final failure
9. Stale `PROCESSING` recovery
10. Package structure cleanup
11. Submission README cleanup

## Commit rule

Each completed slice must end with:

```bash
git add <changed-files>
git commit
```

Allowed prefixes: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`.

Message format:

```text
<prefix>: 간단한 한국어 요약

선택: 필요하면 한국어 1줄 추가 설명
```

Rules:

- Keep the message to 1-2 short lines.
- Do not force phrases such as `~한 이유`.
- Do not add Lore-style trailers unless explicitly requested.
- State what changed and why only as much as needed for the slice.

## Commit verification rule

After every slice commit, run:

```bash
git status --short
git log -1 --oneline
```

Do not claim a slice is complete unless the commit exists. If files remain uncommitted because they belong to the next slice, record that explicitly before continuing.

## GitHub issue / branch / PR workflow

Every new task must start from GitHub issue tracking unless the user explicitly says not to use GitHub for that task.

1. Choose the matching template:
   - Bug fix: `.github/ISSUE_TEMPLATE/bug_report.md`
   - Feature/improvement/performance: `.github/ISSUE_TEMPLATE/feature_request.md`
2. Create a GitHub issue from the chosen template before coding.
   - Fill the branch-name field with the planned branch name when available.
   - Keep scope small enough to match one harness slice when possible.
3. Create a separate branch from current `master` for the issue.
   - Recommended names: `feat/<issue-number>-short-name`, `fix/<issue-number>-short-name`, `docs/<issue-number>-short-name`, `test/<issue-number>-short-name`.
4. Work on the branch using the slice loop above.
   - Keep commits small and Korean 1-2 line messages.
   - Run targeted tests first, then broader tests before PR.
5. After implementation and tests pass, push the branch and open a PR to `master`.
   - Use `.github/PULL_REQUEST_TEMPLATE.md`.
   - Link the issue in the PR.
   - Include test command/result summary.
6. Merge to `master` only after PR checks pass and review requirements are satisfied.
7. After merge, confirm `master` contains the merge and the linked issue is closed or updated.

Do not merge failed tests, missing PR template content, or broad unreviewed scope into `master`.
