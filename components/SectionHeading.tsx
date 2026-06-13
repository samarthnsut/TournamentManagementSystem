type Props = {
  title: string
  description?: string
  eyebrow?: string
}

export default function SectionHeading({ title, description, eyebrow }: Props) {
  return (
    <div className="mb-10 sm:mb-12">
      {eyebrow ? (
        <p className="text-sm font-semibold uppercase tracking-[0.22em] text-accent-pink">{eyebrow}</p>
      ) : null}
      <h2 className={`text-3xl font-bold tracking-tight text-white sm:text-4xl ${eyebrow ? 'mt-3' : ''}`}>
        {title}
      </h2>
      {description ? (
        <p className="mt-3 max-w-2xl text-base text-gray-300 sm:text-lg">{description}</p>
      ) : null}
    </div>
  )
}
