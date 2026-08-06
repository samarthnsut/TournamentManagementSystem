'use client'

import { Suspense } from 'react'
import { useSearchParams } from 'next/navigation'
import PublicTournamentView from './PublicTournamentView'

/**
 * The public tournament page.
 *
 * The design docs specify the URL `/t/{slug}`, but this project builds with `output: 'export'`,
 * and a static export must know every dynamic route at build time — which is impossible for slugs
 * users create at runtime. So the page reads the slug from the query string instead and works
 * under both `next dev` and the static export. Restoring the documented path form needs either a
 * server-rendered deploy or a host rewrite from /t/{slug} to /t?slug={slug}.
 */
function PublicTournamentPageInner() {
  const searchParams = useSearchParams()
  const slug = searchParams.get('slug') ?? ''

  return <PublicTournamentView slug={slug} />
}

export default function PublicTournamentPage() {
  return (
    <Suspense fallback={null}>
      <PublicTournamentPageInner />
    </Suspense>
  )
}
