import type { GameState } from '../../domain/game'
import type { ConnectionState } from '../../services/game-session/useGameSocket'

export function canPlay(
  game: GameState,
  connectionState: ConnectionState,
  isAwaitingResponse: boolean,
) {
  return (
    connectionState === 'connected' &&
    !isAwaitingResponse &&
    game.status === 'IN_PROGRESS' &&
    game.opponentConnected &&
    game.currentTurn === game.yourColor
  )
}

export function gameStatusMessage(
  game: GameState,
  isAwaitingResponse: boolean,
) {
  if (game.status === 'WAITING_FOR_OPPONENT') {
    return 'Waiting for another player to join.'
  }

  if (game.status === 'DRAW') {
    return 'Draw — the board is full.'
  }

  if (game.status === 'RED_WON' || game.status === 'YELLOW_WON') {
    const winningColor = game.status === 'RED_WON' ? 'RED' : 'YELLOW'
    return winningColor === game.yourColor
      ? 'You won! Four in a row.'
      : game.mode === 'ONLINE'
        ? 'Your opponent won this round.'
        : 'The computer won this round.'
  }

  if (!game.opponentConnected) {
    return 'Your opponent is offline. Play will resume when they reconnect.'
  }

  if (game.mode === 'COMPUTER') {
    return isAwaitingResponse
      ? 'Computer is thinking…'
      : `Your turn — drop a ${game.yourColor.toLowerCase()} counter.`
  }

  if (isAwaitingResponse) {
    return 'Sending your move…'
  }

  return game.currentTurn === game.yourColor
    ? `Your turn — drop a ${game.yourColor.toLowerCase()} counter.`
    : 'Your opponent’s turn.'
}

export function statusLabel(game: GameState) {
  if (game.status === 'WAITING_FOR_OPPONENT') {
    return 'Waiting room'
  }
  if (game.status === 'IN_PROGRESS' && !game.opponentConnected) {
    return 'Game paused'
  }
  return game.status === 'IN_PROGRESS' ? 'Game in progress' : 'Game over'
}
