import Badge from './ui/Badge'
import Card from './ui/Card'

type Props = {
  description: string
  location: string
  dates: string
  categories: string[]
  stats: {
    athletes: number
    events: number
    matches: number
  }
}

export default function TournamentOverview({ description, location, dates, categories, stats }: Props) {
  return (
    <Card className="space-y-6">
      <div className="space-y-3">
        <div className="flex flex-wrap items-center gap-3">
          <Badge>State Championship</Badge>
          <Badge variant="secondary">Federation platform</Badge>
        </div>
        <p className="text-gray-300">{description}</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div>
          <p className="text-sm text-gray-400">Location</p>
          <p className="mt-2 font-semibold text-white">{location}</p>
        </div>
        <div>
          <p className="text-sm text-gray-400">Dates</p>
          <p className="mt-2 font-semibold text-white">{dates}</p>
        </div>
        <div>
          <p className="text-sm text-gray-400">Categories</p>
          <div className="mt-2 flex flex-wrap gap-2">
            {categories.map((category) => (
              <Badge key={category} variant="secondary">
                {category}
              </Badge>
            ))}
          </div>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <div className="rounded-lg bg-accent-orange/10 border border-accent-orange/20 p-5">
          <p className="text-sm text-gray-400">Athletes</p>
          <p className="mt-3 text-3xl font-semibold text-accent-orange">{stats.athletes}</p>
        </div>
        <div className="rounded-lg bg-accent-purple/10 border border-accent-purple/20 p-5">
          <p className="text-sm text-gray-400">Events</p>
          <p className="mt-3 text-3xl font-semibold text-accent-purple">{stats.events}</p>
        </div>
        <div className="rounded-lg bg-accent-blue/10 border border-accent-blue/20 p-5">
          <p className="text-sm text-gray-400">Matches</p>
          <p className="mt-3 text-3xl font-semibold text-accent-blue">{stats.matches}</p>
        </div>
      </div>
    </Card>
  )
}
