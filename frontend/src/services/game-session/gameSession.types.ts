import type { FirstPlayer, PlayerColor } from '../../domain/game'

export type StartGameIntent =
  | {
      kind: 'computer'
      playerColor: PlayerColor
      firstPlayer: FirstPlayer
    }
  | { kind: 'create-online'; playerColor: PlayerColor }
  | { kind: 'join-online'; roomCode: string }
