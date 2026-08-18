import { useEffect, useMemo, useRef, useState } from 'react'
import GameBoard from './components/GameBoard'
import GameSetup from './components/GameSetup'
import type { OnlineAction, SetupMode } from './components/GameSetup'
import { useGameSocket } from './hooks/useGameSocket'
import type { FirstPlayer, GameState, PlayerColor } from './types/protocol'

function normalizeRoomCode(value: string) {
  return value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6)
}

function roomCodeFromUrl() {
  return normalizeRoomCode(new URLSearchParams(window.location.search).get('room') ?? '')
}

function gameStatusMessage(game: GameState, isAwaitingResponse: boolean) {
  if (game.status === 'WAITING_FOR_OPPONENT') {
    return 'Waiting for another player to join.'
  }

  if (game.status === 'DRAW') {
    return 'Draw — the board is full.'
  }

  if (game.status === 'RED_WON' || game.status === 'YELLOW_WON') {
    const winningColor = game.status === 'RED_WON' ? 'RED' : 'YELLOW'
    return winningColor === game.yourColor
      ? 'You won! Four in a row.'
      : game.mode === 'ONLINE'
        ? 'Your opponent won this round.'
        : 'The computer won this round.'
  }

  if (!game.opponentConnected) {
    return 'Your opponent is offline. Play will resume when they reconnect.'
  }

  if (game.mode === 'COMPUTER') {
    return isAwaitingResponse
      ? 'Computer is thinking…'
      : `Your turn — drop a ${game.yourColor.toLowerCase()} counter.`
  }

  if (isAwaitingResponse) {
    return 'Sending your move…'
  }

  return game.currentTurn === game.yourColor
    ? `Your turn — drop a ${game.yourColor.toLowerCase()} counter.`
    : 'Your opponent’s turn.'
}

function statusLabel(game: GameState) {
  if (game.status === 'WAITING_FOR_OPPONENT') {
    return 'Waiting room'
  }
  if (game.status === 'IN_PROGRESS' && !game.opponentConnected) {
    return 'Game paused'
  }
  return game.status === 'IN_PROGRESS' ? 'Game in progress' : 'Game over'
}

function inviteUrl(roomCode: string) {
  const url = new URL(window.location.href)
  url.search = ''
  url.hash = ''
  url.searchParams.set('room', roomCode)
  return url.toString()
}

