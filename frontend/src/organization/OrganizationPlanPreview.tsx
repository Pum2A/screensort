import {useCallback, useEffect, useState} from 'react'
import {isTauri} from '@tauri-apps/api/core'
import {open} from '@tauri-apps/plugin-dialog'
import {ChevronLeft, ChevronRight, CircleAlert, FolderOpen, LoaderCircle, X} from 'lucide-react'
import {organizationPlan} from './api.js'
import type {OrganizationPlanPage} from './api.js'
import {categoryLabels} from '../review/state.js'
import {OrganizationExport} from './export/OrganizationExport.js'
import './OrganizationPlanPreview.css'

type Props = {batchId: string; revision: number; disabled: boolean; onClose: () => void;
    onBusyChange: (busy: boolean) => void}

export function OrganizationPlanPreview({batchId, revision, disabled, onClose, onBusyChange}: Props) {
    const desktop = isTauri()
    const [targetFolder, setTargetFolder] = useState('')
    const [pageNumber, setPageNumber] = useState(0)
    const [page, setPage] = useState<OrganizationPlanPage | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [exporting, setExporting] = useState(false)
    const reportBusy = useCallback((busy: boolean) => {setExporting(busy); onBusyChange(busy)}, [onBusyChange])

    useEffect(() => {
        const controller = new AbortController()
        void organizationPlan(batchId, pageNumber, controller.signal)
            .then(result => {if (!controller.signal.aborted) {setPage(result); setError(null)}})
            .catch(cause => {if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Nie udało się wczytać planu.')})
            .finally(() => {if (!controller.signal.aborted) setLoading(false)})
        return () => controller.abort()
    }, [batchId, pageNumber, revision])

    async function chooseTarget() {
        try {
            const selected = await open({directory: true, multiple: false, title: 'Wybierz miejsce dla uporządkowanej kopii'})
            if (typeof selected === 'string') setTargetFolder(selected)
        } catch {setError('Nie udało się otworzyć wyboru folderu docelowego.')}
    }

    function changePage(next: number) {
        setLoading(true); setError(null); setPageNumber(next)
    }

    return <section className="organization-plan" aria-labelledby="organization-plan-title">
        <div className="organization-plan-heading"><div><span className="eyebrow">ZATWIERDZONE OBRAZY · UPORZĄDKOWANA KOPIA</span>
            <h3 id="organization-plan-title">Plan folderów i nazw</h3>
            <p>Pokazujemy tylko aktualne propozycje, które zostały przez Ciebie zatwierdzone.</p></div>
            <button type="button" className="icon-button" disabled={exporting} aria-label="Zamknij podgląd planu" onClick={onClose}><X size={17}/></button></div>

        <div className="organization-plan-destination">
            <div><FolderOpen size={17}/><span><strong>Miejsce dla uporządkowanej kopii</strong>
                <small>{targetFolder || 'Wybierz folder, aby zobaczyć pełne ścieżki docelowe.'}</small></span></div>
            {desktop ? <button type="button" className="secondary-button" disabled={exporting || disabled} onClick={() => void chooseTarget()}><FolderOpen size={16}/>
                {targetFolder ? 'Zmień miejsce' : 'Wybierz miejsce'}</button>
                : <label>Folder docelowy<input disabled={exporting || disabled} value={targetFolder} onChange={event => setTargetFolder(event.target.value)} placeholder="Np. D:\\Uporządkowane"/></label>}
        </div>

        <OrganizationExport batchId={batchId} destinationFolder={targetFolder} totalApproved={page?.totalFiles ?? 0}
            disabled={disabled || loading || !!error} onBusyChange={reportBusy}/>

        {loading ? <p className="inline-loading" role="status"><LoaderCircle size={17} className="spin"/>Wczytujemy zatwierdzone pozycje…</p>
            : error ? <div className="feedback feedback-error" role="alert"><CircleAlert size={17}/>{error}</div>
                : page?.totalFiles === 0 ? <div className="organization-plan-empty"><p>Nie ma jeszcze zatwierdzonych, aktualnych propozycji do ułożenia.</p>
                    <small>Zatwierdź nazwę i kategorię przy wybranych obrazach. Pominięte i nieaktualne decyzje nie trafią do planu.</small></div>
                    : <>
                        <div className="organization-plan-summary"><strong>{page?.totalFiles ?? 0} zatwierdzonych obrazów</strong>
                            <span>Plan grupuje je według wybranej kategorii.</span></div>
                        <div className="organization-plan-list" aria-label="Pozycje planu">
                            {page?.files.map(file => <article className="organization-plan-row" key={file.fileId}>
                                <div className="organization-plan-source"><span>OBECNIE</span><strong title={file.relativePath}>{file.relativePath}</strong></div>
                                <ChevronRight size={16} aria-hidden="true"/>
                                <div className="organization-plan-target"><span>{categoryLabels[file.category]}</span>
                                    <strong title={previewPath(targetFolder, file.category, file.approvedName)}>
                                        {targetFolder ? previewPath(targetFolder, file.category, file.approvedName) : `${categoryLabels[file.category]}\\${file.approvedName}`}
                                    </strong></div>
                            </article>)}
                        </div>
                        <nav className="organization-plan-pagination" aria-label="Strony planu">
                            <span>{(pageNumber * (page?.pageSize ?? 50)) + 1}–{Math.min((pageNumber + 1) * (page?.pageSize ?? 50), page?.totalFiles ?? 0)} z {page?.totalFiles ?? 0}</span>
                            <div><button type="button" className="icon-button" aria-label="Poprzednia strona planu" disabled={pageNumber === 0 || loading} onClick={() => changePage(pageNumber - 1)}><ChevronLeft size={17}/></button>
                                <button type="button" className="icon-button" aria-label="Następna strona planu" disabled={!page?.hasNext || loading} onClick={() => changePage(pageNumber + 1)}><ChevronRight size={17}/></button></div>
                        </nav>
                    </>}
        <p className="organization-plan-safety">Nazwy są sprawdzane ponownie podczas zapisu. Przy konflikcie dodamy dopisek „(2)”; istniejące foldery wynikowe dostaną osobny numer.</p>
    </section>
}

function previewPath(root: string, category: keyof typeof categoryLabels, name: string): string {
    const separator = root.includes('\\') ? '\\' : '/'
    return [root.replace(/[\\/]+$/, ''), 'ScreenSort', categoryLabels[category], name].filter(Boolean).join(separator)
}
