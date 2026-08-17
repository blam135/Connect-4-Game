import { useCallback, useEffect, useRef, useState } from 'react'
import type {
  ClientMessage,
  GameError,
  GameState,
  ServerMessage,
} from '../types/protocol'

export const GAME_ID_STORAGE_KEY = 'connect-four.game-id'

const MAX_RECONNECT_ATTEMPTS = 4
const INITIAL_RECONNECT_DELAY_MS = 250

export type ConnectionState =
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'disconnected'

export type GameSocket = {
  connectionState: ConnectionState
  game: GameState | null
  error: GameError | null
  sendMessage: (message: ClientMessage) => boolean
  reconnect: () => void
  clearError: () => void
}

function gameSocketUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/game`
}

function invalidServerMessage(): GameError {
  return {
    code: 'INVALID_SERVER_MESSAGE',
    message: 'The server returned an invalid message',
    recoverable: false,
  }
}

function parseServerMessage(data: unknown): ServerMessage | null {
  if (typeof data !== 'string') {
    return null
  }

  try {
    const message: unknown = JSON.parse(data)
    if (
      typeof message !== 'object' ||
      message === null ||
      !('type' in message) ||
      !('payload' in message) ||
      (message.type !== 'GAME_STATE' &&
        message.type !== 'GAME_ABANDONED' &&
        message.type !== 'ERROR')
    ) {
      return null
    }

    return message as ServerMessage
  } catch {
    return null
  }
}

export function useGameSocket(): GameSocket {
  const [connectionState, setConnectionState] =
    useState<ConnectionState>('connecting')
  const [game, setGame] = useState<GameState | null>(null)
  const [error, setError] = useState<GameError | null>(null)
  const [connectionRequest, setConnectionRequest] = useState(0)
  const socketRef = useRef<WebSocket | null>(null)

  useEffect(() => {
    let disposed = false
    let reconnectTimer: number | null = null
    let reconnectAttempts = 0

    function connect(isReconnect = false) {
      if (disposed) {
        return
      }

      setConnectionState(isReconnect ? 'reconnecting' : 'connecting')
      const socket = new WebSocket(gameSocketUrl())
      socketRef.current = socket

      socket.onopen = () => {
        if (socketRef.current !== socket) {
          return
        }

        reconnectAttempts = 0
        setConnectionState('connected')
        setError(null)

        const gameId = window.localStorage.getItem(GAME_ID_STORAGE_KEY)
        if (gameId !== null) {
          const resumeMessage: ClientMessage = {
            type: 'RESUME_GAME',
            payload: { gameId },
          }
          socket.send(JSON.stringify(resumeMessage))
        }
      }

      socket.onmessage = (event) => {
        if (socketRef.current !== socket) {
          return
        }

        const message = parseServerMessage(event.data)
        if (message === null) {
          setError(invalidServerMessage())
          return
        }

        switch (message.type) {
          case 'GAME_STATE':
            window.localStorage.setItem(
              GAME_ID_STORAGE_KEY,
              message.payload.gameId,
            )
            setGame(message.payload)
            setError(null)
            break
          case 'GAME_ABANDONED':
            window.localStorage.removeItem(GAME_ID_STORAGE_KEY)
            setGame(null)
            setError(null)
            break
          case 'ERROR':
            if (message.payload.code === 'GAME_NOT_FOUND') {
              window.localStorage.removeItem(GAME_ID_STORAGE_KEY)
              setGame(null)
            }
            setError(message.payload)
            break
        }
      }

      socket.onclose = () => {
        if (disposed || socketRef.current !== socket) {
          return
        }

        socketRef.current = null
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
          setConnectionState('disconnected')
          setError({
            code: 'CONNECTION_FAILED',
            message: 'Could not reconnect to the game server',
            recoverable: true,
          })
          return
        }

        const delay = INITIAL_RECONNECT_DELAY_MS * 2 ** reconnectAttempts
        reconnectAttempts += 1
        setConnectionState('reconnecting')
        reconnectTimer = window.setTimeout(() => connect(true), delay)
      }
    }

    connect()

    return () => {
      disposed = true
      if (reconnectTimer !== null) {
        window.clearTimeout(reconnectTimer)
      }
      const socket = socketRef.current
      socketRef.current = null
      socket?.close()
    }
  }, [connectionRequest])

  const sendMessage = useCallback((message: ClientMessage) => {
    const socket = socketRef.current
    if (socket === null || socket.readyState !== WebSocket.OPEN) {
      setError({
        code: 'CONNECTION_UNAVAILABLE',
        message: 'The game server is not connected',
        recoverable: true,
      })
      return false
    }

    socket.send(JSON.stringify(message))
    return true
  }, [])

  const reconnect = useCallback(() => {
    setConnectionRequest((request) => request + 1)
  }, [])

  const clearError = useCallback(() => setError(null), [])

  return {
    connectionState,
    game,
    error,
    sendMessage,
    reconnect,
    clearError,
  }
}
