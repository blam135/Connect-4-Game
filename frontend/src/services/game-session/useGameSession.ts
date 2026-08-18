import { useCallback } from 'react'
import type { StartGameIntent } from './gameSession.types'
import { useGameSocket } from './useGameSocket'

export function useGameSession() {
  const socket = useGameSocket()
  const { sendMessage } = socket

  const startGame = useCallback(
    (intent: StartGameIntent) => {
      switch (intent.kind) {
        case 'computer':
          return sendMessage({
            type: 'START_GAME',
            payload: {
              humanColor: intent.playerColor,
              firstPlayer: intent.firstPlayer,
            },
          })
        case 'create-online':
          return sendMessage({
            type: 'CREATE_ONLINE_GAME',
            payload: { hostColor: intent.playerColor },
          })
        case 'join-online':
          return sendMessage({
            type: 'JOIN_ONLINE_GAME',
            payload: { roomCode: intent.roomCode },
          })
      }
    },
    [sendMessage],
  )

  const dropCounter = useCallback(
    (column: number) => {
      sendMessage({ type: 'DROP_COUNTER', payload: { column } })
    },
    [sendMessage],
  )

  const leaveGame = useCallback(() => {
    sendMessage({ type: 'ABANDON_GAME', payload: {} })
  }, [sendMessage])

  return {
    connectionState: socket.connectionState,
    game: socket.game,
    error: socket.error,
    isAwaitingResponse: socket.isAwaitingResponse,
    reconnect: socket.reconnect,
    clearError: socket.clearError,
    actions: { startGame, dropCounter, leaveGame },
  }
}
