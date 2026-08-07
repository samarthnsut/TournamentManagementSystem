import { request } from './client'

export type AuditLogEntry = {
  id: string
  actorId: string | null
  /** "System" when there was no human actor — an auto-approval, for instance. */
  actorName: string | null
  action: string
  entityType: string
  entityId: string
  beforeState: Record<string, unknown> | null
  afterState: Record<string, unknown> | null
  organizationUnitId: string | null
  ipAddress: string | null
  timestamp: string
}

export function getAuditLog(limit = 100) {
  return request<AuditLogEntry[]>(`/audit-logs?limit=${limit}`)
}

/** The "who changed this" history for one entity, newest first. */
export function getEntityHistory(entityType: string, entityId: string) {
  return request<AuditLogEntry[]>(
    `/audit-logs/entity?entityType=${encodeURIComponent(entityType)}&entityId=${entityId}`,
  )
}
