export type Tournament = {
  id: string
  name: string
  slug: string
  description: string | null
  location: string
  status: 'ongoing' | 'upcoming' | 'completed'
  startDate: string
  endDate: string
  athletes: number
  events: number
}

export type CreateTournamentPayload = {
  name: string
  location: string
  description?: string
  startDate: string
  endDate: string
  maxAthletes?: number
  category?: string
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })

  if (!response.ok) {
    const fallbackMessage = `Request failed with status ${response.status}`
    const errorBody = await response.json().catch(() => null) as { message?: string } | null
    throw new Error(errorBody?.message ?? fallbackMessage)
  }

  return response.json() as Promise<T>
}

export function getTournaments() {
  return request<Tournament[]>('/tournaments')
}

export function createTournament(payload: CreateTournamentPayload) {
  return request<Tournament>('/tournaments', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

