import Link from 'next/link'
import Button from '../components/ui/Button'
import Logo from '../components/Logo'

export default function NotFound() {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center bg-dark-bg px-6">
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -right-24 top-20 h-72 w-72 animate-float rounded-full bg-accent-orange/20 blur-3xl" />
        <div className="absolute bottom-0 left-1/4 h-64 w-64 animate-float-delayed rounded-full bg-accent-blue/20 blur-3xl" />
      </div>

      <div className="relative text-center">
        <Logo className="justify-center" />
        <p className="mt-10 text-gradient-brand text-7xl font-bold sm:text-8xl">404</p>
        <h1 className="mt-4 text-2xl font-bold text-white sm:text-3xl">Page not found</h1>
        <p className="mx-auto mt-3 max-w-md text-gray-400">
          The page you&apos;re looking for doesn&apos;t exist or may have been moved.
        </p>
        <div className="mt-8 flex flex-col justify-center gap-4 sm:flex-row">
          <Link href="/">
            <Button className="btn-gradient w-full sm:w-auto">Back to home</Button>
          </Link>
          <Link href="/signin">
            <Button variant="secondary" className="w-full sm:w-auto">
              Sign in
            </Button>
          </Link>
        </div>
      </div>
    </div>
  )
}
