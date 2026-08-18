import type { FormEvent } from 'react'
import type { StartGameIntent } from '../../services/game-session/gameSession.types'
import type { GameSetupDraft } from './gameSetup.model'
import { setupIntent } from './gameSetup.model'
import { normalizeRoomCode } from './gameSetup.utils'
import './gameSetup.css'

type GameSetupFormProps = {
  value: GameSetupDraft
  disabled: boolean
  isStarting: boolean
  onChange: (update: Partial<GameSetupDraft>) => void
  onSubmit: (intent: StartGameIntent) => void
}

function GameSetupForm({
  value,
  disabled,
  isStarting,
  onChange,
  onSubmit,
}: GameSetupFormProps) {
  const { mode, onlineAction, playerColor, firstPlayer, roomCode } = value
  const isJoin = mode === 'ONLINE' && onlineAction === 'JOIN'
  const submitDisabled = disabled || (isJoin && roomCode.length !== 6)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!submitDisabled) {
      onSubmit(setupIntent(value))
    }
  }

  const buttonLabel = isStarting
    ? isJoin
      ? 'Joining room…'
      : mode === 'ONLINE'
        ? 'Creating room…'
        : 'Starting game…'
    : isJoin
      ? 'Join room'
      : mode === 'ONLINE'
        ? 'Create room'
        : 'Start game'

  return (
    <section className="setup-layout" aria-labelledby="setup-heading">
      <div className="intro-copy">
        <p className="eyebrow">Classic strategy. Your choice of opponent.</p>
        <h1 id="setup-heading">
          Line up four.
          <span>Play your way.</span>
        </h1>
        <p className="intro-text">
          Challenge the minimax computer or share a room with a friend and play
          live from two browsers.
        </p>
        <div className="winning-line" aria-hidden="true">
          <span />
          <span />
          <span />
          <span />
        </div>
      </div>

      <form className="setup-card" onSubmit={submit}>
        <div className="setup-card-heading">
          <span>01</span>
          <div>
            <p className="eyebrow">Game setup</p>
            <h2>Choose how to play</h2>
          </div>
        </div>

        <fieldset disabled={disabled}>
          <legend>Game mode</legend>
          <div className="option-grid">
            {([
              ['COMPUTER', 'Play computer', 'Challenge the AI'],
              ['ONLINE', 'Play online', 'Invite a friend'],
            ] as const).map(([option, title, description]) => (
              <label className="choice-card" key={option}>
                <input
                  type="radio"
                  name="game-mode"
                  value={option}
                  checked={mode === option}
                  onChange={() => onChange({ mode: option })}
                />
                <span className="choice-content text-choice">
                  <strong>{title}</strong>
                  <small>{description}</small>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        {mode === 'ONLINE' && (
          <fieldset disabled={disabled}>
            <legend>Online game</legend>
            <div className="option-grid">
              {([
                ['CREATE', 'Create a room', 'Share your invite code'],
                ['JOIN', 'Join a room', 'Enter a friend’s code'],
              ] as const).map(([option, title, description]) => (
                <label className="choice-card" key={option}>
                  <input
                    type="radio"
                    name="online-action"
                    value={option}
                    checked={onlineAction === option}
                    onChange={() => onChange({ onlineAction: option })}
                  />
                  <span className="choice-content text-choice">
                    <strong>{title}</strong>
                    <small>{description}</small>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>
        )}

        {!isJoin && (
          <fieldset disabled={disabled}>
            <legend>Choose your color</legend>
            <div className="option-grid color-options">
              {(['RED', 'YELLOW'] as const).map((color) => (
                <label className="choice-card" key={color}>
                  <input
                    type="radio"
                    name="player-color"
                    value={color}
                    checked={playerColor === color}
                    onChange={() => onChange({ playerColor: color })}
                  />
                  <span className={`choice-content ${color.toLowerCase()}`}>
                    <i className="counter-preview" aria-hidden="true" />
                    <strong>{color === 'RED' ? 'Red' : 'Yellow'}</strong>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>
        )}

        {mode === 'COMPUTER' && (
          <fieldset disabled={disabled}>
            <legend>Who moves first?</legend>
            <div className="option-grid">
              {([
                ['HUMAN', 'I do', 'Take the opening move'],
                ['COMPUTER', 'Computer', 'Let the AI set the board'],
              ] as const).map(([option, title, description]) => (
                <label className="choice-card" key={option}>
                  <input
                    type="radio"
                    name="first-player"
                    value={option}
                    checked={firstPlayer === option}
                    onChange={() => onChange({ firstPlayer: option })}
                  />
                  <span className="choice-content text-choice">
                    <strong>{title}</strong>
                    <small>{description}</small>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>
        )}

        {isJoin && (
          <div className="room-code-field">
            <label htmlFor="room-code">Room code</label>
            <input
              id="room-code"
              name="room-code"
              value={roomCode}
              maxLength={6}
              autoComplete="off"
              spellCheck={false}
              placeholder="ABC123"
              disabled={disabled}
              onChange={(event) =>
                onChange({ roomCode: normalizeRoomCode(event.target.value) })
              }
            />
            <small>Enter the six-character code your friend shared.</small>
          </div>
        )}

        <button className="primary-button" type="submit" disabled={submitDisabled}>
          {buttonLabel}
          {!isStarting && <span aria-hidden="true">→</span>}
        </button>
      </form>
    </section>
  )
}

export default GameSetupForm
