import Header from '../components/Header'
import Hero from '../components/Hero'
import Footer from '../components/Footer'
import SectionHeading from '../components/SectionHeading'
import FeatureCard from '../components/FeatureCard'
import CallToAction from '../components/CallToAction'

const features = [
  {
    title: 'Multi-tenant tournament portals',
    description:
      'Launch branded event hubs for every federation or client—with isolated data and custom URLs.',
    gradient: 'from-accent-orange to-accent-pink',
    icon: '🏟️'
  },
  {
    title: 'Athlete registration',
    description:
      'Capture sign-ups, age categories, and competitor profiles with shareable registration links.',
    gradient: 'from-accent-pink to-accent-purple',
    icon: '📝'
  },
  {
    title: 'Fixture scheduling',
    description:
      'Plan brackets, match-ups, venues, and time slots from a single operations workspace.',
    gradient: 'from-accent-purple to-accent-blue',
    icon: '📅'
  },
  {
    title: 'Live leaderboards',
    description:
      'Publish results and rankings in real time so athletes, coaches, and fans stay in sync.',
    gradient: 'from-accent-blue to-accent-orange',
    icon: '🏆'
  }
]

const useCases = [
  {
    title: 'Sports federations',
    description: 'Run national and regional championships with role-based access for staff and officials.'
  },
  {
    title: 'Tournament organizers',
    description: 'Spin up events quickly with fixtures, registrations, and live scoring in one place.'
  },
  {
    title: 'Athletes & coaches',
    description: 'Track schedules, brackets, and standings from a clear, mobile-friendly portal.'
  }
]

export default function Home() {
  return (
    <div className="min-h-screen bg-dark-bg">
      <Header />
      <Hero />

      <main id="features" className="mx-auto max-w-7xl px-6 py-20 sm:py-28">
        <SectionHeading
          eyebrow="Platform capabilities"
          title="Built for federations and tournament organizers"
          description="Everything you need to manage sports events end to end—from tenant-aware launches to live results."
        />

        <div className="grid gap-6 md:grid-cols-2">
          {features.map((feature) => (
            <FeatureCard key={feature.title} {...feature} />
          ))}
        </div>
      </main>

      <section id="use-cases" className="border-y border-white/10 bg-dark-surface/30">
        <div className="mx-auto max-w-7xl px-6 py-20 sm:py-28">
          <SectionHeading
            eyebrow="Who it’s for"
            title="Designed for every stakeholder in sport"
            description="From governing bodies to athletes on the ground, Tekspo Infinity keeps everyone aligned."
          />

          <div className="grid gap-6 md:grid-cols-3">
            {useCases.map((item, index) => (
              <div
                key={item.title}
                className="glass-panel p-6 transition duration-300 hover:-translate-y-1 hover:border-white/20"
              >
                <span className="text-gradient-brand text-sm font-semibold">0{index + 1}</span>
                <h3 className="mt-3 text-lg font-semibold text-white">{item.title}</h3>
                <p className="mt-2 text-gray-300 leading-relaxed">{item.description}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <CallToAction />

      <Footer />
    </div>
  )
}
