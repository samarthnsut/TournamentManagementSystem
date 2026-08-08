'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import SettingsShell from '../../../../components/settings/SettingsShell'
import Button from '../../../../components/ui/Button'
import Card from '../../../../components/ui/Card'
import Input from '../../../../components/ui/Input'
import Select from '../../../../components/ui/Select'
import StatusBadge from '../../../../components/ui/StatusBadge'
import { useAuth } from '../../../../lib/useAuth'
import { getOrganizationUnits } from '../../../../lib/api/organizations'
import {
  SYSTEM_ROLES,
  getUsers,
  grantRole,
  inviteUser,
  revokeRole,
  type InvitedUser,
  type ScopeType,
  type UserListItem,
} from '../../../../lib/api/users'

/** Roles that live at ORGANIZATION scope; the others need a tournament or competition id. */
const ORGANIZATION_ROLES = SYSTEM_ROLES.filter((role) => role.scope === 'ORGANIZATION')

/**
 * The same link the invite email contains, built from wherever this page is being served. Built
 * client-side rather than returned by the API so it always matches the host the organizer is on —
 * a link to localhost is no use to someone reading it in production.
 */
function inviteLink(token: string) {
  const origin = typeof window === 'undefined' ? '' : window.location.origin
  return `${origin}/TournamentManagementSystem/accept-invite?token=${encodeURIComponent(token)}`
}

