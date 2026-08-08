'use client'

import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Header from '../../../components/Header'
import RequireAuth from '../../../components/RequireAuth'
import Button from '../../../components/ui/Button'
import Card from '../../../components/ui/Card'
import Input from '../../../components/ui/Input'
import Modal from '../../../components/ui/Modal'
import StatusBadge from '../../../components/ui/StatusBadge'
import { ApiError } from '../../../lib/api/client'
import { clearStoredAuth } from '../../../lib/api/session'
import { SYSTEM_ROLES } from '../../../lib/api/users'
import { changePassword, getProfile, updateProfile } from '../../../lib/api/profile'
import { getOrganizationUnits } from '../../../lib/api/organizations'

export default function ProfilePage() {
  const queryClient = useQueryClient()

  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [savedAt, setSavedAt] = useState<number | null>(null)
  const [detailsError, setDetailsError] = useState('')

  const [isChangingPassword, setIsChangingPassword] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordError, setPasswordError] = useState('')

  const profileQuery = useQuery({ queryKey: ['profile'], queryFn: getProfile })
  const unitsQuery = useQuery({ queryKey: ['organization-units'], queryFn: getOrganizationUnits })

  const profile = profileQuery.data

  // Seed the form once the profile arrives, and again whenever it changes underneath us.
  useEffect(() => {
    if (profile) {
      setFullName(profile.fullName)
      setPhone(profile.phone ?? '')
    }
  }, [profile])

  const saveDetails = useMutation({
    mutationFn: () => updateProfile({ fullName: fullName.trim(), phone: phone.trim() }),
    onSuccess: async () => {
      setDetailsError('')
      setFieldErrors({})
      setSavedAt(Date.now())
      await queryClient.invalidateQueries({ queryKey: ['profile'] })
    },
    onError: (cause: unknown) => {
      // The server names the offending fields; show them against the inputs rather than as one
      // sentence the reader has to map back themselves.
      if (cause instanceof ApiError && cause.fieldErrors.length > 0) {
        setFieldErrors(cause.fieldMessages())
        setDetailsError('')
        return
      }
      setDetailsError(cause instanceof Error ? cause.message : 'Could not save your details')
    },
  })

  const submitPassword = useMutation({
    mutationFn: () => changePassword({ currentPassword, newPassword }),
    onSuccess: () => {
      // Every session just ended server-side, including this one. Clearing the stored token and
      // sending them to sign in is the honest reflection of that; leaving them on a dashboard
      // whose next request 401s is not.
      clearStoredAuth()
      window.location.href = '/TournamentManagementSystem/signin'
    },
    onError: (cause: unknown) =>
      setPasswordError(cause instanceof Error ? cause.message : 'Could not change your password'),
  })

  const units = unitsQuery.data ?? []
  const unitName = (id: string | null) =>
    units.find((unit) => unit.id === id)?.name ?? (id ? 'another organization' : 'everywhere')
  const roleLabel = (code: string) =>
    SYSTEM_ROLES.find((role) => role.code === code)?.label ?? code

  const isDirty =
    profile !== undefined &&
    (fullName !== profile.fullName || phone !== (profile.phone ?? ''))

  const passwordsMatch = newPassword === confirmPassword
  const canSubmitPassword =
    currentPassword.length > 0 && newPassword.length >= 8 && passwordsMatch && !submitPassword.isPending

  return (
    <RequireAuth>
      <Header />
      <main className="min-h-screen bg-dark-bg">
        <div className="mx-auto max-w-3xl px-6 py-8 sm:px-8 sm:py-12">
          <h1 className="mb-2 text-2xl font-bold text-white sm:text-3xl">Your profile</h1>
          <p className="mb-8 text-gray-400">
            Your details, your password, and what you are allowed to do.
          </p>

          {profileQuery.isLoading ? (
            <p className="text-sm text-gray-400">Loading your profile…</p>
          ) : !profile ? (
            <Card>
              <p className="text-sm text-red-300">
                {profileQuery.error instanceof Error
                  ? profileQuery.error.message
                  : 'Your profile could not be loaded.'}
              </p>
            </Card>
          ) : (
            <div className="space-y-6">
              <Card>
                <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
                  <h2 className="text-lg font-semibold text-white">Details</h2>
                  <StatusBadge status={profile.status} />
                </div>

                {detailsError ? (
                  <div
                    role="alert"
                    className="mb-5 rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200"
                  >
                    {detailsError}
                  </div>
                ) : null}

                <form
                  className="space-y-4"
                  onSubmit={(event) => {
                    event.preventDefault()
                    saveDetails.mutate()
                  }}
                >
                  <div>
                    <label htmlFor="fullName" className="mb-2 block text-sm font-medium text-gray-300">
                      Full name
                    </label>
                    <Input
                      id="fullName"
                      value={fullName}
                      onChange={(event) => setFullName(event.target.value)}
                      required
                    />
                    {fieldErrors.fullName ? (
                      <p className="mt-1 text-xs text-red-300">{fieldErrors.fullName}</p>
                    ) : null}
                  </div>

                  <div>
                    <label htmlFor="phone" className="mb-2 block text-sm font-medium text-gray-300">
                      Phone <span className="font-normal text-gray-600">(optional)</span>
                    </label>
                    <Input
                      id="phone"
                      value={phone}
                      placeholder="+91 98765 43210"
                      onChange={(event) => setPhone(event.target.value)}
                    />
                    {fieldErrors.phone ? (
                      <p className="mt-1 text-xs text-red-300">{fieldErrors.phone}</p>
                    ) : null}
                  </div>

                  <div>
                    <label className="mb-2 block text-sm font-medium text-gray-300">Email</label>
                    <Input value={profile.email} disabled readOnly />
                    <p className="mt-2 text-xs text-gray-600">
                      Email cannot be changed here yet. Doing it safely needs a confirmation sent to
                      the new address — without one, a typo would lock you out of your own account.
                    </p>
                  </div>

                  <div className="flex flex-wrap items-center gap-3 pt-1">
                    <Button
                      type="submit"
                      className="btn-gradient"
                      disabled={!isDirty || saveDetails.isPending || fullName.trim().length === 0}
                    >
                      {saveDetails.isPending ? 'Saving…' : 'Save changes'}
                    </Button>
                    {savedAt && !isDirty ? (
                      <span className="text-sm text-green-300">Saved</span>
                    ) : null}
                  </div>
                </form>
              </Card>

              <Card>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h2 className="text-lg font-semibold text-white">Password</h2>
                    <p className="mt-1 text-sm text-gray-500">
                      Changing it signs you out everywhere, including here.
                    </p>
                  </div>
                  <Button
                    variant="secondary"
                    className="px-4 py-2 text-sm"
                    onClick={() => {
                      setPasswordError('')
                      setCurrentPassword('')
                      setNewPassword('')
                      setConfirmPassword('')
                      setIsChangingPassword(true)
                    }}
                  >
                    Change password
                  </Button>
                </div>
              </Card>

              <Card>
                <h2 className="mb-1 text-lg font-semibold text-white">What you can do</h2>
                <p className="mb-5 text-sm text-gray-500">
                  Your roles decide this. Only someone who can assign roles can change it.
                </p>

                {profile.roles.length === 0 ? (
                  <p className="text-sm text-gray-500">You hold no roles yet.</p>
                ) : (
                  <div className="mb-5 flex flex-wrap gap-2">
                    {profile.roles.map((assignment) => (
                      <span
                        key={assignment.id}
                        className="rounded-full border border-dark-border bg-white/5 px-3 py-1 text-xs text-gray-300"
                      >
                        <span className="font-semibold text-gray-200">
                          {roleLabel(assignment.roleCode)}
                        </span>
                        <span className="ml-2 text-gray-500">at {unitName(assignment.scopeId)}</span>
                      </span>
                    ))}
                  </div>
                )}

                <details>
                  <summary className="cursor-pointer text-sm text-gray-500 transition hover:text-gray-300">
                    {profile.permissions.length} permissions in detail
                  </summary>
                  <div className="mt-3 flex flex-wrap gap-1.5">
                    {profile.permissions.map((permission) => (
                      <code
                        key={permission}
                        className="rounded border border-dark-border bg-dark-bg px-2 py-0.5 font-mono text-xs text-gray-500"
                      >
                        {permission}
                      </code>
                    ))}
                  </div>
                </details>
              </Card>
            </div>
          )}
        </div>
      </main>

      <Modal
        isOpen={isChangingPassword}
        title="Change your password"
        description="You will be signed out of every device and will need to sign in again."
        error={passwordError}
        size="sm"
        onClose={() => setIsChangingPassword(false)}
      >
        <form
          id="change-password-form"
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault()
            submitPassword.mutate()
          }}
        >
          <div>
            <label htmlFor="currentPassword" className="mb-2 block text-sm font-medium text-gray-300">
              Current password
            </label>
            <Input
              id="currentPassword"
              type="password"
              autoComplete="current-password"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              required
            />
          </div>

          <div>
            <label htmlFor="newPassword" className="mb-2 block text-sm font-medium text-gray-300">
              New password
            </label>
            <Input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              required
            />
            <p className="mt-1 text-xs text-gray-600">At least 8 characters.</p>
          </div>

          <div>
            <label htmlFor="confirmPassword" className="mb-2 block text-sm font-medium text-gray-300">
              Confirm new password
            </label>
            <Input
              id="confirmPassword"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              required
            />
            {/* Caught here rather than at the server, which never sees the confirmation field. */}
            {confirmPassword.length > 0 && !passwordsMatch ? (
              <p className="mt-1 text-xs text-red-300">The two passwords do not match.</p>
            ) : null}
          </div>
        </form>

        <div className="mt-6 flex flex-wrap justify-end gap-3">
          <Button
            variant="secondary"
            className="px-5 py-2 text-sm"
            onClick={() => setIsChangingPassword(false)}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            form="change-password-form"
            className="btn-gradient px-5 py-2 text-sm"
            disabled={!canSubmitPassword}
          >
            {submitPassword.isPending ? 'Changing…' : 'Change password'}
          </Button>
        </div>
      </Modal>
    </RequireAuth>
  )
}
