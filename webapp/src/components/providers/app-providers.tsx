"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "next-themes";
import { useState } from "react";
import { registerAppQueryClient } from "@/lib/query-client-registry";

type AppProvidersProps = {
  children: React.ReactNode;
};

/** Theme + data fetching. Intl context lives in `NextIntlClientProvider` in the server `[locale]/layout`. */
export function AppProviders({ children }: AppProvidersProps) {
  const [queryClient] = useState(() => {
    const client = new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: 60 * 1000,
          retry: 1,
        },
      },
    });
    registerAppQueryClient(client);
    return client;
  });

  return (
    <ThemeProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
    >
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </ThemeProvider>
  );
}
