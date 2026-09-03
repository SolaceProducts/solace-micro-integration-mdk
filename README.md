# Solace Micro-Integration Development Kit (MDK)

[![License](https://img.shields.io/badge/license-Solace%20Community-blue)](LICENSE.txt)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://adoptium.net/)
[![Maven Central](https://img.shields.io/maven-central/v/com.solace.connector.core/pubsubplus-connector-framework?label=framework)](https://mvnrepository.com/artifact/com.solace.connector.core/pubsubplus-connector-framework)

**Build a custom Solace Micro-Integration for any backend technology — and let a coding agent write most of it.**

The MDK is the developer kit for creating custom, self-managed [Solace Micro-Integrations](https://solace.com/what-are-micro-integrations/). This repository holds the samples, the Claude Code skills that generate new Micro-Integrations end to end, and the development documentation. The framework artifacts themselves are published to Maven Central.

---

## Table of Contents

- [What is a Micro-Integration?](#what-is-a-micro-integration)
- [⭐ Generate a Micro-Integration with Claude Code](#-generate-a-micro-integration-with-claude-code)
- [Architecture](#architecture)
- [Repository Contents](#repository-contents)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [The ABC Sample](#the-abc-sample)
- [Framework Artifacts](#framework-artifacts)
- [Documentation](#documentation)
- [Support](#support)
- [Contributing](#contributing)
- [Authors](#authors)
- [License](#license)
- [Resources](#resources)

---

## What is a Micro-Integration?

Micro-Integrations are small, lightweight, event-driven integration modules that connect enterprise technologies — legacy and SaaS applications, messaging services, databases, filesystems, AI agents — to a Solace event broker or [event mesh](https://solace.com/what-is-an-event-mesh/), so they can exchange information in real time.

Micro-Integrations are to integration what microservices are to application architecture: they decompose a monolithic integration flow into smaller, purpose-built, independently deployable components. Each one is narrowly scoped, which makes it easier to design, deploy, and change without collateral damage elsewhere.

A Micro-Integration is either a **source** (data flows from the external system into Solace) or a **target** (data flows from Solace out to the external system), and may include transforms that modify headers or payload in flight.

Solace publishes a catalogue of ready-made Micro-Integrations in the [Integration Hub](https://solace.com/integration-hub/). **The MDK is for when the system you need to connect isn't in that catalogue.**

> **Note:** The MDK builds **self-managed** Micro-Integrations — ones you deploy and operate in your own infrastructure. Custom Micro-Integrations in Solace Cloud are not supported at this time.

---

## ⭐ Generate a Micro-Integration with Claude Code

This is the headline capability of the MDK.

Writing a custom Micro-Integration by hand means learning the Spring Cloud Stream binder SPI, the Solace Micro-Integration Framework contract, the Testcontainers-based test harness, and the packaging conventions. There is a lot to look at before you write your first line of code.

The MDK ships a set of **Claude Code skills and an orchestrator agent** that do that work for you. They use the [ABC sample](#the-abc-sample) in this repository as their reference — its structure, patterns, and code are the template the agent builds on when generating a Micro-Integration for a different backend technology. That's why the skills live inside the sample project, under `samples/abc-integration/.claude/`.

The workflow is four steps:

```
┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
│  Step 0          │   │  Step 1          │   │  Step 2          │   │  Step 3          │
│                  │──▶│                  │──▶│                  │──▶│                  │
│  Start Claude    │   │  Research the    │   │  Review and      │   │  Generate the    │
│  Code            │   │  target tech     │   │  edit config     │   │  MI (one prompt) │
│                  │   │                  │   │                  │   │                  │
│  claude          │   │ /analyze-        │   │ /prepare-config  │   │  generate-mi     │
│                  │   │  integration-tech│   │                  │   │  agent           │
└──────────────────┘   └──────────────────┘   └──────────────────┘   └──────────────────┘
```

```bash
git clone https://github.com/SolaceProducts/solace-mdk.git
cd solace-mdk/samples/abc-integration     # skills are rooted here, not at the repo root
claude
```

Then research the target technology, inside the Claude Code session:

```
/analyze-integration-tech <technology-name>
```

For example, to target Neo4j:
```
/analyze-integration-tech Neo4j      # research the target technology
```

Prepare configuration based on the research document using `/prepare-config` skill:

```
/prepare-config
```

After it completes, open `configuration.md` and:

- Fill in any remaining `TODO` placeholders (e.g., `TARGET_PROJECT_FOLDER`, paths, credentials)
- Review and adjust pre-filled values where needed

Start a fresh session: 
```
/clear
```
Generate the micro-integration project
```
use generate-mi agent to generate microintegration application
```

The `generate-mi` orchestrator agent reads `configuration.md` and runs six skills in sequence, each in an isolated subagent so verbose build output never floods the main context:

| Step | Skill | What it does |
|---|---|---|
| 1 | `init-mi-project` | Scaffolds folders, Maven wrapper, and POMs |
| 2 | `add-test-support` | Generates Testcontainer wrappers and JUnit 5 extensions |
| 3 | `add-binder` **or** `analyze-3party-binder` | Writes a custom Spring Cloud Stream binder, or analyzes an existing third-party one |
| 4 | `add-microintegration` | Generates the Spring Boot MI application, capabilities factories, and YAML config |
| 5 | `clean-claude-files` | Converts internal generation notes into developer-facing reference docs |
| 6 | `clean-readme-files` | Writes README files for the project root and each module |

Steps 3 and 4 each run integration tests via Testcontainers and **self-repair on failure**, retrying up to five times before giving up. The orchestrator checks a `STATUS: PASSED` / `STATUS: FAILED` line after every step and stops the pipeline on the first failure rather than compounding a bad state.

### Full skill catalogue

| Skill | Purpose |
|---|---|
| `/analyze-integration-tech` | Research a technology from a Java development perspective |
| `/prepare-config` | Pre-fill `configuration.md` from the research document |
| `/init-mi-project` | Bootstrap project folders and Maven POMs |
| `/add-test-support` | Create Testcontainer wrappers and JUnit 5 extensions |
| `/add-binder` | Generate a custom Spring Cloud Stream binder |
| `/analyze-3party-binder` | Analyze an existing third-party binder |
| `/add-microintegration` | Generate the Micro-Integration application module |
| `/verify-binder` | Compile and run binder integration tests, with auto-repair |
| `/verify-microintegration` | Compile and run MI integration tests, with auto-repair |
| `/fix-maven-dependencies` | Detect and fix Maven dependency problems |
| `/clean-claude-files` | Convert generation docs to developer reference docs |
| `/clean-readme-files` | Generate module README files |

Skills can be run individually for incremental development, or re-run after you make manual fixes.

### What you get

A standard Maven multi-module project you own outright: ordinary Java, ordinary Spring, ordinary Maven. Open it in IntelliJ, VS Code, or Eclipse and keep going — add authentication schemes, refine error handling and retry logic, implement your own requirements. There is no MDK-specific scaffolding to reverse-engineer later.

### Review what it produces

> **These skills are powered by language models, which are nondeterministic and may change over time.** Generated code can vary between runs and may be incorrect, insecure, incomplete, or unsuitable for your environment. You are responsible for reviewing, testing, and validating all generated code before use, and especially before deploying to production.

The generated integration tests represent a minimal baseline — expand and adapt them to match your specific use cases. Put generated Micro-Integrations through your normal code review and security review.

### After generation: continuing in your IDE

Once the `generate-mi` agent finishes, the generated project is a standard Maven multi-module project. You can open it in the IDE of your choice (IntelliJ IDEA, VS Code, Eclipse, etc.) and continue improving it — for example, adding support for more advanced authentication schemes, implementing additional user requirements, or refining error handling and retry logic.

To build the project:

```bash
mvn clean install
```

To run integration tests (requires Docker for Testcontainers):

```bash
mvn verify
```

---

## Architecture

```
┌─────────────────┐     ┌──────────────────────────────────────┐     ┌─────────────────┐
│                 │     │   Micro-Integration (Spring Boot)    │     │                 │
│  Your external  │◄───►│  ┌────────┐  ┌────────┐  ┌────────┐  │◄───►│  Solace event   │
│     system      │     │  │ Custom │─▶│   MI   │─▶│ Solace │  │     │     broker      │
│                 │     │  │ binder │  │ frame- │  │ binder │  │     │   / event mesh  │
│                 │     │  │        │  │  work  │  │        │  │     │                 │
└─────────────────┘     │  └────────┘  └────────┘  └────────┘  │     └─────────────────┘
                        └──────────────────────────────────────┘
                             ▲ you (or the agent)   ▲ provided
                               write this
```

- **Spring Cloud Stream binders** handle all data access. One side is always the [Solace binder](https://github.com/SolaceProducts/solace-spring-cloud) — open source and in production for years. The other side is the binder for your technology, which is the main development work.
- **The Solace Micro-Integration Framework** sits in the middle. It moves data from source binder to target binder, applies optional message transforms, and provides framework services such as acknowledgment management that prevents data loss.
- **Packaging** combines the two binders and the framework into a single uber-JAR that runs as a Spring Boot application, bringing with it logging via Logback, metrics via Micrometer, REST management endpoints via Spring Boot Actuator, endpoint security via Spring Security, and enterprise runtime features including failover.
- **Container images** are built via Jib or the Spring Boot Maven plugin and run on Docker, Podman, Kubernetes, and other container runtimes.

Because binders are a Spring ecosystem standard, the MDK also works with **existing third-party Spring Cloud Stream binders** you find in the community — you don't always have to write one. The `analyze-3party-binder` skill exists for exactly that case.

## Repository Contents

```
solace-mdk/
├── samples/
│   ├── README.md
│   └── abc-integration/                    ← standalone project root; open this in your IDE
│       ├── .claude/
│       │   ├── agents/generate-mi.md       ← the generation orchestrator  ⭐
│       │   └── skills/                     ← 12 skills  ⭐
│       ├── README.md                       ← the ABC Micro-Integration guide
│       ├── configuration_template.md       ← template for configuration.md
│       ├── abc-parent/                     ← the fictitious ABC service + Java client
│       ├── abc-test-support/               ← Testcontainers and JUnit 5 extensions
│       ├── spring-cloud-stream-binder-abc/ ← custom binder for ABC
│       └── abc-micro-integration/          ← the Solace ↔ ABC Micro-Integration
├── mdk-javadoc/README.md                   ← where to find the API docs
├── Micro-Integration Framework Development Guide.pdf
├── LICENSE.txt
└── NOTICE
```

## Prerequisites

**To build and run the sample:**

- **Java 17+**
- **Maven** — a wrapper (`./mvnw`) is included, so a local installation is optional
- **Docker**, running — the integration tests use [Testcontainers](https://www.testcontainers.org/)
- A **Solace event broker**. A free [Solace Cloud](https://console.solace.cloud/login/new-account) service or a local [broker in Docker](https://products.solace.com/download/PUBSUB_DOCKER_STANDARD) both work.

**Additionally, to use the AI generation skills:**

- The **[Claude Code CLI](https://docs.claude.com/en/docs/claude-code)**, ideally with the IDE plugin
- A **Claude Pro or Max subscription**, or a Claude Code account with API credit. Generating a full Micro-Integration is token-intensive — we recommend at least **$50** in credit.
- **Claude Opus.** Select it with `/model` before starting each step. The model choice carries over within a session but resets when you reopen Claude Code.

Familiarity with Java and Spring is expected. If you use the agent, you still need enough Spring knowledge to review what it writes.

## Quick Start

### Option A — generate a Micro-Integration for your technology

See [⭐ Generate a Micro-Integration with Claude Code](#-generate-a-micro-integration-with-claude-code) above.

### Option B — build and explore the sample

```bash
git clone https://github.com/SolaceProducts/solace-mdk.git
cd solace-mdk/samples/abc-integration

# Build everything
./mvnw clean install

# Build and run all tests, including integration tests (requires Docker)
./mvnw clean verify
```

Run integration tests for a single module:

```bash
./mvnw verify -pl spring-cloud-stream-binder-abc -am   # binder
./mvnw verify -pl abc-test-support -am                 # test support
./mvnw verify -pl abc-micro-integration -am            # the Micro-Integration
```

## The ABC Sample

`samples/abc-integration` is a complete, working Micro-Integration. "ABC" is a **fictitious** REST-based queuing service, invented so the sample has a realistic non-Solace system on the other side without dragging in a real vendor dependency. The repository includes the ABC service itself, so everything runs locally.

| Module | Description |
|---|---|
| [`abc-parent`](samples/abc-integration/abc-parent/README.md) | The ABC service implementation and its Java client library |
| [`abc-test-support`](samples/abc-integration/abc-test-support/README.md) | Testcontainers wrappers and JUnit 5 extensions, including a Jib-built image from the service JAR |
| [`spring-cloud-stream-binder-abc`](samples/abc-integration/spring-cloud-stream-binder-abc/README.md) | The custom Spring Cloud Stream binder for ABC — the module worth reading first |
| [`abc-micro-integration`](samples/abc-integration/abc-micro-integration/README.md) | The Micro-Integration bridging the Solace event broker and ABC |

The sample demonstrates four test-infrastructure strategies (official Testcontainers module, official Docker image, remote/cloud backend proxy, and local binaries packaged with Jib), so the generated project can use whichever fits your target technology.

## Framework Artifacts

The framework is consumed from Maven Central; it is not built from this repository.

| Artifact | Use it for |
|---|---|
| [`com.solace.connector.core:pubsubplus-connector-framework`](https://mvnrepository.com/artifact/com.solace.connector.core/pubsubplus-connector-framework) | The core runtime — implementing the Micro-Integration application |
| [`com.solace.connector.core:pubsubplus-connector-io-common`](https://mvnrepository.com/artifact/com.solace.connector.core/pubsubplus-connector-io-common) | Common IO interfaces — implementing binders |
| [`com.solace.connector.core.test:pubsubplus-connector-framework-test-support-utilities`](https://mvnrepository.com/artifact/com.solace.connector.core.test/pubsubplus-connector-framework-test-support-utilities) | Writing unit and integration tests |
| `com.solace.microintegration.core:micro-integration-build-parent` | The build parent POM — packaging, uber-JAR, and container image build |

Javadoc ships with the binaries and appears automatically in IntelliJ IDEA, Eclipse, and VS Code when the project is imported as a Maven project. Javadoc JARs can also be downloaded from Maven Central.

## Documentation

- **[Micro-Integration Framework Development Guide](Micro-Integration%20Framework%20Development%20Guide.pdf)** (PDF, in this repo) — the comprehensive reference
- **[AI generation guide](samples/abc-integration/README_AI.md)** — the agent workflow in full
- **[API documentation](mdk-javadoc/README.md)** — Javadoc and module reference
- [Micro-Integrations overview](https://docs.solace.com/Micro-Integrations/Micro-Integrations.htm) — Solace docs
- [Self-Managed Micro-Integrations](https://docs.solace.com/Micro-Integrations/Self-Managed/self-managed-micro-integrations.htm) — deployment and runtime
- [Micro-Integration Manager](https://docs.solace.com/Micro-Integrations/Self-Managed/Connector-Manager/connector-manager.htm) — lifecycle management
- [Integration Hub](https://solace.com/integration-hub/) — pre-built Micro-Integrations
- [Solace Spring Cloud Stream binder](https://github.com/SolaceProducts/solace-spring-cloud)
- [Spring Cloud Stream binder SPI](https://docs.spring.io/spring-cloud-stream/reference/spring-cloud-stream/spi.html)

## Support

The MDK is not covered by Solace product support contracts.

- **Bugs and feature requests** — open a [GitHub issue](https://github.com/SolaceProducts/solace-mdk/issues)
- **Questions, ideas, and feedback** — the [Connectors & Integrations](https://community.solace.com/c/connectors-integrations/9) category on [Solace Community](https://community.solace.com/), where the MDK team is active
- **General Solace support** — https://solace.com/support/

Feedback is welcome, especially on the generation skills: which technology you pointed them at, what they got right, and where they could be refined.

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) for the development workflow and the contributor agreement.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-change`)
3. Commit your changes
4. Push the branch and open a Pull Request

## Authors

See the list of [contributors](https://github.com/SolaceDev/solace-mdk-samples/graphs/contributors) who participated in this project.

## License

This project is licensed under the **Solace Community License Agreement, Version 1.0**. See [LICENSE.txt](LICENSE.txt) for the full text.

Note that the license does not permit use for an "Excluded Purpose" — broadly, offering a hosted service that competes with Solace products. Review the terms before building on the MDK commercially.

## Resources

For more information about Solace technology:

- Solace Developer Portal — https://www.solace.dev/
- Solace Community — https://community.solace.com/
- Solace blog — https://solace.com/blog/
- What are Micro-Integrations? — https://solace.com/what-are-micro-integrations/