function App() {
  const {
    connectionState,
    game,
    error,
    isAwaitingResponse,
    sendMessage,
    reconnect,
    clearError,
  } = useGameSocket()
  const initialRoomCode = useMemo(() => roomCodeFromUrl(), [])
  const [mode, setMode] = useState<SetupMode>(
    initialRoomCode.length > 0 ? 'ONLINE' : 'COMPUTER',
  )
  const [onlineAction, setOnlineAction] = useState<OnlineAction>(
    initialRoomCode.length > 0 ? 'JOIN' : 'CREATE',
  )
  const [playerColor, setPlayerColor] = useState<PlayerColor>('RED')
  const [firstPlayer, setFirstPlayer] = useState<FirstPlayer>('HUMAN')
  const [roomCode, setRoomCode] = useState(initialRoomCode)
  const joinRequestedRef = useRef(false)
  const [copyStatus, setCopyStatus] = useState<'idle' | 'copied' | 'failed'>('idle')

  const isConnected = connectionState === 'connected'
  const canPlay =
    isConnected &&
    !isAwaitingResponse &&
    game?.status === 'IN_PROGRESS' &&
    game.opponentConnected &&
    game.currentTurn === game.yourColor

  useEffect(() => {
    if (!joinRequestedRef.current || game?.mode !== 'ONLINE') {
      return
    }

    const url = new URL(window.location.href)
    url.searchParams.delete('room')
    window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`)
    joinRequestedRef.current = false
  }, [game])

  function startGame() {
    setCopyStatus('idle')
    if (mode === 'COMPUTER') {
      sendMessage({
        type: 'START_GAME',
        payload: { humanColor: playerColor, firstPlayer },
      })
      return
    }

    if (onlineAction === 'CREATE') {
      sendMessage({
        type: 'CREATE_ONLINE_GAME',
        payload: { hostColor: playerColor },
      })
      return
    }

    if (
      sendMessage({
        type: 'JOIN_ONLINE_GAME',
        payload: { roomCode },
      })
    ) {
      joinRequestedRef.current = true
    }
  }

  function dropCounter(column: number) {
    if (canPlay) {
      sendMessage({ type: 'DROP_COUNTER', payload: { column } })
    }
  }

  function leaveGame() {
    if (isConnected && !isAwaitingResponse) {
      sendMessage({ type: 'ABANDON_GAME', payload: {} })
    }
  }

  async function copyInvite() {
    if (game?.roomCode === null || game?.roomCode === undefined) {
      return
    }

    try {
      if (navigator.clipboard === undefined) {
        throw new Error('Clipboard unavailable')
      }
      await navigator.clipboard.writeText(inviteUrl(game.roomCode))
      setCopyStatus('copied')
    } catch {
      setCopyStatus('failed')
    }
  }

  return (
    <main className="app-shell">
      <div className="app-frame">
        <header className="site-header">
          <a className="brand" href="/" aria-label="Connect Four home">
            <span className="brand-mark" aria-hidden="true">
              <span />
              <span />
              <span />
              <span />
            </span>
            <span>Connect Four</span>
          </a>
          <span className={`connection-chip ${connectionState}`}>
            <span aria-hidden="true" />
            {connectionState === 'connected' ? 'Online' : connectionState}
          </span>
        </header>

        {connectionState !== 'connected' && (
          <section className="connection-banner" aria-live="polite">
            <div>
              <strong>
                {connectionState === 'disconnected'
                  ? 'Connection lost'
                  : connectionState === 'reconnecting'
                    ? 'Reconnecting to your game…'
                    : 'Connecting to the game server…'}
              </strong>
              <p>
                {connectionState === 'disconnected'
                  ? 'Your saved game is still available. Try connecting again.'
                  : 'Game controls will be ready in a moment.'}
              </p>
            </div>
            {connectionState === 'disconnected' && (
              <button className="secondary-button" type="button" onClick={reconnect}>
                Reconnect
              </button>
            )}
          </section>
        )}

        {error !== null && (
          <section className="error-banner" role="alert">
            <div>
              <strong>Something went wrong</strong>
              <p>{error.message}</p>
            </div>
            <button type="button" onClick={clearError} aria-label="Dismiss error">
              ×
            </button>
          </section>
        )}

        {game === null ? (
          <GameSetup
            mode={mode}
            onlineAction={onlineAction}
            playerColor={playerColor}
            firstPlayer={firstPlayer}
            roomCode={roomCode}
            disabled={!isConnected || isAwaitingResponse}
            isStarting={isAwaitingResponse}
            onModeChange={setMode}
            onOnlineActionChange={setOnlineAction}
            onPlayerColorChange={setPlayerColor}
            onFirstPlayerChange={setFirstPlayer}
            onRoomCodeChange={(value) => setRoomCode(normalizeRoomCode(value))}
            onStart={startGame}
          />
        ) : (
          <section className="game-view" aria-labelledby="game-heading">
            <div className="game-heading-row">
              <div>
                <p className="eyebrow">
                  {game.mode === 'ONLINE' ? 'Online two-player' : 'Human vs computer'}
                </p>
                <h1 id="game-heading">Your game</h1>
              </div>
              <button
                className="secondary-button"
                type="button"
                disabled={!isConnected || isAwaitingResponse}
                onClick={leaveGame}
              >
                {game.mode === 'ONLINE' ? 'Leave game' : 'New game'}
              </button>
            </div>

            <div className="player-summary" aria-label="Game configuration">
              <span>
                <i className={`mini-counter ${game.yourColor.toLowerCase()}`} />
                You are {game.yourColor.toLowerCase()}
              </span>
              <span>
                {game.mode === 'ONLINE'
                  ? 'Red moves first'
                  : game.startingColor === game.yourColor
                    ? 'You moved first'
                    : 'Computer moved first'}
              </span>
              {game.roomCode !== null && <span>Room {game.roomCode}</span>}
            </div>

            {game.mode === 'ONLINE' &&
              game.status === 'WAITING_FOR_OPPONENT' &&
              game.roomCode !== null && (
                <section className="waiting-room" aria-labelledby="waiting-room-heading">
                  <div>
                    <p className="eyebrow">Invite a friend</p>
                    <h2 id="waiting-room-heading">Room code</h2>
                    <strong className="room-code" aria-label={`Room code ${game.roomCode}`}>
                      {game.roomCode}
                    </strong>
                    <p>Share this code or copy the invite link below.</p>
                  </div>
                  <button className="secondary-button" type="button" onClick={copyInvite}>
                    Copy invite link
                  </button>
                  {copyStatus === 'copied' && (
                    <p className="copy-feedback success" role="status">Invite link copied.</p>
                  )}
                  {copyStatus === 'failed' && (
                    <p className="copy-feedback failure" role="alert">
                      Could not copy the link. Share the room code shown above instead.
                    </p>
                  )}
                </section>
              )}

            <GameBoard game={game} disabled={!canPlay} onDrop={dropCounter} />

            <section
              className={`game-status ${game.status.toLowerCase()}`}
              aria-live="polite"
              aria-busy={isAwaitingResponse}
            >
              {isAwaitingResponse && <span className="spinner" aria-hidden="true" />}
              <div>
                <p className="status-label">{statusLabel(game)}</p>
                <p>{gameStatusMessage(game, isAwaitingResponse)}</p>
              </div>
            </section>
          </section>
        )}
      </div>
    </main>
  )
}

export default App
