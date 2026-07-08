import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { LabEmbeddingRetrievalParametersSection } from "./lab-embedding-retrieval-parameters-section";
import { IntlTestProvider } from "@/test-utils/intl";

describe("LabRagExplicitRetrievalParams", () => {
  it("shows explicit campaign retrieval hint for RAG variant", () => {
    render(
      <IntlTestProvider>
        <LabEmbeddingRetrievalParametersSection
          variant="rag"
          value={{}}
          onChange={() => {}}
          selectedModels={[]}
        />
      </IntlTestProvider>,
    );
    expect(screen.getByTestId("lab-rag-explicit-retrieval-hint")).toBeTruthy();
    expect(screen.getByTestId("lab-rag-explicit-retrieval-hint").textContent).toContain("explicit campaign");
  });
});
