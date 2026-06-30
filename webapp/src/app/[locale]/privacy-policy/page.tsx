type PrivacyPolicyPageProps = {
  params: Promise<{ locale: string }>;
};

export default async function PrivacyPolicyPage({ params }: PrivacyPolicyPageProps) {
  const { locale } = await params;
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-8">
      <h1 className="font-semibold text-2xl">Privacy Policy</h1>
      <p className="text-sm text-muted-foreground">
        Version: 2026-06-21 · Last updated: 21 June 2026 · Locale: {locale}
      </p>
      <p>
        This Privacy Policy explains how personal data is processed in RAG Console, a web application
        developed as part of an academic Final Degree Project. The application allows users to configure
        intelligent assistants based on language models, upload documents, consult them through natural
        language questions and evaluate different assistant configurations.
      </p>
      <p>
        The application may process account data, authentication data, project data, uploaded documents,
        extracted text, document metadata, conversation data, evaluation results and technical diagnostic
        information. This data is processed to create and manage user accounts, authenticate users,
        maintain secure sessions, organise projects, process uploaded documents, generate document-based
        answers, store conversations, configure assistant behaviour, execute evaluations, detect errors
        and support academic validation of the system.
      </p>
      <p>
        Users are responsible for ensuring that they have the right to upload and process the documents
        they add to the application. Uploaded documents may be processed to extract text, metadata and
        searchable representations. Assistant answers are generated from the available document content
        and selected configuration, but they may contain mistakes, incomplete interpretations or
        unsupported statements. Important information should always be verified against the original
        documents.
      </p>
      <p>
        The system may use local or configured language model services to generate answers and
        embeddings. If external model providers are enabled in the future, users must be informed before
        document content or questions are sent to those providers.
      </p>
      <p>
        Personal data is kept only for as long as necessary to provide the application features, support
        academic evaluation, maintain security or preserve justified technical evidence. Users may
        request access, correction, deletion, restriction, portability where applicable and objection
        where legally applicable.
      </p>
      <p>
        The application includes technical measures such as authentication, protected routes, restricted
        administrative access and user/project separation. However, this academic version is not
        certified as a production-grade system and must not be presented as a complete legal compliance
        solution without further legal, organisational and security review.
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
