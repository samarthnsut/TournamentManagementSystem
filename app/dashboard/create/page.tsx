'use client'

import { useState } from 'react'
import Link from 'next/link'
import Header from '../../../components/Header'
import Button from '../../../components/ui/Button'
import Input from '../../../components/ui/Input'
import Card from '../../../components/ui/Card'

export default function CreateTournamentPage() {
  const [formData, setFormData] = useState({
    name: '',
    location: '',
    startDate: '',
    endDate: '',
    description: '',
    maxAthletes: '',
    category: 'athletics'
  })

  const [isSubmitted, setIsSubmitted] = useState(false)

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    })
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    console.log('Form submitted:', formData)
    setIsSubmitted(true)
    setTimeout(() => setIsSubmitted(false), 3000)
  }

  const categories = [
    { value: 'athletics', label: '🏃 Athletics' },
    { value: 'basketball', label: '🏀 Basketball' },
    { value: 'swimming', label: '🏊 Swimming' },
    { value: 'wrestling', label: '🤼 Wrestling' },
    { value: 'cricket', label: '🏏 Cricket' },
    { value: 'badminton', label: '🏸 Badminton' },
    { value: 'tennis', label: '🎾 Tennis' },
    { value: 'multi-sport', label: '⚽ Multi-Sport' }
  ]

  return (
    <>
      <Header />
      <main className="min-h-screen bg-dark-bg">
        {/* Hero Section */}
        <div className="border-b border-dark-border bg-gradient-to-b from-dark-surface to-dark-bg py-8 sm:py-12">
          <div className="mx-auto max-w-5xl px-6 sm:px-8">
            <Link href="/dashboard" className="text-sm text-accent-cyan hover:text-accent-cyan/80 transition mb-6 inline-flex items-center gap-1">
              ← Back to Dashboard
            </Link>
            <h1 className="text-3xl sm:text-4xl font-bold text-white mb-2">Create New Tournament</h1>
            <p className="text-gray-400">Set up a new sports tournament with events, categories, and athlete management</p>
          </div>
        </div>

        {/* Main Content */}
        <div className="mx-auto max-w-5xl px-6 sm:px-8 py-8 sm:py-12">
          <div className="grid gap-8 lg:grid-cols-3">
            {/* Form */}
            <Card className="lg:col-span-2">
              <form onSubmit={handleSubmit} className="space-y-6">
                {/* Section: Basic Info */}
                <div>
                  <h2 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                    <span className="text-2xl">📋</span>
                    Basic Information
                  </h2>
                  <div className="space-y-4">
                    {/* Tournament Name */}
                    <div>
                      <label htmlFor="name" className="block text-sm font-medium text-gray-300 mb-2">
                        Tournament Name *
                      </label>
                      <Input
                        id="name"
                        name="name"
                        type="text"
                        placeholder="e.g., State Championships 2026"
                        value={formData.name}
                        onChange={handleChange}
                        required
                      />
                    </div>

                    {/* Location */}
                    <div>
                      <label htmlFor="location" className="block text-sm font-medium text-gray-300 mb-2">
                        Location *
                      </label>
                      <Input
                        id="location"
                        name="location"
                        type="text"
                        placeholder="e.g., Mumbai, India"
                        value={formData.location}
                        onChange={handleChange}
                        required
                      />
                    </div>

                    {/* Category */}
                    <div>
                      <label htmlFor="category" className="block text-sm font-medium text-gray-300 mb-2">
                        Primary Sport Category *
                      </label>
                      <select
                        id="category"
                        name="category"
                        value={formData.category}
                        onChange={handleChange}
                        className="w-full rounded-lg border border-dark-border bg-dark-surface px-4 py-3 text-white outline-none transition focus:border-accent-purple focus:ring-2 focus:ring-accent-purple/20"
                      >
                        {categories.map(cat => (
                          <option key={cat.value} value={cat.value}>{cat.label}</option>
                        ))}
                      </select>
                    </div>
                  </div>
                </div>

                {/* Divider */}
                <div className="border-t border-dark-border" />

                {/* Section: Date & Capacity */}
                <div>
                  <h2 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                    <span className="text-2xl">📅</span>
                    Schedule & Capacity
                  </h2>
                  <div className="space-y-4">
                    {/* Date Range */}
                    <div className="grid gap-4 sm:grid-cols-2">
                      <div>
                        <label htmlFor="startDate" className="block text-sm font-medium text-gray-300 mb-2">
                          Start Date *
                        </label>
                        <Input
                          id="startDate"
                          name="startDate"
                          type="date"
                          value={formData.startDate}
                          onChange={handleChange}
                          required
                        />
                      </div>
                      <div>
                        <label htmlFor="endDate" className="block text-sm font-medium text-gray-300 mb-2">
                          End Date *
                        </label>
                        <Input
                          id="endDate"
                          name="endDate"
                          type="date"
                          value={formData.endDate}
                          onChange={handleChange}
                          required
                        />
                      </div>
                    </div>

                    {/* Max Athletes */}
                    <div>
                      <label htmlFor="maxAthletes" className="block text-sm font-medium text-gray-300 mb-2">
                        Max Athletes (Optional)
                      </label>
                      <Input
                        id="maxAthletes"
                        name="maxAthletes"
                        type="number"
                        placeholder="Leave empty for unlimited"
                        value={formData.maxAthletes}
                        onChange={handleChange}
                      />
                    </div>
                  </div>
                </div>

                {/* Divider */}
                <div className="border-t border-dark-border" />

                {/* Section: Description */}
                <div>
                  <h2 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                    <span className="text-2xl">📝</span>
                    Description & Details
                  </h2>
                  <div>
                    <label htmlFor="description" className="block text-sm font-medium text-gray-300 mb-2">
                      Tournament Description
                    </label>
                    <textarea
                      id="description"
                      name="description"
                      placeholder="Describe your tournament, eligibility criteria, rules, and any special requirements for participants..."
                      value={formData.description}
                      onChange={handleChange}
                      rows={5}
                      className="w-full rounded-lg border border-dark-border bg-dark-surface px-4 py-3 text-white outline-none transition focus:border-accent-purple focus:ring-2 focus:ring-accent-purple/20 placeholder:text-gray-500"
                    />
                  </div>
                </div>

                {/* Form Actions */}
                <div className="flex flex-col gap-3 border-t border-dark-border pt-6 sm:flex-row">
                  <Link href="/dashboard" className="flex-1">
                    <Button variant="secondary" className="w-full">
                      Cancel
                    </Button>
                  </Link>
                  <button
                    type="submit"
                    className="flex-1 rounded-full bg-gradient-cta px-6 py-3 font-semibold text-white shadow-lg hover:shadow-xl hover:scale-105 transition-all"
                  >
                    {isSubmitted ? '✓ Created!' : 'Create Tournament'}
                  </button>
                </div>
              </form>
            </Card>

            {/* Sidebar: Help & Benefits */}
            <div className="lg:col-span-1 space-y-6">
              {/* Quick Tips */}
              <Card className="bg-accent-purple/5 border-accent-purple/30">
                <h3 className="text-lg font-semibold text-accent-purple mb-4 flex items-center gap-2">
                  <span>💡</span>
                  Quick Tips
                </h3>
                <ul className="space-y-3 text-sm text-gray-300">
                  <li className="flex gap-2">
                    <span className="text-accent-cyan">→</span>
                    <span>Use clear, descriptive names for easy discovery</span>
                  </li>
                  <li className="flex gap-2">
                    <span className="text-accent-cyan">→</span>
                    <span>Set realistic dates and participant limits</span>
                  </li>
                  <li className="flex gap-2">
                    <span className="text-accent-cyan">→</span>
                    <span>Add detailed descriptions to attract participants</span>
                  </li>
                  <li className="flex gap-2">
                    <span className="text-accent-cyan">→</span>
                    <span>You can edit all details after creation</span>
                  </li>
                </ul>
              </Card>

              {/* What Happens Next */}
              <Card className="bg-accent-cyan/5 border-accent-cyan/30">
                <h3 className="text-lg font-semibold text-accent-cyan mb-4 flex items-center gap-2">
                  <span>✨</span>
                  What Happens Next?
                </h3>
                <ul className="space-y-3 text-sm text-gray-300">
                  <li className="flex gap-3">
                    <span className="text-accent-orange font-bold">1.</span>
                    <span>Your tournament gets a unique public URL</span>
                  </li>
                  <li className="flex gap-3">
                    <span className="text-accent-orange font-bold">2.</span>
                    <span>Athletes can register and view details</span>
                  </li>
                  <li className="flex gap-3">
                    <span className="text-accent-orange font-bold">3.</span>
                    <span>Add events, fixtures, and manage brackets</span>
                  </li>
                  <li className="flex gap-3">
                    <span className="text-accent-orange font-bold">4.</span>
                    <span>Track results and live leaderboards</span>
                  </li>
                </ul>
              </Card>

              {/* Support */}
              <Card className="bg-gradient-to-br from-dark-surface to-dark-bg border-dark-border/50">
                <h3 className="text-lg font-semibold text-white mb-3">Need Help?</h3>
                <p className="text-sm text-gray-400 mb-4">
                  Check our documentation or contact support for assistance with tournament setup.
                </p>
                <Button variant="secondary" className="w-full text-sm">
                  View Documentation
                </Button>
              </Card>
            </div>
          </div>
        </div>
      </main>
    </>
  )
}
