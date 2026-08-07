import { request } from './client'

export type Venue = {
  id: string
  organizationUnitId: string
  name: string
  addressLine: string | null
  city: string | null
  state: string | null
  capacity: number | null
  createdAt: string
}

export type VenuePayload = {
  organizationUnitId: string
  name: string
  addressLine?: string | null
  city?: string | null
  state?: string | null
  capacity?: number | null
}

export function getVenues() {
  return request<Venue[]>('/venues')
}

export function createVenue(payload: VenuePayload) {
  return request<Venue>('/venues', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateVenue(id: string, payload: VenuePayload) {
  return request<Venue>(`/venues/${id}`, { method: 'PATCH', body: JSON.stringify(payload) })
}

export function archiveVenue(id: string) {
  return request<void>(`/venues/${id}`, { method: 'DELETE' })
}
