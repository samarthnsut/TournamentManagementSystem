'use client'

import Input from '../ui/Input'
import Select from '../ui/Select'
import type { FormFieldSchema, FormSchema } from '../../lib/api/registrations'

type DynamicFormProps = {
  schema: FormSchema
  values: Record<string, unknown>
  onChange: (values: Record<string, unknown>) => void
  errors?: Record<string, string>
  /** Approvers viewing a submitted answer set see it rendered, not editable. */
  readOnly?: boolean
}

function labelFor(name: string, field: FormFieldSchema) {
  if (field.title) {
    return field.title
  }
  // Turn camelCase or snake_case keys into something a human would read.
  const spaced = name.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ')
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}

/** Mirrors the server's rules so a submission is not the first place a mistake is noticed. */
export function validateAnswers(schema: FormSchema, values: Record<string, unknown>) {
  const errors: Record<string, string> = {}
  const required = schema.required ?? []

  Object.entries(schema.properties ?? {}).forEach(([name, field]) => {
    const value = values[name]
    const isEmpty = value === undefined || value === null || value === ''

    if (required.includes(name) && isEmpty && field.type !== 'boolean') {
      errors[name] = 'This field is required.'
      return
    }
    if (isEmpty) {
      return
    }

    if (field.type === 'string' && typeof value === 'string') {
      if (field.maxLength !== undefined && value.length > field.maxLength) {
        errors[name] = `Must be ${field.maxLength} characters or fewer.`
      }
      if (field.pattern && !new RegExp(field.pattern).test(value)) {
        errors[name] = 'This value is not in the expected format.'
      }
    }

    if ((field.type === 'number' || field.type === 'integer') && value !== '') {
      const numeric = Number(value)
      if (Number.isNaN(numeric)) {
        errors[name] = 'Must be a number.'
      } else if (field.minimum !== undefined && numeric < field.minimum) {
        errors[name] = `Must be at least ${field.minimum}.`
      } else if (field.maximum !== undefined && numeric > field.maximum) {
        errors[name] = `Must be at most ${field.maximum}.`
      }
    }
  })

  return errors
}

/**
 * Renders a registration form from its JSON schema.
 *
 * The schema passed in is always a specific version — the active one when entering, or the pinned
 * one when viewing a past submission — so an old answer set is never redrawn against a form its
 * author never saw.
 */
export default function DynamicForm({ schema, values, onChange, errors = {}, readOnly }: DynamicFormProps) {
  const properties = schema.properties ?? {}
  const required = schema.required ?? []

  if (Object.keys(properties).length === 0) {
    return <p className="text-sm text-gray-500">This form has no fields yet.</p>
  }

  const setValue = (name: string, value: unknown) => onChange({ ...values, [name]: value })

  return (
    <div className="space-y-5">
      {Object.entries(properties).map(([name, field]) => {
        const value = values[name]
        const error = errors[name]
        const isRequired = required.includes(name)
        const describedBy = error ? `${name}-error` : field.description ? `${name}-help` : undefined

        return (
          <div key={name}>
            <label htmlFor={name} className="mb-2 block text-sm font-medium text-gray-300">
              {labelFor(name, field)}
              {isRequired ? <span className="ml-1 text-accent-pink">*</span> : null}
            </label>

            {renderControl({ name, field, value, setValue, readOnly, describedBy })}

            {field.description && !error ? (
              <p id={`${name}-help`} className="mt-1.5 text-xs text-gray-500">
                {field.description}
              </p>
            ) : null}

            {error ? (
              // An icon alongside the colour, so the error is not signalled by colour alone.
              <p id={`${name}-error`} className="mt-1.5 flex items-center gap-1.5 text-xs text-red-300">
                <svg className="h-3.5 w-3.5 shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path
                    fillRule="evenodd"
                    d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm.75-11.5a.75.75 0 0 0-1.5 0v4a.75.75 0 0 0 1.5 0v-4Zm-.75 7.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z"
                    clipRule="evenodd"
                  />
                </svg>
                {error}
              </p>
            ) : null}
          </div>
        )
      })}
    </div>
  )
}

function renderControl({
  name,
  field,
  value,
  setValue,
  readOnly,
  describedBy,
}: {
  name: string
  field: FormFieldSchema
  value: unknown
  setValue: (name: string, value: unknown) => void
  readOnly?: boolean
  describedBy?: string
}) {
  if (readOnly) {
    const shown =
      value === undefined || value === null || value === ''
        ? '—'
        : typeof value === 'boolean'
          ? value ? 'Yes' : 'No'
          : String(value)
    return <p className="rounded-lg border border-dark-border bg-dark-bg/40 px-4 py-3 text-white">{shown}</p>
  }

  if (field.enum) {
    return (
      <Select
        id={name}
        value={typeof value === 'string' ? value : ''}
        onChange={(next) => setValue(name, next)}
        options={field.enum.map((option) => ({ value: option, label: option }))}
        placeholder="Select an option"
      />
    )
  }

  if (field.type === 'boolean') {
    return (
      <label className="flex cursor-pointer items-center gap-3 text-sm text-gray-300">
        <input
          id={name}
          type="checkbox"
          checked={value === true}
          onChange={(event) => setValue(name, event.target.checked)}
          className="h-4 w-4 shrink-0 rounded border-dark-border bg-dark-surface accent-accent-purple"
        />
        Yes
      </label>
    )
  }

  if (field.type === 'number' || field.type === 'integer') {
    return (
      <Input
        id={name}
        type="number"
        aria-describedby={describedBy}
        min={field.minimum}
        max={field.maximum}
        value={value === undefined || value === null ? '' : String(value)}
        onChange={(event) => setValue(name, event.target.value === '' ? '' : Number(event.target.value))}
      />
    )
  }

  if (field.type === 'string' && field.format === 'date') {
    return (
      <Input
        id={name}
        type="date"
        aria-describedby={describedBy}
        value={typeof value === 'string' ? value : ''}
        onChange={(event) => setValue(name, event.target.value)}
      />
    )
  }

  if (field.type === 'string' && field.format === 'textarea') {
    return (
      <textarea
        id={name}
        rows={4}
        aria-describedby={describedBy}
        value={typeof value === 'string' ? value : ''}
        onChange={(event) => setValue(name, event.target.value)}
        className="w-full rounded-lg border border-dark-border bg-dark-surface px-4 py-3 text-white outline-none transition placeholder:text-gray-500 focus:border-accent-purple focus:ring-2 focus:ring-accent-purple/20"
      />
    )
  }

  if (field.type === 'string') {
    return (
      <Input
        id={name}
        type={field.format === 'email' ? 'email' : 'text'}
        aria-describedby={describedBy}
        maxLength={field.maxLength}
        value={typeof value === 'string' ? value : ''}
        onChange={(event) => setValue(name, event.target.value)}
      />
    )
  }

  // Unknown type: show what we have and say so, rather than crashing on a schema we predate.
  return (
    <div>
      <Input
        id={name}
        aria-describedby={describedBy}
        value={value === undefined || value === null ? '' : String(value)}
        onChange={(event) => setValue(name, event.target.value)}
      />
      <p className="mt-1.5 text-xs text-amber-300">
        This field uses an unsupported type ({field.type ?? 'unknown'}) and is shown as plain text.
      </p>
    </div>
  )
}
