export type FrontChannelFrame = {
  setAttribute: (name: string, value: string) => void
  remove: () => void
  src: string
  style: { display?: string }
}

export type FrontChannelDocument = {
  body: {
    appendChild: (frame: FrontChannelFrame) => unknown
  }
  createElement: (tagName: 'iframe') => FrontChannelFrame
}

export function propagateFrontChannelLogout(
  logoutUris: string[],
  targetDocument: FrontChannelDocument | undefined = typeof document === 'undefined'
    ? undefined
    : document as unknown as FrontChannelDocument,
) {
  if (!targetDocument) {
    return
  }

  const uniqueLogoutUris = [...new Set(logoutUris.map((logoutUri) => logoutUri.trim()).filter(Boolean))]
  for (const logoutUri of uniqueLogoutUris) {
    const frame = targetDocument.createElement('iframe')
    frame.style.display = 'none'
    frame.setAttribute('aria-hidden', 'true')
    frame.src = logoutUri
    targetDocument.body.appendChild(frame)
    globalThis.setTimeout(() => frame.remove(), 2000)
  }
}
