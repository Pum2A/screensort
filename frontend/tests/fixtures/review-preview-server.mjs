// Test-only preview process. Never forwards image requests to a developer's real backend.
import {createServer} from 'node:http'
import {spawn} from 'node:child_process'
import {fileURLToPath} from 'node:url'

const png = Buffer.from('89504e470d0a1a0a0000000d4948445200000020000000200802000000fc18eda30000003749444154789cedd1c10d003008c3c094fd774e47301f7ebe0182645edb5c9ad3f57860c11f20132113211321132113211321132113857cc7f1033d38c33a7c0000000049454e44ae426082', 'hex')
const images = createServer((request, response) => {
    if (/^\/api\/catalog\/files\/[1-3]\/(thumbnail|preview)(\?|$)/.test(request.url ?? '')) {
        response.writeHead(200, {'Content-Type': 'image/png', 'Cache-Control': 'no-store'})
        response.end(png)
    } else { response.writeHead(404); response.end() }
})
images.listen(0, '127.0.0.1', () => {
    const vite = fileURLToPath(new URL('../../node_modules/vite/bin/vite.js', import.meta.url))
    const root = fileURLToPath(new URL('../../', import.meta.url))
    const child = spawn(process.execPath, [vite, '--port', '5183'], {cwd: root, stdio: 'inherit',
        env: {...process.env, SCREENSORT_BACKEND_URL: `http://127.0.0.1:${images.address().port}`}})
    child.on('exit', code => images.close(() => { process.exitCode = code ?? 1 }))
    child.on('error', error => { console.error(error.message); images.close(); process.exitCode = 1 })
    for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => { child.kill(); images.close() })
})
