export type CopyStatus = 'idle' | 'copied' | 'failed'

type WaitingRoomProps = {
  roomCode: string
  copyStatus: CopyStatus
  onCopyInvite: () => void
}

export function WaitingRoom({
  roomCode,
  copyStatus,
  onCopyInvite,
}: WaitingRoomProps) {
  return (
    <section className="waiting-room" aria-labelledby="waiting-room-heading">
      <div>
        <p className="eyebrow">Invite a friend</p>
        <h2 id="waiting-room-heading">Room code</h2>
        <strong className="room-code" aria-label={`Room code ${roomCode}`}>
          {roomCode}
        </strong>
        <p>Share this code or copy the invite link below.</p>
      </div>
      <button className="secondary-button" type="button" onClick={onCopyInvite}>
        Copy invite link
      </button>
      {copyStatus === 'copied' && (
        <p className="copy-feedback success" role="status">
          Invite link copied.
        </p>
      )}
      {copyStatus === 'failed' && (
        <p className="copy-feedback failure" role="alert">
          Could not copy the link. Share the room code shown above instead.
        </p>
      )}
    </section>
  )
}
