// Manual UI fixture only. Not an application entry point; no real DB, models or user files.
import {createRoot} from 'react-dom/client'
import {OrganizationPanel} from '../../src/organization/OrganizationPanel.js'
import {MotionProvider} from '../../src/ui/MotionProvider.js'
import {QueryProvider} from '../../src/shared/QueryProvider.js'
import type {BatchItem} from '../../src/organization/api.js'
import '../../src/index.css'
import '../../src/App.css'

const batchId = '11111111-2222-4333-8444-555555555555'
const hash = 'a'.repeat(64)
const initial: BatchItem = {fileId: 1, originalName: 'IMG_0026.png', status: 'COMPLETED',
    category: 'PHOTOGRAPHS', proposedName: 'kubek_na_biurku.png', message: null,
    analysisId: 8, sourceSha256: hash, analysisMatchesCatalog: true, namingRevision: -1, approvedName: null, review: null}
let rows: BatchItem[] = [initial, {...initial, fileId: 2, originalName: 'screenshot_42.png', category: 'REVIEW', proposedName: null},
    {...initial, fileId: 3, originalName: 'broken.png', status: 'FAILED', analysisId: null, message: 'Nie udało się odczytać obrazu.'}]
const job = {id: batchId, folderPath: 'synthetic-fixture', status: 'COMPLETED', total: 3,
    completed: 2, failed: 1, processing: 0, ocr: true, visual: true, createdAt: '2026-10-19T12:00:00Z'}
const nativeFetch = window.fetch.bind(window)
window.fetch = async (input, options) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
    if (!url.startsWith('/api/organization/')) return nativeFetch(input, options)
    const reviewId = url.match(/\/items\/(\d+)\/review$/)?.[1]
    if (reviewId) {
        const index = rows.findIndex(row => row.fileId === Number(reviewId))
        const item = rows[index]
        if (options?.method === 'PUT') {
            const request = JSON.parse(String(options.body))
            if (request.namingRevision !== item.namingRevision || request.reviewRevision !== (item.review?.revision ?? -1)) {
                return Response.json({code: 'REVIEW_CHANGED'}, {status: 409})
            }
            rows[index] = {...item, approvedName: request.decision === 'APPROVED' ? request.name : item.approvedName,
                namingRevision: item.namingRevision + (request.decision === 'APPROVED' ? 1 : 0),
                review: {decision: request.decision, category: request.category, reviewedAt: new Date().toISOString(),
                    revision: (item.review?.revision ?? -1) + 1, matchesCatalog: true}}
        }
        return Response.json(rows[index])
    }
    if (url.includes('/items?')) return Response.json({items: rows, hasNext: true})
    return Response.json(job)
}

const root = createRoot(document.getElementById('root')!)
const mobile = new URLSearchParams(location.search).has('mobile')
root.render(<QueryProvider><MotionProvider>{mobile ? <main style={{padding: 20}}>
    <p>Test UI · telefon 390 px · dane fikcyjne · <a href="review-preview.html">Widok desktopowy</a></p>
    <iframe title="Formularz na telefonie" src="review-preview.html?frame=1" style={{width: 390, height: 1100, border: '1px solid #ccc'}}/>
</main> : <main className="app-shell">
    <h1>Test przeglądu propozycji</h1>
    <p>Dane fikcyjne. Decyzje istnieją tylko w pamięci tej karty. <a href="?mobile=1">Widok telefonu</a></p>
    <button className="secondary-button" onClick={() => { rows = rows.map(item => ({...item,
        namingRevision: item.namingRevision + 1, approvedName: 'zmiana_z_innego_okna.png'})) }}>Symuluj zmianę w drugim oknie</button>
    <OrganizationPanel folderPath="synthetic-fixture" fileCount={3} scanning={false}/>
</main>}</MotionProvider></QueryProvider>)
