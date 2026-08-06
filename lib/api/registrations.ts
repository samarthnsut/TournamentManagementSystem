import { request } from './client'

export type RegistrationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'
export type MemberRole = 'CAPTAIN' | 'PLAYER' | 'COACH'

/** A JSON Schema document, as authored by the organizer and stored per form version. */
export type FormSchema = {
  type?: string
  required?: string[]
  properties?: Record<string, FormFieldSchema>
  additionalProperties?: boolean
}

export type FormFieldSchema = {
  type?: string
  title?: string
  description?: string
  format?: string
  enum?: string[]
  minimum?: number
  maximum?: number
  maxLength?: number
  pattern?: string
}

export type FormDefinition = {
  id: string
  competitionId: string
  version: number
  schema: FormSchema
  isActive: boolean
  createdAt: string
}

export type TeamMember = {
  id?: string
  fullName: string
  dateOfBirth?: string | null
  memberRole?: MemberRole
  jerseyNumber?: number | null
}

export type Participant = {
  id: string
  participantType: 'INDIVIDUAL' | 'TEAM' | 'ORGANIZATION'
  displayName: string
  contactEmail: string | null
  members: TeamMember[]
}

export type Registration = {
  id: string
  competitionId: string
  status: RegistrationStatus
  participant: Participant
  /** The form version these answers were validated against — never a newer one. */
  formDefinitionId: string | null
  formVersion: number
  answers: Record<string, unknown> | null
  submittedAt: string
  decidedAt: string | null
  withdrawnAt: string | null
}

export function getFormDefinitions(competitionId: string) {
  return request<FormDefinition[]>(`/competitions/${competitionId}/form-definitions`)
}

/** 409 NO_ACTIVE_FORM_DEFINITION when the organizer has not published one yet. */
export function getActiveFormDefinition(competitionId: string) {
  return request<FormDefinition>(`/competitions/${competitionId}/form-definitions/active`)
}

export function publishFormDefinition(competitionId: string, schema: FormSchema) {
  return request<FormDefinition>(`/competitions/${competitionId}/form-definitions`, {
    method: 'POST',
    body: JSON.stringify({ schema }),
  })
}

export function replaceFormSchema(formDefinitionId: string, schema: FormSchema) {
  return request<FormDefinition>(`/form-definitions/${formDefinitionId}`, {
    method: 'PUT',
    body: JSON.stringify({ schema }),
  })
}

export function getRegistrations(competitionId: string) {
  return request<Registration[]>(`/competitions/${competitionId}/registrations`)
}

export function submitRegistration(payload: {
  competitionId: string
  participant: {
    participantType: 'INDIVIDUAL' | 'TEAM' | 'ORGANIZATION'
    displayName: string
    contactEmail?: string
    members?: TeamMember[]
  }
  answers: Record<string, unknown>
}) {
  return request<Registration>('/registrations', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function withdrawRegistration(registrationId: string) {
  return request<Registration>(`/registrations/${registrationId}/withdraw`, {
    method: 'POST',
    body: JSON.stringify({}),
  })
}

export function getCompetition(competitionId: string) {
  return request<import('./tournaments').Competition>(`/competitions/${competitionId}`)
}
