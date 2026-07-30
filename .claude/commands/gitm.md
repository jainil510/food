---
description: Generate a meaningful commit message from the current changes and commit
argument-hint: [optional extra context, e.g. "fixes the login redirect bug"]
allowed-tools: Bash(git status:*), Bash(git diff:*), Bash(git log:*), Bash(git add:*), Bash(git commit:*)
---

## Context

- Git status: !`git status`
- Staged diff: !`git diff --cached`
- Unstaged diff: !`git diff`
- Recent commit style (for reference): !`git log --oneline -10`

## Your task

Extra context from the user (may be empty): $ARGUMENTS

1. If nothing is staged but there are relevant modified/untracked files, stage them yourself with `git add <specific files>` — never `git add -A` or `git add .`. Skip anything that looks like a secret, credential, or build artifact, and warn the user if you do.
2. Read the staged diff in full before writing anything — don't draft from the file list alone. If the diff spans clearly unrelated concerns (e.g. an unrelated formatting pass mixed with a feature), point that out to the user and suggest splitting, rather than writing one message that papers over both.
3. Write a commit message that explains *why* the change was made, not just what changed:
   - Subject line: imperative mood ("add", "fix", "remove" — not "added"/"fixes"), no trailing period, ≤50 chars where possible. Match this repo's existing prefix convention if `git log --oneline` shows one established (e.g. `feat:`/`fix:`); otherwise default to conventional-commit prefixes (`feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `build`, `ci`) picked by what the diff actually does, not guessed.
   - Never restate the diff in prose ("changed X to Y") — the diff already shows that. The subject + body should carry information the diff doesn't: intent, the problem being solved, or a tradeoff, not a narration of lines changed.
   - Add a body only when the *why* isn't obvious from the subject alone. When present: wrap at ~72 chars/line, and use a `- ` bulleted list instead of one paragraph if the commit bundles more than one distinct logical change.
   - If the diff touches an area tracked in `.taskmaster/tasks/tasks.json` and the matching task/subtask is unambiguous from context, reference it (e.g. `Refs task 5.2`) — don't guess an ID if it isn't clear.
   - Fold in the user's extra context ($ARGUMENTS) if provided — it takes precedence over inferred rationale.
4. Show the drafted message to the user and ask for confirmation before committing.
5. Once confirmed, commit with the message via a heredoc (so multi-line messages format correctly):
   ```
   git commit -m "$(cat <<'EOF'
   <subject>

   <body if any>
   EOF
   )"
   ```
6. Run `git status` after committing to confirm it succeeded.

Never push, amend, force anything, or use `--no-verify` — this command only drafts and creates a single commit.
