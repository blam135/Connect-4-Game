import { useState } from 'react'
import type { GameState } from '../../domain/game'
import type { ConnectionState } from '../../services/game-session/useGameSocket'
import GameBoard from './GameBoard'
import { GameStatus } from './GameStatus'
import { canPlay } from './gameplay.selectors'
import { PlayerSummary } from './PlayerSummary'
import { WaitingRoom } from './WaitingRoom'
import type { CopyStatus } from './WaitingRoom'
import './gameplay.css'

type GameScreenProps = {
  game: GameState
  connectionState: ConnectionState
  isAwaitingResponse: boolean
  onDropCounter: (column: number) => void
  onLeaveGame: () => void
}

function inviteUrl(roomCode: string) {
  const url = new URL(window.location.href)
  url.search = ''
  url.hash = ''
  url.searchParams.set('room', roomCode)
  return url.toString()
}

function GameScreen({
  game,
  connectionState,
  isAwaitingResponse,
  onDropCounter,
  onLeaveGame,
}: GameScreenProps) {
  const [copyStatus, setCopyStatus] = useState<CopyStatus>('idle')
  const inputEnabled = canPlay(game, connectionState, isAwaitingResponse)
  const isConnected = connectionState === 'connected'

  async function copyInvite() {
    if (game.roomCode === null) {
      return
    }

    try {
      if (navigator.clipboard === undefined) {
        throw new Error('Clipboard unavailable')
      }
      await navigator.clipboard.writeText(inviteUrl(game.roomCode))
      setCopyStatus('copied')
    } catch {
      setCopyStatus('failed')
    }
  }

  return (
    <section className="game-view" aria-labelledby="game-heading">
      <div className="game-heading-row">
        <div>
          <p className="eyebrow">
            {game.mode === 'ONLINE' ? 'Online two-player' : 'Human vs computer'}
          </p>
          <h1 id="game-heading">Your game</h1>
        </div>
        <button
          className="secondary-button"
          type="button"
          disabled={!isConnected || isAwaitingResponse}
          onClick={onLeaveGame}
        >
          {game.mode === 'ONLINE' ? 'Leave game' : 'New game'}
        </button>
      </div>

      <PlayerSummary game={game} />

      {game.mode === 'ONLINE' &&
        game.status === 'WAITING_FOR_OPPONENT' &&
        game.roomCode !== null && (
          <WaitingRoom
            roomCode={game.roomCode}
            copyStatus={copyStatus}
            onCopyInvite={copyInvite}
          />
        )}

      <GameBoard
        board={game.board}
        computerColumn={game.computerColumn}
        disabled={!inputEnabled}
        onDrop={(column) => {
          if (inputEnabled) {
            onDropCounter(column)
          }
        }}
      />

      <GameStatus game={game} isAwaitingResponse={isAwaitingResponse} />
    </section>
  )
}

export default GameScreen
