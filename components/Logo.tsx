type LogoProps = {
  className?: string
  showWordmark?: boolean
}

export default function Logo({ className = '', showWordmark = true }: LogoProps) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`}>
      <svg
        viewBox="0 0 40 24"
        className="h-7 w-10 shrink-0"
        aria-hidden
        fill="none"
      >
        <defs>
          <linearGradient id="tekspo-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#FF6B35" />
            <stop offset="35%" stopColor="#FF1B8D" />
            <stop offset="65%" stopColor="#9D4EDD" />
            <stop offset="100%" stopColor="#5390D9" />
          </linearGradient>
        </defs>
        <path
          d="M28 12c0-4.4-3.6-8-8-8-2.2 0-4.2.9-5.7 2.3C12.8 4.9 10.8 4 8.5 4 4.1 4 .5 7.6.5 12s3.6 8 8 8c2.2 0 4.2-.9 5.7-2.3 1.5 1.4 3.5 2.3 5.8 2.3 4.4 0 8-3.6 8-8zm-8 4c-2.2 0-4-1.8-4-4s1.8-4 4-4 4 1.8 4 4-1.8 4-4 4z"
          fill="url(#tekspo-gradient)"
        />
      </svg>
      {showWordmark ? (
        <span className="text-gradient-brand text-xl font-bold tracking-tight">
          Tekspo Infinity
        </span>
      ) : null}
    </span>
  )
}
