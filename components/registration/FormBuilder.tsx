'use client'

import { useState } from 'react'
import Button from '../ui/Button'
import Input from '../ui/Input'
import Select from '../ui/Select'
import type { FormFieldSchema, FormSchema } from '../../lib/api/registrations'

/** The field types an organizer can pick, mapped to what the renderer and the server understand. */
const FIELD_TYPES = [
  { value: 'text', label: 'Short text' },
  { value: 'textarea', label: 'Long text' },
  { value: 'number', label: 'Number' },
  { value: 'date', label: 'Date' },
  { value: 'email', label: 'Email' },
  { value: 'boolean', label: 'Yes / no' },
  { value: 'choice', label: 'Choice list' },
]

export type BuilderField = {
  key: string
  label: string
  type: string
  required: boolean
  /** Comma-separated, only meaningful for a choice list. */
  options: string
}

export function emptyField(): BuilderField {
  return { key: '', label: '', type: 'text', required: true, options: '' }
}

/** Derives a stable JSON pointer key from the label, so answers are readable in the database. */
export function keyFor(field: BuilderField) {
  if (field.key.trim()) {
    return field.key.trim()
  }
  const parts = field.label.trim().toLowerCase().split(/[^a-z0-9]+/).filter(Boolean)
  if (parts.length === 0) {
    return ''
  }
  return parts[0] + parts.slice(1).map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join('')
}

/** Turns the builder rows into the JSON Schema the backend stores and validates against. */
export function buildSchema(fields: BuilderField[]): FormSchema {
  const properties: Record<string, FormFieldSchema> = {}
  const required: string[] = []

  fields.forEach((field) => {
    const key = keyFor(field)
    if (!key) {
      return
    }

    let property: FormFieldSchema
    switch (field.type) {
      case 'textarea':
        property = { type: 'string', format: 'textarea' }
        break
      case 'number':
        property = { type: 'number' }
        break
      case 'date':
        property = { type: 'string', format: 'date' }
        break
      case 'email':
        property = { type: 'string', format: 'email' }
        break
      case 'boolean':
        property = { type: 'boolean' }
        break
      case 'choice':
        property = {
          type: 'string',
          enum: field.options.split(',').map((option) => option.trim()).filter(Boolean),
        }
        break
      default:
        property = { type: 'string' }
    }

    if (field.label.trim()) {
      property.title = field.label.trim()
    }
    properties[key] = property
    if (field.required) {
      required.push(key)
    }
  })

  return {
    type: 'object',
    properties,
    // Unlisted answers are rejected, so a stale client cannot smuggle in extra data.
    additionalProperties: false,
    ...(required.length > 0 ? { required } : {}),
  }
}

/** Reads an existing schema back into builder rows so a version can be edited, not just replaced. */
export function fieldsFromSchema(schema: FormSchema): BuilderField[] {
  const required = schema.required ?? []
  return Object.entries(schema.properties ?? {}).map(([key, property]) => {
    let type = 'text'
    if (property.enum) {
      type = 'choice'
    } else if (property.type === 'boolean') {
      type = 'boolean'
    } else if (property.type === 'number' || property.type === 'integer') {
      type = 'number'
    } else if (property.format === 'date') {
      type = 'date'
    } else if (property.format === 'email') {
      type = 'email'
    } else if (property.format === 'textarea') {
      type = 'textarea'
    }

    return {
      key,
      label: property.title ?? key,
      type,
      required: required.includes(key),
      options: (property.enum ?? []).join(', '),
    }
  })
}

export default function FormBuilder({
  fields,
  onChange,
  disabled,
}: {
  fields: BuilderField[]
  onChange: (fields: BuilderField[]) => void
  disabled?: boolean
}) {
  const [showKeys, setShowKeys] = useState(false)

  const update = (index: number, patch: Partial<BuilderField>) =>
    onChange(fields.map((field, position) => (position === index ? { ...field, ...patch } : field)))

  const remove = (index: number) => onChange(fields.filter((_, position) => position !== index))

  const move = (index: number, delta: number) => {
    const target = index + delta
    if (target < 0 || target >= fields.length) {
      return
    }
    const next = [...fields]
    const [moved] = next.splice(index, 1)
    next.splice(target, 0, moved)
    onChange(next)
  }

  return (
    <div className="space-y-4">
      {fields.map((field, index) => (
        <div key={index} className="rounded-lg border border-dark-border bg-dark-bg/40 p-4">
          <div className="grid gap-3 sm:grid-cols-[1fr_10rem]">
            <div>
              <label className="mb-1.5 block text-xs font-medium text-gray-400">Question</label>
              <Input
                placeholder="e.g., Coach phone number"
                value={field.label}
                disabled={disabled}
                onChange={(event) => update(index, { label: event.target.value })}
              />
            </div>
            <div>
              <label className="mb-1.5 block text-xs font-medium text-gray-400">Type</label>
              <Select
                value={field.type}
                onChange={(type) => update(index, { type })}
                options={FIELD_TYPES}
                disabled={disabled}
              />
            </div>
          </div>

          {field.type === 'choice' ? (
            <div className="mt-3">
              <label className="mb-1.5 block text-xs font-medium text-gray-400">
                Options, separated by commas
              </label>
              <Input
                placeholder="Under 14, Under 16, Under 19"
                value={field.options}
                disabled={disabled}
                onChange={(event) => update(index, { options: event.target.value })}
              />
            </div>
          ) : null}

          {showKeys ? (
            <div className="mt-3">
              <label className="mb-1.5 block text-xs font-medium text-gray-400">
                Stored as
              </label>
              <Input
                placeholder={keyFor(field) || 'fieldName'}
                value={field.key}
                disabled={disabled}
                onChange={(event) => update(index, { key: event.target.value })}
              />
            </div>
          ) : null}

          <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
            <label className="flex cursor-pointer items-center gap-2 text-sm text-gray-300">
              <input
                type="checkbox"
                checked={field.required}
                disabled={disabled}
                onChange={(event) => update(index, { required: event.target.checked })}
                className="h-4 w-4 rounded border-dark-border bg-dark-surface accent-accent-purple"
              />
              Required
            </label>

            <div className="flex items-center gap-2 text-sm">
              <button
                type="button"
                onClick={() => move(index, -1)}
                disabled={disabled || index === 0}
                className="rounded px-2 py-1 text-gray-400 transition hover:text-white disabled:opacity-30"
                aria-label="Move up"
              >
                ↑
              </button>
              <button
                type="button"
                onClick={() => move(index, 1)}
                disabled={disabled || index === fields.length - 1}
                className="rounded px-2 py-1 text-gray-400 transition hover:text-white disabled:opacity-30"
                aria-label="Move down"
              >
                ↓
              </button>
              <button
                type="button"
                onClick={() => remove(index)}
                disabled={disabled}
                className="rounded px-2 py-1 text-red-300 transition hover:text-red-200 disabled:opacity-30"
              >
                Remove
              </button>
            </div>
          </div>
        </div>
      ))}

      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="button"
          variant="secondary"
          className="px-4 py-2 text-sm"
          disabled={disabled}
          onClick={() => onChange([...fields, emptyField()])}
        >
          + Add question
        </Button>
        <button
          type="button"
          onClick={() => setShowKeys((current) => !current)}
          className="text-xs text-gray-500 transition hover:text-gray-300"
        >
          {showKeys ? 'Hide' : 'Show'} stored field names
        </button>
      </div>
    </div>
  )
}
