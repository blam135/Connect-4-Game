import type { GameState } from '../../domain/game'

export function PlayerSummary({ game }: { game: GameState }) {
  return (
    <div className="player-summary" aria-label="Game configuration">
      <span>
        <i className={`mini-counter ${game.yourColor.toLowerCase()}`} />
        You are {game.yourColor.toLowerCase()}
      </span>
      <span>
        {game.mode === 'ONLINE'
          ? 'Red moves first'
          : game.startingColor === game.yourColor
            ? 'You moved first'
            : 'Computer moved first'}
      </span>
      {game.roomCode !== null && <span>Room {game.roomCode}</span>}
    </div>
  )
}
