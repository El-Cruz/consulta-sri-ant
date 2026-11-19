import type {
  ContribuyenteResponse,
  HealthResponse,
  LicenciaResponse,
  VehiculoResponse,
} from './types'

const BASE_URL = import.meta.env.VITE_API_URL ?? ''

export class ApiError extends Error {
  status: number
  code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

async function fetchJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${url}`, init)
  const payload = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new ApiError(
      response.status,
      payload.code ?? 'API_ERROR',
      payload.error ?? `Error HTTP ${response.status}`,
    )
  }
  return payload as T
}

export function getHealth(): Promise<HealthResponse> {
  return fetchJson<HealthResponse>('/api/health')
}

export function consultarContribuyente(
  email: string,
  ruc: string,
): Promise<ContribuyenteResponse> {
  const params = new URLSearchParams({ email, ruc })
  return fetchJson<ContribuyenteResponse>(`/api/consulta/contribuyente?${params}`)
}

export function consultarVehiculo(placa: string): Promise<VehiculoResponse> {
  const params = new URLSearchParams({ placa })
  return fetchJson<VehiculoResponse>(`/api/consulta/vehiculo?${params}`)
}

export function consultarLicencia(
  cedula: string,
  placa = '',
  options: { nombre?: string; forceRefresh?: boolean; simularFalloAnt?: boolean } = {},
): Promise<LicenciaResponse> {
  const params = new URLSearchParams({
    cedula,
    placa,
    nombre: options.nombre ?? '',
    forceRefresh: String(Boolean(options.forceRefresh)),
    simularFalloAnt: String(Boolean(options.simularFalloAnt)),
  })
  return fetchJson<LicenciaResponse>(`/api/consulta/licencia?${params}`)
}

export function guardarLicenciaEnCache(body: {
  cedula: string
  placa: string
  nombreConductor: string
  puntos: number
  totalCitaciones: number
}): Promise<LicenciaResponse> {
  return fetchJson<LicenciaResponse>('/api/consulta/licencia/cache', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}
