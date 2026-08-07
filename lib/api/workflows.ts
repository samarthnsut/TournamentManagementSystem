import { request } from './client'

export type WorkflowStep = {
  id?: string
  level: number
  roleCode: string
  stepName: string | null
  approvalRequired: boolean
}

export type Workflow = {
  id: string
  organizationUnitId: string
  workflowName: string
  entityType: string
  isActive: boolean
  steps: WorkflowStep[]
  createdAt: string
}

export function getWorkflows() {
  return request<Workflow[]>('/approval-workflows')
}

export function createWorkflow(payload: {
  organizationUnitId: string
  workflowName: string
  steps: { level: number; roleCode: string; stepName?: string; approvalRequired?: boolean }[]
}) {
  return request<Workflow>('/approval-workflows', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/** Deactivates rather than deletes: instances already in flight keep their definition. */
export function deactivateWorkflow(id: string) {
  return request<void>(`/approval-workflows/${id}`, { method: 'DELETE' })
}
