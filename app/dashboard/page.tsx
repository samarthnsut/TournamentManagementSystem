'use client'

import Link from 'next/link'
import { useState, useMemo } from 'react'
import Header from '../../components/Header'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'

const allTournaments = [
  {
    id: '1',
    name: 'State Championships 2026',
    location: 'Mumbai, India',
    status: 'ongoing',
    startDate: '2026-09-10',
    endDate: '2026-09-14',
    athletes: 254,
    events: 18,
    slug: 'sample-tournament'
  },
  {
    id: '2',
    name: 'Inter-University Games',
    location: 'Delhi, India',
    status: 'upcoming',
    startDate: '2026-10-05',
    endDate: '2026-10-12',
    athletes: 512,
    events: 24,
    slug: 'inter-university-games'
  },
  {
    id: '3',
    name: 'District Qualifiers',
    location: 'Bangalore, India',
    status: 'completed',
    startDate: '2026-08-15',
    endDate: '2026-08-22',
    athletes: 180,
    events: 12,
    slug: 'district-qualifiers'
  },
  {
    id: '4',
    name: 'National Finals',
    location: 'Hyderabad, India',
    status: 'upcoming',
    startDate: '2026-11-20',
    endDate: '2026-11-27',
    athletes: 420,
    events: 32,
    slug: 'national-finals'
  }
]

function getStatusColor(status: string) {
  switch (status) {
    case 'ongoing':
      return {
        bg: 'bg-accent-cyan/10',
        border: 'border-accent-cyan/30',
        text: 'text-accent-cyan',
        badge: 'bg-accent-cyan/20 text-accent-cyan border border-accent-cyan/40'
      }
    case 'upcoming':
      return {
        bg: 'bg-accent-purple/10',
        border: 'border-accent-purple/30',
        text: 'text-accent-purple',
        badge: 'bg-accent-purple/20 text-accent-purple border border-accent-purple/40'
      }
    case 'completed':
      return {
        bg: 'bg-accent-orange/10',
        border: 'border-accent-orange/30',
        text: 'text-accent-orange',
        badge: 'bg-accent-orange/20 text-accent-orange border border-accent-orange/40'
      }
    default:
      return {
        bg: 'bg-gray-800/30',
        border: 'border-gray-700/30',
        text: 'text-gray-400',
        badge: 'bg-gray-800/20 text-gray-400 border border-gray-700/40'
      }
  }
}

