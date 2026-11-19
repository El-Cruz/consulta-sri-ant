import type { FormEvent } from 'react'

interface FormularioProps {
  email: string
  ruc: string
  placa: string
  contribuyenteVerificado: boolean
  cargandoContribuyente: boolean
  cargandoVehiculo: boolean
  errorContribuyente: string | null
  errorVehiculo: string | null
  onEmailChange: (value: string) => void
  onRucChange: (value: string) => void
  onPlacaChange: (value: string) => void
  onSubmitContribuyente: (e: FormEvent) => void
  onSubmitVehiculo: (e: FormEvent) => void
  onReset: () => void
}

/**
 * Componente C4: Formulario (Aplicación Web -> Formulario).
 * Gestiona el ingreso secuencial de Correo + RUC/Cédula y Matrícula de Vehículo.
 */
export function Formulario({
  email,
  ruc,
  placa,
  contribuyenteVerificado,
  cargandoContribuyente,
  cargandoVehiculo,
  errorContribuyente,
  errorVehiculo,
  onEmailChange,
  onRucChange,
  onPlacaChange,
  onSubmitContribuyente,
  onSubmitVehiculo,
  onReset,
}: FormularioProps) {
  return (
    <section className="panel panel-form" aria-label="Formulario de consulta">
      <div className="panel-header">
        <h2>Consulta Ciudadana SRI &amp; ANT</h2>
      </div>

      {/* Paso 1: Correo + RUC/Cédula */}
      <form onSubmit={onSubmitContribuyente} className="step-card">
        <div className="step-title-row">
          <span className={`step-badge ${contribuyenteVerificado ? 'done' : 'active'}`}>1</span>
          <div>
            <h3>Contribuyente y Licencia</h3>
          </div>
        </div>

        <div className="field">
          <label htmlFor="email-input">Correo electrónico</label>
          <input
            id="email-input"
            type="email"
            required
            placeholder="usuario@udla.edu.ec"
            value={email}
            onChange={(e) => onEmailChange(e.target.value)}
          />
        </div>

        <div className="field">
          <label htmlFor="ruc-input">RUC o Cédula de Persona Natural</label>
          <input
            id="ruc-input"
            type="text"
            inputMode="numeric"
            required
            maxLength={13}
            placeholder="Ej. 1710034065001 o 1710034065"
            value={ruc}
            onChange={(e) => onRucChange(e.target.value.replace(/[^\d]/g, ''))}
          />
        </div>

        {errorContribuyente && (
          <div className="alert alert-error" role="alert">
            {errorContribuyente}
          </div>
        )}

        <div className="actions-row">
          <button type="submit" className="btn btn-primary" disabled={cargandoContribuyente}>
            {cargandoContribuyente ? 'Consultando SRI y ANT...' : 'Validar RUC y Puntos ANT'}
          </button>
          {contribuyenteVerificado && (
            <button type="button" className="btn btn-ghost" onClick={onReset}>
              Nueva consulta
            </button>
          )}
        </div>
      </form>

      {/* Paso 2: Matrícula del vehículo */}
      <form
        onSubmit={onSubmitVehiculo}
        className={`step-card ${!contribuyenteVerificado ? 'step-locked' : ''}`}
      >
        <div className="step-title-row">
          <span className={`step-badge ${contribuyenteVerificado ? 'active' : ''}`}>2</span>
          <div>
            <h3>Matrícula del Vehículo</h3>
            <small>
              {contribuyenteVerificado
                ? 'Ingresa la placa, CAMV o CPN registrada en el SRI'
                : 'Completa primero la validación de Persona Natural en el Paso 1'}
            </small>
          </div>
        </div>

        <div className="field">
          <label htmlFor="placa-input">Placa / Matrícula</label>
          <input
            id="placa-input"
            type="text"
            required
            disabled={!contribuyenteVerificado}
            placeholder="Ej. PBA1234"
            value={placa}
            onChange={(e) => onPlacaChange(e.target.value.toUpperCase())}
          />
        </div>

        {errorVehiculo && (
          <div className="alert alert-error" role="alert">
            {errorVehiculo}
          </div>
        )}

        <button
          type="submit"
          className="btn btn-secondary"
          disabled={!contribuyenteVerificado || cargandoVehiculo}
        >
          {cargandoVehiculo ? 'Consultando vehículo SRI...' : 'Consultar Vehículo'}
        </button>
      </form>
    </section>
  )
}
