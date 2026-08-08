import type { CompetitionAction, CompetitionStatus, TournamentAction, TournamentStatus } from './api/tournaments'

/**
 * Plain-language descriptions of every lifecycle move, so a confirmation dialog can say what a
 * button will actually do rather than "are you sure?".
 *
 * The two status systems confuse people, and the reason is worth stating plainly: **entries are
 * gated by the competition alone.** `RegistrationService` checks `competition.status == OPEN` and
 * nothing else — the tournament's REGISTRATION_OPEN and REGISTRATION_CLOSED states gate no code
 * path at all. They describe the event for the public page; the competition is what actually runs.
 */
export type TransitionCopy = {
  title: string
  /** What this does, in one sentence. */
  summary: string
  /** What becomes possible afterwards. */
  unlocks?: string
  /** What stops being possible. Absent when nothing is lost. */
  locks?: string
  /** True when there is no way back. */
  irreversible?: boolean
  confirmLabel: string
}

export const TOURNAMENT_TRANSITIONS: Record<TournamentAction, TransitionCopy> = {
  publish: {
    title: 'Publish this tournament?',
    summary: 'It becomes visible on its public page, and its competitions can start taking entries.',
    unlocks: 'Competitions can be opened for entries.',
    locks: 'The web address (slug) is fixed from now on.',
    confirmLabel: 'Publish',
  },
  'open-registration': {
    title: 'Mark registration open?',
    summary:
      'This records the event as being in its entry window. It is a label for the public page — each competition still controls its own entries.',
    unlocks: 'Nothing further; open the individual competitions to actually accept entries.',
    confirmLabel: 'Mark open',
  },
  'close-registration': {
    title: 'Mark registration closed?',
    summary:
      'This records the entry window as over. Competitions that are still open will keep accepting entries until you close them individually.',
    confirmLabel: 'Mark closed',
  },
  start: {
    title: 'Start this tournament?',
    summary: 'Marks the event as under way.',
    confirmLabel: 'Start',
  },
  complete: {
    title: 'Complete this tournament?',
    summary: 'Marks the whole event as finished.',
    locks: 'No further changes to the tournament, and its standings are final.',
    irreversible: true,
    confirmLabel: 'Complete',
  },
  cancel: {
    title: 'Cancel this tournament?',
    summary: 'Calls the whole event off. Entries and results are kept for the record.',
    irreversible: true,
    confirmLabel: 'Cancel tournament',
  },
  archive: {
    title: 'Archive this tournament?',
    summary: 'Files it away. It stays readable but drops out of the active list.',
    irreversible: true,
    confirmLabel: 'Archive',
  },
}

export const COMPETITION_TRANSITIONS: Record<CompetitionAction, TransitionCopy> = {
  open: {
    title: 'Open entries?',
    summary: 'People can enter this competition from now until you close it.',
    unlocks: 'Entries can be submitted and approved.',
    confirmLabel: 'Open entries',
  },
  close: {
    title: 'Close entries?',
    summary: 'No further entries are accepted. The field is settled.',
    unlocks: 'The draw can be generated from the approved entries.',
    locks: 'Nobody new can enter — reopening is possible, but late entrants miss the draw.',
    confirmLabel: 'Close entries',
  },
  start: {
    title: 'Start this competition?',
    summary:
      'Marks it as under way. You normally do not need this — generating the draw starts it for you.',
    confirmLabel: 'Start',
  },
  complete: {
    title: 'Complete this competition?',
    summary: 'Marks the contest as finished and freezes its standings.',
    locks: 'The standings stop updating and the draw can no longer be generated or changed.',
    irreversible: true,
    confirmLabel: 'Complete',
  },
  cancel: {
    title: 'Cancel this competition?',
    summary: 'Calls this contest off. Entries and any results are kept for the record.',
    irreversible: true,
    confirmLabel: 'Cancel competition',
  },
}

/** The organizer's journey for one competition, and where they are on it. */
export type Step = { label: string; hint: string; done: boolean; current: boolean }

