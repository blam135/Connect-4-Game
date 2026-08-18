import { useState } from 'react'
import type { GameSetupDraft } from './gameSetup.model'

export function useGameSetup(initialRoomCode: string) {
  const [draft, setDraft] = useState<GameSetupDraft>({
    mode: initialRoomCode.length > 0 ? 'ONLINE' : 'COMPUTER',
    onlineAction: initialRoomCode.length > 0 ? 'JOIN' : 'CREATE',
    playerColor: 'RED',
    firstPlayer: 'HUMAN',
    roomCode: initialRoomCode,
  })

  function updateDraft(update: Partial<GameSetupDraft>) {
    setDraft((current) => ({ ...current, ...update }))
  }

  return { draft, updateDraft }
}
