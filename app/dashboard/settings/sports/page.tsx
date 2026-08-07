'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import SettingsShell from '../../../../components/settings/SettingsShell'
import Button from '../../../../components/ui/Button'
import Card from '../../../../components/ui/Card'
import Select from '../../../../components/ui/Select'
import { useAuth } from '../../../../lib/useAuth'
import { getOrganizationUnits } from '../../../../lib/api/organizations'
import {
  createSportConfiguration,
  getSportConfigurations,
  getSports,
  replaceSportConfiguration,
  type SportConfiguration,
} from '../../../../lib/api/tournaments'

/**
 * The frozen key sets from ARCHITECTURE_BRIEF §7. Listing them here rather than fetching is
 * deliberate: they are a closed enum, and the server rejects a key with no deployed strategy
 * anyway — so the worst a stale entry causes is a clear error, not a broken save.
 */
const PARTICIPANT_TYPES = ['INDIVIDUAL', 'TEAM', 'ORGANIZATION']
const FIXTURE_GENERATORS = ['ROUND_ROBIN', 'SINGLE_ELIMINATION', 'DOUBLE_ELIMINATION', 'SWISS', 'NONE']
const RESULT_EVALUATORS = ['POINTS', 'WIN_LOSS', 'TIME', 'DISTANCE', 'SCORE']
const LEADERBOARD_STRATEGIES = ['POINTS_TABLE', 'LOWEST_TIME', 'HIGHEST_DISTANCE', 'HIGHEST_SCORE', 'BRACKET']

/** Starting points that are known to validate, so the first save is not a guessing game. */
const PRESETS: Record<string, Record<string, unknown>> = {
  'League (points table)': {
    participantType: 'TEAM',
    fixtureGenerator: 'ROUND_ROBIN',
    resultEvaluator: 'POINTS',
    leaderboardStrategy: 'POINTS_TABLE',
    rules: {
      pointsForWin: 3,
      pointsForDraw: 1,
      pointsForLoss: 0,
      legs: 1,
      tiebreakers: ['SCORE_DIFFERENCE', 'SCORE_FOR', 'HEAD_TO_HEAD'],
    },
  },
  'Timed event (fastest wins)': {
    participantType: 'INDIVIDUAL',
    fixtureGenerator: 'NONE',
    resultEvaluator: 'TIME',
    leaderboardStrategy: 'LOWEST_TIME',
    rules: { timeUnit: 'SECONDS', precision: 3 },
  },
}

type Draft = {
  organizationUnitId: string
  sportId: string
  participantType: string
  fixtureGenerator: string
  resultEvaluator: string
  leaderboardStrategy: string
  rulesJson: string
}

