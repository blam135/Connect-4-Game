export type PlayerColor = 'RED' | 'YELLOW'

export type FirstPlayer = 'HUMAN' | 'COMPUTER'

export type Cell = 'EMPTY' | PlayerColor

export type GameStatus =
  | 'IN_PROGRESS'
  | 'HUMAN_WON'
  | 'COMPUTER_WON'
  | 'DRAW'

export type ClientMessage =
  | {
      type: 'START_GAME'
      payload: {
        humanColor: PlayerColor
        firstPlayer: FirstPlayer
      }
    }
  | {
      type: 'RESUME_GAME'
      payload: { gameId: string }
    }
  | {
      type: 'DROP_COUNTER'
      payload: { column: number }
    }
  | {
      type: 'ABANDON_GAME'
      payload: Record<string, never>
    }

export type ServerMessage =
  | {
      type: 'GAME_STATE'
      payload: {
        gameId: string
        board: Cell[][]
        status: GameStatus
        humanColor: PlayerColor
        firstPlayer: FirstPlayer
        computerColumn: number | null
      }
    }
  | {
      type: 'GAME_ABANDONED'
      payload: Record<string, never>
    }
  | {
      type: 'ERROR'
      payload: {
        code: string
        message: string
        recoverable: boolean
      }
    }
