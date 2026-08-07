import { request } from './client'

/** The seven system roles seeded in V4__seed_rbac.sql. There is no /roles endpoint to read. */
export const SYSTEM_ROLES = [
  { code: 'TENANT_ADMIN', label: 'Tenant Admin', scope: 'ORGANIZATION', blurb: 'Full control of an organization subtree' },
  { code: 'ORG_OFFICIAL', label: 'Organization Official', scope: 'ORGANIZATION', blurb: 'Operational staff within a subtree' },
  { code: 'TOURNAMENT_ADMIN', label: 'Tournament Admin', scope: 'TOURNAMENT', blurb: 'Administers a single tournament' },
  { code: 'COMPETITION_OFFICIAL', label: 'Competition Official', scope: 'COMPETITION', blurb: 'Runs one competition on the day' },
  { code: 'PARTICIPANT_USER', label: 'Participant', scope: 'GLOBAL', blurb: 'Manages their own entries' },
  { code: 'PUBLIC_VIEWER', label: 'Public Viewer', scope: 'GLOBAL', blurb: 'Read-only visitor' },
] as const

export type ScopeType = 'GLOBAL' | 'ORGANIZATION' | 'TOURNAMENT' | 'COMPETITION'
export type UserStatus = 'ACTIVE' | 'INVITED' | 'SUSPENDED' | 'DEACTIVATED'

export type RoleAssignment = {
  id: string
  userId: string
  roleCode: string
  scopeType: ScopeType
  scopeId: string | null
}

export type UserListItem = {
  id: string
  email: string
  displayName: string
  status: UserStatus
  createdAt: string
  roles: RoleAssignment[]
}

export type InvitedUser = {
  id: string
  email: string
  displayName: string
  status: UserStatus
  organizationUnitId: string
  /** Shown once, at invite time: there is no email delivery yet, so this is how they get in. */
  inviteToken: string
}

export function getUsers() {
  return request<UserListItem[]>('/users')
}

export function inviteUser(payload: {
  email: string
  displayName: string
  organizationUnitId: string
  initialRole?: { roleCode: string; scopeType: ScopeType; scopeId?: string }
}) {
  return request<InvitedUser>('/users/invite', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getRoleAssignments(userId: string) {
  return request<RoleAssignment[]>(`/users/${userId}/role-assignments`)
}

export function grantRole(
  userId: string,
  payload: { roleCode: string; scopeType: ScopeType; scopeId?: string },
) {
  return request<RoleAssignment>(`/users/${userId}/role-assignments`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function revokeRole(userId: string, assignmentId: string) {
  return request<void>(`/users/${userId}/role-assignments/${assignmentId}`, { method: 'DELETE' })
}
