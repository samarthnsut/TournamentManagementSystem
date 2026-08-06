import { request } from './client'

export type OrganizationUnit = {
  id: string
  parentOrganizationUnitId: string | null
  name: string
  slug: string
  type: string
  status: string
  createdAt: string
}

/** Returns only the units the signed-in user can actually see. */
export function getOrganizationUnits() {
  return request<OrganizationUnit[]>('/organization-units')
}
