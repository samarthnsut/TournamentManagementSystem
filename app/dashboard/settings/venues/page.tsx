'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import SettingsShell from '../../../../components/settings/SettingsShell'
import Button from '../../../../components/ui/Button'
import Card from '../../../../components/ui/Card'
import Input from '../../../../components/ui/Input'
import Select from '../../../../components/ui/Select'
import { useAuth } from '../../../../lib/useAuth'
import { getOrganizationUnits } from '../../../../lib/api/organizations'
import {
  archiveVenue,
  createVenue,
  getVenues,
  updateVenue,
  type Venue,
  type VenuePayload,
} from '../../../../lib/api/venues'

const EMPTY: VenuePayload = {
  organizationUnitId: '',
  name: '',
  addressLine: '',
  city: '',
  state: '',
  capacity: null,
}

export default function VenuesPage() {
  const queryClient = useQueryClient()
  const { can } = useAuth()
  const [error, setError] = useState('')
  const [draft, setDraft] = useState<VenuePayload | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)

  const report = (cause: unknown) =>
    setError(cause instanceof Error ? cause.message : 'Something went wrong')

  const venuesQuery = useQuery({ queryKey: ['venues'], queryFn: getVenues })
  const unitsQuery = useQuery({ queryKey: ['organization-units'], queryFn: getOrganizationUnits })

  const units = unitsQuery.data ?? []
  const unitOptions = units.map((unit) => ({ value: unit.id, label: unit.name }))
  const unitName = (id: string) => units.find((unit) => unit.id === id)?.name ?? '—'

  const done = async () => {
    setError('')
    setDraft(null)
    setEditingId(null)
    await queryClient.invalidateQueries({ queryKey: ['venues'] })
  }

  const save = useMutation({
    mutationFn: () => {
      const payload = { ...draft!, organizationUnitId: draft!.organizationUnitId || units[0]?.id }
      return editingId ? updateVenue(editingId, payload) : createVenue(payload)
    },
    onSuccess: done,
    onError: report,
  })

  const archive = useMutation({ mutationFn: archiveVenue, onSuccess: done, onError: report })

  const canCreate = can('venue:create')
  const canEdit = can('venue:update')
  const canArchive = can('venue:delete')
  const venues = venuesQuery.data ?? []

  const startEdit = (venue: Venue) => {
    setEditingId(venue.id)
    setDraft({
      organizationUnitId: venue.organizationUnitId,
      name: venue.name,
      addressLine: venue.addressLine ?? '',
      city: venue.city ?? '',
      state: venue.state ?? '',
      capacity: venue.capacity,
    })
  }

  return (
    <SettingsShell
      title="Venues"
      description="The grounds your matches are played at. Scheduling a match picks from this list."
      error={error}
      actions={
        canCreate && !draft ? (
          <Button className="btn-gradient" onClick={() => setDraft({ ...EMPTY })}>
            Add a venue
          </Button>
        ) : null
      }
    >
      {draft ? (
        <Card className="mb-6">
          <h2 className="mb-5 text-lg font-semibold text-white">
            {editingId ? 'Edit venue' : 'Add a venue'}
          </h2>
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault()
              save.mutate()
            }}
          >
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Name</label>
                <Input
                  value={draft.name}
                  onChange={(e) => setDraft({ ...draft, name: e.target.value })}
                  required
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Organization</label>
                <Select
                  options={unitOptions}
                  value={draft.organizationUnitId || units[0]?.id}
                  onChange={(value) => setDraft({ ...draft, organizationUnitId: value })}
                  disabled={Boolean(editingId)}
                />
                {editingId ? (
                  <p className="mt-2 text-xs text-gray-600">
                    A venue does not move between organizations — matches already scheduled there
                    would move with it.
                  </p>
                ) : null}
              </div>
              <div className="sm:col-span-2">
                <label className="mb-2 block text-sm font-medium text-gray-300">Address</label>
                <Input
                  value={draft.addressLine ?? ''}
                  onChange={(e) => setDraft({ ...draft, addressLine: e.target.value })}
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">City</label>
                <Input
                  value={draft.city ?? ''}
                  onChange={(e) => setDraft({ ...draft, city: e.target.value })}
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">State</label>
                <Input
                  value={draft.state ?? ''}
                  onChange={(e) => setDraft({ ...draft, state: e.target.value })}
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Capacity</label>
                <Input
                  type="number"
                  min="1"
                  value={draft.capacity ?? ''}
                  onChange={(e) =>
                    setDraft({ ...draft, capacity: e.target.value ? Number(e.target.value) : null })
                  }
                />
              </div>
            </div>

            <div className="flex flex-wrap gap-3">
              <Button type="submit" className="btn-gradient" disabled={save.isPending || !draft.name}>
                {save.isPending ? 'Saving…' : editingId ? 'Save changes' : 'Add venue'}
              </Button>
              <Button
                type="button"
                variant="secondary"
                onClick={() => {
                  setDraft(null)
                  setEditingId(null)
                }}
              >
                Cancel
              </Button>
            </div>
          </form>
        </Card>
      ) : null}

      {venuesQuery.isLoading ? (
        <p className="text-sm text-gray-400">Loading venues…</p>
      ) : venues.length === 0 ? (
        <Card>
          <p className="text-sm text-gray-500">No venues yet.</p>
        </Card>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {venues.map((venue) => (
            <Card key={venue.id}>
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <h3 className="font-semibold text-white">{venue.name}</h3>
                  <p className="mt-1 text-sm text-gray-500">
                    {[venue.addressLine, venue.city, venue.state].filter(Boolean).join(', ') ||
                      'No address recorded'}
                  </p>
                  <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-600">
                    <span>{unitName(venue.organizationUnitId)}</span>
                    {venue.capacity ? <span>Capacity {venue.capacity.toLocaleString('en-IN')}</span> : null}
                  </div>
                </div>
              </div>

              {canEdit || canArchive ? (
                <div className="mt-4 flex flex-wrap gap-4 border-t border-dark-border pt-3 text-sm">
                  {canEdit ? (
                    <button
                      type="button"
                      onClick={() => startEdit(venue)}
                      className="text-accent-cyan transition hover:text-accent-cyan/80"
                    >
                      Edit
                    </button>
                  ) : null}
                  {canArchive ? (
                    <button
                      type="button"
                      onClick={() => {
                        if (window.confirm(`Archive ${venue.name}? It stops appearing when scheduling.`)) {
                          archive.mutate(venue.id)
                        }
                      }}
                      className="text-red-300 transition hover:text-red-200"
                    >
                      Archive
                    </button>
                  ) : null}
                </div>
              ) : null}
            </Card>
          ))}
        </div>
      )}
    </SettingsShell>
  )
}
