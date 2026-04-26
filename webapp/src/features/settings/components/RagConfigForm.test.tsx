import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";

const putUser = vi.fn();
const putProject = vi.fn();
const delProject = vi.fn();

type ConfigField = {
  key: string;
  type: "integer" | "number" | "boolean" | "string";
  userEditable: boolean;
  min?: number;
  max?: number;
};

type QueryState<T> = {
  isLoading: boolean;
  isError: boolean;
  data: T;
};

const mockSchemaState: QueryState<{ fields: ConfigField[] }> = {
  isLoading: false,
  isError: false,
  data: { fields: [] },
};
const mockUserState: QueryState<Record<string, unknown>> = { isLoading: false, isError: false, data: {} };
const mockProjectState: QueryState<Record<string, unknown>> = { isLoading: false, isError: false, data: {} };

vi.mock("@/features/settings/hooks/use-rag-config", () => ({
  useConfigSchemaQuery: () => mockSchemaState,
  useUserRagConfigQuery: () => mockUserState,
  useProjectRagConfigQuery: () => mockProjectState,
  usePutUserRagConfig: () => ({ mutateAsync: putUser, isPending: false, isError: false }),
  usePutProjectRagConfig: () => ({ mutateAsync: putProject, isPending: false, isError: false }),
  useDeleteProjectRagConfig: () => ({ mutateAsync: delProject, isPending: false }),
}));

import { RagConfigForm } from "./RagConfigForm";

describe("RagConfigForm", () => {
  beforeEach(() => {
    putUser.mockReset();
    putProject.mockReset();
    delProject.mockReset();
    mockSchemaState.isLoading = false;
    mockSchemaState.isError = false;
    mockSchemaState.data = { fields: [] };
    mockUserState.isLoading = false;
    mockUserState.isError = false;
    mockUserState.data = {};
    mockProjectState.isLoading = false;
    mockProjectState.isError = false;
    mockProjectState.data = {};
  });

  it("renders no-active-project message in project mode without projectId", () => {
    render(
      <IntlTestProvider>
        <RagConfigForm mode="project" projectId={undefined} />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("submits user config form when schema has editable fields", async () => {
    mockSchemaState.data = {
      fields: [
        { key: "topK", type: "integer", userEditable: true, min: 1, max: 50 },
        { key: "enableFoo", type: "boolean", userEditable: true },
      ],
    };
    mockUserState.data = { topK: 5, enableFoo: false };
    putUser.mockResolvedValueOnce({});

    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );

    const topK = screen.getByLabelText("topK");
    await user.clear(topK);
    await user.type(topK, "7");
    await user.click(screen.getByLabelText("enableFoo"));
    await user.click(screen.getByRole("button", { name: /save/i }));

    expect(putUser).toHaveBeenCalled();
  });
});

