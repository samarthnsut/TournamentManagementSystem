import Link from 'next/link'
import Logo from './Logo'

export default function Footer() {
  return (
    <footer className="border-t border-white/10 bg-dark-surface/40">
      <div className="mx-auto flex max-w-7xl flex-col gap-6 px-6 py-12 sm:flex-row sm:items-center sm:justify-between">
        <Logo />
        <p className="max-w-md text-sm text-gray-400">
          Tournament management built for federations, organizers, and athletes—powered by Tekspo
          Infinity.
        </p>
        <div className="flex flex-col gap-2 sm:items-end">
          <Link
            href="/terms"
            className="text-sm text-gray-400 transition hover:text-white"
          >
            Terms of use
          </Link>
          <p className="text-sm text-gray-500">© {new Date().getFullYear()} Tekspo Infinity</p>
        </div>
      </div>
    </footer>
  )
}
