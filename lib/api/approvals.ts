import { request } from './client'

export type ApprovalInstanceStatus = 'IN_PROGRESS' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export type ApprovalAction = {
  id: string
  stepLevel: number
  actorId: string
  decision: 'APPROVE' | 'REJECT'
  comment: string | null
  timestamp: string
}

export type ApprovalInstance = {
  id: string
  entityType: string
  entityId: string
  status: ApprovalInstanceStatus
  currentLevel: number
  totalLevels: number
  currentStepName: string | null
  currentStepRole: string | null
  /** Ready to render, e.g. "Awaiting State Approval (2 of 3)". */
  progressLabel: string
  actions: ApprovalAction[]
}

export type InboxItem = {
  registrationId: string
  competitionId: string
  participantName: string
  currentLevel: number
  totalLevels: number
  currentStepRole: string | null
  currentStepName: string | null
  progressLabel: string
  submittedAt: string
}

export type WorkflowStep = {
  id?: string
  level: number
  roleCode: string
  stepName?: string | null
  approvalRequired?: boolean
}

export type ApprovalWorkflow = {
  id: string
  organizationUnitId: string
  workflowName: string
  entityType: string
  isActive: boolean
  steps: WorkflowStep[]
  createdAt: string
}

export function getInbox() {
  return request<InboxItem[]>('/approvals/inbox')
}

/** 404 when the entry never entered a chain, which is the case under automatic acceptance. */
export function getRegistrationApproval(registrationId: string) {
  return request<ApprovalInstance>(`/registrations/${registrationId}/approval`)
}

export function approveRegistration(registrationId: string, comment?: string) {
  return request<ApprovalInstance>(`/registrations/${registrationId}/approve`, {
    method: 'POST',
    body: JSON.stringify({ comment: comment ?? null }),
  })
}

export function rejectRegistration(registrationId: string, comment: string) {
  return request<ApprovalInstance>(`/registrations/${registrationId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ comment }),
  })
}

export function getWorkflows() {
  return request<ApprovalWorkflow[]>('/approval-workflows')
}

export function createWorkflow(payload: {
  organizationUnitId: string
  workflowName: string
  steps: WorkflowStep[]
}) {
  return request<ApprovalWorkflow>('/approval-workflows', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
