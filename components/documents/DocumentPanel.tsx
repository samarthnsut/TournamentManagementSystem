'use client'

import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Card from '../ui/Card'
import { useAuth } from '../../lib/useAuth'
import {
  ALLOWED_MIME_TYPES,
  MAX_UPLOAD_BYTES,
  getDocuments,
  uploadDocument,
  type DocumentEntityType,
  type TmsDocument,
} from '../../lib/api/documents'

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function iconFor(mimeType: string) {
  if (mimeType.startsWith('image/')) return '🖼️'
  return '📄'
}

/**
 * Attachments for one entity: age proofs on an entry, a rulebook on a tournament.
 *
 * The upload is two-phase and the middle leg goes straight to object storage, so a large file never
 * travels through the API. The client-side size and type checks here are courtesy — they save a
 * round trip — and the server enforces both again against the object that actually arrives.
 */
export default function DocumentPanel({
  entityType,
  entityId,
  title = 'Documents',
  description,
}: {
  entityType: DocumentEntityType
  entityId: string
  title?: string
  description?: string
}) {
  const queryClient = useQueryClient()
  const { can } = useAuth()
  const inputRef = useRef<HTMLInputElement>(null)
  const [error, setError] = useState('')

  const queryKey = ['documents', entityType, entityId]

  const documentsQuery = useQuery({
    queryKey,
    queryFn: () => getDocuments(entityType, entityId),
    enabled: Boolean(entityId),
    retry: false,
  })

  const upload = useMutation({
    mutationFn: (file: File) => uploadDocument(file, entityType, entityId),
    onSuccess: async () => {
      setError('')
      if (inputRef.current) {
        inputRef.current.value = ''
      }
      await queryClient.invalidateQueries({ queryKey })
    },
    onError: (cause: unknown) =>
      setError(cause instanceof Error ? cause.message : 'The upload failed'),
  })

  const choose = (file: File | undefined) => {
    if (!file) return

    if (!ALLOWED_MIME_TYPES.includes(file.type)) {
      setError(`${file.type || 'That file type'} is not accepted — PDF, JPEG or PNG only.`)
      return
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      setError(`That file is ${formatSize(file.size)}; the limit is ${formatSize(MAX_UPLOAD_BYTES)}.`)
      return
    }

    setError('')
    upload.mutate(file)
  }

  const canUpload = can('document:upload')
  const documents = documentsQuery.data ?? []
  // 403 here means the caller cannot read this entity's files; that is a state, not a failure.
  const unreadable = Boolean(documentsQuery.error)

  return (
    <Card>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-white">{title}</h2>
          <p className="mt-1 text-sm text-gray-500">
            {description ?? 'PDF, JPEG or PNG, up to 10 MB each.'}
          </p>
        </div>

        {canUpload ? (
          <>
            <input
              ref={inputRef}
              type="file"
              className="hidden"
              accept={ALLOWED_MIME_TYPES.join(',')}
              onChange={(event) => choose(event.target.files?.[0])}
            />
            <button
              type="button"
              disabled={upload.isPending}
              onClick={() => inputRef.current?.click()}
              className="rounded-lg border border-accent-purple/40 bg-accent-purple/10 px-4 py-2 text-sm font-semibold text-accent-purple transition hover:border-accent-purple hover:bg-accent-purple/20 focus:outline-none focus:ring-2 focus:ring-accent-purple/40 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {upload.isPending ? 'Uploading…' : 'Upload a file'}
            </button>
          </>
        ) : null}
      </div>

      {error ? (
        <div className="mb-4 rounded-lg border border-red-500/30 bg-red-500/5 px-4 py-3 text-sm text-red-300">
          {error}
        </div>
      ) : null}

      {documentsQuery.isLoading ? (
        <p className="text-sm text-gray-400">Loading documents…</p>
      ) : unreadable ? (
        <p className="text-sm text-gray-500">You do not have access to this entity&apos;s documents.</p>
      ) : documents.length === 0 ? (
        <p className="text-sm text-gray-500">Nothing attached yet.</p>
      ) : (
        <div className="space-y-3">
          {documents.map((document: TmsDocument) => (
            <div
              key={document.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-dark-border bg-dark-bg/40 p-4"
            >
              <div className="flex min-w-0 items-center gap-3">
                <span aria-hidden="true" className="text-xl">
                  {iconFor(document.mimeType)}
                </span>
                <div className="min-w-0">
                  <p className="truncate font-medium text-white">{document.fileName}</p>
                  <p className="mt-0.5 text-xs text-gray-600">
                    {formatSize(document.sizeBytes)} ·{' '}
                    {new Date(document.createdAt).toLocaleDateString('en-IN', {
                      day: 'numeric',
                      month: 'short',
                      year: 'numeric',
                    })}
                  </p>
                </div>
              </div>

              {document.downloadUrl ? (
                <a
                  href={document.downloadUrl}
                  // The link is signed and short-lived; opening it elsewhere keeps this page put.
                  target="_blank"
                  rel="noopener noreferrer"
                  className="whitespace-nowrap text-sm text-accent-cyan transition hover:text-accent-cyan/80"
                >
                  Download
                </a>
              ) : null}
            </div>
          ))}
        </div>
      )}
    </Card>
  )
}
