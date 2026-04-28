import type { PoseAnchor } from '../drill/PoseAnchor'
import { useSavedPoses, type SavedPose } from '../hooks/useSavedPoses'

interface Props {
  currentAnchor: PoseAnchor
  onLoad: (anchor: PoseAnchor) => void
}

function relativeTime(ts: number): string {
  const delta = Math.max(0, Date.now() - ts)
  const s = Math.floor(delta / 1000)
  if (s < 60) return 'just now'
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ago`
  const d = Math.floor(h / 24)
  return `${d}d ago`
}

export default function SavedPosesList({ currentAnchor, onLoad }: Props) {
  const { list, save, remove } = useSavedPoses()

  const handleSave = () => {
    const defaultName = `Pose ${list.length + 1}`
    const raw = window.prompt('Name this position:', defaultName)
    const name = raw?.trim()
    if (!name) return
    save(name, currentAnchor)
  }

  const handleDelete = (e: React.MouseEvent, p: SavedPose) => {
    e.stopPropagation()
    if (window.confirm(`Delete "${p.name}"?`)) remove(p.id)
  }

  return (
    <div className="w-60 shrink-0 bg-gray-800/60 rounded-lg p-3 flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <div className="text-sm font-semibold text-gray-200">
          Saved <span className="text-gray-500 font-normal">({list.length})</span>
        </div>
        <button
          className="px-2 py-1 rounded bg-emerald-700 hover:bg-emerald-600 text-xs text-white"
          onClick={handleSave}
        >
          + Save
        </button>
      </div>

      {list.length === 0 ? (
        <div className="text-xs text-gray-500 py-4 text-center">
          No saved positions yet.<br />
          Click Save to store the current pose.
        </div>
      ) : (
        <ul className="flex flex-col gap-1 max-h-[640px] overflow-auto">
          {list.map(p => (
            <li
              key={p.id}
              className="flex items-start justify-between gap-2 px-2 py-1.5 rounded hover:bg-gray-700/60 cursor-pointer group"
              onClick={() => onLoad(p.anchor)}
              title="Click to load"
            >
              <div className="min-w-0 flex-1">
                <div className="text-sm text-gray-100 truncate">{p.name}</div>
                <div className="text-[10px] text-gray-500">{relativeTime(p.createdAt)}</div>
              </div>
              <button
                type="button"
                aria-label={`Delete ${p.name}`}
                className="opacity-0 group-hover:opacity-100 text-gray-400 hover:text-red-400 text-sm leading-none px-1"
                onClick={e => handleDelete(e, p)}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
