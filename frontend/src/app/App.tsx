import { useEffect, useMemo, useRef } from 'react'
import GameSetupForm from '../features/game-setup/GameSetupForm'
import {
  consumeRoomCodeFromUrl,
  roomCodeFromUrl,
} from '../features/game-setup/gameSetup.utils'
import { useGameSetup } from '../features/game-setup/useGameSetup'
import GameScreen from '../features/gameplay/GameScreen'
import type { StartGameIntent } from '../services/game-session/gameSession.types'
import { useGameSession } from '../services/game-session/useGameSession'
import { AppShell } from './AppShell'

function App() {
  const session = useGameSession()
  const initialRoomCode = useMemo(() => roomCodeFromUrl(), [])
  const setup = useGameSetup(initialRoomCode)
  const joinRequestedRef = useRef(false)
  const isConnected = session.connectionState === 'connected'

  useEffect(() => {
    if (!joinRequestedRef.current || session.game?.mode !== 'ONLINE') {
      return
    }

    consumeRoomCodeFromUrl()
    joinRequestedRef.current = false
  }, [session.game])

  function startGame(intent: StartGameIntent) {
    if (session.actions.startGame(intent) && intent.kind === 'join-online') {
      joinRequestedRef.current = true
    }
  }

  return (
    <AppShell
      connectionState={session.connectionState}
      error={session.error}
      onReconnect={session.reconnect}
      onClearError={session.clearError}
    >
      {session.game === null ? (
        <GameSetupForm
          value={setup.draft}
          disabled={!isConnected || session.isAwaitingResponse}
          isStarting={session.isAwaitingResponse}
          onChange={setup.updateDraft}
          onSubmit={startGame}
        />
      ) : (
        <GameScreen
          game={session.game}
          connectionState={session.connectionState}
          isAwaitingResponse={session.isAwaitingResponse}
          onDropCounter={session.actions.dropCounter}
          onLeaveGame={session.actions.leaveGame}
        />
      )}
    </AppShell>
  )
}

export default App
