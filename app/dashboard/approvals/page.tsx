'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Header from '../../../components/Header'
import Button from '../../../components/ui/Button'
import Card from '../../../components/ui/Card'
import { approveRegistration, getInbox, rejectRegistration } from '../../../lib/api/approvals'

/**
 * Everything waiting on this user. The backend already narrows it to steps their roles can decide,
 * so anything shown here is genuinely actionable.
 */
export default function ApprovalsInboxPage() {
  const queryClient = useQueryClient()
  const [error, setError] = useState('')
  const [rejectingId, setRejectingId] = useState<string | null>(null)
  const [reason, setReason] = useState('')

  const inboxQuery = useQuery({ queryKey: ['approvals-inbox'], queryFn: getInbox })

  const refresh = async () => {
    setError('')
    setRejectingId(null)
    setReason('')
    await queryClient.invalidateQueries({ queryKey: ['approvals-inbox'] })
  }
  const report = (cause: unknown) =>
    setError(cause instanceof Error ? cause.message : 'Action failed')

  const approve = useMutation({
    mutationFn: (registrationId: string) => approveRegistration(registrationId),
    onSuccess: refresh,
    onError: report,
  })

  const reject = useMutation({
    mutationFn: ({ id, comment }: { id: string; comment: string }) => rejectRegistration(id, comment),
    onSuccess: refresh,
    onError: report,
  })

  const items = inboxQuery.data ?? []

  return (
    <>
      <Header />
      <main className="min-h-screen bg-dark-bg">
        <div className="border-b border-dark-border bg-gradient-to-b from-dark-surface to-dark-bg py-8 sm:py-12">
          <div className="mx-auto max-w-5xl px-6 sm:px-8">
            <h1 className="text-3xl font-bold text-white sm:text-4xl">Approvals</h1>
            <p className="mt-2 text-gray-400">
              {items.length === 0
                ? 'Nothing is waiting on you.'
                : `${items.length} ${items.length === 1 ? 'entry needs' : 'entries need'} your decision.`}
            </p>
          </div>
        </div>

        <div className="mx-auto max-w-5xl px-6 py-8 sm:px-8 sm:py-12">
          {error ? (
            <div className="mb-6 rounded-lg border border-red-500/30 bg-red-500/5 px-4 py-3 text-sm text-red-300">
              {error}
            </div>
          ) : null}

          {inboxQuery.isLoading ? (
            <p className="text-gray-400">Loading…</p>
          ) : items.length === 0 ? (
            <Card className="text-center">
              <div className="mb-3 text-4xl">✅</div>
              <p className="text-gray-400">
                Your inbox is empty. Entries appear here when they reach a step you can decide.
              </p>
            </Card>
          ) : (
            <div className="space-y-4">
              {items.map((item) => {
                const isRejecting = rejectingId === item.registrationId
                const isBusy =
                  (approve.isPending && approve.variables === item.registrationId) ||
                  (reject.isPending && reject.variables?.id === item.registrationId)

                return (
                  <Card key={item.registrationId}>
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <p className="font-semibold text-white">{item.participantName}</p>
                        <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-xs text-gray-500">
                          <span>
                            Submitted{' '}
                            {new Date(item.submittedAt).toLocaleString('en-IN', {
                              day: 'numeric',
                              month: 'short',
                              hour: 'numeric',
                              minute: '2-digit',
                            })}
                          </span>
                          {item.currentStepRole ? <span>Decided by {item.currentStepRole}</span> : null}
                        </div>
                      </div>
                      {/* Where it is in the chain — the question workflow state exists to answer. */}
                      <span className="whitespace-nowrap rounded-full border border-amber-500/40 bg-amber-500/20 px-3 py-1 text-xs font-semibold text-amber-300">
                        {item.progressLabel}
                      </span>
                    </div>

                    {isRejecting ? (
                      <div className="mt-4 border-t border-dark-border pt-4">
                        <label
                          htmlFor={`reason-${item.registrationId}`}
                          className="mb-2 block text-sm font-medium text-gray-300"
                        >
                          Why is this being rejected?
                          <span className="ml-1 text-accent-pink">*</span>
                        </label>
                        <textarea
                          id={`reason-${item.registrationId}`}
                          rows={3}
                          value={reason}
                          onChange={(event) => setReason(event.target.value)}
                          placeholder="e.g., Age documents missing"
                          className="w-full rounded-lg border border-dark-border bg-dark-surface px-4 py-3 text-white outline-none transition placeholder:text-gray-500 focus:border-accent-purple focus:ring-2 focus:ring-accent-purple/20"
                        />
                        <p className="mt-1.5 text-xs text-gray-500">
                          The entrant is told why, so make it something they can act on.
                        </p>
                        <div className="mt-3 flex flex-wrap gap-3">
                          <Button
                            className="btn-gradient"
                            disabled={!reason.trim() || isBusy}
                            onClick={() =>
                              reject.mutate({ id: item.registrationId, comment: reason.trim() })
                            }
                          >
                            {isBusy ? 'Rejecting…' : 'Confirm rejection'}
                          </Button>
                          <Button
                            variant="ghost"
                            onClick={() => {
                              setRejectingId(null)
                              setReason('')
                            }}
                          >
                            Cancel
                          </Button>
                        </div>
                      </div>
                    ) : (
                      <div className="mt-4 flex flex-wrap gap-3 border-t border-dark-border pt-4">
                        <Button
                          className="btn-gradient px-5 py-2 text-sm"
                          disabled={isBusy}
                          onClick={() => approve.mutate(item.registrationId)}
                        >
                          {isBusy ? 'Working…' : 'Approve'}
                        </Button>
                        <Button
                          variant="secondary"
                          className="px-5 py-2 text-sm"
                          disabled={isBusy}
                          onClick={() => {
                            setRejectingId(item.registrationId)
                            setReason('')
                          }}
                        >
                          Reject
                        </Button>
                        <Link
                          href={`/dashboard/competition?id=${item.competitionId}`}
                          className="self-center text-sm text-gray-400 transition hover:text-accent-purple"
                        >
                          View competition
                        </Link>
                      </div>
                    )}
                  </Card>
                )
              })}
            </div>
          )}
        </div>
      </main>
    </>
  )
}
