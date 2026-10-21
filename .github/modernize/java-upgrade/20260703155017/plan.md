# Upgrade Plan: iot-backend (20260703155017)

- **Generated**: 2026-07-03 15:50:17
- **HEAD Branch**: N/A
- **HEAD Commit ID**: N/A

## Available Tools

**JDKs**
- JDK 17: not available (baseline will be skipped)
- JDK 25: **<TO_BE_INSTALLED>** (required by steps 1, 3, and 5)

**Build Tools**
- Maven: **<TO_BE_INSTALLED>** (required by steps 1, 3, 4, and 5)

## Guidelines

- Upgrade the runtime to the latest LTS Java release, which is Java 25.
- Keep the change set as small as possible.
- Use the Maven build once installed to verify the upgrade.
- Version control is unavailable in this workspace, so changes will remain uncommitted during this upgrade.

> Note: You can add any specific guidelines or constraints for the upgrade process here if needed, bullet points are preferred.

## Options

- Working branch: appmod/java-upgrade-20260703155017
- Run tests before and after the upgrade: true

## Upgrade Goals

- Upgrade the project runtime target from Java 17 to Java 25.

## Technology Stack

| Technology/Dependency | Current | Min Compatible Version | Why Incompatible |
| --------------------- | ------- | ---------------------- | ---------------- |
| Java | 17 | 25 | User requested latest LTS runtime target |
| Spring Boot starter parent | 3.3.5 | 3.3.5 | No framework migration is required by the scan; validate the current line on Java 25 |
| Maven | none | 3.9.0+ | A Maven installation is required to compile and test the project on the target JDK |
| spring-boot-starter-test | managed by Spring Boot 3.3.5 | managed by Spring Boot 3.3.5 | No direct test-framework upgrade is required for the Java-only bump |

## Derived Upgrades

- Install JDK 25 so the project can be built and tested on the target runtime.
- Install Maven 3.9+ because no Maven installation is present in the workspace.
- Keep the Spring Boot line unchanged unless validation exposes a Java 25 compatibility issue.

## Impact Analysis

### Dependency Changes

| File | Dependency | Current | Action | Target | Reason |
|------|-----------|---------|--------|--------|--------|
| pom.xml | java.version | 17 | upgrade | 25 | Raise the compiler/runtime target to the latest LTS |

### Source Code Changes

No source code changes are currently required. The scan found no legacy `javax` imports, JDK-internal APIs, or Java-version-sensitive code paths.

### Configuration Changes

No application configuration changes are currently required.

### CI/CD Changes

No CI/CD files are present in the workspace that reference a hardcoded Java version.

### Risks & Warnings

- **Missing baseline JDK 17**: The current project JDK is not installed on this machine, so the baseline compile/test step will be skipped. **Mitigation**: Validate directly on Java 25 after the upgrade.
- **Java 25 compatibility of the current Spring Boot line**: The project is on Spring Boot 3.3.5, which may still require a patch-line bump if validation exposes toolchain or framework incompatibilities on Java 25. **Mitigation**: Keep the initial change narrow, then upgrade the Spring Boot line only if compilation or tests fail.
- **No Maven installation**: Validation depends on installing Maven first. **Mitigation**: Install Maven 3.9+ in the environment setup step and use it for all build/test checks.

## Upgrade Steps

- Step 1: Setup Environment
  - **Rationale**: Install the target JDK and Maven so the project can be built and tested on Java 25.
  - **Changes to Make**: Install JDK 25 and Maven 3.9+; verify the installed paths.
  - **Verification**: Confirm the installed JDK and Maven paths are available for the build.

- Step 2: Setup Baseline
  - **Rationale**: Establish a pre-upgrade baseline if the current project JDK is available.
  - **Changes to Make**: No project changes; baseline compile/test only if JDK 17 is present.
  - **Verification**: `mvn clean compile test-compile -q && mvn clean test -q` using the base JDK if available; otherwise skip.

- Step 3: Update Java Target in pom.xml
  - **Rationale**: The only project file change currently identified is the Java compiler/runtime target.
  - **Changes to Make**: Update the `java.version` property in `pom.xml` from 17 to 25.
  - **Verification**: `mvn clean test-compile -q` using JDK 25.

- Step 4: CVE Validation & Fix
  - **Rationale**: Required by the upgrade workflow even if no dependency replacements are expected.
  - **Changes to Make**: Scan direct dependencies and apply patch-level fixes only if the scan reports actionable CVEs.
  - **Verification**: Re-run the compile check after any dependency updates and confirm the CVE scan is clean.

- Step 5: Final Validation
  - **Rationale**: Confirm the target Java 25 runtime works end-to-end with the full test suite.
  - **Changes to Make**: No additional code changes unless validation exposes a compatibility issue.
  - **Verification**: `mvn clean test-compile -q && mvn clean test -q` using JDK 25 and Maven 3.9+.
