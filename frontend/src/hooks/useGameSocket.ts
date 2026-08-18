import { useCallback, useEffect, useRef, useState } from 'react'
import type {
  Cell,
  ClientMessage,
  GameError,
  GameState,
  ServerMessage,
} from '../types/protocol'

export const GAME_SESSION_STORAGE_KEY = 'connect-four.game-session'
const LEGACY_GAME_ID_STORAGE_KEY = 'connect-four.game-id'

const MAX_RECONNECT_ATTEMPTS = 4
const INITIAL_RECONNECT_DELAY_MS = 250

type StoredGameSession = {
  gameId: string
  playerToken: string
}

type PendingCommand =
  | { type: 'DROP_COUNTER'; board: Cell[][] }
  | { type: Exclude<ClientMessage['type'], 'DROP_COUNTER'> }

export type ConnectionState =
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'disconnected'

export type GameSocket = {
  connectionState: ConnectionState
  game: GameState | null
  error: GameError | null
  isAwaitingResponse: boolean
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
      (message.type !== 'GAME_SESSION' &&
        message.type !== 'GAME_STATE' &&
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

function clearStoredSession() {
  window.localStorage.removeItem(GAME_SESSION_STORAGE_KEY)
  window.localStorage.removeItem(LEGACY_GAME_ID_STORAGE_KEY)
}

function readStoredSession(): StoredGameSession | null {
  window.localStorage.removeItem(LEGACY_GAME_ID_STORAGE_KEY)
  const rawSession = window.localStorage.getItem(GAME_SESSION_STORAGE_KEY)
  if (rawSession === null) {
    return null
  }

  try {
    const session: unknown = JSON.parse(rawSession)
    if (
      typeof session !== 'object' ||
      session === null ||
      !('gameId' in session) ||
      !('playerToken' in session) ||
      typeof session.gameId !== 'string' ||
      session.gameId.length === 0 ||
      typeof session.playerToken !== 'string' ||
      session.playerToken.length === 0
    ) {
      clearStoredSession()
      return null
    }

    return {
      gameId: session.gameId,
      playerToken: session.playerToken,
    }
  } catch {
    clearStoredSession()
    return null
  }
}

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

function acknowledgesPendingCommand(
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

export function useGameSocket(): GameSocket {
  const [connectionState, setConnectionState] =
    useState<ConnectionState>('connecting')
  const [game, setGame] = useState<GameState | null>(null)
  const [error, setError] = useState<GameError | null>(null)
  const [isAwaitingResponse, setIsAwaitingResponse] = useState(false)
  const [connectionRequest, setConnectionRequest] = useState(0)
  const socketRef = useRef<WebSocket | null>(null)
  const gameRef = useRef<GameState | null>(null)
  const pendingCommandRef = useRef<PendingCommand | null>(null)

  useEffect(() => {
    gameRef.current = game
  }, [game])

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

        const session = readStoredSession()
        if (session !== null) {
          const resumeMessage: ClientMessage = {
            type: 'RESUME_GAME',
            payload: session,
          }
          pendingCommandRef.current = { type: 'RESUME_GAME' }
          setIsAwaitingResponse(true)
          socket.send(JSON.stringify(resumeMessage))
        } else {
          setIsAwaitingResponse(false)
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

        if (acknowledgesPendingCommand(message, pendingCommandRef.current)) {
          pendingCommandRef.current = null
          setIsAwaitingResponse(false)
        }

        switch (message.type) {
          case 'GAME_SESSION':
            window.localStorage.setItem(
              GAME_SESSION_STORAGE_KEY,
              JSON.stringify({
                gameId: message.payload.game.gameId,
                playerToken: message.payload.playerToken,
              } satisfies StoredGameSession),
            )
            setGame(message.payload.game)
            setError(null)
            break
          case 'GAME_STATE':
            setGame(message.payload)
            setError(null)
            break
          case 'GAME_ABANDONED':
            pendingCommandRef.current = null
            setIsAwaitingResponse(false)
            clearStoredSession()
            setGame(null)
            setError(
              message.payload.reason === 'OPPONENT_LEFT'
                ? {
                    code: 'OPPONENT_LEFT',
                    message: 'Your opponent left the game',
                    recoverable: true,
                  }
                : null,
            )
            break
          case 'ERROR':
            if (
              message.payload.code === 'GAME_NOT_FOUND' ||
              message.payload.code === 'INVALID_PLAYER_TOKEN'
            ) {
              clearStoredSession()
              setGame(null)
            }
            setError(message.payload)
            break
        }
      }

      socket.onclose = (event) => {
        if (disposed || socketRef.current !== socket) {
          return
        }

        socketRef.current = null
        pendingCommandRef.current = null
        setIsAwaitingResponse(false)
        if (
          event.code === 1000 &&
          event.reason === 'Game resumed on another connection'
        ) {
          setConnectionState('disconnected')
          setError({
            code: 'SESSION_REPLACED',
            message: 'This game was resumed in another browser or tab',
            recoverable: true,
          })
          return
        }
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
    pendingCommandRef.current =
      message.type === 'DROP_COUNTER'
        ? { type: message.type, board: gameRef.current?.board ?? [] }
        : { type: message.type }
    setIsAwaitingResponse(true)
    return true
  }, [])

  const reconnect = useCallback(() => {
    pendingCommandRef.current = null
    setIsAwaitingResponse(false)
    setConnectionRequest((request) => request + 1)
  }, [])

  const clearError = useCallback(() => setError(null), [])

  return {
    connectionState,
    game,
    error,
    isAwaitingResponse,
    sendMessage,
    reconnect,
    clearError,
  }
}
