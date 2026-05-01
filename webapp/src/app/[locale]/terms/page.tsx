type TermsPageProps = {
  params: Promise<{ locale: string }>;
};

export default async function TermsPage({ params }: TermsPageProps) {
  const { locale } = await params;
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-8">
      <h1 className="font-semibold text-2xl">Terms and Conditions</h1>
      <p className="text-sm text-muted-foreground">Locale: {locale}</p>
      <p>
        These terms define acceptable use of the product and baseline responsibilities for account
        ownership.
      </p>
      <p>
        This document is a technical placeholder and must be replaced by approved legal text before
        production use.
      </p>
    </main>
  );
}
