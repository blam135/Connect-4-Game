import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Cell, GameState } from '../../domain/game'
import GameBoard from './GameBoard'

function boardWith(...counters: Array<[number, number, Cell]>) {
  const board: Cell[][] = Array.from({ length: 6 }, () =>
    Array<Cell>(7).fill('EMPTY'),
  )
  for (const [row, column, cell] of counters) {
    board[row][column] = cell
  }
  return board
}

function game(board: Cell[][], computerColumn: number | null): GameState {
  return {
    gameId: '6484817f-89d1-4518-874a-dba30795a481',
    mode: 'COMPUTER',
    board,
    status: 'IN_PROGRESS',
    yourColor: 'RED',
    startingColor: 'RED',
    currentTurn: 'RED',
    roomCode: null,
    opponentConnected: true,
    computerColumn,
  }
}

describe('GameBoard counter animation', () => {
  it('drops new human and computer counters, with the computer staggered', () => {
    const initialGame = game(boardWith(), null)
    const { rerender } = render(
      <GameBoard
        board={initialGame.board}
        computerColumn={initialGame.computerColumn}
        disabled={false}
        onDrop={vi.fn()}
      />,
    )

    const updatedGame = game(
      boardWith([5, 1, 'RED'], [5, 4, 'YELLOW']),
      4,
    )
    rerender(
      <GameBoard
        board={updatedGame.board}
        computerColumn={updatedGame.computerColumn}
        disabled={false}
        onDrop={vi.fn()}
      />,
    )

    const humanCounter = screen.getByRole('gridcell', {
      name: 'Row 6, column 2: red counter',
    })
    const computerCounter = screen.getByRole('gridcell', {
      name: 'Row 6, column 5: yellow counter',
    })

    expect(humanCounter).toHaveClass('is-dropping')
    expect(humanCounter).toHaveStyle('--drop-delay: 0ms')
    expect(computerCounter).toHaveClass('is-dropping')
    expect(computerCounter).toHaveStyle('--drop-delay: 240ms')

    const nextGame = game(
      boardWith([4, 1, 'RED'], [5, 1, 'RED'], [5, 4, 'YELLOW']),
      null,
    )
    rerender(
      <GameBoard
        board={nextGame.board}
        computerColumn={nextGame.computerColumn}
        disabled={false}
        onDrop={vi.fn()}
      />,
    )
    expect(humanCounter).not.toHaveClass('is-dropping')
    expect(computerCounter).not.toHaveClass('is-dropping')
    expect(
      screen.getByRole('gridcell', { name: 'Row 5, column 2: red counter' }),
    ).toHaveClass('is-dropping')
  })

  it('does not animate counters when a saved game is first restored', () => {
    const restoredGame = game(boardWith([5, 2, 'RED']), null)
    render(
      <GameBoard
        board={restoredGame.board}
        computerColumn={restoredGame.computerColumn}
        disabled={false}
        onDrop={vi.fn()}
      />,
    )

    expect(
      screen.getByRole('gridcell', { name: 'Row 6, column 3: red counter' }),
    ).not.toHaveClass('is-dropping')
  })
})
