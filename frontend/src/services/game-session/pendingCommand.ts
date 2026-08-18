import type { Cell } from '../../domain/game'
import type { ClientMessage, ServerMessage } from './protocol'

export type PendingCommand =
  | { type: 'DROP_COUNTER'; board: Cell[][] }
  | { type: Exclude<ClientMessage['type'], 'DROP_COUNTER'> }

function boardsEqual(left: Cell[][], right: Cell[][]) {
  return (
    left.length === right.length &&
    left.every(
      (row, rowIndex) =>
        row.length === right[rowIndex]?.length &&
        row.every((cell, columnIndex) => cell === right[rowIndex][columnIndex]),
    )
  )
}

export function acknowledgesPendingCommand(
  message: ServerMessage,
  pendingCommand: PendingCommand | null,
) {
  if (pendingCommand === null) {
    return false
  }
  if (message.type === 'ERROR') {
    return true
  }
  if (pendingCommand.type === 'DROP_COUNTER') {
    return (
      message.type === 'GAME_STATE' &&
      !boardsEqual(pendingCommand.board, message.payload.board)
    )
  }
  if (pendingCommand.type === 'RESUME_GAME') {
    return message.type === 'GAME_STATE'
  }
  if (pendingCommand.type === 'ABANDON_GAME') {
    return message.type === 'GAME_ABANDONED'
  }
  return message.type === 'GAME_SESSION'
}
