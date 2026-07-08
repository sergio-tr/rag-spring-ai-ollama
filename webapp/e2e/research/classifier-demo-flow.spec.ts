import { expect, test } from "@playwright/test";
import { uniqueProjectName } from "../fixtures/projects";
import {
  activateClassifierModel,
  classifyInLab,
  evaluateClassifierModel,
  openLabClassifierPage,
  trainClassifierModel,
} from "../support/classifier-closure-helpers";
import {
  createAndActivateProject,
  createNewChatConversation,
  loginAsSeedUser,
  openChatConfigurationPanel,
  openChatForProject,
} from "../support/helpers";

/**
 * Research/demo variant - prefer {@code e2e/closure/classifier-train-evaluate-activate.spec.ts} for Wave 3 gate.
 */
test.describe.serial("Demo classifier flow @fullstack @demoHeavy", () => {
  test("train, evaluate, activate, classify, and expose selected model for chat @fullstack", async ({ page }) => {
    test.setTimeout(360_000);
    await loginAsSeedUser(page);
    const projectId = await createAndActivateProject(page, uniqueProjectName("e2e-classifier-demo"));
    await openLabClassifierPage(page);
    const { modelName, modelTag } = await trainClassifierModel(page);
    await evaluateClassifierModel(page, modelTag);
    await activateClassifierModel(page, modelName);
    await classifyInLab(page, modelTag, "How many meetings mention the lift?");

    await openChatForProject(page, projectId);
    await createNewChatConversation(page, { projectId });
    const panel = await openChatConfigurationPanel(page);
    const classifierSelect = panel.getByTestId("chat-classifier-select");
    await expect(classifierSelect.locator(`option[value="${modelTag}"]`)).toHaveCount(1, { timeout: 30_000 });
  });
});
