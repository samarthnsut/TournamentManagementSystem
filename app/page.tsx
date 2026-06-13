import Link from 'next/link'
import Header from '../components/Header'
import Hero from '../components/Hero'
import Footer from '../components/Footer'
import SectionHeading from '../components/SectionHeading'
import FeatureCard from '../components/FeatureCard'
import Button from '../components/ui/Button'

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

      <section className="mx-auto max-w-7xl px-6 py-20 sm:py-28">
        <div className="relative overflow-hidden rounded-3xl bg-gradient-brand p-[1px]">
          <div className="relative rounded-[23px] bg-dark-bg px-8 py-12 text-center sm:px-12 sm:py-16">
            <div className="pointer-events-none absolute inset-0 opacity-40">
              <div className="absolute -left-20 top-0 h-48 w-48 rounded-full bg-accent-orange/40 blur-3xl" />
              <div className="absolute -right-20 bottom-0 h-48 w-48 rounded-full bg-accent-blue/40 blur-3xl" />
            </div>
            <div className="relative">
              <h2 className="text-3xl font-bold sm:text-4xl">Ready to transform your next event?</h2>
              <p className="mx-auto mt-4 max-w-xl text-gray-300">
                Launch your first tournament portal in minutes and scale across federations with
                confidence.
              </p>
              <div className="mt-8 flex flex-col justify-center gap-4 sm:flex-row">
                <Link href="/signup">
                  <Button className="btn-gradient w-full sm:w-auto">Start free trial</Button>
                </Link>
                <Link href="/signin">
                  <Button variant="secondary" className="w-full sm:w-auto">
                    Talk to sales
                  </Button>
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  )
}
