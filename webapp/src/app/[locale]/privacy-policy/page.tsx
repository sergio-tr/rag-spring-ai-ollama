type PrivacyPolicyPageProps = {
  params: Promise<{ locale: string }>;
};

export default async function PrivacyPolicyPage({ params }: PrivacyPolicyPageProps) {
  const { locale } = await params;
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-8">
      <h1 className="font-semibold text-2xl">Privacy Policy</h1>
      <p className="text-sm text-muted-foreground">Locale: {locale}</p>
      <p>
        This policy explains what account data is collected for authentication flows, including email
        verification and password reset operations.
      </p>
      <p>
        Legal text is a baseline placeholder for engineering integration and must be reviewed by legal
        counsel before production release.
      </p>
    </main>
  );
}
