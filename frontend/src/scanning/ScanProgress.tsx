import type {ScanJob} from './types'

const stages = ['PREPARING', 'SCANNING', 'SAVING', 'COMPLETED'] as const
const labels = ['Przygotowanie', 'Sprawdzanie plików', 'Zapis do bazy', 'Gotowe']

export function ScanProgress({job, lost}: {job: ScanJob, lost: boolean}) {
    const processed = Object.values(job.summary).reduce((sum, count) => sum + count, 0)
    const stage = stages.findIndex((status) => status === job.status)
    const heading = lost ? 'Zadanie niedostępne' : job.status === 'FAILED' ? 'Skan nie został ukończony' : labels[stage]
    return <section className="scan-progress" aria-label="Postęp skanu">
        <div className="scan-progress-heading" role="status" aria-live="polite">
            <span>{heading}</span><span>Sprawdzone wpisy: {processed}</span>
        </div>
        <ol className="scan-stages" aria-label="Etapy skanowania">
            {stages.map((status, index) => <li key={status}
                aria-current={!lost && job.status === status ? 'step' : undefined}
                data-current={!lost && job.status === status} data-done={!lost && stage > index}>
                <span aria-hidden="true">{index + 1}</span>{labels[index]}
            </li>)}
        </ol>
        <p>Oryginały pozostają bez zmian. To liczba sprawdzonych wpisów, nie procent ani liczba zapisanych obrazów.</p>
        <details><summary>ID zadania</summary><code>{job.id}</code></details>
    </section>
}
