import { ApiError } from '../../api/client'

export function messageFor(error: ApiError): string {
  if (error.status === 401) {
    return 'Sign in again. This session was not accepted.'
  }
  if (error.status === 403) {
    return 'You do not have permission for this catalog action.'
  }
  if (error.status === 404) {
    return 'That catalog record was not found.'
  }
  if (error.status === 409) {
    return error.message || 'This record changed. Reload it and review your edits.'
  }
  if (error.status === 400) {
    return error.message || 'Check the entered values.'
  }
  if (error.status === 502 || error.status === 503 || error.status === 504) {
    return 'Catalog is unavailable. Try again shortly.'
  }
  return error.message || 'The catalog request failed.'
}

export function CatalogNotice({
  error,
  loading = false,
  empty = false,
}: {
  error: ApiError | null
  loading?: boolean
  empty?: boolean
}) {
  if (loading) {
    return <p role="status">Loading catalog…</p>
  }
  if (error) {
    return (
      <div className="error" role="alert">
        <p>{messageFor(error)}</p>
        {error.correlationId ? <p>Reference {error.correlationId}</p> : null}
      </div>
    )
  }
  if (empty) {
    return <p>No matching catalog records.</p>
  }
  return null
}

export function asApiError(cause: unknown, fallback: string): ApiError {
  return cause instanceof ApiError ? cause : new ApiError(0, fallback)
}
