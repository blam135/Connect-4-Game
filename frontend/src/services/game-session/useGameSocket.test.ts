import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { installMockBrowserApis, MockWebSocket } from '../../test/MockWebSocket'
import type { GameState } from '../../domain/game'
import type { ServerMessage } from './protocol'
import { GAME_SESSION_STORAGE_KEY } from './sessionStorage'
import { useGameSocket } from './useGameSocket'

const game: GameState = {
  gameId: '6484817f-89d1-4518-874a-dba30795a481',
  mode: 'ONLINE',
  board: Array.from({ length: 6 }, () => Array(7).fill('EMPTY')),
  status: 'IN_PROGRESS',
  yourColor: 'RED',
  startingColor: 'RED',
  currentTurn: 'RED',
  roomCode: 'ABC123',
  opponentConnected: true,
  computerColumn: null,
}

const credential = {
  gameId: game.gameId,
  playerToken: 'private-player-token',
}

const gameSession: Extract<ServerMessage, { type: 'GAME_SESSION' }> = {
  type: 'GAME_SESSION',
  payload: { playerToken: credential.playerToken, game },
}

describe('useGameSocket', () => {
  beforeEach(() => {
    installMockBrowserApis()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('connects to the game endpoint and exposes the open connection', () => {
    const { result } = renderHook(() => useGameSocket())
    const socket = MockWebSocket.instances[0]

    expect(socket.url).toBe('ws://localhost:3000/ws/game')
    expect(result.current.connectionState).toBe('connecting')

    act(() => socket.open())

    expect(result.current.connectionState).toBe('connected')
  })

  it('resumes a stored game with both private credential fields', () => {
    window.localStorage.setItem(
      GAME_SESSION_STORAGE_KEY,
      JSON.stringify(credential),
    )
    renderHook(() => useGameSocket())

    const socket = MockWebSocket.instances[0]
    act(() => socket.open())

    expect(socket.send).toHaveBeenCalledWith(
      JSON.stringify({ type: 'RESUME_GAME', payload: credential }),
    )
  })

  it('discards malformed stored credentials without sending a resume', () => {
    window.localStorage.setItem(GAME_SESSION_STORAGE_KEY, '{broken')
    renderHook(() => useGameSocket())

    const socket = MockWebSocket.instances[0]
    act(() => socket.open())

    expect(socket.send).not.toHaveBeenCalled()
    expect(window.localStorage.getItem(GAME_SESSION_STORAGE_KEY)).toBeNull()
  })

  it('stores credentials from a new session and accepts later snapshots', () => {
    const { result } = renderHook(() => useGameSocket())
    const socket = MockWebSocket.instances[0]

    act(() => {
      socket.open()
      socket.receive(gameSession)
    })

    expect(result.current.game).toEqual(game)
    expect(window.localStorage.getItem(GAME_SESSION_STORAGE_KEY)).toBe(
      JSON.stringify(credential),
    )

    const opponentTurn: GameState = { ...game, currentTurn: 'YELLOW' }
    act(() => socket.receive({ type: 'GAME_STATE', payload: opponentTurn }))

    expect(result.current.game).toEqual(opponentTurn)
    expect(window.localStorage.getItem(GAME_SESSION_STORAGE_KEY)).toBe(
      JSON.stringify(credential),
    )
  })

  it('clears credentials when either player abandons the game', () => {
    const { result } = renderHook(() => useGameSocket())
    const socket = MockWebSocket.instances[0]

    act(() => {
      socket.open()
      socket.receive(gameSession)
      socket.receive({
        type: 'GAME_ABANDONED',
        payload: { reason: 'OPPONENT_LEFT' },
      })
    })

    expect(result.current.game).toBeNull()
    expect(result.current.error?.code).toBe('OPPONENT_LEFT')
    expect(window.localStorage.getItem(GAME_SESSION_STORAGE_KEY)).toBeNull()
  })

  it.each(['GAME_NOT_FOUND', 'INVALID_PLAYER_TOKEN'])(
    'clears a stale stored game after %s',
    (code) => {
      window.localStorage.setItem(
        GAME_SESSION_STORAGE_KEY,
        JSON.stringify(credential),
      )
      const { result } = renderHook(() => useGameSocket())
      const socket = MockWebSocket.instances[0]

      act(() => {
        socket.open()
        socket.receive({
          type: 'ERROR',
          payload: {
            code,
            message: 'Could not restore the game',
            recoverable: false,
          },
        })
      })

      expect(window.localStorage.getItem(GAME_SESSION_STORAGE_KEY)).toBeNull()
      expect(result.current.game).toBeNull()
      expect(result.current.error?.code).toBe(code)
    },
  )

  it('retries unexpected disconnects with bounded exponential backoff', () => {
    vi.useFakeTimers()
    const { result } = renderHook(() => useGameSocket())

    act(() => {
      MockWebSocket.instances[0].open()
      MockWebSocket.instances[0].disconnect()
    })
    expect(result.current.connectionState).toBe('reconnecting')

    for (const delay of [250, 500, 1_000, 2_000]) {
      act(() => vi.advanceTimersByTime(delay))
      const socket = MockWebSocket.instances.at(-1)!
      act(() => socket.disconnect())
    }

    expect(MockWebSocket.instances).toHaveLength(5)
    expect(result.current.connectionState).toBe('disconnected')
    expect(result.current.error?.code).toBe('CONNECTION_FAILED')
    expect(vi.getTimerCount()).toBe(0)
  })

  it('waits for explicit reconnect when this session was replaced', () => {
    vi.useFakeTimers()
    const { result } = renderHook(() => useGameSocket())
    const originalSocket = MockWebSocket.instances[0]

    act(() => {
      originalSocket.open()
      originalSocket.receive(gameSession)
      originalSocket.disconnect(1000, 'Game resumed on another connection')
    })

    expect(result.current.connectionState).toBe('disconnected')
    expect(result.current.error?.code).toBe('SESSION_REPLACED')
    expect(window.localStorage.getItem(GAME_SESSION_STORAGE_KEY)).toBe(
      JSON.stringify(credential),
    )
    expect(vi.getTimerCount()).toBe(0)

    act(() => vi.advanceTimersByTime(10_000))
    expect(MockWebSocket.instances).toHaveLength(1)

    act(() => result.current.reconnect())
    expect(MockWebSocket.instances).toHaveLength(2)
    const replacementSocket = MockWebSocket.instances[1]
    act(() => replacementSocket.open())
    expect(replacementSocket.send).toHaveBeenCalledWith(
      JSON.stringify({ type: 'RESUME_GAME', payload: credential }),
    )
  })

  it('resumes the current seat after a temporary disconnect', () => {
    vi.useFakeTimers()
    renderHook(() => useGameSocket())

    const originalSocket = MockWebSocket.instances[0]
    act(() => {
      originalSocket.open()
      originalSocket.receive(gameSession)
      originalSocket.disconnect()
      vi.advanceTimersByTime(250)
    })

    const reconnectedSocket = MockWebSocket.instances[1]
    act(() => reconnectedSocket.open())

    expect(reconnectedSocket.send).toHaveBeenCalledWith(
      JSON.stringify({ type: 'RESUME_GAME', payload: credential }),
    )
  })

  it('sends typed commands only while connected', () => {
    const { result } = renderHook(() => useGameSocket())
    const socket = MockWebSocket.instances[0]
    const command = { type: 'DROP_COUNTER', payload: { column: 3 } } as const

    act(() => expect(result.current.sendMessage(command)).toBe(false))
    expect(result.current.error?.code).toBe('CONNECTION_UNAVAILABLE')

    act(() => socket.open())
    act(() => expect(result.current.sendMessage(command)).toBe(true))
    expect(socket.send).toHaveBeenLastCalledWith(JSON.stringify(command))
    expect(result.current.isAwaitingResponse).toBe(true)

    act(() =>
      socket.receive({
        type: 'ERROR',
        payload: {
          code: 'COLUMN_FULL',
          message: 'Column is full',
          recoverable: true,
        },
      }),
    )
    expect(result.current.isAwaitingResponse).toBe(false)
  })

  it('keeps a drop pending through presence snapshots until the board changes', () => {
    const { result } = renderHook(() => useGameSocket())
    const socket = MockWebSocket.instances[0]

    act(() => {
      socket.open()
      socket.receive(gameSession)
    })
    act(() => {
      result.current.sendMessage({
        type: 'DROP_COUNTER',
        payload: { column: 0 },
      })
    })

    expect(result.current.isAwaitingResponse).toBe(true)

    act(() =>
      socket.receive({
        type: 'GAME_STATE',
        payload: { ...game, opponentConnected: false },
      }),
    )
    expect(result.current.game?.opponentConnected).toBe(false)
    expect(result.current.isAwaitingResponse).toBe(true)

    const boardAfterMove = game.board.map((row) => [...row])
    boardAfterMove[5][0] = 'RED'
    act(() =>
      socket.receive({
        type: 'GAME_STATE',
        payload: { ...game, board: boardAfterMove, currentTurn: 'YELLOW' },
      }),
    )
    expect(result.current.game?.board).toEqual(boardAfterMove)
    expect(result.current.isAwaitingResponse).toBe(false)
  })
})
