import type { FirstPlayer, PlayerColor } from '../../domain/game'
import type { StartGameIntent } from '../../services/game-session/gameSession.types'

export type SetupMode = 'COMPUTER' | 'ONLINE'
export type OnlineAction = 'CREATE' | 'JOIN'

export type GameSetupDraft = {
  mode: SetupMode
  onlineAction: OnlineAction
  playerColor: PlayerColor
  firstPlayer: FirstPlayer
  roomCode: string
}

export function setupIntent(draft: GameSetupDraft): StartGameIntent {
  if (draft.mode === 'COMPUTER') {
    return {
      kind: 'computer',
      playerColor: draft.playerColor,
      firstPlayer: draft.firstPlayer,
    }
  }

  return draft.onlineAction === 'CREATE'
    ? { kind: 'create-online', playerColor: draft.playerColor }
    : { kind: 'join-online', roomCode: draft.roomCode }
}
