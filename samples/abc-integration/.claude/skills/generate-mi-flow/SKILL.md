---
name: generate-mi-flow
description: Orchestrate end-to-end generation of a Solace micro-integration project from the main session, printing one-line progress before and after each step. Prefer this over the generate-mi agent for interactive runs. Reads configuration.md to determine binder mode, then launches isolated subagents for init-mi-project, add-test-support, the appropriate binder skill (analyze-3party-binder or add-binder), add-microintegration, clean-claude-files, and clean-readme-files in strict sequence.
tools_required:
  - Read
  - Task
  - AskUserQuestion
---

# Micro-Integration Generation Flow

You orchestrate the generation of a complete Solace micro-integration project by launching
isolated subagents in strict sequential order. You read `configuration.md` once, determine
the binder mode, then chain six subagents to completion — never modifying any skill's own
files or logic.

## Context isolation architecture

Each skill runs inside a `general-purpose` subagent launched via the `Task` tool, giving it
its own context window. Verbose Maven output, file writes, and repair loops are fully
contained in the subagent. Only a single `STATUS:` line returns to you. No file lists, build
logs, or repair tables reach this session.

```
This session (generate-mi-flow)
  ├─ ▶/✓ [1/6] Task(general-purpose): /init-mi-project
  ├─ ▶/✓ [2/6] Task(general-purpose): /add-test-support
  ├─ ▶/✓ [3/6] Task(general-purpose): /add-binder or /analyze-3party-binder
  ├─ ▶/✓ [4/6] Task(general-purpose): /add-microintegration
  ├─ ▶/✓ [5/6] Task(general-purpose): /clean-claude-files
  ├─ ▶/✓ [6/6] Task(general-purpose): /clean-readme-files
  └─ Print pipeline summary
```

---

## Critical rules

### One Task per message — never parallel

**Send exactly ONE `Task` tool call per message.** Never include two `Task` calls in the same
response. Calls issued in one message run in parallel, which would break the sequential
dependency chain and start a skill before its prerequisite finished.

Pattern: send one `Task` → wait → check the result → if success, send the next `Task` in a
**new message** → if failure, stop and report.

### Result checking is mandatory

After every `Task` returns, read the result and find the `STATUS:` line before proceeding:

- **`STATUS: PASSED`** — proceed to the next step.
- **`STATUS: FAILED`** — print the failure details, stop the pipeline, do NOT invoke the next step.
- **No `STATUS:` line** — treat as failure, print the full result, stop.

Also treat these phrases anywhere in a result as a hard stop: `"Configuration incomplete"`,
`"cannot proceed"`, `"Docker is required"`, `"Target module not found"`.

### Progress reporting — one line before, one line after

Each step emits exactly **two** lines of developer-visible text. Nothing else.

**Before** the subagent, print the start line as plain text in the *same message* as the
`Task` call (text first, then the tool call):

```
▶ [n/6] {skill-name} — {action label}
```

**After** the subagent returns, print the result line as the *first* line of your next
message, before any other output:

```
✓ [n/6] {skill-name} — PASSED
✗ [n/6] {skill-name} — FAILED: {one-line reason}
```

Rules:

- One line each. No preamble ("Now I will…"), no restating the plan, no blank padding.
- Always set the `Task` tool's `description` parameter from the step catalogue, so the
  running-tool indicator names the step while it is in flight.
- Subagent output is not streamed to the developer — these lines are the only progress
  signal between Step 0 and Step 7.
- On `✗`, print the failure details *after* the result line, then stop.
- If `▶ ✓ ✗` render as boxes in the developer's terminal, substitute `>`, `OK`, `FAIL`.

### Filesystem boundary

All file operations across the pipeline stay within exactly **two** directory trees:

1. **Workspace root** — the directory containing `configuration.md`.
2. **`TARGET_PROJECT_FOLDER`** — the value read from `configuration.md`.

Never access, list, or search a parent directory of either. Never verify that parent
directories exist. Always pass an explicit `path` to `Glob` and `Grep`. The subagent prompt
template below repeats these rules inside each subagent's own context.

### Orchestrator boundaries

- **You are only an orchestrator.** Do NOT write code, edit source files, fix compilation
  errors, modify POMs, or do anything a skill would do.
- **Do NOT modify any skill.** Everything under `.claude/skills/` is read-only.
- **Do NOT modify generated output.** Never edit a file a skill created.
- **No improvisation.** No extra steps, no build commands, no manual repairs between steps.
- **No Python.** This is a Java/Maven project.

---

## Subagent prompt template

Every step uses this template. Substitute `{SKILL_COMMAND}`, `{SKILL_BRIEF}`, and
`{EXTRA_NOTES}` from the step catalogue; omit the `{EXTRA_NOTES}` paragraph when the
catalogue lists none.

```
You are running a single skill for the micro-integration generation pipeline.

PATH CONSTRAINT: All file operations must stay within exactly two directory trees:
(1) the workspace root (the directory containing configuration.md), and
(2) TARGET_PROJECT_FOLDER as read from configuration.md.
Do NOT access, list, or search any parent directory of either location.
Assume TARGET_PROJECT_FOLDER already exists — skip parent directory verification.
When using Glob or Grep, always set the path parameter to one of these two roots.

Invoke the skill {SKILL_COMMAND} using the Skill tool. {SKILL_BRIEF}

{EXTRA_NOTES}

Wait for the skill to complete fully. Do NOT attempt to fix errors yourself — the skill
has its own repair logic. Do not suggest skipping tests.

RETURN CONTRACT — your ENTIRE final response must be one line. Do not list files created,
do not summarize what the skill did, do not include build output, Maven logs, or repair
details. Output exactly one of:
STATUS: PASSED
STATUS: FAILED — {one-line reason}
```

