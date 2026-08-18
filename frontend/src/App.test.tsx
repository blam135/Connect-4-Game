import { act, cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { installMockBrowserApis, MockWebSocket } from './test/MockWebSocket'
import type { Cell, GameState, ServerMessage } from './types/protocol'

function boardWith(...counters: Array<[number, number, Cell]>) {
  const board: Cell[][] = Array.from({ length: 6 }, () =>
    Array<Cell>(7).fill('EMPTY'),
  )
  for (const [row, column, cell] of counters) {
    board[row][column] = cell
  }
  return board
}

function computerGame(overrides: Partial<GameState> = {}): GameState {
  return {
    gameId: '6484817f-89d1-4518-874a-dba30795a481',
    mode: 'COMPUTER',
    board: boardWith([5, 3, 'RED']),
    status: 'IN_PROGRESS',
    yourColor: 'YELLOW',
    startingColor: 'RED',
    currentTurn: 'YELLOW',
    roomCode: null,
    opponentConnected: true,
    computerColumn: 3,
    ...overrides,
  }
}

function onlineGame(overrides: Partial<GameState> = {}): GameState {
  return {
    gameId: 'e11a745a-53a0-44b3-98f8-8a80030dc468',
    mode: 'ONLINE',
    board: boardWith(),
    status: 'IN_PROGRESS',
    yourColor: 'RED',
    startingColor: 'RED',
    currentTurn: 'RED',
    roomCode: 'ABC123',
    opponentConnected: true,
    computerColumn: null,
    ...overrides,
  }
}

function session(game: GameState): ServerMessage {
  return {
    type: 'GAME_SESSION',
    payload: { playerToken: 'private-player-token', game },
  }
}

describe('App', () => {
  beforeEach(() => {
    installMockBrowserApis()
    window.history.replaceState({}, '', '/')
    Object.defineProperty(window.navigator, 'clipboard', {
      configurable: true,
      value: undefined,
    })
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('preserves a complete game against the computer', async () => {
    const user = userEvent.setup()
    render(<App />)

    const socket = MockWebSocket.instances[0]
    const startButton = screen.getByRole('button', { name: 'Start game' })
    expect(startButton).toBeDisabled()
    expect(screen.getByText('Connecting to the game server…')).toBeInTheDocument()

    act(() => socket.open())
    await user.click(screen.getByRole('radio', { name: /Yellow/ }))
    await user.click(screen.getByRole('radio', { name: /Computer/ }))
    await user.click(startButton)

    expect(socket.send).toHaveBeenLastCalledWith(
      JSON.stringify({
        type: 'START_GAME',
        payload: { humanColor: 'YELLOW', firstPlayer: 'COMPUTER' },
      }),
    )
    expect(screen.getByRole('button', { name: 'Starting game…' })).toBeDisabled()

    act(() => socket.receive(session(computerGame())))

    expect(screen.getByRole('grid', { name: 'Connect Four board' })).toBeInTheDocument()
    expect(screen.getAllByRole('gridcell')).toHaveLength(42)
    expect(screen.getByText('Your turn — drop a yellow counter.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Drop counter in column 4' }))
    expect(socket.send).toHaveBeenLastCalledWith(
      JSON.stringify({ type: 'DROP_COUNTER', payload: { column: 3 } }),
    )
    expect(screen.getByText('Computer is thinking…')).toBeInTheDocument()

    act(() =>
      socket.receive({
        type: 'GAME_STATE',
        payload: computerGame({
          board: boardWith(
            [5, 0, 'YELLOW'],
            [5, 1, 'YELLOW'],
            [5, 2, 'YELLOW'],
            [5, 3, 'YELLOW'],
          ),
          status: 'YELLOW_WON',
          currentTurn: null,
          computerColumn: null,
        }),
      }),
    )

    expect(screen.getByText('You won! Four in a row.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'New game' }))
    expect(socket.send).toHaveBeenLastCalledWith(
      JSON.stringify({ type: 'ABANDON_GAME', payload: {} }),
    )

    act(() =>
      socket.receive({
        type: 'GAME_ABANDONED',
        payload: { reason: 'YOU_LEFT' },
      }),
    )
    expect(screen.getByRole('button', { name: 'Start game' })).toBeEnabled()
  })

  it('creates an online waiting room and copies its invite link', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    Object.defineProperty(window.navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    })
    render(<App />)
    const socket = MockWebSocket.instances[0]
    act(() => socket.open())

    await user.click(screen.getByRole('radio', { name: /Play online/ }))
    await user.click(screen.getByRole('radio', { name: /Yellow/ }))
    await user.click(screen.getByRole('button', { name: 'Create room' }))

    expect(socket.send).toHaveBeenLastCalledWith(
      JSON.stringify({
        type: 'CREATE_ONLINE_GAME',
        payload: { hostColor: 'YELLOW' },
      }),
    )

    act(() =>
      socket.receive(
        session(
          onlineGame({
            status: 'WAITING_FOR_OPPONENT',
            yourColor: 'YELLOW',
            currentTurn: null,
            opponentConnected: false,
          }),
        ),
      ),
    )

    expect(screen.getByText('Waiting for another player to join.')).toBeInTheDocument()
    expect(screen.getByLabelText('Room code ABC123')).toHaveTextContent('ABC123')
    await user.click(screen.getByRole('button', { name: 'Copy invite link' }))
    expect(writeText).toHaveBeenCalledWith('http://localhost:3000/?room=ABC123')
    expect(screen.getByText('Invite link copied.')).toBeInTheDocument()
  })

  it('prefills and consumes an invite query after joining successfully', async () => {
    window.history.replaceState({}, '', '/?room=abc123')
    const user = userEvent.setup()
    render(<App />)
    const socket = MockWebSocket.instances[0]

    expect(screen.getByRole('radio', { name: /Play online/ })).toBeChecked()
    expect(screen.getByRole('radio', { name: /Join a room/ })).toBeChecked()
    expect(screen.getByRole('textbox', { name: 'Room code' })).toHaveValue('ABC123')

    act(() => socket.open())
    await user.click(screen.getByRole('button', { name: 'Join room' }))
    expect(socket.send).toHaveBeenLastCalledWith(
      JSON.stringify({
        type: 'JOIN_ONLINE_GAME',
        payload: { roomCode: 'ABC123' },
      }),
    )
    expect(window.location.search).toBe('?room=abc123')

    act(() => socket.receive(session(onlineGame({ yourColor: 'YELLOW' }))))

    expect(window.location.search).toBe('')
    expect(screen.getByText('Your opponent’s turn.')).toBeInTheDocument()
  })

  it('handles opponent updates, offline pauses, and personalized outcomes', async () => {
    render(<App />)
    const socket = MockWebSocket.instances[0]
    act(() => {
      socket.open()
      socket.receive(session(onlineGame({ currentTurn: 'YELLOW' })))
    })

    expect(screen.getByText('Your opponent’s turn.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Drop counter in column 1' })).toBeDisabled()

    act(() =>
      socket.receive({
        type: 'GAME_STATE',
        payload: onlineGame({
          board: boardWith([5, 2, 'YELLOW']),
          currentTurn: 'RED',
        }),
      }),
    )
    expect(screen.getByText('Your turn — drop a red counter.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Drop counter in column 1' })).toBeEnabled()

    act(() =>
      socket.receive({
        type: 'GAME_STATE',
        payload: onlineGame({ opponentConnected: false }),
      }),
    )
    expect(
      screen.getByText('Your opponent is offline. Play will resume when they reconnect.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Drop counter in column 1' })).toBeDisabled()

    act(() =>
      socket.receive({
        type: 'GAME_STATE',
        payload: onlineGame({ status: 'YELLOW_WON', currentTurn: null }),
      }),
    )
    expect(screen.getByText('Your opponent won this round.')).toBeInTheDocument()

    act(() =>
      socket.receive({
        type: 'GAME_STATE',
        payload: onlineGame({ status: 'DRAW', currentTurn: null }),
      }),
    )
    expect(screen.getByText('Draw — the board is full.')).toBeInTheDocument()
  })

  it('keeps the room code visible when clipboard access fails', async () => {
    const writeText = vi.fn().mockRejectedValue(new Error('denied'))
    const user = userEvent.setup()
    Object.defineProperty(window.navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    })
    render(<App />)
    const socket = MockWebSocket.instances[0]
    act(() => {
      socket.open()
      socket.receive(
        session(
          onlineGame({
            status: 'WAITING_FOR_OPPONENT',
            currentTurn: null,
            opponentConnected: false,
          }),
        ),
      )
    })

    await user.click(screen.getByRole('button', { name: 'Copy invite link' }))

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Could not copy the link. Share the room code shown above instead.',
    )
    expect(screen.getByLabelText('Room code ABC123')).toHaveTextContent('ABC123')
  })
})
