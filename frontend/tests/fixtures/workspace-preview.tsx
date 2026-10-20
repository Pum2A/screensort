// Manual UI verification: real React components, fictional in-memory API, no models or user DB.
import {createRoot} from 'react-dom/client'
import type {CatalogFile} from '../../src/catalog/types'
import type {Batch, BatchItem} from '../../src/organization/api'
import type {ExportJob} from '../../src/organization/export/api'
import {WorkspacePreview} from './workspace-preview-ui'
import '../../src/index.css'
import '../../src/App.css'
import '../../src/desktop/DesktopStartup.css'

const query = new URLSearchParams(location.search)
const scenario = query.get('scenario') ?? 'catalog'
const batchId = '11111111-2222-4333-8444-555555555555'
const date = '2026-10-20T10:00:00Z'
const captions = ['A terminal screen with database connection text.', 'A sheet of paper with text on it.', 'A cartoon character with a square head.',
    'A landscape with mountains and a sun.', 'A sandwich wrapped in foil.', 'A card with a portrait and some text.']
const texts = ['ERROR: connection refused\nJava / PostgreSQL', 'JAVA\nNotatki: kolekcje i strumienie', 'ROBLOX\nLobby', '', '', 'KARTA TESTOWA\nDANE FIKCYJNE']
const names = ['Screenshot_terminal', 'notatki', 'Screenshot_141809', 'IMG_0026', 'IMG_0042', 'karta_testowa']
const categories = ['PROGRAMMING', 'DOCUMENTS', 'GAMES', 'PHOTOGRAPHS', 'OTHER', 'DOCUMENTS']
const proposals = ['terminal_database_connection', 'sheet_of_paper_with_text', 'cartoon_character_square_head', 'landscape_mountains_sun', 'sandwich_wrapped_in_foil', 'card_portrait_text']
const files: CatalogFile[] = Array.from({length: 94}, (_, index) => {
    const id = index + 1, variant = index % 6, extension = variant === 3 || variant === 4 ? 'jpg' : 'png'
    const name = names[variant] + '_' + String(id).padStart(3, '0') + '.' + extension
    return {id, allowedRootId: 1, sourceFolder: 'synthetic-fixture', relativePath: name, originalName: name, extension,
        sizeBytes: 130000 + id * 12617, lastModifiedAt: date, sha256: id.toString(16).padStart(64, '0'), firstDiscoveredAt: date, lastSeenAt: date}
})
let rows: BatchItem[] = files.map(file => ({fileId: file.id, originalName: file.originalName, status: scenario === 'paused' && file.id > 7 ? 'PENDING' : file.id % 31 === 0 ? 'FAILED' : 'COMPLETED',
    category: categories[(file.id - 1) % 6], proposedName: proposals[(file.id - 1) % 6] + '.' + file.extension,
    analysisId: 100 + file.id, sourceSha256: file.sha256, message: file.id % 31 === 0 ? 'Nie udało się odczytać obrazu. Oryginał pozostaje bez zmian.' : null,
    analysisMatchesCatalog: true, namingRevision: -1, approvedName: null, review: null}))
let job: Batch = {id: batchId, folderPath: 'synthetic-fixture', status: scenario === 'paused' ? 'PAUSED' : 'COMPLETED',
    total: 94, completed: scenario === 'paused' ? 7 : 91, failed: scenario === 'paused' ? 0 : 3, processing: 0, ocr: true, visual: true, createdAt: date}
if (scenario === 'organized') rows = rows.map((row, index) => index >= 12 ? row : {...row,
    approvedName: ['baza_danych', 'notatki_java', 'roblox_lobby', 'gory', 'kebab', 'karta_testowa'][index % 6] + '.' + files[index].extension,
    namingRevision: 0, review: {decision: 'APPROVED', category: row.category, matchesCatalog: true, revision: 0, reviewedAt: date}})
