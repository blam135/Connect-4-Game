import type { FormEvent } from 'react'
import type { FirstPlayer, PlayerColor } from '../types/protocol'

type GameSetupProps = {
  humanColor: PlayerColor
  firstPlayer: FirstPlayer
  disabled: boolean
  isStarting: boolean
  onHumanColorChange: (color: PlayerColor) => void
  onFirstPlayerChange: (firstPlayer: FirstPlayer) => void
  onStart: () => void
}

function GameSetup({
  humanColor,
  firstPlayer,
  disabled,
  isStarting,
  onHumanColorChange,
  onFirstPlayerChange,
  onStart,
}: GameSetupProps) {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!disabled) {
      onStart()
    }
  }

  return (
    <section className="setup-layout" aria-labelledby="setup-heading">
      <div className="intro-copy">
        <p className="eyebrow">Classic strategy. Clever opponent.</p>
        <h1 id="setup-heading">
          Line up four.
          <span>Outthink the machine.</span>
        </h1>
        <p className="intro-text">
          Choose your side, decide who opens, and take on a depth-four minimax opponent.
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
            <h2>Make your move</h2>
          </div>
        </div>

        <fieldset disabled={disabled}>
          <legend>Choose your color</legend>
          <div className="option-grid color-options">
            {(['RED', 'YELLOW'] as const).map((color) => (
              <label className="choice-card" key={color}>
                <input
                  type="radio"
                  name="human-color"
                  value={color}
                  checked={humanColor === color}
                  onChange={() => onHumanColorChange(color)}
                />
                <span className={`choice-content ${color.toLowerCase()}`}>
                  <i className="counter-preview" aria-hidden="true" />
                  <strong>{color === 'RED' ? 'Red' : 'Yellow'}</strong>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset disabled={disabled}>
          <legend>Who moves first?</legend>
          <div className="option-grid">
            {([
              ['HUMAN', 'I do', 'Take the opening move'],
              ['COMPUTER', 'Computer', 'Let the AI set the board'],
            ] as const).map(([value, title, description]) => (
              <label className="choice-card" key={value}>
                <input
                  type="radio"
                  name="first-player"
                  value={value}
                  checked={firstPlayer === value}
                  onChange={() => onFirstPlayerChange(value)}
                />
                <span className="choice-content text-choice">
                  <strong>{title}</strong>
                  <small>{description}</small>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <button className="primary-button" type="submit" disabled={disabled}>
          {isStarting ? 'Starting game…' : 'Start game'}
          {!isStarting && <span aria-hidden="true">→</span>}
        </button>
      </form>
    </section>
  )
}

export default GameSetup