export default function UsersPage() {
  const queryClient = useQueryClient()
  const { can } = useAuth()
  const [error, setError] = useState('')
  const [isInviting, setIsInviting] = useState(false)
  const [invited, setInvited] = useState<InvitedUser | null>(null)
  const [copied, setCopied] = useState(false)

  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [organizationUnitId, setOrganizationUnitId] = useState('')
  const [roleCode, setRoleCode] = useState(ORGANIZATION_ROLES[0].code as string)

  const [grantingFor, setGrantingFor] = useState<string | null>(null)
  const [grantRoleCode, setGrantRoleCode] = useState(ORGANIZATION_ROLES[0].code as string)
  const [grantScopeId, setGrantScopeId] = useState('')

  const report = (cause: unknown) =>
    setError(cause instanceof Error ? cause.message : 'Something went wrong')

  const usersQuery = useQuery({ queryKey: ['users'], queryFn: getUsers })
  const unitsQuery = useQuery({ queryKey: ['organization-units'], queryFn: getOrganizationUnits })

  const units = unitsQuery.data ?? []
  const unitOptions = units.map((unit) => ({ value: unit.id, label: unit.name }))
  const unitName = (id: string | null) =>
    units.find((unit) => unit.id === id)?.name ?? (id ? 'Elsewhere' : 'Everywhere')

  const invite = useMutation({
    mutationFn: () =>
      inviteUser({
        email: email.trim(),
        displayName: displayName.trim(),
        organizationUnitId: organizationUnitId || units[0]?.id,
        initialRole: {
          roleCode,
          scopeType: 'ORGANIZATION' as ScopeType,
          scopeId: organizationUnitId || units[0]?.id,
        },
      }),
    onSuccess: async (user) => {
      setError('')
      setIsInviting(false)
      setEmail('')
      setDisplayName('')
      // There is no email delivery yet, so the token is shown once, here, or it is lost.
      setInvited(user)
      await queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: report,
  })

  const grant = useMutation({
    mutationFn: (userId: string) =>
      grantRole(userId, {
        roleCode: grantRoleCode,
        scopeType: 'ORGANIZATION' as ScopeType,
        scopeId: grantScopeId || units[0]?.id,
      }),
    onSuccess: async () => {
      setError('')
      setGrantingFor(null)
      await queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: report,
  })

  const revoke = useMutation({
    mutationFn: ({ userId, assignmentId }: { userId: string; assignmentId: string }) =>
      revokeRole(userId, assignmentId),
    onSuccess: async () => {
      setError('')
      await queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: report,
  })

  const canInvite = can('user:invite')
  const canAssign = can('role:assign')
  const users = usersQuery.data ?? []

  return (
    <SettingsShell
      title="People & roles"
      description="Who can act in your organization, and what they are allowed to do."
      error={error}
      actions={
        canInvite && !isInviting ? (
          <Button className="btn-gradient" onClick={() => setIsInviting(true)}>
            Invite someone
          </Button>
        ) : null
      }
    >
      {invited ? (
        <Card className="mb-6 border-l-4 border-l-green-500">
          <h2 className="mb-1 text-lg font-semibold text-white">Invite sent to {invited.email}</h2>
          <p className="mb-4 text-sm text-gray-400">
            They have been emailed a link to set a password. It is valid for seven days. If the email
            does not arrive, send them this link yourself — it is shown only now.
          </p>
          <code className="block overflow-x-auto rounded-lg border border-dark-border bg-dark-bg px-4 py-3 text-xs text-accent-cyan">
            {inviteLink(invited.inviteToken)}
          </code>
          <div className="mt-4 flex flex-wrap gap-3">
            <Button
              variant="secondary"
              className="px-4 py-2 text-sm"
              onClick={() => {
                void navigator.clipboard?.writeText(inviteLink(invited.inviteToken))
                setCopied(true)
              }}
            >
              {copied ? 'Copied' : 'Copy link'}
            </Button>
            <Button
              variant="secondary"
              className="px-4 py-2 text-sm"
              onClick={() => {
                setInvited(null)
                setCopied(false)
              }}
            >
              Done
            </Button>
          </div>
        </Card>
      ) : null}

      {isInviting ? (
        <Card className="mb-6">
          <h2 className="mb-5 text-lg font-semibold text-white">Invite someone</h2>
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault()
              invite.mutate()
            }}
          >
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Full name</label>
                <Input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Email</label>
                <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Organization</label>
                <Select
                  options={unitOptions}
                  value={organizationUnitId || units[0]?.id}
                  onChange={setOrganizationUnitId}
                  placeholder="Choose an organization"
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Role</label>
                <Select
                  options={ORGANIZATION_ROLES.map((role) => ({ value: role.code, label: role.label }))}
                  value={roleCode}
                  onChange={setRoleCode}
                />
                <p className="mt-2 text-xs text-gray-600">
                  {ORGANIZATION_ROLES.find((role) => role.code === roleCode)?.blurb}
                </p>
              </div>
            </div>

            <div className="flex flex-wrap gap-3">
              <Button type="submit" className="btn-gradient" disabled={invite.isPending}>
                {invite.isPending ? 'Inviting…' : 'Send invite'}
              </Button>
              <Button type="button" variant="secondary" onClick={() => setIsInviting(false)}>
                Cancel
              </Button>
            </div>
          </form>
        </Card>
      ) : null}

      {usersQuery.isLoading ? (
        <p className="text-sm text-gray-400">Loading people…</p>
      ) : users.length === 0 ? (
        <Card>
          <p className="text-sm text-gray-500">
            Nobody to show yet. Invited people appear here once they hold a role in an organization
            you can administer.
          </p>
        </Card>
      ) : (
        <div className="space-y-4">
          {users.map((user: UserListItem) => (
            <Card key={user.id}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-semibold text-white">{user.displayName}</p>
                  <p className="mt-1 text-sm text-gray-500">{user.email}</p>
                </div>
                <StatusBadge status={user.status} />
              </div>

              <div className="mt-4 border-t border-dark-border pt-4">
                <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-gray-500">
                  Roles
                </p>
                {user.roles.length === 0 ? (
                  <p className="text-sm text-gray-600">No roles granted.</p>
                ) : (
                  <div className="flex flex-wrap gap-2">
                    {user.roles.map((assignment) => (
                      <span
                        key={assignment.id}
                        className="inline-flex items-center gap-2 rounded-full border border-dark-border bg-white/5 px-3 py-1 text-xs text-gray-300"
                      >
                        <span className="font-semibold text-gray-200">
                          {SYSTEM_ROLES.find((role) => role.code === assignment.roleCode)?.label ??
                            assignment.roleCode}
                        </span>
                        <span className="text-gray-500">at {unitName(assignment.scopeId)}</span>
                        {canAssign ? (
                          <button
                            type="button"
                            aria-label={`Revoke ${assignment.roleCode}`}
                            onClick={() =>
                              revoke.mutate({ userId: user.id, assignmentId: assignment.id })
                            }
                            className="text-red-300 transition hover:text-red-200"
                          >
                            ×
                          </button>
                        ) : null}
                      </span>
                    ))}
                  </div>
                )}

                {canAssign ? (
                  grantingFor === user.id ? (
                    <div className="mt-4 flex flex-wrap items-end gap-3">
                      <div className="min-w-[180px]">
                        <label className="mb-2 block text-xs text-gray-400">Role</label>
                        <Select
                          options={ORGANIZATION_ROLES.map((r) => ({ value: r.code, label: r.label }))}
                          value={grantRoleCode}
                          onChange={setGrantRoleCode}
                        />
                      </div>
                      <div className="min-w-[180px]">
                        <label className="mb-2 block text-xs text-gray-400">Organization</label>
                        <Select
                          options={unitOptions}
                          value={grantScopeId || units[0]?.id}
                          onChange={setGrantScopeId}
                        />
                      </div>
                      <Button
                        className="btn-gradient px-4 py-2 text-sm"
                        disabled={grant.isPending}
                        onClick={() => grant.mutate(user.id)}
                      >
                        {grant.isPending ? 'Granting…' : 'Grant'}
                      </Button>
                      <Button
                        variant="secondary"
                        className="px-4 py-2 text-sm"
                        onClick={() => setGrantingFor(null)}
                      >
                        Cancel
                      </Button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setGrantingFor(user.id)}
                      className="mt-4 text-sm text-accent-cyan transition hover:text-accent-cyan/80"
                    >
                      + Grant a role
                    </button>
                  )
                ) : null}
              </div>
            </Card>
          ))}
        </div>
      )}
    </SettingsShell>
  )
}
