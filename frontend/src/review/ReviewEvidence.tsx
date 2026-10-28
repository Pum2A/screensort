import {useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {CircleAlert, LoaderCircle} from 'lucide-react'
import {analysisKeys, latestAnalysis} from '../analysis/api.js'

/** Lazy, read-only evidence. Never display a newer/older analysis as this proposal's evidence. */
export function ReviewEvidence({fileId, analysisId, sourceHash}: {fileId: number; analysisId: number; sourceHash: string}) {
    const [open, setOpen] = useState(false)
    // Same key as the catalog's analysis panel: both views share one cached copy of this analysis.
    const query = useQuery({
        queryKey: analysisKeys.latest(fileId),
        queryFn: ({signal}) => latestAnalysis(fileId, signal),
        enabled: open,
    })
    const result = query.data
    const matches = Boolean(result && result.id === analysisId && result.result.source_sha256 === sourceHash)
    const saved = query.isSuccess && matches ? result : null
    const loading = open && query.isFetching && !query.data
    const error = !open ? null
        : query.isError ? 'Nie udało się pobrać wyniku analizy.'
        : query.isSuccess && !matches ? 'Zapisana analiza zmieniła się. Nie pokazujemy jej jako uzasadnienia tej propozycji.'
        : null

    return <details className="review-evidence" onToggle={event => setOpen(event.currentTarget.open)}>
        <summary>Zobacz tekst i opis z tej analizy</summary>
        {loading && <p className="inline-loading"><LoaderCircle size={15} className="spin"/>Odczytujemy wynik…</p>}
        {error && <p className="feedback feedback-error" role="alert"><CircleAlert size={16}/>{error}</p>}
        {saved && <div><h4>Opis wizualny</h4><p>{saved.result.visual.status === 'COMPLETED' ? saved.result.visual.result.description : 'Brak poprawnego opisu wizualnego.'}</p>
            <h4>Tekst na obrazie</h4><pre>{saved.result.ocr.status === 'COMPLETED' ? saved.result.ocr.result.text || 'Nie rozpoznano napisów.' : 'Brak poprawnego wyniku OCR.'}</pre>
            <small>Obecna nazwa automatyczna powstaje z opisu wizualnego. Nie jest jeszcze wspólną interpretacją obu wyników.</small></div>}
    </details>
}
