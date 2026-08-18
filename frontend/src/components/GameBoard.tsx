import { useState } from 'react'
import type { CSSProperties } from 'react'
import type { Cell, GameState } from '../types/protocol'

type GameBoardProps = {
  game: GameState
  disabled: boolean
  onDrop: (column: number) => void
}

function cellName(cell: Cell) {
  return cell === 'EMPTY' ? 'empty' : `${cell.toLowerCase()} counter`
}

type DropAnimation = {
  board: Cell[][]
  counters: Set<string>
  computerRow: number | null
}

function findDropAnimation(
  previousBoard: Cell[][] | null,
  game: GameState,
): DropAnimation {
  const counters = new Set<string>()

  game.board.forEach((row, rowIndex) => {
    row.forEach((cell, columnIndex) => {
      const wasEmpty = previousBoard?.[rowIndex]?.[columnIndex] === 'EMPTY'
      const isOpeningComputerMove =
        previousBoard === null && game.computerColumn === columnIndex

      if (cell !== 'EMPTY' && (wasEmpty || isOpeningComputerMove)) {
        counters.add(`${rowIndex}:${columnIndex}`)
      }
    })
  })

  const computerRow =
    game.computerColumn === null
      ? null
      : game.board.findIndex((_, rowIndex) =>
          counters.has(`${rowIndex}:${game.computerColumn}`),
        )

  return { board: game.board, counters, computerRow }
}

function GameBoard({ game, disabled, onDrop }: GameBoardProps) {
  const [dropAnimation, setDropAnimation] = useState(() =>
    findDropAnimation(null, game),
  )

  if (dropAnimation.board !== game.board) {
    setDropAnimation(findDropAnimation(dropAnimation.board, game))
  }

  return (
    <div className="board-area">
      <div className="column-controls" aria-label="Choose a column">
        {Array.from({ length: 7 }, (_, column) => {
          const isFull = game.board[0]?.[column] !== 'EMPTY'
          return (
            <button
              key={column}
              type="button"
              disabled={disabled || isFull}
              onClick={() => onDrop(column)}
              aria-label={`Drop counter in column ${column + 1}`}
            >
              <span aria-hidden="true">↓</span>
            </button>
          )
        })}
      </div>

      <div className="game-board" role="grid" aria-label="Connect Four board">
        {game.board.map((row, rowIndex) => (
          <div className="board-row" role="row" key={rowIndex}>
            {row.map((cell, columnIndex) => {
              const counter = `${rowIndex}:${columnIndex}`
              const isDropping = dropAnimation.counters.has(counter)
              const isComputerCounter =
                isDropping &&
                columnIndex === game.computerColumn &&
                rowIndex === dropAnimation.computerRow
              const dropStyle = isDropping
                ? ({
                    '--drop-distance': `${-(rowIndex + 1) * 112}%`,
                    '--drop-delay': isComputerCounter ? '240ms' : '0ms',
                  } as CSSProperties)
                : undefined

              return (
                <div
                  className={`board-cell ${cell.toLowerCase()}${isDropping ? ' is-dropping' : ''}`}
                  role="gridcell"
                  aria-label={`Row ${rowIndex + 1}, column ${columnIndex + 1}: ${cellName(cell)}`}
                  style={dropStyle}
                  key={columnIndex}
                >
                  <span aria-hidden="true" />
                </div>
              )
            })}
          </div>
        ))}
      </div>
    </div>
  )
}

export default GameBoard
