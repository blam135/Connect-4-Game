import { useState } from 'react'
import GameBoard from './components/GameBoard'
import GameSetup from './components/GameSetup'
import { useGameSocket } from './hooks/useGameSocket'
import type { FirstPlayer, GameState, PlayerColor } from './types/protocol'

function gameStatusMessage(game: GameState, isAwaitingResponse: boolean) {
  switch (game.status) {
    case 'HUMAN_WON':
      return 'You won! Four in a row.'
    case 'COMPUTER_WON':
      return 'The computer won this round.'
    case 'DRAW':
      return 'Draw — the board is full.'
    case 'IN_PROGRESS':
      return isAwaitingResponse
        ? 'Computer is thinking…'
        : `Your turn — drop a ${game.humanColor.toLowerCase()} counter.`
  }
}

function App() {
  const {
    connectionState,
    game,
    error,
    isAwaitingResponse,
    sendMessage,
    reconnect,
    clearError,
  } = useGameSocket()
  const [humanColor, setHumanColor] = useState<PlayerColor>('RED')
  const [firstPlayer, setFirstPlayer] = useState<FirstPlayer>('HUMAN')

  const isConnected = connectionState === 'connected'
  const canPlay =
    isConnected && !isAwaitingResponse && game?.status === 'IN_PROGRESS'

  function startGame() {
    sendMessage({
      type: 'START_GAME',
      payload: { humanColor, firstPlayer },
    })
  }

  function dropCounter(column: number) {
    if (canPlay) {
      sendMessage({ type: 'DROP_COUNTER', payload: { column } })
    }
  }

  function newGame() {
    if (isConnected && !isAwaitingResponse) {
      sendMessage({ type: 'ABANDON_GAME', payload: {} })
    }
  }

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

        {connectionState !== 'connected' && (
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
              <button className="secondary-button" type="button" onClick={reconnect}>
                Reconnect
              </button>
            )}
          </section>
        )}

        {error !== null && (
          <section className="error-banner" role="alert">
            <div>
              <strong>Something went wrong</strong>
              <p>{error.message}</p>
            </div>
            <button type="button" onClick={clearError} aria-label="Dismiss error">
              ×
            </button>
          </section>
        )}

        {game === null ? (
          <GameSetup
            humanColor={humanColor}
            firstPlayer={firstPlayer}
            disabled={!isConnected || isAwaitingResponse}
            isStarting={isAwaitingResponse}
            onHumanColorChange={setHumanColor}
            onFirstPlayerChange={setFirstPlayer}
            onStart={startGame}
          />
        ) : (
          <section className="game-view" aria-labelledby="game-heading">
            <div className="game-heading-row">
              <div>
                <p className="eyebrow">Human vs computer</p>
                <h1 id="game-heading">Your game</h1>
              </div>
              <button
                className="secondary-button"
                type="button"
                disabled={!isConnected || isAwaitingResponse}
                onClick={newGame}
              >
                New game
              </button>
            </div>

            <div className="player-summary" aria-label="Game configuration">
              <span>
                <i className={`mini-counter ${game.humanColor.toLowerCase()}`} />
                You are {game.humanColor.toLowerCase()}
              </span>
              <span>{game.firstPlayer === 'HUMAN' ? 'You moved first' : 'Computer moved first'}</span>
            </div>

            <GameBoard game={game} disabled={!canPlay} onDrop={dropCounter} />

            <section
              className={`game-status ${game.status.toLowerCase()}`}
              aria-live="polite"
              aria-busy={isAwaitingResponse}
            >
              {isAwaitingResponse && <span className="spinner" aria-hidden="true" />}
              <div>
                <p className="status-label">
                  {game.status === 'IN_PROGRESS' ? 'Game in progress' : 'Game over'}
                </p>
                <p>{gameStatusMessage(game, isAwaitingResponse)}</p>
              </div>
            </section>
          </section>
        )}
      </div>
    </main>
  )
}

export default App
