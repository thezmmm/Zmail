import type { Metadata } from "next";
import "./globals.css";
import QueryProvider from "@/providers/query-provider";
import { Toaster } from "sonner";

export const metadata: Metadata = {
  title: "Zmail",
  description: "AI-powered email agent",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="bg-gray-950 text-gray-100 antialiased">
        <QueryProvider>{children}</QueryProvider>
        <Toaster
          theme="dark"
          position="bottom-right"
          toastOptions={{ duration: 4000 }}
        />
      </body>
    </html>
  );
}