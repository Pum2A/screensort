import {CircleAlert, LoaderCircle} from 'lucide-react'
import {ScanProgress} from './ScanProgress.js'
import {ScanSummary} from './ScanSummary.js'
import {rejectionLabel} from './rejectionLabels.js'
import type {useScanJob} from './useScanJob.js'

/** Everything a running or finished scan shows above the catalog: progress, errors, skipped files. */
export function ScanFeedback({scan}: {scan: ReturnType<typeof useScanJob>}) {
    return <>
        {scan.error && <div className="feedback feedback-error" role="alert"><CircleAlert size={18}/><div><p>{scan.error}</p>
            {scan.job && !scan.lost && scan.job.status !== 'COMPLETED' && scan.job.status !== 'FAILED' && <button className="text-button" disabled={scan.requestActive} onClick={() => void scan.resume()}>Wznów sprawdzanie zadania</button>}</div></div>}
        {scan.busy && !scan.job && <p className="inline-loading" role="status"><LoaderCircle className="spin" size={17}/>Przygotowujemy skan…</p>}
        {scan.job && <div className="scan-feedback"><ScanProgress job={scan.job} lost={scan.lost}/>
            {scan.job.errorMessage && <div className="feedback feedback-error" role="alert">{scan.job.errorMessage}</div>}
            <ScanSummary summary={scan.job.summary} completed={scan.job.status === 'COMPLETED'}/></div>}
        {scan.rejectionsError && <div className="feedback feedback-error" role="alert">{scan.rejectionsError}<button className="text-button" disabled={scan.requestActive} onClick={() => void scan.retryRejections()}>Ponów pobranie powodów</button></div>}
        {scan.rejections && scan.rejections.totalRejected > 0 && <details className="rejected-details"><summary>Nie wszystkie wpisy są obrazami · pominięto {scan.rejections.totalRejected}</summary>
            {scan.rejections.truncated && <p>Przykłady: {scan.rejections.files.length} z {scan.rejections.totalRejected} pominiętych wpisów.</p>}
            <ul>{scan.rejections.files.map(file => <li key={file.path}><strong>{file.originalName}</strong><span>{rejectionLabel(file.reason)}<details><summary>Kod diagnostyczny</summary><code>{file.reason}</code></details></span></li>)}</ul></details>}
    </>
}
