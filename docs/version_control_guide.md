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

4. **Develop and write tests:** Build your feature and add the corresponding unit/component test (or E2E test for full user journeys) inside `tests/`:
   ```text
   ├── tests/
   │   ├── unit/                      <-- Fast isolated code & component tests
   │   │   └── frontend/
   │   │       └── feature-01/
   │   │           └── Button.test.tsx
   │   ├── e2e/                       <-- Full browser user flows (Playwright/Cypress)
   │   │   └── checkout-flow.spec.ts
   │   └── load/                      <-- Traffic & stress scripts (k6/JMeter/Locust)
   │       └── api-stress.js
   ```

5. **Test locally, commit, push, and open a Pull Request:**
   * Run unit tests locally first to catch obvious errors before pushing:
     ```bash
     npm run test:unit
     ```
   * Commit and push your branch. Our **CI workflow** automatically runs unit tests on every pushed commit: check [commit conventions](https://gist.github.com/qoomon/5dfcdf8eec66a051ecd85625518cfd13).
     ```bash
     git add .
     git commit -m "feat: add your feature description"
     git push -u origin feat/your-feature-name
     ```
   * Open a **Pull Request** into `main`. The automated **CI/CD pipeline** will run the full PR test suite and block merging until all checks pass and a teammate approves.