let exportJob: ExportJob | null = null
function evidence(id: number) {
    const file = files[id - 1], variant = (id - 1) % 6
    return {id: 100 + id, fileId: id, analyzedAt: date, matchesCatalog: true, result: {source_sha256: file.sha256,
        ocr: {status: 'COMPLETED', error: null, result: {text: texts[variant], engine: 'RapidOCR', engine_version: 'fixture', duration_ms: 112}},
        visual: {status: 'COMPLETED', error: null, result: {description: captions[variant], model: 'Florence-2 (dane testowe)', model_revision: 'fixture', task: 'caption', device: 'cpu', duration_ms: 1250}}}}
}
function advance() {
    if (job.status !== 'RUNNING') return
    const next = rows.findIndex(row => row.status === 'PENDING')
    if (next === -1) {job = {...job, status: 'COMPLETED', processing: 0}; return}
    rows[next] = {...rows[next], status: 'COMPLETED'}
    job = {...job, completed: job.completed + 1, processing: 0}
}
const nativeFetch = window.fetch.bind(window)
window.fetch = async (input, options) => {
    const raw = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
    const url = new URL(raw, location.href)
    if (!url.pathname.startsWith('/api/')) return nativeFetch(input, options)
    const method = options?.method ?? 'GET'
    // This fixture simulates progress in memory. It never writes to the selected destination.
    if (url.pathname.endsWith('/exports/latest')) return exportJob ? Response.json(exportJob) : new Response(null, {status: 404})
    if (/^\/api\/organization\/batches\/[^/]+\/exports$/.test(url.pathname) && method === 'POST') {
        const body = JSON.parse(String(options?.body))
        const total = rows.filter(row => row.review?.decision === 'APPROVED' && row.review.matchesCatalog).length
        if (!total) return Response.json({code: 'NO_APPROVALS'}, {status: 409})
        exportJob = {id: 'synthetic-export', batchId, status: 'RUNNING', outputFolder: body.destinationFolder + '\\ScreenSort',
            total, copied: 0, failed: 0, message: 'SYMULACJA PODGLĄDU UI — żadne pliki nie są zapisywane.', createdAt: date}
        return Response.json(exportJob, {status: 202})
    }
    if (url.pathname === '/api/organization/exports/synthetic-export') {
        if (!exportJob) return new Response(null, {status: 404})
        const copied = Math.min(exportJob.total, exportJob.copied + 3)
        exportJob = {...exportJob, copied, status: copied === exportJob.total ? 'COMPLETED' : 'RUNNING'}
        return Response.json(exportJob)
    }
    if (url.pathname.endsWith('/plan')) {
        const pageNumber = Number(url.searchParams.get('page') ?? 0)
        const approved = rows.filter(row => row.review?.decision === 'APPROVED' && row.review.matchesCatalog)
            .map(row => ({fileId: row.fileId, relativePath: files[row.fileId - 1].relativePath,
                originalName: row.originalName, category: row.review!.category, approvedName: row.approvedName}))
        return Response.json({files: approved.slice(pageNumber * 50, (pageNumber + 1) * 50), pageNumber, pageSize: 50,
            totalFiles: approved.length, hasNext: (pageNumber + 1) * 50 < approved.length})
    }
    if (url.pathname === '/api/catalog/page') {
        if (query.has('error')) return Response.json({code: 'FIXTURE_UNAVAILABLE'}, {status: 503})
        const pageNumber = Number(url.searchParams.get('pageNumber') ?? 0), pageSize = Number(url.searchParams.get('pageSize') ?? 50)
        return Response.json({files: files.slice(pageNumber * pageSize, (pageNumber + 1) * pageSize), pageNumber, pageSize, totalFiles: files.length, hasNext: (pageNumber + 1) * pageSize < files.length})
    }
    const analysis = url.pathname.match(/^\/api\/catalog\/files\/(\d+)\/analysis$/)
    if (analysis) return Response.json(evidence(Number(analysis[1])))
    const naming = url.pathname.match(/^\/api\/catalog\/files\/(\d+)\/naming$/)
    if (naming) {
        const row = rows[Number(naming[1]) - 1]
        if (method === 'PUT') {const body = JSON.parse(String(options?.body)); row.approvedName = body.name; row.namingRevision++}
        return Response.json({fileId: row.fileId, originalName: row.originalName, analysisId: row.analysisId, sourceSha256: row.sourceSha256,
            matchesCatalog: true, suggestedName: row.proposedName, approvedName: row.approvedName, revision: row.namingRevision, approvedAt: row.approvedName ? date : null})
    }
    const review = url.pathname.match(/\/items\/(\d+)\/review$/)
    if (review) {
        const index = Number(review[1]) - 1, item = rows[index]
        if (method === 'PUT') {
            await new Promise(resolve => setTimeout(resolve, 250)) // Test-only latency: make saving feedback observable.
            const body = JSON.parse(String(options?.body))
            if (body.namingRevision !== item.namingRevision || body.reviewRevision !== (item.review?.revision ?? -1)) return Response.json({code: 'REVIEW_CHANGED'}, {status: 409})
            rows[index] = {...item, approvedName: body.decision === 'APPROVED' ? body.name : item.approvedName,
                namingRevision: item.namingRevision + (body.decision === 'APPROVED' ? 1 : 0), review: {decision: body.decision, category: body.category, matchesCatalog: true, revision: (item.review?.revision ?? -1) + 1, reviewedAt: date}}
        }
        return Response.json(rows[index])
    }
    if (url.pathname.startsWith('/api/organization/batches')) {
        if (method === 'POST') {
            const body = JSON.parse(String(options?.body))
            if (url.pathname.endsWith('/control')) {job = {...job, status: body.action === 'RESUME' ? 'RUNNING' : body.action === 'CANCEL' ? 'CANCELLED' : 'PAUSED'}}
            else {rows = rows.map(row => ({...row, status: 'PENDING'})); job = {...job, status: 'RUNNING', completed: 0, failed: 0, ocr: body.ocr, visual: body.visual}}
        } else if (!url.pathname.endsWith('/items')) advance()
        if (url.pathname.endsWith('/items')) {const page = Number(url.searchParams.get('page') ?? 0); return Response.json({items: rows.slice(page * 50, (page + 1) * 50), hasNext: (page + 1) * 50 < rows.length})}
        return Response.json(job)
    }
    if (url.pathname.startsWith('/api/scan/jobs')) {
        if (url.pathname.endsWith('/rejections')) return Response.json({files: [], totalRejected: 0, truncated: false})
        return Response.json({id: batchId, folderPath: 'synthetic-fixture', status: 'COMPLETED', startedAt: date, finishedAt: date,
            summary: {newImages: 0, rescannedImages: 0, unchangedImages: 94, rejectedFiles: 0}, errorMessage: null})
    }
    return Response.json({code: 'FIXTURE_NOT_IMPLEMENTED'}, {status: 404})
}


const root = createRoot(document.getElementById('root')!)
root.render(<WorkspacePreview scenario={scenario} mobile={query.has('mobile')} onConflict={() => {rows[0] = {...rows[0], namingRevision: rows[0].namingRevision + 1}}}/>)
if (import.meta.hot) import.meta.hot.dispose(() => root.unmount())
