import { describe, expect, it } from "vitest";
import {
  defaultParametersFromCatalog,
  formatRoleSummary,
  mergeTaskModelRolesIntoConfig,
  parametersToMap,
  readTaskModelRolesFromConfig,
  resetRoleToDefaults,
  TASK_LLM_OVERRIDES_KEY,
} from "./task-generation-parameters";

const catalogTasks = [
  {
    id: "final_answer",
    role: "FINAL_ANSWER",
    label: "Final answer",
    inheritsMainModelByDefault: true,
    defaultModelId: "gemma4:12b",
    defaultParameters: {
      temperature: 0.1,
      topP: 1,
      maxTokens: 1024,
      responseFormat: "text",
      think: false,
    },
  },
  {
    id: "query_rewrite",
    role: "QUERY_REWRITE",
    label: "Query rewrite",
    inheritsMainModelByDefault: false,
    defaultModelId: "qwen3.5:9b",
    defaultParameters: {
      temperature: 0,
      topP: 0.8,
      maxTokens: 256,
      responseFormat: "json_object",
      stopSequences: ["END"],
      think: true,
      timeoutSeconds: 30,
    },
  },
];

describe("task-generation-parameters", () => {
  it("reads roles from config overrides with inheritance flags", () => {
    const roles = readTaskModelRolesFromConfig(
      {
        [TASK_LLM_OVERRIDES_KEY]: {
          query_rewrite: {
            inheritModel: false,
            model: "custom-model",
            inheritParameters: false,
            temperature: 0.4,
            topP: 0.7,
          },
        },
      },
      catalogTasks,
    );

    const rewrite = roles.find((role) => role.roleId === "query_rewrite");
    expect(rewrite?.inheritModel).toBe(false);
    expect(rewrite?.modelId).toBe("custom-model");
    expect(rewrite?.inheritParameters).toBe(false);
    expect(rewrite?.parameters.temperature).toBe(0.4);
    expect(rewrite?.hasOverride).toBe(true);
  });

  it("maps non-default parameters and clamps ranges", () => {
    expect(
      parametersToMap({
        topP: 2,
        maxTokens: 0,
        presencePenalty: -3,
        frequencyPenalty: 3,
        responseFormat: "text",
        think: false,
      }),
    ).toEqual({
      topP: 1,
      maxTokens: 1,
      presencePenalty: -2,
      frequencyPenalty: 2,
    });
  });

  it("merges role overrides into config and removes empty map", () => {
    const base = { llmModel: "main" };
    const withOverride = mergeTaskModelRolesIntoConfig(base, [
      {
        role: "QUERY_REWRITE",
        roleId: "query_rewrite",
        label: "Query rewrite",
        inheritModel: false,
        modelId: "custom",
        inheritParameters: false,
        parameters: { temperature: 0.2 },
        hasOverride: true,
      },
    ]);
    expect(withOverride[TASK_LLM_OVERRIDES_KEY]).toBeDefined();

    const cleared = mergeTaskModelRolesIntoConfig(withOverride, [
      {
        role: "QUERY_REWRITE",
        roleId: "query_rewrite",
        label: "Query rewrite",
        inheritModel: true,
        modelId: "qwen3.5:9b",
        inheritParameters: true,
        parameters: defaultParametersFromCatalog(catalogTasks[1].defaultParameters),
        hasOverride: false,
      },
    ]);
    expect(cleared[TASK_LLM_OVERRIDES_KEY]).toBeUndefined();
    expect(cleared.llmModel).toBe("main");
  });

  it("resets a role to catalog defaults", () => {
    const reset = resetRoleToDefaults(
      {
        role: "QUERY_REWRITE",
        roleId: "query_rewrite",
        label: "Query rewrite",
        inheritModel: false,
        modelId: "custom",
        inheritParameters: false,
        parameters: { temperature: 0.9 },
        hasOverride: true,
      },
      catalogTasks[1],
    );
    expect(reset.inheritModel).toBe(false);
    expect(reset.modelId).toBe("qwen3.5:9b");
    expect(reset.parameters.responseFormat).toBe("json_object");
    expect(reset.hasOverride).toBe(false);
  });

  it("formats role summary for display", () => {
    const summary = formatRoleSummary({
      role: "FINAL_ANSWER",
      roleId: "final_answer",
      label: "Final answer",
      inheritModel: true,
      modelId: "gemma4:12b",
      inheritParameters: true,
      parameters: { temperature: 0.1, maxTokens: 1024, responseFormat: "text" },
    });
    expect(summary).toContain("inherits model");
    expect(summary).toContain("temp 0.1");
    expect(summary).toContain("max 1024");
  });

  it("uses catalog fallbacks when defaults are missing", () => {
    expect(defaultParametersFromCatalog()).toEqual({ think: false, responseFormat: "text" });
  });

  it("skips roles that match catalog defaults without overrides", () => {
    const merged = mergeTaskModelRolesIntoConfig(
      { llmModel: "main" },
      [
        {
          role: "QUERY_REWRITE",
          roleId: "query_rewrite",
          label: "Query rewrite",
          inheritModel: false,
          modelId: "qwen3.5:9b",
          inheritParameters: false,
          parameters: defaultParametersFromCatalog(catalogTasks[1].defaultParameters),
          hasOverride: false,
        },
      ],
    );
    expect(merged[TASK_LLM_OVERRIDES_KEY]).toBeUndefined();
  });

  it("maps optional generation parameters when provided", () => {
    expect(
      parametersToMap({
        responseFormat: "json_object",
        stopSequences: ["END", "STOP"],
        think: true,
        timeoutSeconds: 12.8,
        seed: 7.2,
      }),
    ).toEqual({
      responseFormat: "json_object",
      stopSequences: ["END", "STOP"],
      think: true,
      timeoutSeconds: 12,
      seed: 7,
    });
  });

  it("hides internal lab-only roles from settings rows", () => {
    const roles = readTaskModelRolesFromConfig({}, [
      ...catalogTasks,
      {
        id: "llm_baseline_evaluation",
        role: "LLM_BASELINE_EVALUATION",
        label: "LLM baseline evaluation",
        inheritsMainModelByDefault: true,
        settingsVisible: false,
        defaultModelId: "gemma4:12b",
        defaultParameters: { temperature: 0.1 },
      },
    ]);
    expect(roles.map((r) => r.roleId)).not.toContain("llm_baseline_evaluation");
    expect(roles.map((r) => r.roleId)).toContain("final_answer");
  });
});
