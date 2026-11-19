import { useEffect, useState, type FormEvent } from 'react'
import {
  ApiError,
  consultarContribuyente,
  consultarLicencia,
  consultarVehiculo,
  getHealth,
  guardarLicenciaEnCache,
} from './api'
import { Formulario } from './components/Formulario'
import { VisualizadorConsulta } from './components/VisualizadorConsulta'
import type {
  ContribuyenteResponse,
  HealthResponse,
  LicenciaResponse,
  VehiculoResponse,
} from './types'

export function App() {
  const [email, setEmail] = useState('')
  const [ruc, setRuc] = useState('')
  const [placa, setPlaca] = useState('')

  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [contribuyente, setContribuyente] = useState<ContribuyenteResponse | null>(null)
  const [vehiculo, setVehiculo] = useState<VehiculoResponse | null>(null)
  const [licencia, setLicencia] = useState<LicenciaResponse | null>(null)

  const [cargandoContribuyente, setCargandoContribuyente] = useState(false)
  const [cargandoVehiculo, setCargandoVehiculo] = useState(false)
  const [cargandoLicencia, setCargandoLicencia] = useState(false)

  const [errorContribuyente, setErrorContribuyente] = useState<string | null>(null)
  const [errorVehiculo, setErrorVehiculo] = useState<string | null>(null)
  const [errorLicencia, setErrorLicencia] = useState<string | null>(null)

  useEffect(() => {
    getHealth()
      .then(setHealth)
      .catch(() => setHealth(null))
  }, [])

  const cedulaActual = contribuyente?.cedula || (ruc.length >= 10 ? ruc.slice(0, 10) : '')

  const cargarLicencia = async (
    cedula: string,
    placaFiltro = '',
    options: { forceRefresh?: boolean; simularFalloAnt?: boolean } = {},
  ) => {
    setCargandoLicencia(true)
    setErrorLicencia(null)
    try {
      const res = await consultarLicencia(cedula, placaFiltro, options)
      setLicencia(res)
    } catch (err) {
      setLicencia(null)
      setErrorLicencia(err instanceof ApiError ? err.message : 'No se pudo consultar puntos en la ANT')
    } finally {
      setCargandoLicencia(false)
    }
  }

  const handleSubmitContribuyente = async (e: FormEvent) => {
    e.preventDefault()
    setCargandoContribuyente(true)
    setErrorContribuyente(null)
    setErrorVehiculo(null)
    setVehiculo(null)

    try {
      const data = await consultarContribuyente(email, ruc)
      setContribuyente(data)
      // Al ingresar y validar la cédula, invoca automáticamente el microservicio ANT con caché cloud
      void cargarLicencia(data.cedula, placa)
    } catch (err) {
      setContribuyente(null)
      setLicencia(null)
      setErrorContribuyente(
        err instanceof ApiError ? err.message : 'No se pudo validar el contribuyente en el SRI',
      )
    } finally {
      setCargandoContribuyente(false)
    }
  }

  const handleSubmitVehiculo = async (e: FormEvent) => {
    e.preventDefault()
    if (!contribuyente) return
    setCargandoVehiculo(true)
    setErrorVehiculo(null)

    try {
      const data = await consultarVehiculo(placa)
      setVehiculo(data)
      if (cedulaActual) {
        void cargarLicencia(cedulaActual, data.numeroPlaca)
      }
    } catch (err) {
      setVehiculo(null)
      setErrorVehiculo(
        err instanceof ApiError ? err.message : 'No se pudo consultar el vehículo en el SRI',
      )
    } finally {
      setCargandoVehiculo(false)
    }
  }

  const handleGuardarCacheDemo = async (datos: {
    nombreConductor: string
    puntos: number
    totalCitaciones: number
  }) => {
    if (!cedulaActual) return
    const saved = await guardarLicenciaEnCache({
      cedula: cedulaActual,
      placa,
      ...datos,
    })
    setLicencia(saved)
    setErrorLicencia(null)
  }

  const handleReset = () => {
    setContribuyente(null)
    setVehiculo(null)
    setLicencia(null)
    setErrorContribuyente(null)
    setErrorVehiculo(null)
    setErrorLicencia(null)
    setPlaca('')
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div>
          <h1>Sistema de Consulta SRI y ANT</h1>
          <p>ISWZ2202 · Diseño y Arquitectura de Software · React + Java Spring Boot + Upstash Redis</p>
        </div>
        <div className="cloud-pill" title="Estado de la Caché Cloud en Upstash Redis">
          <span className={`dot ${health?.upstashCloudConnected ? 'up' : 'down'}`} />
          <span>
            {health
              ? `${health.cacheProvider} (${health.upstashCloudConnected ? 'Conectado' : 'Modo local'})`
              : 'Backend Java desconectado'}
          </span>
        </div>
      </header>

      <main className="workspace-grid">
        <Formulario
          email={email}
          ruc={ruc}
          placa={placa}
          contribuyenteVerificado={Boolean(contribuyente?.esPersonaNatural)}
          cargandoContribuyente={cargandoContribuyente}
          cargandoVehiculo={cargandoVehiculo}
          errorContribuyente={errorContribuyente}
          errorVehiculo={errorVehiculo}
          onEmailChange={setEmail}
          onRucChange={setRuc}
          onPlacaChange={setPlaca}
          onSubmitContribuyente={handleSubmitContribuyente}
          onSubmitVehiculo={handleSubmitVehiculo}
          onReset={handleReset}
        />

        <VisualizadorConsulta
          contribuyente={contribuyente}
          vehiculo={vehiculo}
          licencia={licencia}
          licenciaError={errorLicencia}
          cargandoLicencia={cargandoLicencia}
          cedulaActual={cedulaActual}
          onConsultarDesdeCache={() =>
            cedulaActual && void cargarLicencia(cedulaActual, placa, { forceRefresh: false })
          }
          onForzarAntVivo={() =>
            cedulaActual && void cargarLicencia(cedulaActual, placa, { forceRefresh: true })
          }
          onSimularCaidaAnt={() =>
            cedulaActual &&
            void cargarLicencia(cedulaActual, placa, { forceRefresh: true, simularFalloAnt: true })
          }
          onGuardarCacheDemo={handleGuardarCacheDemo}
        />
      </main>
    </div>
  )
}
