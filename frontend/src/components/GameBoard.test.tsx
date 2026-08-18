import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Cell, GameState } from '../types/protocol'
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
    board,
    status: 'IN_PROGRESS',
    humanColor: 'RED',
    firstPlayer: 'HUMAN',
    computerColumn,
  }
}

describe('GameBoard counter animation', () => {
  it('drops new human and computer counters, with the computer staggered', () => {
    const { rerender } = render(
      <GameBoard game={game(boardWith(), null)} disabled={false} onDrop={vi.fn()} />,
    )

    const updatedGame = game(
      boardWith([5, 1, 'RED'], [5, 4, 'YELLOW']),
      4,
    )
    rerender(<GameBoard game={updatedGame} disabled={false} onDrop={vi.fn()} />)

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

    rerender(
      <GameBoard
        game={game(
          boardWith([4, 1, 'RED'], [5, 1, 'RED'], [5, 4, 'YELLOW']),
          null,
        )}
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
    render(
      <GameBoard
        game={game(boardWith([5, 2, 'RED']), null)}
        disabled={false}
        onDrop={vi.fn()}
      />,
    )

    expect(
      screen.getByRole('gridcell', { name: 'Row 6, column 3: red counter' }),
    ).not.toHaveClass('is-dropping')
  })
})
