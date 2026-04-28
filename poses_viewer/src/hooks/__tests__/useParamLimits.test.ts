// @vitest-environment jsdom
import { renderHook, act } from '@testing-library/react'
import { useParamLimits } from '../useParamLimits'

const LS_KEY = 'poses_viewer_param_limits'

beforeEach(() => localStorage.clear())

describe('useParamLimits', () => {
  it('returns spec defaults when no override exists', () => {
    const { result } = renderHook(() => useParamLimits())
    expect(result.current.getLimits('rightHip', 'figureYawDeg', -180, 180)).toEqual({ min: -180, max: 180 })
  })

  it('setLimits persists override and returns it', () => {
    const { result } = renderHook(() => useParamLimits())
    act(() => { result.current.setLimits('rightHip', 'figureYawDeg', -30, 30) })
    expect(result.current.getLimits('rightHip', 'figureYawDeg', -180, 180)).toEqual({ min: -30, max: 30 })
    const stored = JSON.parse(localStorage.getItem(LS_KEY) ?? '{}')
    expect(stored['rightHip.figureYawDeg']).toEqual({ min: -30, max: 30 })
  })

  it('resetLimits removes override and returns spec defaults', () => {
    const { result } = renderHook(() => useParamLimits())
    act(() => { result.current.setLimits('rightHip', 'figureYawDeg', -30, 30) })
    act(() => { result.current.resetLimits('rightHip', 'figureYawDeg') })
    expect(result.current.getLimits('rightHip', 'figureYawDeg', -180, 180)).toEqual({ min: -180, max: 180 })
    const stored = JSON.parse(localStorage.getItem(LS_KEY) ?? '{}')
    expect('rightHip.figureYawDeg' in stored).toBe(false)
  })

  it('different joint×param pairs are independent', () => {
    const { result } = renderHook(() => useParamLimits())
    act(() => { result.current.setLimits('rightHip', 'figureYawDeg', -30, 30) })
    expect(result.current.getLimits('leftHip', 'figureYawDeg', -180, 180)).toEqual({ min: -180, max: 180 })
    expect(result.current.getLimits('rightHip', 'bodyRotationDeg', -90, 90)).toEqual({ min: -90, max: 90 })
  })

  it('survives page reload — reads initial state from localStorage', () => {
    localStorage.setItem(LS_KEY, JSON.stringify({ 'rightHip.figureYawDeg': { min: -45, max: 45 } }))
    const { result } = renderHook(() => useParamLimits())
    expect(result.current.getLimits('rightHip', 'figureYawDeg', -180, 180)).toEqual({ min: -45, max: 45 })
  })
})
