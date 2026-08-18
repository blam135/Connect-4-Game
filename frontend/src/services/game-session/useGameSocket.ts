import { useCallback, useEffect, useRef, useState } from 'react'
import type { GameState } from '../../domain/game'
import { acknowledgesPendingCommand } from './pendingCommand'
import type { PendingCommand } from './pendingCommand'
import type { ClientMessage, GameError } from './protocol'
import { invalidServerMessage, parseServerMessage } from './protocol.parsers'
import {
  clearStoredSession,
  readStoredSession,
  storeGameSession,
} from './sessionStorage'

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
  isAwaitingResponse: boolean
  sendMessage: (message: ClientMessage) => boolean
  reconnect: () => void
  clearError: () => void
}

function gameSocketUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/game`
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
            storeGameSession({
              gameId: message.payload.game.gameId,
              playerToken: message.payload.playerToken,
            })
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
