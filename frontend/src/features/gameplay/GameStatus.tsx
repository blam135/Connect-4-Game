import type { GameState } from '../../domain/game'
import { gameStatusMessage, statusLabel } from './gameplay.selectors'

type GameStatusProps = {
  game: GameState
  isAwaitingResponse: boolean
}

export function GameStatus({ game, isAwaitingResponse }: GameStatusProps) {
  return (
    <section
      className={`game-status ${game.status.toLowerCase()}`}
      aria-live="polite"
      aria-busy={isAwaitingResponse}
    >
      {isAwaitingResponse && <span className="spinner" aria-hidden="true" />}
      <div>
        <p className="status-label">{statusLabel(game)}</p>
        <p>{gameStatusMessage(game, isAwaitingResponse)}</p>
      </div>
    </section>
  )
}
