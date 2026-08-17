import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { installMockBrowserApis, MockWebSocket } from '../test/MockWebSocket'
import type { ServerMessage } from '../types/protocol'
import { GAME_ID_STORAGE_KEY, useGameSocket } from './useGameSocket'

const gameState: Extract<ServerMessage, { type: 'GAME_STATE' }> = {
  type: 'GAME_STATE',
  payload: {
    gameId: '6484817f-89d1-4518-874a-dba30795a481',
    board: Array.from({ length: 6 }, () => Array(7).fill('EMPTY')),
    status: 'IN_PROGRESS',
    humanColor: 'RED',
    firstPlayer: 'HUMAN',
    computerColumn: null,
  },
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

  it('resumes a stored game whenever a connection opens', () => {
    window.localStorage.setItem(GAME_ID_STORAGE_KEY, gameState.payload.gameId)
    renderHook(() => useGameSocket())

    const socket = MockWebSocket.instances[0]
    act(() => socket.open())

    expect(socket.send).toHaveBeenCalledWith(
      JSON.stringify({
        type: 'RESUME_GAME',
        payload: { gameId: gameState.payload.gameId },
      }),
    )
  })

  it('stores snapshots and clears a game abandoned by the server', () => {
    const { result } = renderHook(() => useGameSocket())
    const socket = MockWebSocket.instances[0]

    act(() => {
      socket.open()
      socket.receive(gameState)
    })

    expect(result.current.game).toEqual(gameState.payload)
    expect(window.localStorage.getItem(GAME_ID_STORAGE_KEY)).toBe(
      gameState.payload.gameId,
    )

    act(() =>
      socket.receive({ type: 'GAME_ABANDONED', payload: {} }),
    )

    expect(result.current.game).toBeNull()
    expect(window.localStorage.getItem(GAME_ID_STORAGE_KEY)).toBeNull()
  })

  it('clears a stale stored game when the backend cannot resume it', () => {
    window.localStorage.setItem(GAME_ID_STORAGE_KEY, gameState.payload.gameId)
    const { result } = renderHook(() => useGameSocket())
    const socket = MockWebSocket.instances[0]

    act(() => {
      socket.open()
      socket.receive({
        type: 'ERROR',
        payload: {
          code: 'GAME_NOT_FOUND',
          message: 'Game does not exist',
          recoverable: false,
        },
      })
    })

    expect(window.localStorage.getItem(GAME_ID_STORAGE_KEY)).toBeNull()
    expect(result.current.game).toBeNull()
    expect(result.current.error?.code).toBe('GAME_NOT_FOUND')
  })

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

  it('resumes the current game after a temporary disconnect', () => {
    vi.useFakeTimers()
    renderHook(() => useGameSocket())

    const originalSocket = MockWebSocket.instances[0]
    act(() => {
      originalSocket.open()
      originalSocket.receive(gameState)
      originalSocket.disconnect()
      vi.advanceTimersByTime(250)
    })

    const reconnectedSocket = MockWebSocket.instances[1]
    act(() => reconnectedSocket.open())

    expect(reconnectedSocket.send).toHaveBeenCalledWith(
      JSON.stringify({
        type: 'RESUME_GAME',
        payload: { gameId: gameState.payload.gameId },
      }),
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
})
