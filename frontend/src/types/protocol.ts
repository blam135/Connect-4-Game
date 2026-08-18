export type PlayerColor = 'RED' | 'YELLOW'

export type FirstPlayer = 'HUMAN' | 'COMPUTER'

export type GameMode = 'COMPUTER' | 'ONLINE'

export type Cell = 'EMPTY' | PlayerColor

export type GameStatus =
  | 'WAITING_FOR_OPPONENT'
  | 'IN_PROGRESS'
  | 'RED_WON'
  | 'YELLOW_WON'
  | 'DRAW'

export type GameState = {
  gameId: string
  mode: GameMode
  board: Cell[][]
  status: GameStatus
  yourColor: PlayerColor
  startingColor: PlayerColor
  currentTurn: PlayerColor | null
  roomCode: string | null
  opponentConnected: boolean
  computerColumn: number | null
}

export type GameError = {
  code: string
  message: string
  recoverable: boolean
}

export type ClientMessage =
  | {
      type: 'START_GAME'
      payload: {
        humanColor: PlayerColor
        firstPlayer: FirstPlayer
      }
    }
  | {
      type: 'CREATE_ONLINE_GAME'
      payload: { hostColor: PlayerColor }
    }
  | {
      type: 'JOIN_ONLINE_GAME'
      payload: { roomCode: string }
    }
  | {
      type: 'RESUME_GAME'
      payload: { gameId: string; playerToken: string }
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
      type: 'GAME_SESSION'
      payload: {
        playerToken: string
        game: GameState
      }
    }
  | {
      type: 'GAME_STATE'
      payload: GameState
    }
  | {
      type: 'GAME_ABANDONED'
      payload: { reason: 'YOU_LEFT' | 'OPPONENT_LEFT' }
    }
  | {
      type: 'ERROR'
      payload: GameError
    }
