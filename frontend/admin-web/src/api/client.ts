export class ApiError extends Error {
  readonly status: number
  readonly code: string | null
  readonly correlationId: string | null

  constructor(status: number, message: string, code: string | null = null, correlationId: string | null = null) {
    super(message)
    this.status = status
    this.code = code
    this.correlationId = correlationId
  }
}

export async function api<T>(path: string, token: string | undefined, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch(path, { ...init, headers })
  if (!response.ok) {
    const problem = await readProblem(response)
    throw new ApiError(response.status, problem.message, problem.code, problem.correlationId)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

async function readProblem(response: Response): Promise<{ message: string; code: string | null; correlationId: string | null }> {
  const headerId = response.headers.get('X-Correlation-ID')
  try {
    const body = await response.json() as { detail?: string; code?: string; correlationId?: string }
    return {
      message: body.detail ?? response.statusText,
      code: body.code ?? null,
      correlationId: body.correlationId ?? headerId,
    }
  } catch {
    return { message: response.statusText, code: null, correlationId: headerId }
  }
}

export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}
