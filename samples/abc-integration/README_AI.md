# AI-Native MDK Samples — Agentic Code Generation

Claude Code skills and agents help you generate an entire micro-integration (MI) — including Spring Cloud Stream binders, test support, and the integration application. The skills use the ABC sample project as context: its structure, patterns, and code serve as the reference that the AI builds on when generating MIs for other backend technologies. For this reason, the skills and the agent are distributed as part of this MDK example project, located in the `.claude/skills/` and `.claude/agents/` folders.
```
Warning

These skills are powered by language models, which are nondeterministic and may change over time. 
Generated code can vary between runs and may be incorrect, insecure, incomplete, or unsuitable for your environment. 
You are responsible for reviewing, testing, and validating all generated code before use, especially before deploying to production.
```
You start by researching the target technology, review a generated configuration, and then a single agent prompt produces a full MI with tests and documentation.

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Step 0         │     │  Step 1         │     │  Step 2         │     │  Step 3         │
│                 │     │                 │     │                 │     │                 │
│  Start Claude   │──>  │  Research the   │──>  │  Review and     │──>  │  Generate the   │
│  Code           │     │  target tech    │     │  edit config    │     │  MI             │
│                 │     │                 │     │                 │     │  (one prompt)   │
│                 │     │ /analyze-       │     │ /prepare-config │     │                 │
│                 │     │  integration-   │     │                 │     │  generate-mi    │
│                 │     │  tech           │     │                 │     │                 │
└─────────────────┘     └─────────────────┘     └─────────────────┘     └─────────────────┘
```

## Prerequisites

- [Claude Code CLI](https://docs.anthropic.com/en/docs/claude-code) installed
- Java 17+ and Maven available on PATH
- Docker running (required for Testcontainers-based integration tests)
- A Claude Pro or Max subscription, or a Claude Code CLI account with sufficient API credit. Generating a full micro-integration project is token-intensive — we recommend at least $50 in credit.

## Step 0: Start Claude Code

Start Claude Code from the `abc-integration` root folder:

```bash
cd abc-integration
claude
```

For best results, open the `abc-integration` folder in your IDE with the Claude Code IDE plugin. The CLI without IDE integration also works.

All skills and agents expect Claude Code to run from the `abc-integration` root folder.

### Select the Claude Opus model

The skills were developed and tested with **Claude Opus**, and work best on it. After launching Claude Code, select the latest available Opus model by running the `/model` command inside the session:

```
/model claude-opus-5
```

You can verify the active model at any time by running `/model` without arguments. Make sure Opus is selected before starting each step — the model choice carries over within a session but resets if you close and reopen Claude Code.

## Step 1: Analyze the target technology

Run the `/analyze-integration-tech` skill with the technology name:

```
/analyze-integration-tech <technology-name>
```

For example, to target Neo4j:

```
/analyze-integration-tech Neo4j
```

This produces a research document covering Java client libraries, Maven dependencies, testing approaches, security, and configuration for the target technology. The report is saved as a markdown file in the project root directory and referenced by downstream skills. The analysis also determines whether an existing third-party Spring Cloud Stream binder is available. If none exists (as is the case for Neo4j), the agent will generate a basic custom binder during project generation. You can extend it with additional features or adjust the configuration later.

## Step 2: Prepare configuration

Run the `/prepare-config` skill:

```
/prepare-config
```

This reads the research document from Step 1 and pre-fills a `configuration.md` file using `configuration_template.md` as the template. Most fields are populated automatically from the analysis.

After it completes, open `configuration.md` and:

- Fill in any remaining `TODO` placeholders (e.g., `TARGET_PROJECT_FOLDER`, paths, credentials)
- Review and adjust pre-filled values where needed

`configuration.md` is the single source of truth for all downstream generation steps.

## Step 3: Generate the micro-integration project

> **Start a fresh session.** The generation orchestrator works best with a clean context window.
> Close your current Claude Code session and open a new one, or run `/clear` to reset the conversation context before proceeding.

> **Do not abandon the running session.** The orchestrator does not run in unsafe mode, so it will pause and ask for your permission at various points during execution (file writes, shell commands, etc.). Stay with the session and approve prompts as they appear — leaving it unattended will stall the pipeline.

Launch the orchestrator agent with a prompt:

```
use generate-mi agent to generate microintegration application
```

The orchestrator reads `configuration.md` and runs five skills in sequence:

1. **init-mi-project** — scaffolds the project structure and Maven POMs
2. **add-test-support** — creates Testcontainer wrappers and JUnit 5 extensions
3. **add-binder** or **analyze-3party-binder** — generates a custom Spring Cloud Stream binder or analyzes an existing third-party binder
4. **add-microintegration** — generates the micro-integration application bridging Solace and the target technology
5. **clean-claude-files** — converts internal CLAUDE.md files into developer-facing reference documentation

Each skill runs in an isolated subagent. The orchestrator reports pass/fail status after each step and stops on failure.

### `.TEMP` folder (third-party binder mode)

When using a third-party binder (`analyze-3party-binder`), the skill clones and analyzes the binder's GitHub repository into a `.TEMP` folder at the workspace root. This caches the analysis locally so token-intensive web searches are not repeated on subsequent runs.

The `.TEMP` folder is deleted and recreated each time the generate-mi orchestrator runs a third-party binder analysis. After generation completes, the folder remains for:

- Inspecting the third-party binder source code
- Re-running individual skills without re-fetching from GitHub
- Fine-tuning the generated implementation with follow-up skill invocations

## Available skills

| Skill | Purpose |
|---|---|
| `/analyze-integration-tech` | Research a technology from a Java development perspective |
| `/prepare-config` | Pre-fill `configuration.md` from a research document |
| `/init-mi-project` | Bootstrap project folders and Maven POMs |
| `/add-test-support` | Create Testcontainer wrappers and JUnit 5 extensions |
| `/add-binder` | Generate a custom Spring Cloud Stream binder |
| `/analyze-3party-binder` | Analyze an existing third-party binder |
| `/add-microintegration` | Generate the micro-integration application module |
| `/verify-binder` | Compile and run binder integration tests with auto-repair |
| `/verify-microintegration` | Compile and run MI integration tests with auto-repair |
| `/fix-maven-dependencies` | Detect and fix Maven dependency issues |
| `/clean-claude-files` | Convert generation docs to developer reference docs |

Skills can be run individually for incremental development or re-runs after manual fixes.

## After generation: continuing in your IDE

Once the `generate-mi` agent finishes, the generated project is a standard Maven multi-module project. You can open it in the IDE of your choice (IntelliJ IDEA, VS Code, Eclipse, etc.) and continue improving it — for example, adding support for more advanced authentication schemes, implementing additional user requirements, or refining error handling and retry logic.

To build the project:

```bash
mvn clean install
```

To run integration tests (requires Docker for Testcontainers):

```bash
mvn verify
```
