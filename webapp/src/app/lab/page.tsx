import { routing } from "@/i18n/routing";
import { redirect } from "next/navigation";

export default function BareLabRedirectPage() {
  redirect(`/${routing.defaultLocale}/lab`);
}

