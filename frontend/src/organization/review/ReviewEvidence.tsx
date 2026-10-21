import {useEffect, useState} from 'react'
import {CircleAlert, LoaderCircle} from 'lucide-react'
import {latestAnalysis} from '../../analysis/api'
import type {SavedAnalysis} from '../../analysis/types'

/** Lazy, read-only evidence. Never display a newer/older analysis as this proposal's evidence. */
export function ReviewEvidence({fileId, analysisId, sourceHash}: {fileId: number; analysisId: number; sourceHash: string}) {
    const [open, setOpen] = useState(false)
    const [saved, setSaved] = useState<SavedAnalysis | null>(null)
    const [error, setError] = useState<string | null>(null)
    const [loading, setLoading] = useState(false)
    useEffect(() => {
        if (!open || saved) return
        const controller = new AbortController()
        void latestAnalysis(fileId, controller.signal).then(result => {
            if (controller.signal.aborted) return
            if (!result || result.id !== analysisId || result.result.source_sha256 !== sourceHash) {
                setError('Zapisana analiza zmieniła się. Nie pokazujemy jej jako uzasadnienia tej propozycji.')
            } else setSaved(result)
            setLoading(false)
        }).catch(() => {if (!controller.signal.aborted) {setError('Nie udało się pobrać wyniku analizy.'); setLoading(false)}})
        return () => controller.abort()
    }, [open, saved, fileId, analysisId, sourceHash])
    return <details className="review-evidence" onToggle={event => {
        const next = event.currentTarget.open
        setOpen(next); setLoading(next && !saved); setError(null)
    }}>
        <summary>Zobacz tekst i opis z tej analizy</summary>
        {loading && <p className="inline-loading"><LoaderCircle size={15} className="spin"/>Odczytujemy wynik…</p>}
        {error && <p className="feedback feedback-error" role="alert"><CircleAlert size={16}/>{error}</p>}
        {saved && <div><h4>Opis wizualny</h4><p>{saved.result.visual.status === 'COMPLETED' ? saved.result.visual.result.description : 'Brak poprawnego opisu wizualnego.'}</p>
            <h4>Tekst na obrazie</h4><pre>{saved.result.ocr.status === 'COMPLETED' ? saved.result.ocr.result.text || 'Nie rozpoznano napisów.' : 'Brak poprawnego wyniku OCR.'}</pre>
            <small>Obecna nazwa automatyczna powstaje z opisu wizualnego. Nie jest jeszcze wspólną interpretacją obu wyników.</small></div>}
    </details>
}
