## Feature Branches

The goal is to merge your short-lived feature branch into our `main` (or current working) branch. It is recommended to use the **Git Bash** terminal instead of relying on a GUI.

> **Note:** ALWAYS use `git switch` instead of `git checkout` to switch between branches. `git switch` is safer because it focuses strictly on branch switching and will block you if uncommitted changes conflict with the target branch.

1. **Stash uncommitted work:** Before switching branches, temporarily save any uncommitted changes:
   
   ```bash
   git stash
   ```

2. **Switch and update the base branch:** Switch to `main` (or your current working branch) and pull the latest changes so you don't branch off outdated code:
   ```bash
   git branch -a 
   git switch spring-mqtt-ingestion
   git pull origin spring-mqtt-ingestion
   ```
> **Note:** You can rename renote name (like origin -> github or origin -> foregejo) using `git remote rename <old-name> <new-name>` . To view list of remotes : git remote -v
3. **Create a new branch:** Use `git switch -c` and follow the [Conventional Branch](https://conventionalbranch.org) naming convention (lowercase letters, numbers, and hyphens only, such as `feat/add-login-page` or `fix/header-bug`):
   
   ```bash
   git switch -c feat/your-feature-name
   ```
   *(If you stashed changes in Step 1 that belong on this new branch, run `git stash pop` now.)*

4. **Develop and write tests:** Build your feature and add tests next to the matching layer (full table in [Testing and CI](testing/README.md)):
   ```text
   ├── frontend/
   │   ├── src/views/AuthScreen.tsx
   │   └── tests/unit/                <-- Frontend unit/component tests (Vitest), mirroring src/
   │       └── views/AuthScreen.test.tsx
   ├── src/test/java/com/openroots/   <-- Backend tests: *Test = unit, *IT = integration (Testcontainers)
   └── tests/                         <-- Black-box tests against a running stack
       ├── e2e/mqtt/                  <-- End-to-end MQTT smoke test
       ├── load/mqtt/                 <-- MQTT fleet load test
       └── support/mqtt/              <-- Shared provisioning scripts and local .env
   ```

5. **Test locally, commit, push, and open a Pull Request:**
   * Run the same checks CI runs before pushing:
     ```bash
     # frontend changes
     cd frontend
     npm run lint && npm run typecheck && npm test && npm run build

     # backend changes (from the repository root)
     mvn test        # unit tests; use `mvn verify` to add integration tests (needs Docker)
     ```
   * Commit and push your branch. Follow the [commit conventions](https://gist.github.com/qoomon/5dfcdf8eec66a051ecd85625518cfd13).
     ```bash
     git add <files>
     git commit -m "feat: add your feature description"
     git push -u origin feat/your-feature-name
     ```
   * Open a **Pull Request** on GitHub into the branch the maintainer names (`spring-mqtt-ingestion` or `go-mqtt-ingestion`, which differ only in the ingestion layer; `main` once an ingestion branch is chosen). The **CI** workflow runs four checks, `backend`, `ingestion`, `frontend` and `docker`, and merging is blocked until all pass and a teammate approves. Pushing more commits to the PR re-runs them.
