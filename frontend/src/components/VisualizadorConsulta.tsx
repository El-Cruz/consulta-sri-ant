import type { ContribuyenteResponse, LicenciaResponse, VehiculoResponse } from '../types'

interface VisualizadorConsultaProps {
  contribuyente: ContribuyenteResponse | null
  vehiculo: VehiculoResponse | null
  licencia: LicenciaResponse | null
  licenciaError: string | null
  cargandoLicencia: boolean
  onConsultarDesdeCache: () => void
  onForzarAntVivo: () => void
  onSimularCaidaAnt: () => void
}

/**
 * Componente C4: Visualizador de Consulta (Aplicación Web -> Visualizador de Consulta).
 */
export function VisualizadorConsulta({
  contribuyente,
  vehiculo,
  licencia,
  licenciaError,
  cargandoLicencia,
  onConsultarDesdeCache,
  onForzarAntVivo,
  onSimularCaidaAnt,
}: VisualizadorConsultaProps) {

  const getBadgeStyle = (fuente: string) => {
    if (fuente === 'ANT_ONLINE') return { className: 'badge-online', label: 'ANT EN VIVO' }
    if (fuente === 'CACHE_CLOUD') return { className: 'badge-cache', label: 'DESDE CACHÉ' }
    if (fuente === 'CACHE_CLOUD_FALLBACK') return { className: 'badge-fallback', label: 'RESPALDO CACHÉ' }
    return { className: 'badge-cache', label: fuente }
  }

  return (
    <section className="panel panel-results" aria-label="Visualizador de resultados">
      <div className="panel-header">
        <h2>Resultados</h2>
      </div>

      {!contribuyente && !vehiculo && !licencia && !licenciaError && (
        <div className="empty-state">
          <div className="empty-icon" aria-hidden="true">SRI + ANT</div>
          <h3>Esperando consulta</h3>
          <p>
            Ingresa un correo y RUC o cédula de persona natural para iniciar la consulta.
          </p>
        </div>
      )}

      {/* Tarjeta Contribuyente SRI */}
      {contribuyente && (
        <article className="result-card">
          <div className="card-header">
            <div>
              <span className="source-chip chip-sri">SRI Contribuyente</span>
              <h3>{contribuyente.razonSocial}</h3>
            </div>
            <span className="status-badge badge-online">
              {contribuyente.esPersonaNatural ? 'Persona Natural Verificada' : contribuyente.tipoContribuyente}
            </span>
          </div>

          <dl className="data-grid">
            <div>
              <dt>Correo</dt>
              <dd>{contribuyente.email}</dd>
            </div>
            <div>
              <dt>RUC / Cédula</dt>
              <dd className="mono">
                {contribuyente.numeroRuc} <span className="muted">({contribuyente.cedula})</span>
              </dd>
            </div>
            <div>
              <dt>Estado RUC</dt>
              <dd>{contribuyente.estadoContribuyenteRuc || 'N/D'}</dd>
            </div>
            <div>
              <dt>Régimen</dt>
              <dd>{contribuyente.regimen || 'GENERAL'}</dd>
            </div>
            <div>
              <dt>Obligado a Contabilidad</dt>
              <dd>{contribuyente.obligadoLlevarContabilidad || 'N/D'}</dd>
            </div>
            <div>
              <dt>Inicio de Actividades</dt>
              <dd>{contribuyente.fechaInicioActividades || 'N/D'}</dd>
            </div>
            <div className="span-2">
              <dt>Actividad Económica Principal</dt>
              <dd>{contribuyente.actividadEconomicaPrincipal || 'N/D'}</dd>
            </div>
          </dl>
        </article>
      )}

      {/* Tarjeta Puntos de Licencia ANT */}
      {(licencia || licenciaError || cargandoLicencia) && (
        <article className="result-card card-ant">
          <div className="card-header">
            <div>
              <span className="source-chip chip-ant">ANT Licencia</span>
              <h3>Puntos de Licencia</h3>
            </div>
            {licencia && (
              <span className={`status-badge ${getBadgeStyle(licencia.fuenteDatos).className}`}>
                {getBadgeStyle(licencia.fuenteDatos).label}
              </span>
            )}
          </div>

          {cargandoLicencia && <p className="muted">Consultando puntos de licencia ANT...</p>}

          {licencia && (
            <>
              <div className="points-hero">
                <div className="points-circle">
                  <strong>{licencia.puntos ?? '--'}</strong>
                  <span>/ {licencia.puntosMaximos} pts</span>
                </div>
                <div className="points-meta">
                  <p className="driver-name">{licencia.nombreConductor}</p>
                  <div className="cache-tags">
                    <span>Citaciones: {licencia.totalCitaciones}</span>
                    <span>Obtenido: {new Date(licencia.obtenidoEn).toLocaleString()}</span>
                  </div>
                </div>
              </div>

              <div className="demo-controls">
                <button type="button" className="btn btn-sm" onClick={onConsultarDesdeCache} disabled={cargandoLicencia}>
                  Leer desde Caché
                </button>
                <button type="button" className="btn btn-sm" onClick={onForzarAntVivo} disabled={cargandoLicencia}>
                  Forzar consulta ANT en vivo
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-warn"
                  onClick={onSimularCaidaAnt}
                  disabled={cargandoLicencia}
                >
                  Simular caída ANT
                </button>
              </div>
            </>
          )}

          {licenciaError && (
            <div className="alert alert-warn" role="status">
              {licenciaError}
            </div>
          )}
        </article>
      )}

      {/* Tarjeta Vehículo SRI */}
      {vehiculo && (
        <article className="result-card">
          <div className="card-header">
            <div>
              <span className="source-chip chip-vehiculo">SRI Matriculación Vehicular</span>
              <h3>
                {vehiculo.descripcionMarca} {vehiculo.descripcionModelo}
              </h3>
            </div>
            <span className="status-badge badge-plate mono">{vehiculo.numeroPlaca}</span>
          </div>

          <dl className="data-grid">
            <div>
              <dt>Año Modelo</dt>
              <dd>{vehiculo.anioAuto ?? 'N/D'}</dd>
            </div>
            <div>
              <dt>Último Año Pagado</dt>
              <dd>{vehiculo.ultimoAnioPagado ?? 'N/D'}</dd>
            </div>
            <div>
              <dt>CAMV / CPN</dt>
              <dd className="mono">{vehiculo.numeroCamvCpn || 'N/D'}</dd>
            </div>
            <div>
              <dt>País de Origen</dt>
              <dd>{vehiculo.descripcionPais || 'N/D'}</dd>
            </div>
            <div>
              <dt>Cantón / Servicio</dt>
              <dd>
                {[vehiculo.descripcionCanton, vehiculo.descripcionServicio].filter(Boolean).join(' · ') || 'N/D'}
              </dd>
            </div>
            <div>
              <dt>Exoneración / Prohibición</dt>
              <dd>
                Exonerado: {vehiculo.estadoExoneracion || 'NO'} · Prohibido enajenar:{' '}
                {vehiculo.prohibidoEnajenar || 'NO'}
              </dd>
            </div>
          </dl>
        </article>
      )}
    </section>
  )
}
