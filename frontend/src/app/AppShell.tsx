import type { ReactNode } from 'react'
import type { GameError } from '../services/game-session/protocol'
import type { ConnectionState } from '../services/game-session/useGameSocket'
import './app.css'

type AppShellProps = {
  connectionState: ConnectionState
  error: GameError | null
  onReconnect: () => void
  onClearError: () => void
  children: ReactNode
}

function ConnectionBanner({
  connectionState,
  onReconnect,
}: Pick<AppShellProps, 'connectionState' | 'onReconnect'>) {
  if (connectionState === 'connected') {
    return null
  }

  return (
    <section className="connection-banner" aria-live="polite">
      <div>
        <strong>
          {connectionState === 'disconnected'
            ? 'Connection lost'
            : connectionState === 'reconnecting'
              ? 'Reconnecting to your game…'
              : 'Connecting to the game server…'}
        </strong>
        <p>
          {connectionState === 'disconnected'
            ? 'Your saved game is still available. Try connecting again.'
            : 'Game controls will be ready in a moment.'}
        </p>
      </div>
      {connectionState === 'disconnected' && (
        <button className="secondary-button" type="button" onClick={onReconnect}>
          Reconnect
        </button>
      )}
    </section>
  )
}

function ErrorBanner({
  error,
  onClearError,
}: Pick<AppShellProps, 'error' | 'onClearError'>) {
  if (error === null) {
    return null
  }

  return (
    <section className="error-banner" role="alert">
      <div>
        <strong>Something went wrong</strong>
        <p>{error.message}</p>
      </div>
      <button type="button" onClick={onClearError} aria-label="Dismiss error">
        ×
      </button>
    </section>
  )
}

export function AppShell({
  connectionState,
  error,
  onReconnect,
  onClearError,
  children,
}: AppShellProps) {
  return (
    <main className="app-shell">
      <div className="app-frame">
        <header className="site-header">
          <a className="brand" href="/" aria-label="Connect Four home">
            <span className="brand-mark" aria-hidden="true">
              <span />
              <span />
              <span />
              <span />
            </span>
            <span>Connect Four</span>
          </a>
          <span className={`connection-chip ${connectionState}`}>
            <span aria-hidden="true" />
            {connectionState === 'connected' ? 'Online' : connectionState}
          </span>
        </header>

        <ConnectionBanner
          connectionState={connectionState}
          onReconnect={onReconnect}
        />
        <ErrorBanner error={error} onClearError={onClearError} />
        {children}
      </div>
    </main>
  )
}
