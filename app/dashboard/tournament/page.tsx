'use client'

import { Suspense } from 'react'
import { useSearchParams } from 'next/navigation'
import TournamentDetailView from './TournamentDetailView'

/**
 * Reads the tournament id from the query string rather than a path segment, for the same reason as
 * the public page: `output: 'export'` cannot prerender routes whose ids are created at runtime.
 */
function TournamentDetailPageInner() {
  const searchParams = useSearchParams()
  return <TournamentDetailView tournamentId={searchParams.get('id') ?? ''} />
}

export default function TournamentDetailPage() {
  return (
    <Suspense fallback={null}>
      <TournamentDetailPageInner />
    </Suspense>
  )
}
