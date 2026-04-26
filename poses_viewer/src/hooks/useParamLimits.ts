import { useState } from 'react'

const LS_KEY = 'poses_viewer_param_limits'

type LimitsMap = Record<string, { min: number; max: number }>

function readLS(): LimitsMap {
  try {
    return JSON.parse(localStorage.getItem(LS_KEY) ?? '{}') as LimitsMap
  } catch {
    return {}
  }
}

function writeLS(map: LimitsMap): void {
  localStorage.setItem(LS_KEY, JSON.stringify(map))
}

export function useParamLimits() {
  const [map, setMap] = useState<LimitsMap>(() => readLS())

  const getLimits = (
    jointId: string,
    paramKey: string,
    specMin: number,
    specMax: number,
  ): { min: number; max: number } => {
    const override = map[`${jointId}.${paramKey}`]
    return override ?? { min: specMin, max: specMax }
  }

  const setLimits = (jointId: string, paramKey: string, min: number, max: number): void => {
    const next = { ...map, [`${jointId}.${paramKey}`]: { min, max } }
    writeLS(next)
    setMap(next)
  }

  const resetLimits = (jointId: string, paramKey: string): void => {
    const next = { ...map }
    delete next[`${jointId}.${paramKey}`]
    writeLS(next)
    setMap(next)
  }

  return { getLimits, setLimits, resetLimits }
}
