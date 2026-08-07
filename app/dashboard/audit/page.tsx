'use client'

import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import SettingsShell from '../../../components/settings/SettingsShell'
import Card from '../../../components/ui/Card'
import Input from '../../../components/ui/Input'
import Select from '../../../components/ui/Select'
import { getAuditLog, type AuditLogEntry } from '../../../lib/api/audit'

/** Colour carries emphasis only; the action text is what actually says what happened. */
function toneFor(action: string) {
  if (action.includes('delete') || action.includes('revoke') || action.includes('reject')) {
    return 'border-red-500/40 bg-red-500/10 text-red-300'
  }
  if (action.includes('create') || action.includes('grant') || action.includes('approve')) {
    return 'border-green-500/40 bg-green-500/10 text-green-300'
  }
  return 'border-dark-border bg-white/5 text-gray-300'
}

/** Only the fields that differ, so a diff of a wide entity stays readable. */
function changedKeys(before: Record<string, unknown> | null, after: Record<string, unknown> | null) {
  const keys = new Set([...Object.keys(before ?? {}), ...Object.keys(after ?? {})])
  return [...keys].filter(
    (key) => JSON.stringify(before?.[key]) !== JSON.stringify(after?.[key]),
  )
}

function render(value: unknown) {
  if (value === undefined) return '—'
  if (value === null) return 'null'
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}

function Row({ entry }: { entry: AuditLogEntry }) {
  const [isOpen, setIsOpen] = useState(false)
  const changed = changedKeys(entry.beforeState, entry.afterState)

  return (
    <Card>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className={`rounded-full border px-3 py-1 text-xs font-semibold ${toneFor(entry.action)}`}>
              {entry.action}
            </span>
            <span className="text-sm text-gray-400">{entry.entityType}</span>
          </div>
          <p className="mt-2 text-sm text-gray-300">
            <span className="font-medium text-white">{entry.actorName ?? 'System'}</span>
            {entry.ipAddress ? <span className="text-gray-600"> from {entry.ipAddress}</span> : null}
          </p>
        </div>
        <div className="text-right">
          <p className="whitespace-nowrap text-xs text-gray-500">
            {new Date(entry.timestamp).toLocaleString('en-IN', {
              day: 'numeric',
              month: 'short',
              hour: 'numeric',
              minute: '2-digit',
              second: '2-digit',
            })}
          </p>
          {changed.length > 0 ? (
            <button
              type="button"
              onClick={() => setIsOpen(!isOpen)}
              className="mt-2 text-xs text-accent-cyan transition hover:text-accent-cyan/80"
            >
              {isOpen ? 'Hide' : `${changed.length} field${changed.length === 1 ? '' : 's'} changed`}
            </button>
          ) : null}
        </div>
      </div>

      {isOpen ? (
        <div className="-mx-6 mt-4 overflow-x-auto border-t border-dark-border px-6 pt-4">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wide text-gray-500">
                <th className="py-2 pr-4 font-semibold">Field</th>
                <th className="py-2 pr-4 font-semibold">Before</th>
                <th className="py-2 font-semibold">After</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-dark-border">
              {changed.map((key) => (
                <tr key={key}>
                  <td className="py-2 pr-4 font-mono text-xs text-gray-400">{key}</td>
                  <td className="py-2 pr-4 font-mono text-xs text-red-300/80">
                    {render(entry.beforeState?.[key])}
                  </td>
                  <td className="py-2 font-mono text-xs text-green-300/80">
                    {render(entry.afterState?.[key])}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </Card>
  )
}

export default function AuditPage() {
  const [search, setSearch] = useState('')
  const [entityType, setEntityType] = useState('')

  const auditQuery = useQuery({ queryKey: ['audit-log'], queryFn: () => getAuditLog(200) })

  // `?? []` would allocate a fresh array on every render, which would in turn make the memo below
  // recompute on every render — the exact thing it exists to avoid.
  const entries = useMemo(() => auditQuery.data ?? [], [auditQuery.data])

  const entityTypes = useMemo(
    () => [...new Set(entries.map((entry) => entry.entityType))].sort(),
    [entries],
  )

  const filtered = entries.filter((entry) => {
    if (entityType && entry.entityType !== entityType) return false
    if (!search) return true
    const haystack = `${entry.action} ${entry.actorName ?? ''} ${entry.entityType}`.toLowerCase()
    return haystack.includes(search.toLowerCase())
  })

  return (
    <SettingsShell
      title="Audit trail"
      description="Every change, who made it and what it changed. Append-only — nothing here can be edited or removed."
    >
      <div className="mb-6 flex flex-wrap gap-3">
        <div className="min-w-[220px] flex-1">
          <Input
            placeholder="Search by action or person…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <div className="min-w-[200px]">
          <Select
            options={[
              { value: '', label: 'All entity types' },
              ...entityTypes.map((type) => ({ value: type, label: type })),
            ]}
            value={entityType}
            onChange={setEntityType}
            placeholder="All entity types"
          />
        </div>
      </div>

      {auditQuery.isLoading ? (
        <p className="text-sm text-gray-400">Loading the trail…</p>
      ) : entries.length === 0 ? (
        <Card>
          <p className="text-sm text-gray-500">
            Nothing recorded yet, or nothing within the organizations you can see.
          </p>
        </Card>
      ) : filtered.length === 0 ? (
        <Card>
          <p className="text-sm text-gray-500">Nothing matches that filter.</p>
        </Card>
      ) : (
        <>
          <p className="mb-4 text-sm text-gray-500">
            {filtered.length} of {entries.length} most recent entries
          </p>
          <div className="space-y-3">
            {filtered.map((entry) => (
              <Row key={entry.id} entry={entry} />
            ))}
          </div>
        </>
      )}
    </SettingsShell>
  )
}
