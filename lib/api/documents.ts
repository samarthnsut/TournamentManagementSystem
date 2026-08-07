import { API_BASE_URL, request } from './client'

export type DocumentEntityType = 'TOURNAMENT' | 'REGISTRATION'

export type TmsDocument = {
  id: string
  organizationUnitId: string
  entityType: string
  entityId: string
  fileName: string
  fileUrl: string
  mimeType: string
  sizeBytes: number
  uploadedBy: string
  createdAt: string
  /** Short-lived and signed; never store it. */
  downloadUrl: string | null
}

export type InitUploadResponse = {
  uploadId: string
  presignedUrl: string
  expiresAt: string
}

export const ALLOWED_MIME_TYPES = ['application/pdf', 'image/jpeg', 'image/png']
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024

export function getDocuments(entityType: DocumentEntityType, entityId: string) {
  return request<TmsDocument[]>(`/documents?entityType=${entityType}&entityId=${entityId}`)
}

function initUpload(payload: {
  fileName: string
  mimeType: string
  sizeBytes: number
  entityType: DocumentEntityType
  entityId: string
}) {
  return request<InitUploadResponse>('/documents/upload-init', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

function attach(uploadId: string) {
  return request<TmsDocument>(`/documents/${uploadId}/attach`, {
    method: 'POST',
    body: JSON.stringify({}),
  })
}

/**
 * The whole two-phase dance, so callers see one promise.
 *
 * The middle step deliberately does not go through `client.ts`: it is a PUT straight to object
 * storage, and attaching our bearer token to a presigned S3 URL would both leak it to a third
 * party and break the signature.
 */
export async function uploadDocument(
  file: File,
  entityType: DocumentEntityType,
  entityId: string,
): Promise<TmsDocument> {
  const initiated = await initUpload({
    fileName: file.name,
    mimeType: file.type,
    sizeBytes: file.size,
    entityType,
    entityId,
  })

  const uploaded = await fetch(initiated.presignedUrl, {
    method: 'PUT',
    headers: { 'Content-Type': file.type },
    body: file,
  })

  if (!uploaded.ok) {
    throw new Error(`Upload failed (${uploaded.status}). The link may have expired — try again.`)
  }

  return attach(initiated.uploadId)
}

/** Only used to show where files live; the API base is not otherwise interesting to the UI. */
export const STORAGE_HINT = API_BASE_URL