---

## Step catalogue

**Step 1 — `/init-mi-project`**
`description`: `bootstrap project structure` · label: `bootstrapping project structure` · `max_turns: 10`
*Brief:* This skill bootstraps folder structure, Maven wrapper, and POM files for a new micro-integration project. It reads configuration.md internally and validates all variables.
*Extra:* It includes a build verification step that may invoke /fix-maven-dependencies on errors — let it run its full repair loop without interference.

**Step 2 — `/add-test-support`**
`description`: `build Testcontainer support` · label: `building Testcontainer support` · `max_turns: 10`
*Brief:* This skill generates the Testcontainer wrapper, JUnit 5 extension, and basic integration test in the test-support module. It reads configuration.md and the technology overview report internally.
*Extra:* It includes build verification with a self-healing repair loop — let it run fully without interference.

**Step 3a — `/analyze-3party-binder`** *(third-party binder mode only)*
`description`: `analyze third-party binder` · label: `analyzing third-party binder` · `max_turns: 10`
*Brief:* This skill downloads the third-party binder source, analyzes its Spring Cloud Stream SPI implementation, and produces a CLAUDE.md so downstream skills can reference it.
*Extra:* none.

**Step 3b — `/add-binder`** *(custom binder mode only)*
`description`: `generate binder and tests` · label: `generating binder + tests (longest step)` · `max_turns: 10`
*Brief:* This skill generates the full Spring Cloud Stream binder — shared infrastructure, producer/consumer capabilities, integration tests, and build verification. Internally it invokes /verify-binder, which compiles the module and runs integration tests with up to 5 repair attempts on failure.
*Extra:* This is the longest-running skill in the pipeline. Be patient. Do NOT interfere with the repair process.

**Step 4 — `/add-microintegration`**
`description`: `generate MI application` · label: `generating MI application` · `max_turns: 10`
*Brief:* This skill generates the micro-integration Spring Boot application — capabilities factories, main application class, Spring Cloud Stream YAML configuration, and integration tests. Internally it invokes /verify-microintegration, which compiles the module and runs integration tests with up to 5 repair attempts on failure.
*Extra:* Be patient with the verification and repair loop. Do NOT interfere with it.

**Step 5 — `/clean-claude-files`**
`description`: `clean CLAUDE.md docs` · label: `cleaning CLAUDE.md docs` · `max_turns: 5`
*Brief:* This skill transforms the three CLAUDE.md files (MI module, binder module, test-support module) from code-generation blueprints into clean, developer-facing reference documentation. It reads configuration.md internally.
*Extra:* none.

**Step 6 — `/clean-readme-files`**
`description`: `write README files` · label: `writing README files` · `max_turns: 5`
*Brief:* This skill generates concise README.md files for the project root and each module, using the cleaned CLAUDE.md files as source of truth. It reads configuration.md internally.
*Extra:* none.

---

## Your workflow

### Step 0 — Read configuration and determine binder mode

Read `configuration.md` from the workspace root. Parse the configuration tables and extract
`BINDER_SKIP_GENERATION` from the **Configuration Value** column.

- `true` (case-insensitive) → **third-party binder mode** (Step 3 uses 3a).
- `false` (case-insensitive) → **custom binder mode** (Step 3 uses 3b).

If it is missing or empty, print an error and stop. Otherwise print exactly one line:

```
Binder mode: {custom | third-party}  ·  target: {TARGET_PROJECT_FOLDER}
```

### Steps 1–6 — Run each step in sequence

For each step `n` in order, **blocked by** the previous step returning `STATUS: PASSED`:

1. Print the `▶ [n/6]` start line using the catalogue's label.
2. In the **same message**, send exactly ONE `Task` call — `subagent_type: "general-purpose"`,
   the catalogue's `description` and `max_turns`, and the prompt template filled in from the
   catalogue row. This must be the only tool call in the message.
3. Wait for the result.
4. Open your next message with the `✓ [n/6]` or `✗ [n/6]` result line.
5. On `✓`, continue to step `n+1`. On `✗`, print the failure details and stop the pipeline.

For Step 3, use catalogue row 3a or 3b according to the mode resolved in Step 0. Only the
taken branch prints its progress lines.

### Step 7 — Pipeline summary

**Blocked by:** Step 6 returned `STATUS: PASSED`.

```
## Micro-Integration Generation — Complete

**Binder mode:** {custom | third-party}

| Step | Skill | Status |
|---|---|---|
| 1 | init-mi-project | {PASSED/FAILED} |
| 2 | add-test-support | {PASSED/FAILED} |
| 3 | {add-binder / analyze-3party-binder} | {PASSED/FAILED} |
| 4 | add-microintegration | {PASSED/FAILED} |
| 5 | clean-claude-files | {PASSED/FAILED} |
| 6 | clean-readme-files | {PASSED/FAILED} |

**Target project:** {TARGET_PROJECT_FOLDER}
```

The table restates the values already shown in the per-step `✓`/`✗` lines. Do not re-print
the progress lines here, and do not add details the subagents never returned.

---

## Error handling

- `configuration.md` not found — print error, stop.
- `BINDER_SKIP_GENERATION` missing or empty — print error, stop.
- Any result contains `STATUS: FAILED` — print the failure details, stop. Do NOT invoke the next step.
- Any result contains a hard-stop phrase (`"Configuration incomplete"`, `"cannot proceed"`,
  `"Docker is required"`, `"Target module not found"`) — treat as failure, stop.
- No `STATUS:` line in a result — treat as failure, print the full result, stop.
- A subagent relays a question from a skill via `AskUserQuestion` — it reaches the developer
  automatically. No special handling needed.
- Every abort prints its `✗ [n/6]` line first, then the failure details, so the developer
  always sees which step stopped the pipeline.
