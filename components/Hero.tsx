import Link from 'next/link'
import Button from './ui/Button'

const stats = [
  { label: 'Federation clients', value: '18+', accent: 'text-accent-orange' },
  { label: 'Tournaments hosted', value: '320+', accent: 'text-accent-pink' },
  { label: 'Athletes registered', value: '12K+', accent: 'text-accent-purple' },
  { label: 'Live events yearly', value: '48+', accent: 'text-accent-blue' }
]

export default function Hero() {
  return (
    <section className="relative overflow-hidden bg-gradient-dark text-white">
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute -right-24 top-16 h-80 w-80 animate-float rounded-full bg-accent-orange/30 blur-3xl" />
        <div className="absolute left-0 top-1/3 h-72 w-72 animate-float-delayed rounded-full bg-accent-pink/25 blur-3xl" />
        <div className="absolute bottom-0 right-1/3 h-96 w-96 animate-pulse-glow rounded-full bg-accent-purple/20 blur-3xl" />
        <div className="absolute -bottom-20 left-1/4 h-64 w-64 animate-float rounded-full bg-accent-blue/25 blur-3xl" />
      </div>

      <div className="relative mx-auto max-w-7xl px-6 py-20 sm:py-28 lg:py-32">
        <div className="flex flex-col gap-14 lg:flex-row lg:items-center lg:justify-between">
          <div className="max-w-2xl space-y-6">
            <div className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-dark-surface/50 px-4 py-2 backdrop-blur-sm">
              <span className="h-2 w-2 rounded-full bg-gradient-brand" />
              <span className="text-sm font-medium text-gray-200">
                Infinite possibilities for tournament management
              </span>
            </div>

            <h1 className="text-4xl font-bold leading-tight tracking-tight sm:text-5xl lg:text-6xl">
              <span className="text-gradient-brand">Revolutionize</span> sports tournament management
            </h1>

            <p className="text-lg leading-relaxed text-gray-300">
              Empower federations, organizers, and athletes with a cutting-edge platform—from
              registration and fixtures to live leaderboards and multi-tenant event portals.
            </p>

            <div className="flex flex-col gap-4 pt-2 sm:flex-row">
              <Link href="/signup">
                <Button className="btn-gradient w-full sm:w-auto">Get started</Button>
              </Link>
              <Link href="/signin">
                <Button variant="secondary" className="w-full sm:w-auto">
                  Request demo
                </Button>
              </Link>
            </div>
          </div>

          <div className="grid w-full gap-4 sm:grid-cols-2 lg:max-w-md">
            {stats.map((stat) => (
              <div
                key={stat.label}
                className="glass-panel p-6 transition duration-300 hover:-translate-y-1 hover:border-white/20 hover:shadow-xl"
              >
                <p className="text-xs font-semibold uppercase tracking-[0.2em] text-gray-400">
                  {stat.label}
                </p>
                <p className={`mt-3 text-4xl font-bold ${stat.accent}`}>{stat.value}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}
