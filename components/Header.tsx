import Link from 'next/link'
import Button from './ui/Button'
import Logo from './Logo'

export default function Header() {
  return (
    <header className="sticky top-0 z-50 border-b border-white/10 bg-dark-bg/80 backdrop-blur-lg">
      <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
        <Link href="/">
          <Logo />
        </Link>

        <nav className="flex flex-wrap items-center gap-5 text-sm sm:gap-6">
          <Link href="/" className="text-gray-300 transition hover:text-white">
            Home
          </Link>
          <Link href="/#features" className="text-gray-300 transition hover:text-white">
            Features
          </Link>
          <Link href="/dashboard" className="text-gray-300 transition hover:text-white">
            Dashboard
          </Link>
          <Link href="/dashboard/create">
            <Button className="btn-gradient whitespace-nowrap px-4 py-2 text-sm">
              Create Tournament
            </Button>
          </Link>
          <Link href="/signin">
            <Button variant="ghost" className="px-3 py-2 text-sm">
              Login
            </Button>
          </Link>
          <Link href="/signup">
            <Button className="btn-gradient px-5 py-2 text-sm">Get started</Button>
          </Link>
        </nav>
      </div>
    </header>
  )
}
