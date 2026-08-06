'use client'

import { useEffect, useId, useRef, useState } from 'react'

export type SelectOption = {
  value: string
  label: string
}

type SelectProps = {
  options: SelectOption[]
  value?: string
  defaultValue?: string
  onChange?: (value: string) => void
  placeholder?: string
  /** Renders a hidden input so the value still appears in FormData on submit. */
  name?: string
  id?: string
  required?: boolean
  disabled?: boolean
  className?: string
}

/**
 * A themed replacement for `<select>`.
 *
 * A native select's option list is drawn by the operating system, so no amount of CSS makes the
 * open menu match a dark theme — on macOS it stays resolutely Aqua. This renders the list itself
 * while keeping the parts that matter: keyboard navigation, click-outside dismissal, and a hidden
 * input so uncontrolled `FormData` submission still works.
 */
export default function Select({
  options,
  value,
  defaultValue,
  onChange,
  placeholder = 'Select an option',
  name,
  id,
  required,
  disabled,
  className = '',
}: SelectProps) {
  const generatedId = useId()
  const listboxId = `${id ?? generatedId}-listbox`
  const isControlled = value !== undefined

  const [internalValue, setInternalValue] = useState(defaultValue ?? '')
  const selectedValue = isControlled ? value : internalValue

  const [isOpen, setIsOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const containerRef = useRef<HTMLDivElement>(null)

  const selectedOption = options.find((option) => option.value === selectedValue)

  useEffect(() => {
    if (!isOpen) {
      return
    }
    const handlePointerDown = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handlePointerDown)
    return () => document.removeEventListener('mousedown', handlePointerDown)
  }, [isOpen])

  const commit = (nextValue: string) => {
    if (!isControlled) {
      setInternalValue(nextValue)
    }
    onChange?.(nextValue)
    setIsOpen(false)
  }

  const open = () => {
    if (disabled) {
      return
    }
    setActiveIndex(Math.max(0, options.findIndex((option) => option.value === selectedValue)))
    setIsOpen(true)
  }

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (disabled) {
      return
    }

    if (!isOpen) {
      if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
        event.preventDefault()
        open()
      }
      return
    }

    switch (event.key) {
      case 'Escape':
        event.preventDefault()
        setIsOpen(false)
        break
      case 'ArrowDown':
        event.preventDefault()
        setActiveIndex((index) => (index + 1) % options.length)
        break
      case 'ArrowUp':
        event.preventDefault()
        setActiveIndex((index) => (index - 1 + options.length) % options.length)
        break
      case 'Home':
        event.preventDefault()
        setActiveIndex(0)
        break
      case 'End':
        event.preventDefault()
        setActiveIndex(options.length - 1)
        break
      case 'Enter':
      case ' ':
        event.preventDefault()
        if (options[activeIndex]) {
          commit(options[activeIndex].value)
        }
        break
      case 'Tab':
        setIsOpen(false)
        break
      default:
        break
    }
  }

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      {name ? (
        <input type="hidden" name={name} value={selectedValue} required={required} />
      ) : null}

      <button
        type="button"
        id={id}
        disabled={disabled}
        onClick={() => (isOpen ? setIsOpen(false) : open())}
        onKeyDown={handleKeyDown}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        aria-controls={isOpen ? listboxId : undefined}
        className="flex w-full items-center justify-between gap-2 rounded-lg border border-dark-border bg-dark-surface px-4 py-3 text-left text-white outline-none transition hover:border-gray-600 focus:border-accent-purple focus:ring-2 focus:ring-accent-purple/20 disabled:cursor-not-allowed disabled:opacity-50"
      >
        <span className={selectedOption ? 'text-white' : 'text-gray-500'}>
          {selectedOption?.label ?? placeholder}
        </span>
        <svg
          className={`h-4 w-4 shrink-0 text-gray-400 transition-transform ${isOpen ? 'rotate-180' : ''}`}
          viewBox="0 0 20 20"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.75"
          aria-hidden="true"
        >
          <path d="M5 7.5 10 12.5 15 7.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>

      {isOpen ? (
        <ul
          id={listboxId}
          role="listbox"
          tabIndex={-1}
          className="absolute z-50 mt-2 max-h-64 w-full overflow-auto rounded-lg border border-dark-border bg-dark-surface py-1 shadow-2xl shadow-black/60"
        >
          {options.map((option, index) => {
            const isSelected = option.value === selectedValue
            const isActive = index === activeIndex
            return (
              <li key={option.value} role="none">
                <button
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  onMouseEnter={() => setActiveIndex(index)}
                  onClick={() => commit(option.value)}
                  className={`flex w-full items-center justify-between gap-2 px-4 py-2.5 text-left text-sm transition ${
                    isActive ? 'bg-accent-purple/20 text-white' : 'text-gray-300'
                  }`}
                >
                  <span>{option.label}</span>
                  {isSelected ? (
                    <svg className="h-4 w-4 text-accent-purple" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                      <path
                        fillRule="evenodd"
                        d="M16.7 5.3a1 1 0 0 1 0 1.4l-7.5 7.5a1 1 0 0 1-1.4 0l-3.5-3.5a1 1 0 1 1 1.4-1.4l2.8 2.79 6.8-6.79a1 1 0 0 1 1.4 0Z"
                        clipRule="evenodd"
                      />
                    </svg>
                  ) : null}
                </button>
              </li>
            )
          })}
        </ul>
      ) : null}
    </div>
  )
}
