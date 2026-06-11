import { useCallback, useEffect, useState } from 'react'

export type Route = 'main' | 'mannequin' | 'drill2' | 'dataset'

export const ROUTES: readonly Route[] = ['main', 'mannequin', 'drill2', 'dataset']

const DEFAULT_ROUTE: Route = 'main'

const ROUTE_TITLES: Record<Route, string> = {
  'main': 'Poses Viewer',
  'mannequin': 'Mannequin Editor — Poses Viewer',
  'drill2': 'Drill 2 Preview — Poses Viewer',
  'dataset': 'Dataset Browser — Poses Viewer',
}

function parseHash(hash: string): Route {
  const stripped = hash.replace(/^#\/?/, '').trim()
  if (!stripped) return DEFAULT_ROUTE
  return (ROUTES as readonly string[]).includes(stripped) ? (stripped as Route) : DEFAULT_ROUTE
}

export function useHashRoute(): { route: Route; navigate: (next: Route) => void } {
  const [route, setRoute] = useState<Route>(() => parseHash(window.location.hash))

  useEffect(() => {
    if (!window.location.hash || parseHash(window.location.hash) !== route) {
      window.location.hash = `#/${route}`
    }
  }, [])

  useEffect(() => {
    const onHash = () => setRoute(parseHash(window.location.hash))
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  useEffect(() => {
    document.title = ROUTE_TITLES[route]
  }, [route])

  const navigate = useCallback((next: Route) => {
    if (parseHash(window.location.hash) === next) return
    window.location.hash = `#/${next}`
  }, [])

  return { route, navigate }
}
