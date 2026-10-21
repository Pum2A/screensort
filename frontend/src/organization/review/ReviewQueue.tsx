import {useCallback, useState} from 'react'
import {CheckCircle2, CircleAlert, Image, Search, SkipForward} from 'lucide-react'
import * as m from 'motion/react-m'
import {CatalogImage} from '../../catalog/CatalogImage'
import type {BatchItem} from '../api'
import {ReviewEditor} from './ReviewEditor'
import type {EditingState} from './ReviewEditor'
import {reviewImageUrl, reviewLabel} from './state'
import {filterReviewPage} from './filters'
import {ReviewCategoryTree} from './ReviewCategoryTree'

type Props = {batchId: string; items: BatchItem[]; disabled: boolean;
    onSaved: (item: BatchItem) => void; onEditingChange: (editing: boolean) => void}
type Navigation = {type: 'file'; id: number} | {type: 'filter'; filter: string}

export function ReviewQueue({batchId, items, disabled, onSaved, onEditingChange}: Props) {
    const [selectedId, setSelectedId] = useState<number | null>(() => (items.find(item => item.status === 'COMPLETED') ?? items[0])?.fileId ?? null)
    const [editing, setEditing] = useState<EditingState>({dirty: false, saving: false})
    const [pending, setPending] = useState<Navigation | null>(null)
    const [filter, setFilter] = useState('ALL')
    const visible = filterReviewPage(items, filter)
    const selected = visible.find(item => item.fileId === selectedId) ?? visible.find(item => item.status === 'COMPLETED') ?? visible[0]
    const selectedIndex = visible.findIndex(item => item.fileId === selected?.fileId)
    const reportEditing = useCallback((state: EditingState) => {
        setEditing(state); onEditingChange(state.dirty || state.saving)
    }, [onEditingChange])

    function apply(navigation: Navigation) {
        if (navigation.type === 'file') setSelectedId(navigation.id)
        else {setFilter(navigation.filter); setSelectedId(null)}
    }
    function navigate(navigation: Navigation) {
        if (editing.saving) return
        if (navigation.type === 'file' && navigation.id === selected?.fileId) return
        if (navigation.type === 'filter' && navigation.filter === filter) return
        if (editing.dirty) setPending(navigation)
        else apply(navigation)
    }

    const approved = filterReviewPage(items, 'APPROVED').length
    const filters = [{value: 'ALL', label: 'Wszystkie'}, {value: 'UNREVIEWED', label: 'Do sprawdzenia'}, {value: 'APPROVED', label: 'Zatwierdzone'}, {value: 'FAILED', label: 'Błędy'}]
    return <div className="review-queue">
        {pending && <div className="feedback feedback-warning" role="alert"><div><strong>Twoje zmiany nie są zapisane.</strong><p>Zapisz je w formularzu albo zdecyduj, co zrobić przed przejściem.</p>
            <div className="button-row"><button className="secondary-button" onClick={() => setPending(null)}>Zostań przy edycji</button><button className="secondary-button" onClick={() => {apply(pending); setPending(null)}}>Odrzuć edycję i przejdź</button></div></div></div>}
        <div className="review-page-summary"><div className="review-filter-chips" role="group" aria-label="Stan propozycji na tej stronie">{filters.map(option =>
            <button key={option.value} aria-pressed={filter === option.value} disabled={editing.saving} onClick={() => navigate({type: 'filter', filter: option.value})}>{option.label}</button>)}</div>
            <span><CheckCircle2 size={14}/>{approved} / {items.length} zatwierdzonych na stronie</span></div>
        <div className="review-workspace"><ReviewCategoryTree items={items} filter={filter} disabled={editing.saving} onFilter={filter => navigate({type: 'filter', filter})}/>
            <div className="review-image-grid" aria-label="Obrazy do zatwierdzenia">
                {visible.map(item => {
                    const src = reviewImageUrl(item, 'thumbnail'), valid = item.review?.matchesCatalog
                    const isSelected = item.fileId === selected?.fileId
                    return <m.button key={item.fileId} type="button" aria-pressed={isSelected} className="review-image-tile" data-state={item.status === 'FAILED' ? 'error' : valid && item.review?.decision === 'APPROVED' ? 'approved' : 'pending'}
                        disabled={editing.saving} onClick={() => navigate({type: 'file', id: item.fileId})} whileHover={{y: -3}} whileTap={{scale: 0.98}}>
                        <span className="review-image-preview"><CatalogImage key={src} src={src} name=""/></span>
                        <strong title={item.originalName}>{item.originalName}</strong>
                        <span className="review-image-status">{valid ? item.review?.decision === 'APPROVED' ? <CheckCircle2 size={12}/> : <SkipForward size={12}/> : item.status === 'FAILED' ? <CircleAlert size={12}/> : <Image size={12}/>}{reviewLabel(item)}</span>
                    </m.button>
                })}
                {!visible.length && <div className="review-no-results"><Search size={25}/><p>Brak takich propozycji na tej stronie.</p><button className="text-button" onClick={() => navigate({type: 'filter', filter: 'ALL'})}>Pokaż wszystkie</button></div>}
            </div>
            <div className="review-inspector">{selected && (selected.status === 'COMPLETED' && selected.analysisId != null
                ? <ReviewEditor key={selected.fileId} batchId={batchId} item={selected} disabled={disabled} onSaved={onSaved} onStateChange={reportEditing}
                    hasNext={selectedIndex < visible.length - 1} onNext={() => navigate({type: 'file', id: visible[selectedIndex + 1].fileId})}/>
                : <div className={'review-item-message ' + (selected.status === 'FAILED' ? 'feedback-error' : '')} role="status"><CircleAlert size={24}/><h4>{reviewLabel(selected)}</h4><p>{selected.message ?? 'Formularz pojawi się po zakończeniu analizy tego obrazu.'}</p></div>)}</div>
        </div>
    </div>
}
