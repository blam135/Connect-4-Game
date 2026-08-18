export const GAME_SESSION_STORAGE_KEY = 'connect-four.game-session'
const LEGACY_GAME_ID_STORAGE_KEY = 'connect-four.game-id'

export type StoredGameSession = {
  gameId: string
  playerToken: string
}

export function clearStoredSession() {
  window.localStorage.removeItem(GAME_SESSION_STORAGE_KEY)
  window.localStorage.removeItem(LEGACY_GAME_ID_STORAGE_KEY)
}

export function readStoredSession(): StoredGameSession | null {
  window.localStorage.removeItem(LEGACY_GAME_ID_STORAGE_KEY)
  const rawSession = window.localStorage.getItem(GAME_SESSION_STORAGE_KEY)
  if (rawSession === null) {
    return null
  }

  try {
    const session: unknown = JSON.parse(rawSession)
    if (
      typeof session !== 'object' ||
      session === null ||
      !('gameId' in session) ||
      !('playerToken' in session) ||
      typeof session.gameId !== 'string' ||
      session.gameId.length === 0 ||
      typeof session.playerToken !== 'string' ||
      session.playerToken.length === 0
    ) {
      clearStoredSession()
      return null
    }

    return {
      gameId: session.gameId,
      playerToken: session.playerToken,
    }
  } catch {
    clearStoredSession()
    return null
  }
}

export function storeGameSession(session: StoredGameSession) {
  window.localStorage.setItem(GAME_SESSION_STORAGE_KEY, JSON.stringify(session))
}
