import { AUTH_ACCESS_COOKIE_NAME } from "@/lib/auth-cookie";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";

type HomePageProps = {
  params: Promise<{ locale: string }>;
};

export default async function HomePage({ params }: HomePageProps) {
  const { locale } = await params;
  const jar = await cookies();
  if (jar.get(AUTH_ACCESS_COOKIE_NAME)?.value) {
    redirect(`/${locale}/projects`);
  }
  redirect(`/${locale}/login`);
}
