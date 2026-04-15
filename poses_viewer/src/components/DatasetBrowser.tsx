import { useState, useEffect, useRef, useCallback } from 'react'
import { Trash2, ChevronLeft, ChevronRight, SkipBack, SkipForward } from 'lucide-react'

interface DatasetItem {
  split: string
  name: string
  hasLabel: boolean
}

interface ParsedLabel {
  cx: number; cy: number; w: number; h: number
  keypoints: Array<{ x: number; y: number; v: number }>
}

const NUM_KP = 6
const COLORS = ['#ef4444', '#f97316', '#22c55e', '#3b82f6', '#a855f7', '#ec4899']

function parseLabel(text: string): ParsedLabel | null {
  const parts = text.trim().split(/\s+/).map(Number)
  if (parts.length < 5 + NUM_KP * 3) return null
  const [, cx, cy, w, h] = parts
  const keypoints = []
  for (let i = 0; i < NUM_KP; i++) {
    const idx = 5 + i * 3
    keypoints.push({ x: parts[idx], y: parts[idx + 1], v: parts[idx + 2] })
  }
  return { cx, cy, w, h, keypoints }
}

export default function DatasetBrowser({ onClose }: { onClose: () => void }) {
  const [items, setItems] = useState<DatasetItem[]>([])
  const [index, setIndex] = useState(0)
  const [label, setLabel] = useState<ParsedLabel | null>(null)
  const [imgSrc, setImgSrc] = useState<string | null>(null)
  const [searchText, setSearchText] = useState('')
  const [showSearch, setShowSearch] = useState(false)
  const searchRef = useRef<HTMLInputElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)

  // Load item list
  useEffect(() => {
    fetch('/api/dataset/list')
      .then(r => r.json())
      .then((data: DatasetItem[]) => {
        // Sort by video name then frame number
        data.sort((a, b) => {
          const aBase = a.name.replace(/_f\d+$/, '')
          const bBase = b.name.replace(/_f\d+$/, '')
          if (aBase !== bBase) return aBase.localeCompare(bBase)
          const aFrame = parseInt(a.name.match(/_f(\d+)$/)?.[1] ?? '0')
          const bFrame = parseInt(b.name.match(/_f(\d+)$/)?.[1] ?? '0')
          return aFrame - bFrame
        })
        setItems(data); setIndex(0)
      })
      .catch(() => {})
  }, [])

  const item = items[index] ?? null

  // Load image + label when index changes
  useEffect(() => {
    if (!item) return
    // Clear canvas immediately to avoid stale overlay
    const canvas = canvasRef.current
    if (canvas) {
      const ctx = canvas.getContext('2d')!
      ctx.clearRect(0, 0, canvas.width, canvas.height)
    }
    setLabel(null)
    setImgSrc(`/api/dataset/image/${item.split}/${item.name}`)
    fetch(`/api/dataset/label/${item.split}/${item.name}`)
      .then(r => r.ok ? r.text() : '')
      .then(txt => setLabel(txt ? parseLabel(txt) : null))
      .catch(() => setLabel(null))
  }, [item])

  // Draw on canvas
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas || !imgSrc) return
    const ctx = canvas.getContext('2d')!
    const img = new Image()
    img.onload = () => {
      canvas.width = img.width
      canvas.height = img.height
      ctx.drawImage(img, 0, 0)
      const W = img.width
      const H = img.height

      if (!label) return

      // Draw bbox
      const bx = (label.cx - label.w / 2) * W
      const by = (label.cy - label.h / 2) * H
      ctx.strokeStyle = 'rgba(168, 85, 247, 0.5)'
      ctx.lineWidth = 2
      ctx.strokeRect(bx, by, label.w * W, label.h * H)

      // Draw table outline (corners 0-3)
      const corners = label.keypoints.slice(0, 4)
      const visCorners = corners.filter(k => k.v > 0)
      if (visCorners.length >= 2) {
        const order = [0, 1, 2, 3]
        ctx.beginPath()
        let started = false
        for (const i of order) {
          const k = corners[i]
          if (k.v <= 0) continue
          if (!started) { ctx.moveTo(k.x * W, k.y * H); started = true }
          else ctx.lineTo(k.x * W, k.y * H)
        }
        if (corners[0].v > 0 && visCorners.length >= 3) ctx.closePath()
        ctx.strokeStyle = 'rgba(168, 85, 247, 0.6)'
        ctx.lineWidth = 2
        ctx.stroke()
      }

      // Draw net line (points 4-5)
      const n1 = label.keypoints[4], n2 = label.keypoints[5]
      if (n1.v > 0 && n2.v > 0) {
        ctx.beginPath()
        ctx.moveTo(n1.x * W, n1.y * H)
        ctx.lineTo(n2.x * W, n2.y * H)
        ctx.strokeStyle = 'rgba(168, 85, 247, 0.7)'
        ctx.lineWidth = 2
        ctx.setLineDash([4, 3])
        ctx.stroke()
        ctx.setLineDash([])
      }

      // Draw keypoints
      for (let i = 0; i < NUM_KP; i++) {
        const k = label.keypoints[i]
        if (k.v <= 0) continue
        const px = k.x * W, py = k.y * H
        // Glow
        ctx.beginPath()
        ctx.arc(px, py, 10, 0, Math.PI * 2)
        ctx.fillStyle = COLORS[i] + '40'
        ctx.fill()
        // Dot
        ctx.beginPath()
        ctx.arc(px, py, 6, 0, Math.PI * 2)
        ctx.fillStyle = COLORS[i]
        ctx.fill()
        ctx.strokeStyle = '#000'
        ctx.lineWidth = 1
        ctx.stroke()
        // Number
        ctx.font = 'bold 12px monospace'
        ctx.fillStyle = '#fff'
        ctx.textAlign = 'center'
        ctx.textBaseline = 'middle'
        ctx.fillText(String(i + 1), px, py)
        ctx.textAlign = 'start'
        ctx.textBaseline = 'alphabetic'
      }
    }
    img.src = imgSrc
  }, [imgSrc, label])

  // Delete current sample
  const handleDelete = useCallback(async () => {
    if (!item) return
    await fetch(`/api/dataset/delete/${item.split}/${item.name}`, { method: 'DELETE' })
    setItems(prev => {
      const next = prev.filter((_, i) => i !== index)
      if (index >= next.length) setIndex(Math.max(0, next.length - 1))
      return next
    })
  }, [item, index])

  // Jump to name
  const handleSearch = useCallback((query: string) => {
    const q = query.toLowerCase().trim()
    if (!q) return
    const found = items.findIndex(i => i.name.toLowerCase().includes(q))
    if (found >= 0) {
      setIndex(found)
      setShowSearch(false)
      setSearchText('')
    }
  }, [items])

  // Keyboard
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (showSearch) {
        if (e.code === 'Escape') { e.preventDefault(); setShowSearch(false); setSearchText('') }
        return // don't handle other keys while search is open
      }
      if (e.code === 'ArrowRight') { e.preventDefault(); setIndex(i => Math.min(i + 1, items.length - 1)) }
      if (e.code === 'ArrowLeft') { e.preventDefault(); setIndex(i => Math.max(i - 1, 0)) }
      if (e.code === 'Delete' || e.code === 'KeyD') { e.preventDefault(); handleDelete() }
      if (e.code === 'Escape') { e.preventDefault(); onClose() }
      if (e.code === 'Slash' || e.code === 'KeyF' && e.ctrlKey) {
        e.preventDefault()
        setShowSearch(true)
        setTimeout(() => searchRef.current?.focus(), 50)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [items, handleDelete, onClose, showSearch])

  if (items.length === 0) {
    return (
      <div className="h-screen bg-gray-950 text-gray-100 flex items-center justify-center">
        <div className="text-gray-500">Loading dataset...</div>
      </div>
    )
  }

  const trainCount = items.filter(i => i.split === 'train').length
  const valCount = items.filter(i => i.split === 'val').length

  return (
    <div className="h-screen bg-gray-950 text-gray-100 flex flex-col select-none overflow-hidden">
      {/* Header */}
      <header className="border-b border-gray-800 px-4 py-2.5 flex items-center gap-4 shrink-0">
        <h1 className="font-semibold text-white">Dataset Browser</h1>
        <span className="text-xs text-gray-500">
          {trainCount} train + {valCount} val = {items.length} total
        </span>
        <span
          className="text-sm font-mono text-purple-400 cursor-pointer select-text hover:text-purple-300"
          onClick={(e) => {
            const range = document.createRange()
            range.selectNodeContents(e.currentTarget)
            const sel = window.getSelection()
            sel?.removeAllRanges()
            sel?.addRange(range)
          }}
          title="Click to select"
        >
          {item?.split}/{item?.name}
        </span>
        <span className="text-xs text-gray-500">
          {index + 1} / {items.length}
        </span>

        <div className="ml-auto flex items-center gap-2">
          {showSearch ? (
            <input
              ref={searchRef}
              type="text"
              className="bg-gray-800 text-sm text-gray-200 rounded px-2 py-1 border border-purple-500 w-48 outline-none"
              placeholder="Image name..."
              value={searchText}
              onChange={e => setSearchText(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter') { e.preventDefault(); handleSearch(searchText) }
                if (e.key === 'Escape') { e.preventDefault(); setShowSearch(false); setSearchText('') }
              }}
              autoFocus
            />
          ) : (
            <button
              className="px-2 py-1 rounded text-xs bg-gray-800 text-gray-400 hover:bg-gray-700 transition-colors"
              onClick={() => { setShowSearch(true); setTimeout(() => searchRef.current?.focus(), 50) }}
              title="Search by name (/)"
            >
              / Search
            </button>
          )}
          <button
            className="flex items-center gap-1 px-2 py-1 rounded text-xs text-red-400 hover:bg-red-900/30 transition-colors"
            onClick={handleDelete}
            title="Delete this sample (D)"
          >
            <Trash2 size={12} />
            Delete (D)
          </button>
          <button
            className="px-3 py-1 rounded text-xs bg-gray-800 hover:bg-gray-700 transition-colors"
            onClick={onClose}
          >
            Close (Esc)
          </button>
        </div>
      </header>

      {/* Canvas */}
      <div className="flex-1 flex items-center justify-center p-4 min-h-0">
        <canvas
          ref={canvasRef}
          className="max-h-full max-w-full rounded-lg border border-gray-800"
          style={{ imageRendering: 'auto' }}
        />
      </div>

      {/* Footer nav */}
      <div className="border-t border-gray-800 px-4 py-3 flex items-center justify-center gap-2 shrink-0">
        <button className="p-1.5 rounded hover:bg-gray-800 text-gray-300" onClick={() => setIndex(0)}>
          <SkipBack size={14} />
        </button>
        <button className="p-1.5 rounded hover:bg-gray-800 text-gray-300" onClick={() => setIndex(i => Math.max(i - 1, 0))}>
          <ChevronLeft size={14} />
        </button>

        <input
          type="range"
          min={0}
          max={items.length - 1}
          value={index}
          onChange={e => setIndex(Number(e.target.value))}
          className="w-96 accent-purple-500"
        />

        <button className="p-1.5 rounded hover:bg-gray-800 text-gray-300" onClick={() => setIndex(i => Math.min(i + 1, items.length - 1))}>
          <ChevronRight size={14} />
        </button>
        <button className="p-1.5 rounded hover:bg-gray-800 text-gray-300" onClick={() => setIndex(items.length - 1)}>
          <SkipForward size={14} />
        </button>

        <span className="text-xs text-gray-600 ml-4">← → navigate · D delete · Esc close</span>
      </div>
    </div>
  )
}
