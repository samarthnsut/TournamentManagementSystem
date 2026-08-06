'use client'

import { Suspense } from 'react'
import { useSearchParams } from 'next/navigation'
import CompetitionDetailView from './CompetitionDetailView'

function CompetitionDetailPageInner() {
  const searchParams = useSearchParams()
  return (
    <CompetitionDetailView
      competitionId={searchParams.get('id') ?? ''}
      tournamentId={searchParams.get('tournamentId') ?? ''}
    />
  )
}

export default function CompetitionDetailPage() {
  return (
    <Suspense fallback={null}>
      <CompetitionDetailPageInner />
    </Suspense>
  )
}
