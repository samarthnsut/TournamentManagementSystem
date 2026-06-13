type FeatureCardProps = {
  title: string
  description: string
  gradient: string
  icon: string
}

export default function FeatureCard({ title, description, gradient, icon }: FeatureCardProps) {
  return (
    <div
      className={`group relative rounded-2xl bg-gradient-to-br p-[1px] ${gradient} transition duration-300 hover:-translate-y-1 hover:shadow-2xl hover:shadow-accent-purple/10`}
    >
      <div className="flex h-full flex-col rounded-2xl bg-dark-surface/90 p-6 backdrop-blur-md transition duration-300 group-hover:bg-dark-surface/75">
        <span className="text-2xl" aria-hidden>
          {icon}
        </span>
        <h3 className="mt-4 text-xl font-semibold text-white">{title}</h3>
        <p className="mt-3 flex-1 text-gray-300 leading-relaxed">{description}</p>
      </div>
    </div>
  )
}
