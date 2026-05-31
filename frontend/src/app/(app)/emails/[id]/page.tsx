import EmailDetailClient from './EmailDetailClient'

// IDs are not known at build time; client-side router handles navigation in Tauri.
export const dynamicParams = false

export function generateStaticParams() {
  return []
}

export default function EmailDetailPage() {
  return <EmailDetailClient />
}