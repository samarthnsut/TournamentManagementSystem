'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Header from '../../../components/Header'
import Button from '../../../components/ui/Button'
import Input from '../../../components/ui/Input'
import DateField from '../../../components/ui/DateField'
import Card from '../../../components/ui/Card'
import Select from '../../../components/ui/Select'
import { createTournament } from '../../../lib/api/tournaments'
import { getOrganizationUnits } from '../../../lib/api/organizations'

/** Mirrors the backend's slug rule so the user finds out here, not after a round trip. */
const SLUG_PATTERN = /^[a-z0-9-]{3,60}$/

function slugify(value: string) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60)
}

export default function CreateTournamentPage() {
  const router = useRouter()
  const queryClient = useQueryClient()

  const [formData, setFormData] = useState({
    name: '',
    slug: '',
    organizationUnitId: '',
    startDate: '',
    endDate: '',
    description: '',
  })
  const [slugTouched, setSlugTouched] = useState(false)
  const [isSubmitted, setIsSubmitted] = useState(false)

  // A tournament must be owned by an organization unit, and the user may only pick one they can
  // reach — the backend returns exactly those.
  const { data: organizationUnits = [], isLoading: unitsLoading, isError: unitsError } = useQuery({
    queryKey: ['organization-units'],
    queryFn: getOrganizationUnits,
  })

  const ownerId = formData.organizationUnitId || organizationUnits[0]?.id || ''
  const effectiveSlug = slugTouched ? formData.slug : slugify(formData.name)
  const slugIsValid = effectiveSlug === '' || SLUG_PATTERN.test(effectiveSlug)

  const createMutation = useMutation({
    mutationFn: createTournament,
    onSuccess: async () => {
      setIsSubmitted(true)
      await queryClient.invalidateQueries({ queryKey: ['tournaments'] })
      setTimeout(() => router.push('/dashboard'), 700)
    },
  })

  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>,
  ) => {
    if (e.target.name === 'slug') {
      setSlugTouched(true)
    }
    setFormData({ ...formData, [e.target.name]: e.target.value })
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!ownerId || !slugIsValid) {
      return
    }
    createMutation.mutate({
      organizationUnitId: ownerId,
      name: formData.name,
      slug: effectiveSlug || undefined,
      description: formData.description || undefined,
      startDate: formData.startDate || undefined,
      endDate: formData.endDate || undefined,
    })
  }

  const errorMessage = createMutation.error instanceof Error ? createMutation.error.message : ''

  return (
    <>
      <Header />
      <main className="min-h-screen bg-dark-bg">
        <div className="border-b border-dark-border bg-gradient-to-b from-dark-surface to-dark-bg py-8 sm:py-12">
          <div className="mx-auto max-w-5xl px-6 sm:px-8">
            <Link
              href="/dashboard"
              className="text-sm text-accent-cyan hover:text-accent-cyan/80 transition mb-6 inline-flex items-center gap-1"
            >
              ← Back to Dashboard
            </Link>
            <h1 className="text-3xl sm:text-4xl font-bold text-white mb-2">Create New Tournament</h1>
            <p className="text-gray-400">
              Tournaments start as a draft. You add competitions and publish them from the dashboard.
            </p>
          </div>
        </div>

        <div className="mx-auto max-w-5xl px-6 sm:px-8 py-8 sm:py-12">
          <div className="grid gap-8 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <form onSubmit={handleSubmit} className="space-y-6">
                <div>
                  <h2 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                    <span className="text-2xl">📋</span>
                    Basic Information
                  </h2>
                  <div className="space-y-4">
                    <div>
                      <label htmlFor="name" className="block text-sm font-medium text-gray-300 mb-2">
                        Tournament Name *
                      </label>
                      <Input
                        id="name"
                        name="name"
                        type="text"
                        placeholder="e.g., Khelo India Youth Games 2027"
                        value={formData.name}
                        onChange={handleChange}
                        required
                      />
                    </div>

                    <div>
                      <label htmlFor="slug" className="block text-sm font-medium text-gray-300 mb-2">
                        Public URL
                      </label>
                      <Input
                        id="slug"
                        name="slug"
                        type="text"
                        placeholder="auto-generated from the name"
                        value={effectiveSlug}
                        onChange={handleChange}
                      />
                      <p className="mt-2 text-xs text-gray-500">
                        Visitors will find this tournament at <span className="text-gray-400">/t/{effectiveSlug || '…'}</span>. Lowercase
                        letters, digits and hyphens only. It is locked once you publish.
                      </p>
                      {!slugIsValid ? (
                        <p className="mt-1 text-xs text-red-300">
                          Must be 3–60 characters of lowercase letters, digits or hyphens.
                        </p>
                      ) : null}
                    </div>

                    <div>
                      <label htmlFor="organizationUnitId" className="block text-sm font-medium text-gray-300 mb-2">
                        Organizer *
                      </label>
                      <Select
                        id="organizationUnitId"
                        value={ownerId}
                        onChange={(unitId) => setFormData({ ...formData, organizationUnitId: unitId })}
                        disabled={unitsLoading || organizationUnits.length === 0}
                        placeholder={unitsLoading ? 'Loading organizations…' : 'Select an organization'}
                        options={organizationUnits.map((unit) => ({ value: unit.id, label: unit.name }))}
                      />
                      {unitsError ? (
                        <p className="mt-1 text-xs text-red-300">
                          Could not load organizations. Check that you are signed in.
                        </p>
                      ) : null}
                    </div>
                  </div>
                </div>

                <div className="border-t border-dark-border" />

                <div>
                  <h2 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                    <span className="text-2xl">📅</span>
                    Schedule
                  </h2>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <label htmlFor="startDate" className="block text-sm font-medium text-gray-300 mb-2">
                        Start Date
                      </label>
                      <DateField
                        id="startDate"
                        value={formData.startDate}
                        onChange={(value) => setFormData({ ...formData, startDate: value })}
                      />
                    </div>
                    <div>
                      <label htmlFor="endDate" className="block text-sm font-medium text-gray-300 mb-2">
                        End Date
                      </label>
                      <DateField
                        id="endDate"
                        value={formData.endDate}
                        // An end date before the start is rejected by the database anyway; the
                        // picker simply stops offering the invalid half of the calendar.
                        min={formData.startDate || undefined}
                        onChange={(value) => setFormData({ ...formData, endDate: value })}
                      />
                      {formData.startDate && formData.endDate && formData.endDate < formData.startDate ? (
                        <p className="mt-1 text-xs text-red-300">
                          The end date is before the start date.
                        </p>
                      ) : null}
                    </div>
                  </div>
                </div>

                <div className="border-t border-dark-border" />

                <div>
                  <h2 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                    <span className="text-2xl">📝</span>
                    Description
                  </h2>
                  <textarea
                    id="description"
                    name="description"
                    rows={5}
                    placeholder="What is this tournament about?"
                    value={formData.description}
                    onChange={handleChange}
                    className="w-full rounded-lg border border-dark-border bg-dark-surface px-4 py-3 text-white outline-none transition focus:border-accent-purple focus:ring-2 focus:ring-accent-purple/20"
                  />
                </div>

                {errorMessage ? (
                  <div className="rounded-lg border border-red-500/30 bg-red-500/5 px-4 py-3 text-sm text-red-300">
                    {errorMessage}
                  </div>
                ) : null}

                {isSubmitted ? (
                  <div className="rounded-lg border border-green-500/30 bg-green-500/5 px-4 py-3 text-sm text-green-300">
                    Tournament created. Returning to the dashboard…
                  </div>
                ) : null}

                <div className="flex flex-col sm:flex-row gap-3 pt-2">
                  <Button
                    type="submit"
                    className="btn-gradient flex-1"
                    disabled={createMutation.isPending || !ownerId || !slugIsValid}
                  >
                    {createMutation.isPending ? 'Creating…' : 'Create Tournament'}
                  </Button>
                  <Link href="/dashboard" className="flex-1">
                    <Button type="button" className="w-full border border-dark-border bg-dark-surface text-gray-300">
                      Cancel
                    </Button>
                  </Link>
                </div>
              </form>
            </Card>

            <div className="space-y-6">
              <Card>
                <h3 className="text-base font-semibold text-white mb-3">What happens next</h3>
                <ol className="space-y-3 text-sm text-gray-400 list-decimal list-inside">
                  <li>The tournament is created as a <span className="text-gray-200">draft</span>, visible only to your team.</li>
                  <li>Add competitions, each bound to a sport configuration.</li>
                  <li>Publish it — the public page goes live and the URL is locked.</li>
                  <li>Open registration when you are ready to take entries.</li>
                </ol>
              </Card>

              <Card>
                <h3 className="text-base font-semibold text-white mb-3">Good to know</h3>
                <ul className="space-y-2 text-sm text-gray-400">
                  <li>• Dates are optional and can be changed until the tournament starts.</li>
                  <li>• A tournament needs at least one competition before registration can open.</li>
                  <li>• Drafts can be deleted; published tournaments can only be cancelled.</li>
                </ul>
              </Card>
            </div>
          </div>
        </div>
      </main>
    </>
  )
}
