import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlTestProvider } from "@/test-utils/intl";

const putUser = vi.fn();
const putProject = vi.fn();
const delProject = vi.fn();
const mutateState = {
  putUserPending: false,
  putUserError: false,
  putProjectPending: false,
  putProjectError: false,
  delProjectPending: false,
  delProjectError: false,
};

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
  usePutUserRagConfig: () => ({
    mutateAsync: putUser,
    isPending: mutateState.putUserPending,
    isError: mutateState.putUserError,
  }),
  usePutProjectRagConfig: () => ({
    mutateAsync: putProject,
    isPending: mutateState.putProjectPending,
    isError: mutateState.putProjectError,
  }),
  useDeleteProjectRagConfig: () => ({
    mutateAsync: delProject,
    isPending: mutateState.delProjectPending,
    isError: mutateState.delProjectError,
  }),
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
    mutateState.putUserPending = false;
    mutateState.putUserError = false;
    mutateState.putProjectPending = false;
    mutateState.putProjectError = false;
    mutateState.delProjectPending = false;
    mutateState.delProjectError = false;
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

  it("shows loading state", () => {
    mockSchemaState.isLoading = true;
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it("shows load error when schema query fails", () => {
    mockSchemaState.isError = true;
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(/could not load configuration/i);
  });

  it("shows empty schema message when no fields exist", () => {
    mockSchemaState.data = { fields: [] };
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );
    expect(screen.getByText(/no configurable fields/i)).toBeInTheDocument();
  });

  it("shows product-oriented project description without legacy HTTP copy in the card body", async () => {
    mockSchemaState.data = { fields: [{ key: "topK", type: "integer", userEditable: true, min: 1, max: 50 }] };
    mockProjectState.data = { topK: 3 };
    render(
      <IntlTestProvider>
        <RagConfigForm mode="project" projectId="p1" />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByLabelText(/passages to retrieve/i)).toBeInTheDocument());
    expect(screen.queryByText(/Project overrides from GET/i)).not.toBeInTheDocument();
    expect(screen.getByText(/These values apply only while this project is selected/i)).toBeInTheDocument();
  });

  it("surfaces REST paths only inside the optional technical reference section", async () => {
    mockSchemaState.data = { fields: [{ key: "topK", type: "integer", userEditable: true, min: 1, max: 50 }] };
    mockProjectState.data = { topK: 3 };
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <RagConfigForm mode="project" projectId="proj-77" />
      </IntlTestProvider>,
    );
    await waitFor(() => expect(screen.getByText(/Technical reference/i)).toBeInTheDocument());
    await user.click(screen.getByText(/Technical reference \(API\)/i));
    expect(screen.getByText(/\/config\/project\/proj-77/)).toBeInTheDocument();
  });

  it("confirms before clearing project overrides and preserves DELETE semantics", async () => {
    mockSchemaState.data = { fields: [{ key: "temperature", type: "number", userEditable: true }] };
    mockProjectState.data = { temperature: 0.4 };
    delProject.mockResolvedValueOnce({});
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <RagConfigForm mode="project" projectId="p1" />
      </IntlTestProvider>,
    );

    await user.click(screen.getByRole("button", { name: /remove project overrides/i }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /^remove overrides$/i }));
    await waitFor(() => {
      expect(delProject).toHaveBeenCalled();
    });
  });

  it("merges unknown keys into project PUT payload like before", async () => {
    mockSchemaState.data = {
      fields: [{ key: "topK", type: "integer", userEditable: true, min: 1, max: 50 }],
    };
    mockProjectState.data = { topK: 4, futureClientFlag: true };
    putProject.mockResolvedValueOnce({});
    const user = userEvent.setup();
    render(
      <IntlTestProvider>
        <RagConfigForm mode="project" projectId="p9" />
      </IntlTestProvider>,
    );

    const input = await screen.findByLabelText(/passages to retrieve/i);
    await user.clear(input);
    await user.type(input, "6");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    await waitFor(() => expect(putProject).toHaveBeenCalled());
    expect(putProject).toHaveBeenCalledWith(
      expect.objectContaining({ topK: 6, futureClientFlag: true }),
    );
  });

  it("shows save error when mutate hook is in error state", () => {
    mutateState.putUserError = true;
    mockSchemaState.data = { fields: [{ key: "topK", type: "integer", userEditable: true }] };
    render(
      <IntlTestProvider>
        <RagConfigForm mode="user" />
      </IntlTestProvider>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(/could not save configuration/i);
  });
});

