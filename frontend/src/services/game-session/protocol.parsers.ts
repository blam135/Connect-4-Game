import type { GameError, ServerMessage } from './protocol'

export function invalidServerMessage(): GameError {
  return {
    code: 'INVALID_SERVER_MESSAGE',
    message: 'The server returned an invalid message',
    recoverable: false,
  }
}

export function parseServerMessage(data: unknown): ServerMessage | null {
  if (typeof data !== 'string') {
    return null
  }

  try {
    const message: unknown = JSON.parse(data)
    if (
      typeof message !== 'object' ||
      message === null ||
      !('type' in message) ||
      !('payload' in message) ||
      (message.type !== 'GAME_SESSION' &&
        message.type !== 'GAME_STATE' &&
        message.type !== 'GAME_ABANDONED' &&
        message.type !== 'ERROR')
    ) {
      return null
    }

    return message as ServerMessage
  } catch {
    return null
  }
}
