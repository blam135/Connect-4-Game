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
