// Synthetic local preview only. All /api routes stay within this fixture process.
import {createServer} from 'node:http'
import {spawn} from 'node:child_process'
import {fileURLToPath} from 'node:url'

function illustration(id) {
    const variant = (id - 1) % 6
    const scenes = [
        `<rect x="45" y="60" width="390" height="250" rx="13" fill="#20372d"/><circle cx="68" cy="82" r="4" fill="#d9aa79"/><circle cx="83" cy="82" r="4" fill="#94b888"/><text x="68" y="130" fill="#a7c895" font-size="17" font-family="monospace">localhost / database</text><text x="68" y="171" fill="#e9d5a7" font-size="14" font-family="monospace">connection refused</text><path d="M68 201h220m-220 19h290m-290 19h175m-175 19h245" stroke="#729c7d" stroke-width="5"/>`,
        `<rect x="65" y="38" width="350" height="285" rx="10" fill="#fffaf0"/><text x="94" y="86" fill="#9d8052" font-size="16" font-family="monospace">NOTATKI / JAVA</text><path d="M95 115h270M95 145h245M95 175h260M95 205h190" stroke="#d4c8ad" stroke-width="6"/><rect x="95" y="236" width="135" height="43" rx="5" fill="#d7e2c4"/><path d="M255 249h100m-100 17h74" stroke="#c4cbb3" stroke-width="5"/>`,
        `<rect x="38" y="50" width="404" height="265" rx="12" fill="#c4d3e5"/><path d="M38 246h404v69H38z" fill="#a8c3a7"/><rect x="150" y="147" width="177" height="104" rx="3" fill="#b59ac4"/><rect x="190" y="72" width="96" height="83" rx="9" fill="#f3d3a6"/><rect x="100" y="158" width="53" height="91" rx="3" fill="#f3d3a6"/><rect x="325" y="158" width="53" height="91" rx="3" fill="#f3d3a6"/><rect x="168" y="248" width="58" height="55" fill="#6884a0"/><rect x="253" y="248" width="58" height="55" fill="#6884a0"/><circle cx="218" cy="112" r="4" fill="#544a42"/><circle cx="257" cy="112" r="4" fill="#544a42"/><path d="M224 132q15 10 28-1" fill="none" stroke="#544a42" stroke-width="3"/><text x="56" y="79" fill="#5d7290" font-size="15" font-family="monospace">GAME / TEST SCENE</text>`,
        `<circle cx="340" cy="98" r="43" fill="#dbaf72"/><path d="M0 260 152 97 302 266" fill="#9eaf8b"/><path d="m90 260 179-180 211 220v60H0z" fill="#6f8c73"/><path d="m152 97 34 37-20 9-13-13-13 12-23-7z" fill="#e7ebdf"/><path d="m269 80 38 36-25-4-13 16-17-15-16 4z" fill="#e4e9df"/><path d="M0 300q110-55 242 0t238 0v60H0" fill="#b8c8a5"/>`,
        `<ellipse cx="245" cy="277" rx="156" ry="35" fill="#d0c4ad"/><path d="m162 118 165 10 15 144q-85 32-174-3z" fill="#dbba83"/><path d="m151 122 179 7-20 45-147-5z" fill="#8fbd77"/><path d="m162 124 160 8-20 27-129-4z" fill="#c68661"/><path d="m184 127 30 8m33-9 25 12m9-12 27 12" stroke="#e6d5a1" stroke-width="10"/><path d="m173 197 155 7 7 66q-78 23-162-2z" fill="#b1bbc0"/><path d="m184 216 131 11m-122 12 126 7" stroke="#dae0df" stroke-width="5"/>`,
        `<rect x="49" y="82" width="382" height="214" rx="19" fill="#bbcbbd"/><rect x="76" y="111" width="114" height="146" rx="8" fill="#dfdfc8"/><circle cx="132" cy="155" r="25" fill="#b5aa90"/><path d="M92 236q0-58 40-58t41 58" fill="#94a38d"/><text x="215" y="132" fill="#4d685a" font-size="15" font-family="monospace">KARTA TESTOWA</text><text x="215" y="159" fill="#6c7e6d" font-size="11" font-family="monospace">DANE FIKCYJNE</text><path d="M215 188h173m-173 23h136m-136 23h155" stroke="#8eaa93" stroke-width="7"/>`,
    ]
    return `<svg xmlns="http://www.w3.org/2000/svg" width="480" height="360" viewBox="0 0 480 360"><rect width="480" height="360" fill="${['#dce6d5','#ede6d5','#e1e4ef','#dfe9e1','#ece2d5','#dde8dc'][variant]}"/>${scenes[variant]}</svg>`
}

const images = createServer((request, response) => {
    const match = request.url?.match(/^\/api\/catalog\/files\/(\d+)\/(thumbnail|preview)(\?|$)/)
    const id = match ? Number(match[1]) : 0
    if (id >= 1 && id <= 94) {
        response.writeHead(200, {'Content-Type': 'image/svg+xml', 'Cache-Control': 'no-store'})
        response.end(illustration(id))
    } else {response.writeHead(404); response.end()}
})
images.listen(0, '127.0.0.1', () => {
    const vite = fileURLToPath(new URL('../../node_modules/vite/bin/vite.js', import.meta.url))
    const root = fileURLToPath(new URL('../../', import.meta.url))
    const child = spawn(process.execPath, [vite, '--port', '5184'], {cwd: root, stdio: 'inherit',
        env: {...process.env, SCREENSORT_BACKEND_URL: `http://127.0.0.1:${images.address().port}`}})
    child.on('exit', code => images.close(() => {process.exitCode = code ?? 1}))
    child.on('error', error => {console.error(error.message); images.close(); process.exitCode = 1})
    for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => {child.kill(); images.close()})
})
