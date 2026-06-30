type TermsPageProps = {
  params: Promise<{ locale: string }>;
};

export default async function TermsPage({ params }: TermsPageProps) {
  const { locale } = await params;
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-8">
      <h1 className="font-semibold text-2xl">Terms and Conditions</h1>
      <p className="text-sm text-muted-foreground">
        Version: 2026-06-21 · Last updated: 21 June 2026 · Locale: {locale}
      </p>
      <p>
        These Terms and Conditions regulate the use of RAG Console, a web application developed as part
        of an academic Final Degree Project. By creating an account or using the application, the user
        accepts these terms.
      </p>
      <p>
        RAG Console allows users to create projects, upload documents, configure assistant behaviour, ask
        questions about uploaded documents and evaluate different assistant configurations. The
        application is intended for academic, experimental and demonstrative use. It is not provided as
        a commercial production service.
      </p>
      <p>
        Users must provide accurate account information, keep their credentials secure and use the
        application lawfully and responsibly. Users must not upload documents they are not authorised to
        use, upload unlawful or harmful content, attempt to access other users&apos; projects or
        restricted administrative areas, bypass security mechanisms, interfere with the system operation
        or use the assistant to generate harmful or misleading content.
      </p>
      <p>
        Users are responsible for the documents they upload. Uploaded documents may be processed to
        extract text, metadata and searchable representations required by the assistant. The original
        documents remain the reference source.
      </p>
      <p>
        Assistant answers may be useful, but they may also be incomplete, inaccurate or unsupported by
        the source documents. The application does not provide legal, financial, administrative, medical
        or professional advice. Any relevant decision must be checked against the original documents
        and, where necessary, reviewed by a qualified person.
      </p>
      <p>
        The Research Lab area is used to compare models, embeddings, presets and assistant
        configurations. Evaluation results are intended for analysis and academic documentation, not as a
        guarantee that a configuration will always provide correct answers.
      </p>
      <p>
        The application may be modified, interrupted or temporarily unavailable due to maintenance,
        development changes, infrastructure limitations or academic testing. No continuous availability,
        production service level or commercial support commitment is provided.
      </p>
      <p>
        Some areas are restricted to administrator users. Ordinary users must not attempt to access
        restricted administrative features.
      </p>
      <p>
        The processing of personal data is described in the Privacy Policy. Users must read and accept the
        Privacy Policy before creating an account.
      </p>
      <p>
        Contact:{" "}
        <a className="underline" href="mailto:support.rag@gmail.es">
          support.rag@gmail.es
        </a>
      </p>
    </main>
  );
}
