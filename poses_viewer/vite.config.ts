import { defineConfig } from 'vite'
import path from 'path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import fs from 'fs'

// Serve JSON and video files from the repo's top-level Videos/ folder at /videos/*
const REPO_ROOT = path.resolve(__dirname, '..')
const VIDEOS_DIR = path.join(REPO_ROOT, 'Videos')
const DATASET_DIR = path.join(REPO_ROOT, 'datasets', 'table_keypoints')

const MIME: Record<string, string> = {
  '.json': 'application/json',
  '.mp4':  'video/mp4',
  '.webm': 'video/webm',
  '.mov':  'video/mp4',
}

export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    exclude: [
      '**/node_modules/**',
      // Frozen ball-tracking suites (2D pivot): they read IMG_6330 ball data from a
      // hardcoded path on the original dev machine and cannot run here or in CI.
      '**/__tests__/segmentTrajectoryV1.test.ts',
      '**/__tests__/segmentTrajectoryV4.test.ts',
      '**/__tests__/analyzeArcsV2.test.ts',
    ],
  },
  plugins: [
    react(),
    tailwindcss(),
    {
      name: 'save-labels',
      configureServer(server) {
        // POST /api/labels/:base — save labels JSON to Videos/<base>/<base>_labels.json
        server.middlewares.use('/api/labels', (req: any, res: any, next: any) => {
          if (req.method !== 'POST') return next()
          const base = decodeURIComponent((req.url || '/').replace(/^\//, ''))
          if (!base) { res.writeHead(400); res.end('Missing base name'); return }

          let body = ''
          req.on('data', (chunk: string) => { body += chunk })
          req.on('end', () => {
            try {
              const data = JSON.parse(body)
              const dir = path.join(VIDEOS_DIR, base)
              if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true })
              const filePath = path.join(dir, `${base}_labels.json`)
              fs.writeFileSync(filePath, JSON.stringify(data, null, 2))
              res.writeHead(200, { 'Content-Type': 'application/json' })
              res.end(JSON.stringify({ ok: true, path: filePath }))
            } catch (e: any) {
              res.writeHead(500, { 'Content-Type': 'application/json' })
              res.end(JSON.stringify({ error: e.message }))
            }
          })
        })
      },
    },
    {
      name: 'save-table-labels',
      configureServer(server) {
        // POST /api/table-labels/:base — save table keypoint labels
        server.middlewares.use('/api/table-labels', (req: any, res: any, next: any) => {
          if (req.method !== 'POST') return next()
          const base = decodeURIComponent((req.url || '/').replace(/^\//, ''))
          if (!base) { res.writeHead(400); res.end('Missing base name'); return }

          let body = ''
          req.on('data', (chunk: string) => { body += chunk })
          req.on('end', () => {
            try {
              const data = JSON.parse(body)
              const dir = path.join(VIDEOS_DIR, base)
              if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true })
              const filePath = path.join(dir, `${base}_table_labels.json`)
              fs.writeFileSync(filePath, JSON.stringify(data, null, 2))
              res.writeHead(200, { 'Content-Type': 'application/json' })
              res.end(JSON.stringify({ ok: true, path: filePath }))
            } catch (e: any) {
              res.writeHead(500, { 'Content-Type': 'application/json' })
              res.end(JSON.stringify({ error: e.message }))
            }
          })
        })
      },
    },
    {
      name: 'dataset-api',
      configureServer(server) {
        // GET /api/dataset/list — list all dataset images with their labels
        server.middlewares.use('/api/dataset/list', (req: any, res: any, next: any) => {
          if (req.method !== 'GET') return next()
          try {
            const splits = ['train', 'val']
            const items: Array<{ split: string; name: string; hasLabel: boolean }> = []
            for (const split of splits) {
              const imgDir = path.join(DATASET_DIR, split, 'images')
              if (!fs.existsSync(imgDir)) continue
              for (const f of fs.readdirSync(imgDir).sort()) {
                if (!f.endsWith('.jpg') && !f.endsWith('.png')) continue
                const stem = f.replace(/\.[^.]+$/, '')
                const lblPath = path.join(DATASET_DIR, split, 'labels', stem + '.txt')
                items.push({ split, name: stem, hasLabel: fs.existsSync(lblPath) })
              }
            }
            res.writeHead(200, { 'Content-Type': 'application/json' })
            res.end(JSON.stringify(items))
          } catch (e: any) {
            res.writeHead(500); res.end(e.message)
          }
        })

        // GET /api/dataset/image/:split/:name — serve dataset image
        server.middlewares.use('/api/dataset/image', (req: any, res: any, next: any) => {
          const parts = decodeURIComponent(req.url || '').replace(/^\//, '').split('/')
          if (parts.length < 2) return next()
          const [split, name] = parts
          const filePath = path.join(DATASET_DIR, split, 'images', name + '.jpg')
          if (!fs.existsSync(filePath)) { res.writeHead(404); res.end('Not found'); return }
          const data = fs.readFileSync(filePath)
          res.writeHead(200, { 'Content-Type': 'image/jpeg', 'Content-Length': data.length })
          res.end(data)
        })

        // GET /api/dataset/label/:split/:name — serve dataset label
        server.middlewares.use('/api/dataset/label', (req: any, res: any, next: any) => {
          const parts = decodeURIComponent(req.url || '').replace(/^\//, '').split('/')
          if (parts.length < 2) return next()
          const [split, name] = parts
          const filePath = path.join(DATASET_DIR, split, 'labels', name + '.txt')
          if (!fs.existsSync(filePath)) { res.writeHead(404); res.end(''); return }
          const data = fs.readFileSync(filePath, 'utf-8')
          res.writeHead(200, { 'Content-Type': 'text/plain' })
          res.end(data)
        })

        // DELETE /api/dataset/delete/:split/:name — delete bad sample (image + label)
        server.middlewares.use('/api/dataset/delete', (req: any, res: any, next: any) => {
          if (req.method !== 'DELETE') return next()
          const parts = decodeURIComponent(req.url || '').replace(/^\//, '').split('/')
          if (parts.length < 2) { res.writeHead(400); res.end('Missing split/name'); return }
          const [split, name] = parts
          const imgPath = path.join(DATASET_DIR, split, 'images', name + '.jpg')
          const lblPath = path.join(DATASET_DIR, split, 'labels', name + '.txt')
          try {
            if (fs.existsSync(imgPath)) fs.unlinkSync(imgPath)
            if (fs.existsSync(lblPath)) fs.unlinkSync(lblPath)
            res.writeHead(200, { 'Content-Type': 'application/json' })
            res.end(JSON.stringify({ ok: true, deleted: name }))
          } catch (e: any) {
            res.writeHead(500); res.end(e.message)
          }
        })
      },
    },
    {
      name: 'list-videos',
      configureServer(server) {
        // GET /api/videos — list video folder names
        server.middlewares.use('/api/videos', (req: any, res: any, next: any) => {
          if (req.method !== 'GET') return next()
          try {
            const entries = fs.readdirSync(VIDEOS_DIR, { withFileTypes: true })
            const videoExts = ['.mp4', '.mov', '.webm']
            const items = entries
              .filter(e => e.isDirectory())
              .map(e => {
                const files = fs.readdirSync(path.join(VIDEOS_DIR, e.name))
                const vid = files.find((f: string) => videoExts.includes(path.extname(f).toLowerCase()))
                return { name: e.name, ext: vid ? path.extname(vid) : '' }
              })
              .sort((a: any, b: any) => a.name.localeCompare(b.name))
            res.writeHead(200, { 'Content-Type': 'application/json' })
            res.end(JSON.stringify(items))
          } catch (e: any) {
            res.writeHead(500); res.end(e.message)
          }
        })
      },
    },
    {
      name: 'serve-videos',
      configureServer(server) {
        server.middlewares.use('/videos', (req, res, next) => {
          const url = decodeURIComponent(req.url || '/')
          const filePath = path.join(VIDEOS_DIR, url)

          if (!fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
            return next()
          }

          const ext = path.extname(filePath).toLowerCase() as string
          const mimeType = MIME[ext]
          if (!mimeType) return next()

          const fileSize = fs.statSync(filePath).size
          const rangeHeader = (req as any).headers['range'] as string | undefined

          if (rangeHeader && mimeType.startsWith('video/')) {
            // Partial content for video seeking
            const [startStr, endStr] = rangeHeader.replace('bytes=', '').split('-')
            const start = parseInt(startStr, 10)
            const end = endStr ? parseInt(endStr, 10) : fileSize - 1
            const chunkSize = end - start + 1

            res.writeHead(206, {
              'Content-Range':  `bytes ${start}-${end}/${fileSize}`,
              'Accept-Ranges':  'bytes',
              'Content-Length': chunkSize,
              'Content-Type':   mimeType,
            })
            fs.createReadStream(filePath, { start, end }).pipe(res as any)
          } else {
            res.setHeader('Content-Type',   mimeType)
            res.setHeader('Accept-Ranges',  'bytes')
            res.setHeader('Content-Length', fileSize)
            fs.createReadStream(filePath).pipe(res as any)
          }
        })
      },
    },
  ],
  server: {
    port: 5780,
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
})
