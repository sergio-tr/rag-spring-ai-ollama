import { routing } from "@/i18n/routing";
import { redirect } from "next/navigation";

type Props = Readonly<{ params: Promise<{ slug: string[] }> }>;

export default async function BareLabSubpathRedirectPage({ params }: Props) {
  const { slug } = await params;
  const rest = Array.isArray(slug) && slug.length > 0 ? `/${slug.map(encodeURIComponent).join("/")}` : "";
  redirect(`/${routing.defaultLocale}/lab${rest}`);
}

