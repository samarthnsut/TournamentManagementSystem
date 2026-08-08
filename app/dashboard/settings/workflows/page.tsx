'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import SettingsShell from '../../../../components/settings/SettingsShell'
import Button from '../../../../components/ui/Button'
import Card from '../../../../components/ui/Card'
import Input from '../../../../components/ui/Input'
import Select from '../../../../components/ui/Select'
import ConfirmDialog from '../../../../components/ui/ConfirmDialog'
import { ACTION_COPY } from '../../../../lib/lifecycle'
import { useAuth } from '../../../../lib/useAuth'
import { getOrganizationUnits } from '../../../../lib/api/organizations'
import { SYSTEM_ROLES } from '../../../../lib/api/users'
import {
  createWorkflow,
  deactivateWorkflow,
  getWorkflows,
  type WorkflowStep,
} from '../../../../lib/api/workflows'

type DraftStep = { roleCode: string; stepName: string; approvalRequired: boolean }

const APPROVER_ROLES = SYSTEM_ROLES.filter((role) =>
  ['TENANT_ADMIN', 'ORG_OFFICIAL', 'TOURNAMENT_ADMIN', 'COMPETITION_OFFICIAL'].includes(role.code),
)

export default function WorkflowsPage() {
  const queryClient = useQueryClient()
  const { can } = useAuth()
  const [error, setError] = useState('')
  const [isCreating, setIsCreating] = useState(false)
  const [pendingDeactivate, setPendingDeactivate] = useState<{ id: string; name: string } | null>(null)
  const [workflowName, setWorkflowName] = useState('')
  const [organizationUnitId, setOrganizationUnitId] = useState('')
  const [steps, setSteps] = useState<DraftStep[]>([
    { roleCode: 'ORG_OFFICIAL', stepName: '', approvalRequired: true },
  ])

  const report = (cause: unknown) =>
    setError(cause instanceof Error ? cause.message : 'Something went wrong')

  const workflowsQuery = useQuery({ queryKey: ['approval-workflows'], queryFn: getWorkflows })
  const unitsQuery = useQuery({ queryKey: ['organization-units'], queryFn: getOrganizationUnits })

  const units = unitsQuery.data ?? []
  const unitName = (id: string) => units.find((unit) => unit.id === id)?.name ?? '—'

  const create = useMutation({
    mutationFn: () =>
      createWorkflow({
        organizationUnitId: organizationUnitId || units[0]?.id,
        workflowName: workflowName.trim(),
        // Level is the position in the list — the engine advances through them in order.
        steps: steps.map((step, index) => ({
          level: index + 1,
          roleCode: step.roleCode,
          stepName: step.stepName.trim() || undefined,
          approvalRequired: step.approvalRequired,
        })),
      }),
    onSuccess: async () => {
      setError('')
      setIsCreating(false)
      setWorkflowName('')
      setSteps([{ roleCode: 'ORG_OFFICIAL', stepName: '', approvalRequired: true }])
      await queryClient.invalidateQueries({ queryKey: ['approval-workflows'] })
    },
    onError: report,
  })

  const deactivate = useMutation({
    mutationFn: deactivateWorkflow,
    onSuccess: async () => {
      setError('')
      setPendingDeactivate(null)
      await queryClient.invalidateQueries({ queryKey: ['approval-workflows'] })
    },
    onError: (cause) => {
      setPendingDeactivate(null)
      report(cause)
    },
  })

  const workflows = workflowsQuery.data ?? []
  const canConfigure = can('organization:update')
  const roleLabel = (code: string) =>
    SYSTEM_ROLES.find((role) => role.code === code)?.label ?? code

  return (
    <SettingsShell
      title="Approval workflows"
      description="How many people must sign off an entry before it counts, and who they are."
      error={error}
      actions={
        canConfigure && !isCreating ? (
          <Button className="btn-gradient" onClick={() => setIsCreating(true)}>
            New workflow
          </Button>
        ) : null
      }
    >
      <Card className="mb-6 border-l-4 border-l-accent-blue/60">
        <p className="text-sm text-gray-400">
          A workflow applies to entries in the organization it is defined on, and to everything below
          it — the nearest one above an entry wins. With no workflow anywhere, the tournament&apos;s
          approval policy decides, which is why entries can already be approved without one.
        </p>
      </Card>

      {isCreating ? (
        <Card className="mb-6">
          <h2 className="mb-5 text-lg font-semibold text-white">New workflow</h2>
          <form
            className="space-y-5"
            onSubmit={(event) => {
              event.preventDefault()
              create.mutate()
            }}
          >
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Name</label>
                <Input
                  value={workflowName}
                  onChange={(e) => setWorkflowName(e.target.value)}
                  placeholder="District then state sign-off"
                  required
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Applies to</label>
                <Select
                  options={units.map((unit) => ({ value: unit.id, label: unit.name }))}
                  value={organizationUnitId || units[0]?.id}
                  onChange={setOrganizationUnitId}
                />
              </div>
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-gray-300">
                Levels, in order
              </label>
              <div className="space-y-3">
                {steps.map((step, index) => (
                  <div
                    key={index}
                    className="flex flex-wrap items-end gap-3 rounded-lg border border-dark-border bg-dark-bg/40 p-3"
                  >
                    <span className="mb-2 text-sm font-semibold text-accent-orange">{index + 1}</span>
                    <div className="min-w-[200px] flex-1">
                      <label className="mb-2 block text-xs text-gray-400">Approver role</label>
                      <Select
                        options={APPROVER_ROLES.map((role) => ({ value: role.code, label: role.label }))}
                        value={step.roleCode}
                        onChange={(value) =>
                          setSteps(steps.map((s, i) => (i === index ? { ...s, roleCode: value } : s)))
                        }
                      />
                    </div>
                    <div className="min-w-[200px] flex-1">
                      <label className="mb-2 block text-xs text-gray-400">Label (optional)</label>
                      <Input
                        value={step.stepName}
                        placeholder="District check"
                        onChange={(e) =>
                          setSteps(
                            steps.map((s, i) => (i === index ? { ...s, stepName: e.target.value } : s)),
                          )
                        }
                      />
                    </div>
                    {steps.length > 1 ? (
                      <button
                        type="button"
                        onClick={() => setSteps(steps.filter((_, i) => i !== index))}
                        className="mb-2 text-sm text-red-300 transition hover:text-red-200"
                      >
                        Remove
                      </button>
                    ) : null}
                  </div>
                ))}
              </div>
              <Button
                type="button"
                variant="secondary"
                className="mt-3 px-4 py-2 text-sm"
                onClick={() =>
                  setSteps([...steps, { roleCode: 'TENANT_ADMIN', stepName: '', approvalRequired: true }])
                }
              >
                + Add a level
              </Button>
            </div>

            <div className="flex flex-wrap gap-3">
              <Button type="submit" className="btn-gradient" disabled={create.isPending || !workflowName}>
                {create.isPending ? 'Creating…' : 'Create workflow'}
              </Button>
              <Button type="button" variant="secondary" onClick={() => setIsCreating(false)}>
                Cancel
              </Button>
            </div>
          </form>
        </Card>
      ) : null}

      {workflowsQuery.isLoading ? (
        <p className="text-sm text-gray-400">Loading workflows…</p>
      ) : workflows.length === 0 ? (
        <Card>
          <p className="text-sm text-gray-500">
            No workflows configured. Entries follow the tournament&apos;s approval policy until one
            exists.
          </p>
        </Card>
      ) : (
        <div className="space-y-4">
          {workflows.map((workflow) => (
            <Card key={workflow.id}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <h3 className="font-semibold text-white">{workflow.workflowName}</h3>
                  <p className="mt-1 text-sm text-gray-500">
                    {unitName(workflow.organizationUnitId)} · {workflow.entityType.toLowerCase()}
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <span
                    className={`rounded-full border px-3 py-1 text-xs font-semibold ${
                      workflow.isActive
                        ? 'border-green-500/40 bg-green-500/20 text-green-300'
                        : 'border-gray-600/40 bg-gray-700/30 text-gray-400'
                    }`}
                  >
                    {workflow.isActive ? 'Active' : 'Inactive'}
                  </span>
                  {canConfigure && workflow.isActive ? (
                    <button
                      type="button"
                      onClick={() =>
                        setPendingDeactivate({ id: workflow.id, name: workflow.workflowName })
                      }
                      className="text-sm text-red-300 transition hover:text-red-200"
                    >
                      Deactivate
                    </button>
                  ) : null}
                </div>
              </div>

              <ol className="mt-4 space-y-2 border-t border-dark-border pt-4">
                {[...workflow.steps]
                  .sort((a, b) => a.level - b.level)
                  .map((step: WorkflowStep) => (
                    <li key={step.id ?? step.level} className="flex items-center gap-3 text-sm">
                      <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-accent-purple/20 text-xs font-semibold text-accent-purple">
                        {step.level}
                      </span>
                      <span className="text-gray-200">{roleLabel(step.roleCode)}</span>
                      {step.stepName ? (
                        <span className="text-gray-500">— {step.stepName}</span>
                      ) : null}
                    </li>
                  ))}
              </ol>
            </Card>
          ))}
        </div>
      )}
      <ConfirmDialog
        isOpen={pendingDeactivate !== null}
        copy={
          pendingDeactivate
            ? { ...ACTION_COPY.deactivateWorkflow, title: `Deactivate "${pendingDeactivate.name}"?` }
            : null
        }
        isWorking={deactivate.isPending}
        onCancel={() => setPendingDeactivate(null)}
        onConfirm={() => pendingDeactivate && deactivate.mutate(pendingDeactivate.id)}
      />
    </SettingsShell>
  )
}