export function competitionJourney(status: CompetitionStatus, hasFixtures: boolean): Step[] {
  const order: CompetitionStatus[] = ['DRAFT', 'OPEN', 'CLOSED', 'IN_PROGRESS', 'COMPLETED']
  const position = order.indexOf(status)

  const at = (target: CompetitionStatus) => {
    const index = order.indexOf(target)
    return { done: position > index, current: position === index }
  }

  return [
    { label: 'Set up', hint: 'Publish an entry form', ...at('DRAFT') },
    { label: 'Entries open', hint: 'People enter and are approved', ...at('OPEN') },
    { label: 'Entries closed', hint: 'Generate the draw', ...at('CLOSED') },
    {
      label: 'Playing',
      hint: hasFixtures ? 'Record results as they happen' : 'Generate the draw to begin',
      ...at('IN_PROGRESS'),
    },
    { label: 'Finished', hint: 'Standings are final', ...at('COMPLETED') },
  ]
}

/** What the organizer should do next, phrased as an instruction rather than a state name. */
export function nextStepHint(status: CompetitionStatus, hasFixtures: boolean, hasForm: boolean): string {
  switch (status) {
    case 'DRAFT':
      return hasForm
        ? 'Open entries when you are ready to accept them.'
        : 'Publish a registration form, then open entries.'
    case 'OPEN':
      return 'Entries are being accepted. Close them once the field is settled.'
    case 'CLOSED':
      return 'Generate the draw — that also starts the competition.'
    case 'IN_PROGRESS':
      return hasFixtures
        ? 'Record results as matches are played. Standings update automatically.'
        : 'Generate the draw to create the matches.'
    case 'COMPLETED':
      return 'Finished. The standings are final.'
    default:
      return 'This competition was cancelled.'
  }
}

/** Tournament status is the event-level phase; it does not gate entries. */
export function tournamentPhaseNote(status: TournamentStatus): string {
  switch (status) {
    case 'DRAFT':
      return 'Not visible publicly yet. Publish it to put it online and to open competitions.'
    case 'PUBLISHED':
    case 'REGISTRATION_OPEN':
    case 'REGISTRATION_CLOSED':
      return 'Live on its public page. Entries are controlled by each competition, not here.'
    case 'IN_PROGRESS':
      return 'Under way. Results and standings show on the public page.'
    case 'COMPLETED':
      return 'Finished.'
    case 'CANCELLED':
      return 'Called off.'
    default:
      return 'Archived.'
  }
}

/** Match-level moves. Same shape, same dialog. */
export const MATCH_TRANSITIONS: Record<'start' | 'postpone' | 'cancel', TransitionCopy> = {
  start: {
    title: 'Mark this match live?',
    summary: 'Flags the match as being played right now.',
    locks: 'The draw can no longer be regenerated once any match is live.',
    confirmLabel: 'Mark live',
  },
  postpone: {
    title: 'Postpone this match?',
    summary: 'Takes it off the schedule until you set a new time.',
    unlocks: 'Rescheduling it puts it back. The draw can still be regenerated.',
    confirmLabel: 'Postpone',
  },
  cancel: {
    title: 'Cancel this match?',
    summary: 'The match will not be played and records no result.',
    locks: 'It stops counting towards the standings.',
    irreversible: true,
    confirmLabel: 'Cancel match',
  },
}

/** One-off destructive actions that are not lifecycle transitions but deserve the same care. */
export const ACTION_COPY = {
  regenerateDraw: {
    title: 'Regenerate the draw?',
    summary: 'Discards the current pairings and draws everyone again from scratch.',
    locks: 'The existing fixtures are deleted. Only possible while nothing has been played.',
    irreversible: true,
    confirmLabel: 'Regenerate',
  },
  archiveVenue: {
    title: 'Archive this venue?',
    summary: 'It stops appearing when scheduling matches.',
    locks: 'Matches already scheduled there keep their venue.',
    confirmLabel: 'Archive venue',
  },
  deactivateWorkflow: {
    title: 'Deactivate this workflow?',
    summary: 'New entries stop following it.',
    locks: 'Entries already part-way through keep the levels they were started with.',
    confirmLabel: 'Deactivate',
  },
  withdrawEntry: {
    title: 'Withdraw this entry?',
    summary: 'The entrant is removed from the competition and their place is freed.',
    unlocks: 'The same participant can enter again while entries are open.',
    confirmLabel: 'Withdraw',
  },
  approveEntry: {
    title: 'Approve this entry?',
    summary: 'The entrant is accepted and will be included in the draw.',
    confirmLabel: 'Approve',
  },
} satisfies Record<string, TransitionCopy>