export default function DashboardPage() {
  const [searchQuery, setSearchQuery] = useState('')
  const [filterStatus, setFilterStatus] = useState<string>('all')

  const filteredTournaments = useMemo(() => {
    return allTournaments.filter((tournament) => {
      const matchesSearch = tournament.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        tournament.location.toLowerCase().includes(searchQuery.toLowerCase())
      const matchesStatus = filterStatus === 'all' || tournament.status === filterStatus
      return matchesSearch && matchesStatus
    })
  }, [searchQuery, filterStatus])

  const stats = {
    total: allTournaments.length,
    ongoing: allTournaments.filter(t => t.status === 'ongoing').length,
    upcoming: allTournaments.filter(t => t.status === 'upcoming').length,
    completed: allTournaments.filter(t => t.status === 'completed').length
  }

  return (
    <>
      <Header />
      <main className="min-h-screen bg-dark-bg">
        {/* Hero Section */}
        <div className="border-b border-dark-border bg-gradient-to-b from-dark-surface to-dark-bg py-8 sm:py-12">
          <div className="mx-auto max-w-7xl px-6 sm:px-8">
            <div className="flex flex-col gap-2 mb-6">
              <h1 className="text-3xl sm:text-4xl font-bold text-white">Tournament Dashboard</h1>
              <p className="text-gray-400">Manage and monitor all your tournaments in one place</p>
            </div>

            {/* Stats Grid */}
            <div className="grid gap-4 grid-cols-2 sm:grid-cols-4">
              <div className="rounded-lg border border-dark-border bg-dark-bg/50 px-4 py-3">
                <p className="text-sm text-gray-400">Total</p>
                <p className="text-2xl font-bold text-white mt-1">{stats.total}</p>
              </div>
              <div className="rounded-lg border border-accent-cyan/20 bg-accent-cyan/5 px-4 py-3">
                <p className="text-sm text-accent-cyan">Ongoing</p>
                <p className="text-2xl font-bold text-accent-cyan mt-1">{stats.ongoing}</p>
              </div>
              <div className="rounded-lg border border-accent-purple/20 bg-accent-purple/5 px-4 py-3">
                <p className="text-sm text-accent-purple">Upcoming</p>
                <p className="text-2xl font-bold text-accent-purple mt-1">{stats.upcoming}</p>
              </div>
              <div className="rounded-lg border border-accent-orange/20 bg-accent-orange/5 px-4 py-3">
                <p className="text-sm text-accent-orange">Completed</p>
                <p className="text-2xl font-bold text-accent-orange mt-1">{stats.completed}</p>
              </div>
            </div>
          </div>
        </div>

        {/* Main Content */}
        <div className="mx-auto max-w-7xl px-6 sm:px-8 py-8 sm:py-12">
          {/* Controls */}
          <div className="flex flex-col gap-4 sm:gap-6 mb-8">
            <div className="flex flex-col sm:flex-row gap-4 sm:items-center sm:justify-between">
              <Input
                placeholder="Search tournaments by name or location..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="flex-1"
              />
              <Link href="/dashboard/create">
                <Button className="btn-gradient whitespace-nowrap w-full sm:w-auto">
                  + Create Tournament
                </Button>
              </Link>
            </div>

            {/* Filter Buttons */}
            <div className="flex flex-wrap gap-2">
              {['all', 'ongoing', 'upcoming', 'completed'].map((status) => (
                <button
                  key={status}
                  onClick={() => setFilterStatus(status)}
                  className={`px-4 py-2 rounded-lg border font-medium capitalize transition ${
                    filterStatus === status
                      ? 'border-accent-purple bg-accent-purple/20 text-accent-purple'
                      : 'border-dark-border bg-dark-surface text-gray-300 hover:border-gray-600'
                  }`}
                >
                  {status}
                </button>
              ))}
            </div>
          </div>

          {/* Tournament Grid */}
          {filteredTournaments.length > 0 ? (
            <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
              {filteredTournaments.map((tournament) => {
                const colors = getStatusColor(tournament.status)
                return (
                  <Card
                    key={tournament.id}
                    className={`group relative cursor-pointer overflow-hidden transition-all duration-300 hover:-translate-y-1 hover:shadow-2xl hover:shadow-accent-purple/20 border-l-4 ${colors.border}`}
                  >
                    <div className="absolute top-0 left-0 w-1 h-full bg-gradient-to-b from-accent-purple via-accent-cyan to-accent-orange opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
                    
                    <div className="flex flex-col gap-4">
                      {/* Header */}
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex-1">
                          <h3 className="text-lg font-bold text-white group-hover:text-accent-purple transition">
                            {tournament.name}
                          </h3>
                          <p className="mt-1 text-sm text-gray-400">📍 {tournament.location}</p>
                        </div>
                        <span className={`rounded-full px-3 py-1 text-xs font-semibold capitalize whitespace-nowrap ${colors.badge}`}>
                          {tournament.status}
                        </span>
                      </div>

                      {/* Divider */}
                      <div className="border-t border-dark-border" />

                      {/* Stats */}
                      <div className="grid grid-cols-3 gap-3">
                        <div className={`rounded-lg ${colors.bg} border ${colors.border} p-3 text-center`}>
                          <p className={`text-xs font-semibold ${colors.text}`}>Athletes</p>
                          <p className="text-lg font-bold text-white mt-1">{tournament.athletes}</p>
                        </div>
                        <div className={`rounded-lg ${colors.bg} border ${colors.border} p-3 text-center`}>
                          <p className={`text-xs font-semibold ${colors.text}`}>Events</p>
                          <p className="text-lg font-bold text-white mt-1">{tournament.events}</p>
                        </div>
                        <div className={`rounded-lg ${colors.bg} border ${colors.border} p-3 text-center`}>
                          <p className={`text-xs font-semibold ${colors.text}`}>Days</p>
                          <p className="text-lg font-bold text-white mt-1">
                            {Math.ceil((new Date(tournament.endDate).getTime() - new Date(tournament.startDate).getTime()) / (1000 * 60 * 60 * 24)) + 1}
                          </p>
                        </div>
                      </div>

                      {/* Date Info */}
                      <div className="text-xs text-gray-400">
                        <p>{new Date(tournament.startDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })} - {new Date(tournament.endDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}</p>
                      </div>

                      {/* Actions */}
                      <div className="flex gap-2 pt-2">
                        <button className="flex-1 rounded-lg border border-accent-orange/30 bg-accent-orange/5 px-3 py-2 text-sm font-medium text-accent-orange hover:bg-accent-orange/10 transition">
                          Manage
                        </button>
                        <button className="flex-1 rounded-lg border border-dark-border bg-dark-surface px-3 py-2 text-sm font-medium text-gray-300 hover:border-accent-purple hover:text-accent-purple transition">
                          Settings
                        </button>
                      </div>
                    </div>
                  </Card>
                )
              })}
            </div>
          ) : (
            <div className="rounded-2xl border border-dark-border bg-dark-surface p-12 text-center">
              <div className="text-4xl mb-4">🔍</div>
              <p className="text-gray-400 mb-6">No tournaments found matching your search.</p>
              <Link href="/dashboard/create">
                <Button className="btn-gradient inline-flex">
                  Create Your First Tournament
                </Button>
              </Link>
            </div>
          )}
        </div>
      </main>
    </>
  )
}
