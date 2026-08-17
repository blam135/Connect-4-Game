import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { installMockBrowserApis, MockWebSocket } from './test/MockWebSocket'
import type { Cell, ServerMessage } from './types/protocol'

function boardWith(...counters: Array<[number, number, Cell]>) {
  const board: Cell[][] = Array.from({ length: 6 }, () =>
    Array<Cell>(7).fill('EMPTY'),
  )
  for (const [row, column, cell] of counters) {
    board[row][column] = cell
  }
  return board
}

function gameState(
  board: Cell[][],
  status: Extract<ServerMessage, { type: 'GAME_STATE' }>['payload']['status'],
): ServerMessage {
  return {
    type: 'GAME_STATE',
    payload: {
      gameId: '6484817f-89d1-4518-874a-dba30795a481',
      board,
      status,
      humanColor: 'YELLOW',
      firstPlayer: 'COMPUTER',
      computerColumn: 3,
    },
  }
}

describe('App', () => {
  beforeEach(() => installMockBrowserApis())

  afterEach(() => vi.unstubAllGlobals())

  it('plays a complete browser game flow', async () => {
    const user = userEvent.setup()
    render(<App />)

    const socket = MockWebSocket.instances[0]
    const startButton = screen.getByRole('button', { name: 'Start game' })
    expect(startButton).toBeDisabled()
    expect(screen.getByText('Connecting to the game server…')).toBeInTheDocument()

    act(() => socket.open())
    await user.click(screen.getByRole('radio', { name: 'Yellow' }))
    await user.click(screen.getByRole('radio', { name: /Computer/ }))
    await user.click(startButton)

    expect(socket.send).toHaveBeenLastCalledWith(
      JSON.stringify({
        type: 'START_GAME',
        payload: { humanColor: 'YELLOW', firstPlayer: 'COMPUTER' },
      }),
    )
    expect(screen.getByRole('button', { name: 'Starting game…' })).toBeDisabled()

    act(() =>
      socket.receive(gameState(boardWith([5, 3, 'RED']), 'IN_PROGRESS')),
    )

    expect(screen.getByRole('grid', { name: 'Connect Four board' })).toBeInTheDocument()
    expect(screen.getAllByRole('gridcell')).toHaveLength(42)
    expect(screen.getByText('Your turn — drop a yellow counter.')).toBeInTheDocument()

    await user.click(
      screen.getByRole('button', { name: 'Drop counter in column 4' }),
    )
    expect(socket.send).toHaveBeenLastCalledWith(
      JSON.stringify({ type: 'DROP_COUNTER', payload: { column: 3 } }),
    )
    expect(screen.getByText('Computer is thinking…')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Drop counter in column 4' }),
    ).toBeDisabled()

    act(() =>
      socket.receive(
        gameState(
          boardWith(
            [5, 0, 'YELLOW'],
            [5, 1, 'YELLOW'],
            [5, 2, 'YELLOW'],
            [5, 3, 'YELLOW'],
          ),
          'HUMAN_WON',
        ),
      ),
    )

    expect(screen.getByText('You won! Four in a row.')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Drop counter in column 4' }),
    ).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'New game' }))
    expect(socket.send).toHaveBeenLastCalledWith(
      JSON.stringify({ type: 'ABANDON_GAME', payload: {} }),
    )

    act(() => socket.receive({ type: 'GAME_ABANDONED', payload: {} }))
    expect(screen.getByRole('button', { name: 'Start game' })).toBeEnabled()
  })
})