export default function SportConfigurationsPage() {
  const queryClient = useQueryClient()
  const { can } = useAuth()
  const [error, setError] = useState('')
  const [draft, setDraft] = useState<Draft | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)

  const report = (cause: unknown) =>
    setError(cause instanceof Error ? cause.message : 'Something went wrong')

  const configsQuery = useQuery({ queryKey: ['sport-configurations'], queryFn: getSportConfigurations })
  const sportsQuery = useQuery({ queryKey: ['sports'], queryFn: getSports })
  const unitsQuery = useQuery({ queryKey: ['organization-units'], queryFn: getOrganizationUnits })

  const sports = sportsQuery.data ?? []
  const units = unitsQuery.data ?? []
  const sportName = (id: string) => sports.find((sport) => sport.id === id)?.name ?? '—'
  const unitName = (id: string) => units.find((unit) => unit.id === id)?.name ?? '—'

  const buildConfig = (current: Draft) => {
    let rules: unknown
    try {
      rules = JSON.parse(current.rulesJson || '{}')
    } catch {
      throw new Error('Rules must be valid JSON.')
    }
    return {
      sport: sports.find((sport) => sport.id === current.sportId)?.code ?? '',
      participantType: current.participantType,
      fixtureGenerator: current.fixtureGenerator,
      resultEvaluator: current.resultEvaluator,
      leaderboardStrategy: current.leaderboardStrategy,
      rules,
    }
  }

  const save = useMutation({
    mutationFn: async () => {
      const config = buildConfig(draft!)
      return editingId
        ? replaceSportConfiguration(editingId, config)
        : createSportConfiguration({
            organizationUnitId: draft!.organizationUnitId || units[0]?.id,
            sportId: draft!.sportId,
            config,
          })
    },
    onSuccess: async () => {
      setError('')
      setDraft(null)
      setEditingId(null)
      await queryClient.invalidateQueries({ queryKey: ['sport-configurations'] })
    },
    onError: report,
  })

  const startNew = (preset?: Record<string, unknown>) => {
    setEditingId(null)
    setDraft({
      organizationUnitId: units[0]?.id ?? '',
      sportId: sports[0]?.id ?? '',
      participantType: (preset?.participantType as string) ?? 'TEAM',
      fixtureGenerator: (preset?.fixtureGenerator as string) ?? 'ROUND_ROBIN',
      resultEvaluator: (preset?.resultEvaluator as string) ?? 'POINTS',
      leaderboardStrategy: (preset?.leaderboardStrategy as string) ?? 'POINTS_TABLE',
      rulesJson: JSON.stringify(preset?.rules ?? {}, null, 2),
    })
  }

  const startEdit = (configuration: SportConfiguration) => {
    const config = configuration.config as Record<string, unknown>
    setEditingId(configuration.id)
    setDraft({
      organizationUnitId: configuration.organizationUnitId,
      sportId: configuration.sportId,
      participantType: (config.participantType as string) ?? 'TEAM',
      fixtureGenerator: (config.fixtureGenerator as string) ?? 'ROUND_ROBIN',
      resultEvaluator: (config.resultEvaluator as string) ?? 'POINTS',
      leaderboardStrategy: (config.leaderboardStrategy as string) ?? 'POINTS_TABLE',
      rulesJson: JSON.stringify(config.rules ?? {}, null, 2),
    })
  }

  const canCreate = can('sport-config:create')
  const canEdit = can('sport-config:update')
  const configurations = configsQuery.data ?? []
  const options = (values: string[]) => values.map((value) => ({ value, label: value }))

  return (
    <SettingsShell
      title="Sport configurations"
      description="How a sport is run: who competes, how they are paired, how results are scored and ranked."
      error={error}
      actions={
        canCreate && !draft ? (
          <div className="flex flex-wrap gap-2">
            {Object.entries(PRESETS).map(([label, preset]) => (
              <Button
                key={label}
                variant="secondary"
                className="px-4 py-2 text-sm"
                onClick={() => startNew(preset)}
              >
                {label}
              </Button>
            ))}
            <Button className="btn-gradient px-4 py-2 text-sm" onClick={() => startNew()}>
              Blank
            </Button>
          </div>
        ) : null
      }
    >
      {draft ? (
        <Card className="mb-6">
          <h2 className="mb-1 text-lg font-semibold text-white">
            {editingId ? 'Edit configuration' : 'New configuration'}
          </h2>
          <p className="mb-5 text-sm text-gray-500">
            The four strategy keys drive everything downstream — fixtures, result entry and the
            standings table all dispatch on them, never on the sport&apos;s name.
          </p>

          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault()
              save.mutate()
            }}
          >
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Sport</label>
                <Select
                  options={sports.map((sport) => ({ value: sport.id, label: sport.name }))}
                  value={draft.sportId}
                  onChange={(value) => setDraft({ ...draft, sportId: value })}
                  disabled={Boolean(editingId)}
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Organization</label>
                <Select
                  options={units.map((unit) => ({ value: unit.id, label: unit.name }))}
                  value={draft.organizationUnitId || units[0]?.id}
                  onChange={(value) => setDraft({ ...draft, organizationUnitId: value })}
                  disabled={Boolean(editingId)}
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Participant type</label>
                <Select
                  options={options(PARTICIPANT_TYPES)}
                  value={draft.participantType}
                  onChange={(value) => setDraft({ ...draft, participantType: value })}
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Fixture generator</label>
                <Select
                  options={options(FIXTURE_GENERATORS)}
                  value={draft.fixtureGenerator}
                  onChange={(value) => setDraft({ ...draft, fixtureGenerator: value })}
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Result evaluator</label>
                <Select
                  options={options(RESULT_EVALUATORS)}
                  value={draft.resultEvaluator}
                  onChange={(value) => setDraft({ ...draft, resultEvaluator: value })}
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Leaderboard</label>
                <Select
                  options={options(LEADERBOARD_STRATEGIES)}
                  value={draft.leaderboardStrategy}
                  onChange={(value) => setDraft({ ...draft, leaderboardStrategy: value })}
                />
              </div>
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-gray-300">Rules (JSON)</label>
              <textarea
                value={draft.rulesJson}
                onChange={(e) => setDraft({ ...draft, rulesJson: e.target.value })}
                rows={10}
                spellCheck={false}
                className="w-full rounded-lg border border-dark-border bg-dark-bg px-4 py-3 font-mono text-xs text-gray-200 focus:border-accent-purple focus:outline-none focus:ring-1 focus:ring-accent-purple"
              />
              <p className="mt-2 text-xs text-gray-600">
                Each strategy declares the keys it cannot work without; the server lists any that are
                missing when you save.
              </p>
            </div>

            <div className="flex flex-wrap gap-3">
              <Button type="submit" className="btn-gradient" disabled={save.isPending}>
                {save.isPending ? 'Saving…' : editingId ? 'Save new version' : 'Create configuration'}
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

      {configsQuery.isLoading ? (
        <p className="text-sm text-gray-400">Loading configurations…</p>
      ) : configurations.length === 0 ? (
        <Card>
          <p className="text-sm text-gray-500">
            No configurations yet. A competition cannot be created without one — start from a preset
            above.
          </p>
        </Card>
      ) : (
        <div className="space-y-4">
          {configurations.map((configuration) => {
            const config = configuration.config as Record<string, unknown>
            return (
              <Card key={configuration.id}>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <h3 className="font-semibold text-white">
                      {sportName(configuration.sportId)}
                      <span className="ml-2 text-sm font-normal text-gray-500">
                        v{configuration.version}
                      </span>
                    </h3>
                    <p className="mt-1 text-sm text-gray-500">
                      {unitName(configuration.organizationUnitId)}
                    </p>
                  </div>
                  {canEdit ? (
                    <button
                      type="button"
                      onClick={() => startEdit(configuration)}
                      className="text-sm text-accent-cyan transition hover:text-accent-cyan/80"
                    >
                      Edit
                    </button>
                  ) : null}
                </div>

                <div className="mt-4 flex flex-wrap gap-2">
                  {(
                    ['participantType', 'fixtureGenerator', 'resultEvaluator', 'leaderboardStrategy'] as const
                  ).map((key) => (
                    <span
                      key={key}
                      className="rounded-full border border-dark-border bg-white/5 px-3 py-1 text-xs text-gray-300"
                      title={key}
                    >
                      {String(config[key] ?? '—')}
                    </span>
                  ))}
                </div>

                <details className="mt-4">
                  <summary className="cursor-pointer text-sm text-gray-500 transition hover:text-gray-300">
                    Rules
                  </summary>
                  <pre className="mt-3 overflow-x-auto rounded-lg border border-dark-border bg-dark-bg px-4 py-3 font-mono text-xs text-gray-400">
                    {JSON.stringify(config.rules ?? {}, null, 2)}
                  </pre>
                </details>
              </Card>
            )
          })}
        </div>
      )}
    </SettingsShell>
  )
}
