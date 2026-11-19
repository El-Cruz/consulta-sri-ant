export interface ContribuyenteResponse {
  email: string
  numeroRuc: string
  cedula: string
  existeContribuyente: boolean
  esPersonaNatural: boolean
  razonSocial: string
  estadoContribuyenteRuc: string
  actividadEconomicaPrincipal: string
  tipoContribuyente: string
  regimen: string
  obligadoLlevarContabilidad: string
  agenteRetencion: string
  contribuyenteEspecial: string
  fechaInicioActividades: string
  fechaActualizacion: string
}

export interface VehiculoResponse {
  codigoVehiculo: number | null
  numeroPlaca: string
  numeroCamvCpn: string
  descripcionMarca: string
  descripcionModelo: string
  anioAuto: number | null
  descripcionPais: string
  ultimoAnioPagado: number | null
  fechaUltimaMatricula: string
  fechaCaducidadMatricula: string
  descripcionCanton: string
  descripcionServicio: string
  nombreClase: string
  cilindraje: string
  colorVehiculo1: string
  prohibidoEnajenar: string
  estadoExoneracion: string
}

export interface LicenciaResponse {
  cedula: string
  placaConsultada: string
  nombreConductor: string
  puntos: number | null
  puntosMaximos: number
  totalCitaciones: number
  detalleCitaciones: string[]
  fuenteDatos: 'ANT_ONLINE' | 'CACHE_CLOUD' | 'CACHE_CLOUD_FALLBACK' | string
  proveedorCache: string
  cacheKey: string
  desdeCache: boolean
  almacenadoEnCache: boolean
  estadoAnt: string
  obtenidoEn: string
  mensajeDetalle: string
}

export interface HealthResponse {
  status: string
  cacheProvider: string
  upstashCloudConnected: boolean
}
