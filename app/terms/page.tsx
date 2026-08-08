'use client'

import Link from 'next/link'
import Header from '../../components/Header'
import Footer from '../../components/Footer'

/**
 * Terms of use.
 *
 * <p>Written as a plain-language summary of how the platform actually behaves — the data it stores,
 * who can see it, and what it does not do — rather than boilerplate copied from elsewhere. It is
 * deliberately not passed off as reviewed legal text; the notice at the top says so.
 */
const LAST_UPDATED = '8 August 2026'

function Section({ heading, children }: { heading: string; children: React.ReactNode }) {
  return (
    <section className="mb-10">
      <h2 className="mb-3 text-xl font-semibold text-white">{heading}</h2>
      <div className="space-y-3 text-gray-400">{children}</div>
    </section>
  )
}

export default function TermsPage() {
  return (
    <>
      <Header />
      <main className="min-h-screen bg-dark-bg">
        <div className="border-b border-dark-border bg-gradient-to-b from-dark-surface to-dark-bg py-12">
          <div className="mx-auto max-w-3xl px-6 sm:px-8">
            <h1 className="text-3xl font-bold text-white sm:text-4xl">Terms of use</h1>
            <p className="mt-3 text-gray-400">Last updated {LAST_UPDATED}</p>
          </div>
        </div>

        <div className="mx-auto max-w-3xl px-6 py-12 sm:px-8">
          <div className="mb-10 rounded-lg border border-amber-500/35 bg-amber-500/10 px-5 py-4 text-sm text-amber-200">
            This is a plain-language description of how the platform works, not legal advice, and it
            has not been reviewed by a lawyer. If you are running a real event on this platform, have
            it reviewed before you rely on it.
          </div>

          <Section heading="1. What this platform is">
            <p>
              Tekspo Infinity is software for running sports tournaments: creating events, taking
              entries, drawing fixtures, recording results and publishing standings. It is provided
              to the organization that signed up, and to the people that organization invites.
            </p>
          </Section>

          <Section heading="2. Your account">
            <p>
              Accounts are created either by registering an organization or by accepting an invite
              from someone who already administers one. You are responsible for keeping your password
              to yourself and for what happens under your account.
            </p>
            <p>
              What you can do is decided by the roles you have been granted, and only someone with
              permission to assign roles can change them. Your own profile page lists the roles you
              hold and where they apply.
            </p>
          </Section>

          <Section heading="3. What we store">
            <p>
              For every account: name, email address, an optional phone number, and a one-way hash of
              your password. Passwords themselves are never stored and cannot be recovered — only
              reset.
            </p>
            <p>
              For entries: whatever the organizer&apos;s registration form asks for, plus any
              documents attached to the entry. Organizers choose those questions, so what is
              collected varies from one competition to the next.
            </p>
            <p>
              Every change made through the platform is written to an audit trail — who did what, to
              which record, when, and from which address. Those records are append-only and cannot be
              edited or deleted, including by us.
            </p>
          </Section>

          <Section heading="4. Who can see it">
            <p>
              Organizations are separated from one another. Administrators see the data belonging to
              their own organization and to any organizations beneath it in their structure; they
              cannot see another tenant&apos;s data.
            </p>
            <p>
              Some information is deliberately public and needs no sign-in: a published
              tournament&apos;s name, dates, organizer, competitions, fixtures and standings, and the
              names of entrants appearing in them. If you enter a competition, expect your entrant
              name and results to be publicly visible. Contact details and form answers are not
              published.
            </p>
          </Section>

          <Section heading="5. Uploaded files">
            <p>
              Documents attached to a tournament or an entry are stored in object storage and served
              through short-lived links. Only PDF, JPEG and PNG files up to 10 MB are accepted. Do not
              upload anything you are not entitled to share.
            </p>
          </Section>

          <Section heading="6. Fair use">
            <p>
              Do not use the platform to break the law, to impersonate anyone, to upload malicious
              files, or to try to reach data belonging to another organization. Accounts doing so can
              be suspended.
            </p>
          </Section>

          <Section heading="7. Availability and accuracy">
            <p>
              The platform is provided as it is, without a guarantee of uninterrupted service.
              Results, standings and fixtures are whatever organizers record — we do not verify them,
              and disputes about a result are for the organizing body to settle, not us.
            </p>
          </Section>

          <Section heading="8. Ending your use">
            <p>
              An administrator can remove your access at any time. Records of what you did remain in
              the audit trail, and entries and results already recorded remain part of the
              tournament&apos;s history, because removing them would falsify the record of an event
              that took place.
            </p>
          </Section>

          <Section heading="9. Changes">
            <p>
              These terms may change as the platform does. The date at the top shows when they were
              last revised.
            </p>
          </Section>

          <div className="mt-12 border-t border-dark-border pt-8">
            <Link href="/" className="text-accent-cyan transition hover:text-accent-cyan/80">
              ← Back to home
            </Link>
          </div>
        </div>
      </main>
      <Footer />
    </>
  )
}
