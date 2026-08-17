import type { Cell, GameState } from '../types/protocol'

type GameBoardProps = {
  game: GameState
  disabled: boolean
  onDrop: (column: number) => void
}

function cellName(cell: Cell) {
  return cell === 'EMPTY' ? 'empty' : `${cell.toLowerCase()} counter`
}

function GameBoard({ game, disabled, onDrop }: GameBoardProps) {
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
            {row.map((cell, columnIndex) => (
              <div
                className={`board-cell ${cell.toLowerCase()}`}
                role="gridcell"
                aria-label={`Row ${rowIndex + 1}, column ${columnIndex + 1}: ${cellName(cell)}`}
                key={columnIndex}
              >
                <span aria-hidden="true" />
              </div>
            ))}
          </div>
        ))}
      </div>
    </div>
  )
}

export default GameBoard
