export function normalizeRoomCode(value: string) {
  return value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6)
}

export function roomCodeFromUrl() {
  return normalizeRoomCode(
    new URLSearchParams(window.location.search).get('room') ?? '',
  )
}

export function consumeRoomCodeFromUrl() {
  const url = new URL(window.location.href)
  url.searchParams.delete('room')
  window.history.replaceState(
    window.history.state,
    '',
    `${url.pathname}${url.search}${url.hash}`,
  )
}
