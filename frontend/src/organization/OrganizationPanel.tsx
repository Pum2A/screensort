import {useEffect, useRef, useState} from 'react'
import {FolderTree, Sparkles, Pause, Play, CircleAlert, CheckCircle2, ChevronLeft, ChevronRight, LoaderCircle} from 'lucide-react'
import {useAnalysisReady} from '../desktop/readiness'
import {batchItems, controlBatch, latestBatch, readBatch, startBatch} from './api'
import type {Batch, BatchPage} from './api'
import type {BatchItem} from './api'
import {ReviewQueue} from './review/ReviewQueue'

const statuses = {RUNNING: 'Analizujemy folder', PAUSED: 'Wstrzymano', COMPLETED: 'Analiza zakończona', CANCELLED: 'Anulowano'}

export function OrganizationPanel({folderPath, fileCount, scanning}: {folderPath: string, fileCount: number, scanning: boolean}) {
    const ready = useAnalysisReady()
    const [job, setJob] = useState<Batch | null>(null)
    const [items, setItems] = useState<BatchPage | null>(null)
    const [page, setPage] = useState(0)
    const [refresh, setRefresh] = useState(0)
    const [error, setError] = useState<string | null>(null)
    const [working, setWorking] = useState(false)
    const [editingReview, setEditingReview] = useState(false)
    const [ocr, setOcr] = useState(true)
    const [visual, setVisual] = useState(true)
    const abort = useRef<AbortController | null>(null)

    useEffect(() => {
        const controller = new AbortController()
        abort.current = controller
        let timer: ReturnType<typeof setTimeout>
        async function load(id?: string) {
            try {
                const next = id ? await readBatch(id, controller.signal) : await latestBatch(folderPath, controller.signal)
                if (controller.signal.aborted) return
                if (!next) { setJob(null); setItems(null); return }
                const result = await batchItems(next.id, page, controller.signal)
                if (controller.signal.aborted) return
                setJob(next); setItems(result)
                if (next.status === 'RUNNING' || next.processing > 0) timer = setTimeout(() => void load(next.id), 1200)
            } catch (cause) {
                if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Nie udało się pobrać postępu.')
            }
        }
        void load()
        return () => { controller.abort(); clearTimeout(timer) }
    }, [folderPath, page, refresh])

    async function action(kind: 'START' | 'PAUSE' | 'RESUME' | 'CANCEL') {
        const signal = abort.current?.signal
        if (!signal || working) return
        setWorking(true); setError(null)
        try {
            const next = kind === 'START' ? await startBatch(folderPath, ocr, visual, signal) : await controlBatch(job!.id, kind, signal)
            if (signal.aborted) return
            abort.current?.abort()
            setJob(next)
            // Pausing/resuming must not unmount a user's unfinished review form.
            if (kind === 'START') { setItems(null); setPage(0) }
            setRefresh(value => value + 1)
        } catch (cause) {
            if (!signal.aborted) setError(cause instanceof Error ? cause.message : 'Nie udało się zlecić zadania.')
        } finally { setWorking(false) }
    }

    function reviewed(item: BatchItem) {
        // An earlier poll must not replace a decision that was just committed.
        abort.current?.abort()
        setItems(current => current && {...current, items: current.items.map(value => value.fileId === item.fileId ? item : value)})
        setRefresh(value => value + 1)
    }

    const running = job?.status === 'RUNNING'
    const finished = job ? job.completed + job.failed : 0
    return <section className="my-6 rounded-2xl border border-stone-200 bg-white p-5 shadow-sm sm:p-6" aria-labelledby="organize-title">
        <div className="flex flex-wrap items-center justify-between gap-4">
            <div><h2 id="organize-title" className="m-0 flex items-center gap-2 text-2xl"><FolderTree size={24}/>Od obrazów do porządku</h2>
                <p className="my-2 text-stone-600">Analizuj cały zapisany katalog, nie tylko widoczną stronę. Oryginały pozostają bez zmian.</p></div>
            <button type="button" className="primary-button" onClick={() => void action('START')}
                disabled={!ready || scanning || running || working || editingReview || fileCount === 0 || (!ocr && !visual)}>
                {working || running ? <LoaderCircle size={20} className="spin"/> : <Sparkles size={20}/>}
                Analizuj wszystko ({fileCount})</button>
        </div>
        <div className="my-3 flex flex-wrap items-center gap-4 text-stone-600">
            <span>Pakiet lokalny: RapidOCR + Florence · CPU</span>
            <label className="flex items-center gap-2"><input type="checkbox" checked={ocr} disabled={running || working} onChange={event => setOcr(event.target.checked)}/>Odczyt tekstu</label>
            <label className="flex items-center gap-2"><input type="checkbox" checked={visual} disabled={running || working} onChange={event => setVisual(event.target.checked)}/>Opis obrazu</label>
        </div>
        {!ready && <p className="rounded-lg bg-amber-50 p-3 text-amber-900">Najpierw uruchom pakiet AI przyciskiem nad katalogiem. Przeglądanie i skanowanie nie wymagają modeli.</p>}
        {error && <div role="alert" className="rounded-lg bg-red-50 p-3 text-red-800"><CircleAlert size={18}/>{error}
            <button type="button" className="text-button" onClick={() => {setError(null); setRefresh(value => value + 1)}}>Odśwież stan</button></div>}
        {job && <>
            <div className="mt-4 flex flex-wrap items-center justify-between gap-3" role="status">
                <span className={job.status === 'COMPLETED' && job.failed === 0 ? 'flex items-center gap-2 text-emerald-800' : 'text-stone-700'}>
                    {job.status === 'COMPLETED' && job.failed === 0 && <CheckCircle2 size={20}/>}
                    {job.status === 'COMPLETED' && job.failed > 0 ? 'Zakończono z błędami' : statuses[job.status]} · {finished} / {job.total} · błędy: {job.failed}</span>
                <div className="flex flex-wrap gap-2">
                    {running && <button type="button" className="secondary-button" disabled={working} onClick={() => void action('PAUSE')}><Pause size={18}/>Wstrzymaj</button>}
                    {job.status === 'PAUSED' && <button type="button" className="secondary-button" disabled={working || !ready} onClick={() => void action('RESUME')}><Play size={18}/>Wznów</button>}
                    {running && <button type="button" className="text-button" disabled={working} onClick={() => void action('CANCEL')}>Anuluj po bieżącym obrazie</button>}
                </div>
            </div>
            <progress className={`my-3 h-2 w-full ${job.failed > 0 ? 'accent-amber-600' : 'accent-emerald-600'}`} max={Math.max(1, job.total)} value={finished} aria-label="Przetworzone obrazy"/>
            <p className="rounded-lg bg-amber-50 p-3 text-amber-900">Kategorie to wstępne reguły na podstawie opisu i tekstu, nie nowy model AI. Wszystkie wymagają sprawdzenia. Nazwy są propozycjami — niczego jeszcze nie przenosimy.</p>
            <details className="mt-4" open><summary className="cursor-pointer">Przejrzyj i zatwierdź · strona {page + 1}</summary>
                {items && <ReviewQueue key={`${job.id}:${page}`} batchId={job.id} items={items.items} disabled={scanning}
                    onSaved={reviewed} onEditingChange={setEditingReview}/>}
                {!items && <p className="my-4 text-stone-600" role="status">Wczytujemy wyniki tej strony…</p>}
                <nav className="mt-4 flex items-center gap-3" aria-label="Strony propozycji">
                    <button type="button" className="icon-button" aria-label="Poprzednie propozycje" disabled={page === 0 || !items || working || editingReview} onClick={() => {setItems(null); setPage(value => value - 1)}}><ChevronLeft/></button>
                    <span>Strona {page + 1}</span><button type="button" className="icon-button" aria-label="Następne propozycje" disabled={!items?.hasNext || working || editingReview} onClick={() => {setItems(null); setPage(value => value + 1)}}><ChevronRight/></button>
                </nav>
                {editingReview && <p className="mt-2 text-sm text-stone-600">Przed zmianą strony zatwierdź lub odrzuć edycję.</p>}
            </details>
        </>}
    </section>
}
