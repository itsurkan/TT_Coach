export type ArgValue = string | boolean
export type ParsedArgs = Record<string, ArgValue>

export function parseArgs(argv: string[]): ParsedArgs {
  const out: ParsedArgs = {}
  let i = 0
  while (i < argv.length) {
    const tok = argv[i]
    if (!tok.startsWith('--')) {
      throw new Error(`Unexpected positional "${tok}" — expected --flag or --key value`)
    }
    const key = tok.slice(2)
    const next = argv[i + 1]
    if (next === undefined || next.startsWith('--')) {
      out[key] = true
      i += 1
    } else {
      out[key] = next
      i += 2
    }
  }
  return out
}

export function requireString(args: ParsedArgs, key: string): string {
  const v = args[key]
  if (typeof v !== 'string' || v.length === 0) {
    throw new Error(`Missing required arg --${key}`)
  }
  return v
}

export function requireScore(args: ParsedArgs, key: string): number {
  const v = args[key]
  if (typeof v !== 'string') throw new Error(`Missing required arg --${key}`)
  const n = Number(v)
  if (!Number.isInteger(n) || n < 0 || n > 10) {
    throw new Error(`--${key} must be an integer 0..10 (got ${v})`)
  }
  return n
}

export function parseFrameRange(spec: string): number[] {
  if (!spec) throw new Error('Empty frame range')
  const out: number[] = []
  for (const part of spec.split(',')) {
    if (!part) throw new Error(`Empty segment in "${spec}"`)
    // A range looks like "NNN-MMM" where both N and M are non-negative integers.
    // Reject anything that starts with '-' (negative number) before checking for '-'.
    const rangeMatch = /^(\d+)-(\d+)$/.exec(part)
    if (rangeMatch) {
      const a = Number(rangeMatch[1]), b = Number(rangeMatch[2])
      if (b < a) throw new Error(`reversed range "${part}"`)
      for (let n = a; n <= b; n++) out.push(n)
    } else {
      const n = Number(part)
      if (!Number.isInteger(n) || n < 0) throw new Error(`Bad frame "${part}"`)
      out.push(n)
    }
  }
  return out
}
