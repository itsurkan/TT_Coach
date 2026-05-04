import { describe, it, expect } from 'vitest'
import { parseArgs, parseFrameRange } from '../args'

describe('parseArgs', () => {
  it('parses --key value pairs', () => {
    expect(parseArgs(['--video', 'ivan_1', '--frames', '315-320']))
      .toEqual({ video: 'ivan_1', frames: '315-320' })
  })
  it('parses --flag (boolean true)', () => {
    expect(parseArgs(['--force', '--video', 'x']))
      .toEqual({ force: true, video: 'x' })
  })
  it('throws on bare positional', () => {
    expect(() => parseArgs(['ivan_1'])).toThrow(/expected --flag/)
  })
})

describe('parseFrameRange', () => {
  it('single frame', () => {
    expect(parseFrameRange('315')).toEqual([315])
  })
  it('range', () => {
    expect(parseFrameRange('315-318')).toEqual([315, 316, 317, 318])
  })
  it('list', () => {
    expect(parseFrameRange('1,3,5')).toEqual([1, 3, 5])
  })
  it('mixed list+range', () => {
    expect(parseFrameRange('1-3,7,10-11')).toEqual([1, 2, 3, 7, 10, 11])
  })
  it('rejects empty', () => {
    expect(() => parseFrameRange('')).toThrow()
  })
  it('rejects reversed range', () => {
    expect(() => parseFrameRange('10-5')).toThrow(/reversed/)
  })
  it('rejects negative', () => {
    expect(() => parseFrameRange('-1')).toThrow()
  })
})
