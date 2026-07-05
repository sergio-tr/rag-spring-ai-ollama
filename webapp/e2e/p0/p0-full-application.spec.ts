/**
 * P0 full-application E2E suite - validates main product phases from the UI.
 *
 * Requires live stack (Spring profile `e2e` recommended). Run:
 *   npm run test:e2e:p0
 *
 * Tags: @p0-app @fullstack
 */
import { test, expect } from "./fixtures/p0-test";
import { uniqueProjectName } from "../fixtures/projects";
import { actaKnowledgeBaseFilePath } from "../fixtures/documents";
import { adminEmail, adminPassword } from "../fixtures/users";
import { productApiUrl } from "../support/helpers";
import { LoginPage } from "./page-objects/login.page";
import { ProjectsPage } from "./page-objects/projects.page";
import { ChatPage } from "./page-objects/chat.page";
import { DocumentsPage } from "./page-objects/documents.page";
import { AdminPage } from "./page-objects/admin.page";
import { LabPage } from "./page-objects/lab.page";

test.describe("P0 full application E2E @p0-app @p0-app-fast @fullstack", () => {
  test.describe("P0-01 auth login and navigation", () => {
    test("P0-01 seed login reaches projects; sidebar links visible", async ({ page }) => {
      const login = new LoginPage(page);
      await login.loginAsSeedUser();
      await login.expectReachedProjects();

      await expect(page.getByRole("link", { name: /chat|documents|settings|ajustes/i }).first()).toBeVisible();
      await expect(page.getByRole("link", { name: /projects|proyectos/i }).first()).toBeVisible();
    });

    test("P0-01b wrong password shows invalid credentials", async ({ page }) => {
      const login = new LoginPage(page);
      const { email } = LoginPage.seedCredentials();
      await login.loginWithCredentials(email, "definitely-wrong-password-xyz");
      await login.expectInvalidCredentials();
    });
  });

  test.describe("P0-02 project creation", () => {
    test("P0-02 creates project without false error or refresh", async ({ page }) => {
      const login = new LoginPage(page);
      const projects = new ProjectsPage(page);
      await login.loginAsSeedUser();

      const name = uniqueProjectName("p0-create");
      await projects.openNewProjectDialog();
      const dialog = page.getByTestId("new-project-dialog").or(page.getByRole("dialog"));
      await dialog.locator("#proj-name").fill(name);
      await dialog.getByRole("button", { name: /^(create|crear)$/i }).click();

      await expect(dialog).toBeHidden({ timeout: 30_000 });
      await projects.expectNoCreateError();
      await projects.expectProjectActive(name);

      // No manual refresh: URL should remain on projects and card stays visible after short wait.
      await page.waitForTimeout(800);
      await projects.expectNoCreateError();
      await projects.expectProjectActive(name);
      expect(page.url()).toMatch(/\/en\/projects/);
    });
  });

  test.describe("P0-03 configuration panel", () => {
    test("P0-03 chat configuration panel opens with preset and compact summary", async ({ page }) => {
      test.setTimeout(120_000);
      const login = new LoginPage(page);
      const projects = new ProjectsPage(page);
      const chat = new ChatPage(page);

      await login.loginAsSeedUser();
      const projectId = await projects.createProject(uniqueProjectName("p0-config"));
      await chat.openForProject(projectId);
      await chat.createConversation(projectId);

      const panel = await chat.openConfigurationPanel();
      await expect(panel.getByTestId("chat-config-compact-summary")).toBeVisible({ timeout: 15_000 });
      await expect(panel.getByTestId("chat-config-summary-preset")).toBeVisible();
      await expect(panel.getByTestId("chat-preset-select")).toBeVisible({ timeout: 15_000 });
    });
  });

  test.describe("P0-04 admin panel smoke", () => {
    test("P0-04 admin model catalog loads when admin user available", async ({ page, request }) => {
      test.skip(
        process.env.E2E_ADMIN_ENABLED !== "1",
        "Set E2E_ADMIN_ENABLED=1 when backend runs with profile=e2e (admin seeded).",
      );
      let probeStatus = 0;
      try {
        const probe = await request.post(productApiUrl("/auth/login"), {
          data: { email: adminEmail(), password: adminPassword() },
        });
        probeStatus = probe.status();
      } catch {
        test.skip(true, "Admin login probe could not reach backend (connection refused or TLS error).");
      }
      test.skip(probeStatus !== 200, `Admin login probe failed (HTTP ${probeStatus}).`);

      const login = new LoginPage(page);
      const admin = new AdminPage(page);
      await login.loginAsAdmin();
      await admin.open();
      await admin.expectModelCatalogLoaded();
    });
  });

  test.describe("P0-05 document upload ingest smoke", () => {
    test("P0-05 upload acta fixture reaches READY", async ({ page }) => {
      test.setTimeout(180_000);
      const login = new LoginPage(page);
      const projects = new ProjectsPage(page);
      const documents = new DocumentsPage(page);

      await login.loginAsSeedUser();
      await projects.createProject(uniqueProjectName("p0-doc"));
      await documents.openFromSidebar();
      await documents.expectLoaded();
      await documents.uploadFile(actaKnowledgeBaseFilePath());
      await documents.waitForReady("acta-24-02-2025.txt", 120_000);
    });
  });

  test.describe("P0-06 RAG chat smoke @p0-app-rag", () => {
    test("P0-06 Demo_Best acta question returns answer and sources @p0-app-rag", async ({ page }) => {
      test.setTimeout(420_000);
      const login = new LoginPage(page);
      const projects = new ProjectsPage(page);
      const documents = new DocumentsPage(page);
      const chat = new ChatPage(page);

      await login.loginAsSeedUser();
      await projects.createProject(uniqueProjectName("p0-rag"));
      await documents.openFromSidebar();
      await documents.uploadFile(actaKnowledgeBaseFilePath());
      await documents.waitForReady("acta-24-02-2025.txt", 180_000);

      const projectId = new URL(page.url()).searchParams.get("projectId");
      if (!projectId) {
        throw new Error(`Expected projectId after document upload, got ${page.url()}`);
      }
      await chat.openForProject(projectId);
      await chat.createConversation(projectId);

      const panel = await chat.openConfigurationPanel();
      await chat.selectPresetByPattern(panel, /demo_best/i);
      await page.keyboard.press("Escape").catch(() => undefined);

      await chat.sendMessage("dime los lugares donde se han realizado las actas", 45_000);
      await chat.expectAnswerVisible(240_000);
      await chat.expectSourcesVisible(60_000);
      await expect(page.getByText(/could not send message/i)).toHaveCount(0);
    });
  });

  test.describe("P0-07 evaluation pages load", () => {
    test("P0-07 RAG LLM and embedding evaluation pages load", async ({ page }) => {
      const login = new LoginPage(page);
      const lab = new LabPage(page);
      await login.loginAsSeedUser();

      for (const segment of ["rag", "llm", "embedding"] as const) {
        await lab.openEvaluation(segment);
      }
    });
  });

  test.describe("P0-08 classifier training smoke", () => {
    test("P0-08 classifier lab exposes train controls or configured-unavailable state", async ({ page }) => {
      const login = new LoginPage(page);
      const lab = new LabPage(page);
      await login.loginAsSeedUser();
      await lab.openClassifier();
      await lab.expectClassifierTrainReadyOrUnavailable();
    });
  });
});
