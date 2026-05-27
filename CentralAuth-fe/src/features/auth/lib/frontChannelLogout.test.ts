import { describe, expect, it, vi } from 'vitest'
import type { FrontChannelDocument } from './frontChannelLogout'
import { propagateFrontChannelLogout } from './frontChannelLogout'

describe('front-channel logout propagation', () => {
  it('loads each unique logout URI in a hidden iframe and removes it later', () => {
    vi.useFakeTimers()
    type TestFrame = {
      attributes: Record<string, string>
      removed: boolean
      setAttribute: (name: string, value: string) => void
      remove: () => void
      src: string
      style: Record<string, string>
    }
    const appendedFrames: TestFrame[] = []
    const targetDocument: FrontChannelDocument = {
      body: {
        appendChild: (frame) => appendedFrames.push(frame as TestFrame),
      },
      createElement: () => {
        const frame: TestFrame = {
          attributes: {},
          removed: false,
          setAttribute(name: string, value: string) {
            frame.attributes[name] = value
          },
          remove() {
            frame.removed = true
          },
          src: '',
          style: {},
        }
        return frame
      },
    }

    propagateFrontChannelLogout(
      ['https://projects.example.com/logout', 'https://projects.example.com/logout', ''],
      targetDocument,
    )

    expect(appendedFrames).toHaveLength(1)
    expect(appendedFrames[0].src).toBe('https://projects.example.com/logout')
    expect(appendedFrames[0].style.display).toBe('none')
    expect(appendedFrames[0].attributes['aria-hidden']).toBe('true')
    vi.advanceTimersByTime(2000)
    expect(appendedFrames[0].removed).toBe(true)
    vi.useRealTimers()
  })
})
