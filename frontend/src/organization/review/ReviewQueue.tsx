import {useCallback, useState} from 'react'
import {CheckCircle2, CircleAlert, Image, SkipForward} from 'lucide-react'
import {CatalogImage} from '../../catalog/CatalogImage'
import type {BatchItem} from '../api'
import {ReviewEditor} from './ReviewEditor'
import type {EditingState} from './ReviewEditor'
import {reviewImageUrl, reviewLabel} from './state'

type Props = {batchId: string; items: BatchItem[]; disabled: boolean;
    onSaved: (item: BatchItem) => void; onEditingChange: (editing: boolean) => void}

export function ReviewQueue({batchId, items, disabled, onSaved, onEditingChange}: Props) {
    const [selectedId, setSelectedId] = useState<number | null>(() => (items.find(item => item.status === 'COMPLETED') ?? items[0])?.fileId ?? null)
    const [editing, setEditing] = useState<EditingState>({dirty: false, saving: false})
    const [pendingSelection, setPendingSelection] = useState<number | null>(null)
    const selected = items.find(item => item.fileId === selectedId) ?? items.find(item => item.status === 'COMPLETED') ?? items[0]
    const selectedIndex = items.findIndex(item => item.fileId === selected?.fileId)
    const reportEditing = useCallback((state: EditingState) => {
        setEditing(state); onEditingChange(state.dirty || state.saving)
    }, [onEditingChange])
    function select(id: number) {
        if (editing.saving || id === selected?.fileId) return
        if (editing.dirty) setPendingSelection(id)
        else setSelectedId(id)
    }
    if (!selected) return <p className="my-4 text-stone-600">Na tej stronie nie ma obrazów do przejrzenia.</p>
    const approved = items.filter(item => item.review?.matchesCatalog && item.review.decision === 'APPROVED').length
    const skipped = items.filter(item => item.review?.matchesCatalog && item.review.decision === 'SKIPPED').length
    return <div className="mt-4">
        <p className="mb-3 text-sm text-stone-600" role="status">Na tej stronie: zatwierdzone {approved} · pominięte {skipped} · łącznie {items.length}</p>
        {pendingSelection !== null && <div className="mb-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-amber-900" role="alert">
            <p>Masz niezapisane zmiany. Zapisać je możesz w formularzu poniżej.</p>
            <div className="mt-2 flex flex-wrap gap-2"><button type="button" className="secondary-button" onClick={() => setPendingSelection(null)}>Zostań przy edycji</button>
                <button type="button" className="secondary-button" onClick={() => {setSelectedId(pendingSelection); setPendingSelection(null)}}>Odrzuć edycję i przejdź</button></div>
        </div>}
        <div className="grid items-start gap-4 lg:grid-cols-[minmax(220px,0.8fr)_minmax(0,1.5fr)]">
            <ul className="m-0 grid max-h-72 list-none gap-2 overflow-y-auto p-0 pr-1 lg:max-h-[760px]" aria-label="Obrazy do zatwierdzenia">
                {items.map(item => {
                    const src = reviewImageUrl(item, 'thumbnail')
                    const valid = item.review?.matchesCatalog
                    return <li key={item.fileId}><button type="button" aria-pressed={item.fileId === selected.fileId}
                        className={`flex w-full items-center gap-3 rounded-xl border p-3 text-left transition-colors motion-reduce:transition-none ${item.fileId === selected.fileId ? 'border-blue-400 bg-blue-50 ring-1 ring-blue-200' : 'border-stone-200 bg-white hover:border-blue-200 hover:bg-stone-50'}`}
                        disabled={editing.saving} onClick={() => select(item.fileId)}>
                        <div className="h-14 w-14 shrink-0 overflow-hidden rounded-lg bg-stone-100"><CatalogImage key={src} src={src} name=""/></div>
                        <span className="min-w-0"><span className="block truncate" title={item.originalName}>{item.originalName}</span>
                            <span className={`mt-1 flex items-center gap-1 text-sm ${item.status === 'FAILED' ? 'text-red-700' : valid && item.review?.decision === 'APPROVED' ? 'text-emerald-700' : 'text-stone-600'}`}>
                                {valid ? item.review?.decision === 'APPROVED' ? <CheckCircle2 size={14}/> : <SkipForward size={14}/> : item.status === 'FAILED' ? <CircleAlert size={14}/> : <Image size={14}/>}
                                {reviewLabel(item)}</span></span>
                    </button></li>
                })}
            </ul>
            {selected.status === 'COMPLETED' && selected.analysisId != null
                ? <ReviewEditor key={selected.fileId} batchId={batchId} item={selected} disabled={disabled}
                    onSaved={onSaved} onStateChange={reportEditing} hasNext={selectedIndex < items.length - 1}
                    onNext={() => select(items[selectedIndex + 1].fileId)}/>
                : <div className="rounded-xl border border-stone-200 p-5" role="status"><p className="font-semibold">{reviewLabel(selected)}</p>
                    <p className={selected.status === 'FAILED' ? 'text-red-700' : 'text-stone-600'}>{selected.message ?? 'Formularz pojawi się po zakończeniu analizy tego obrazu.'}</p></div>}
        </div>
    </div>
}
