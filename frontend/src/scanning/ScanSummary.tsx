import type {ScanSummaryData} from './types.js'

type ScanSummaryProps = {
    summary: ScanSummaryData
    completed?: boolean
}

export function ScanSummary({summary, completed = true}: ScanSummaryProps) {
    const counters = [
        {
            label: 'Nowe',
            count: summary.newImages,
            description: 'Pierwszy odczyt obrazu i obliczenie SHA-256.',
        },
        {
            label: 'Sprawdzone ponownie',
            count: summary.rescannedImages,
            description: 'Znany obraz z innym rozmiarem lub datą modyfikacji.',
        },
        {
            label: 'Znane, bez zmian',
            count: summary.unchangedImages,
            description: 'Zgodne metadane. Wykorzystano zapisany SHA-256.',
        },
        {
            label: 'Odrzucone',
            count: summary.rejectedFiles,
            description: 'Pliki, które nie przeszły kontroli skanera.',
        },
    ]

    return (
        <section className="scan-summary" aria-labelledby="scan-summary-title">
            <h2 id="scan-summary-title">{completed ? 'Ostatni skan zakończony' : 'Liczniki tego zadania'}</h2>
            <dl className="scan-summary-grid">
                {counters.map(({label, count, description}) => (
                    <div className="scan-summary-card" key={label}>
                        <dt>{label}</dt>
                        <dd title={description}>{count}</dd>
                    </div>
                ))}
            </dl>
            <details className="scan-summary-help"><summary>Co oznaczają te liczby?</summary>
                <p>Te liczniki opisują ostatni skan, nie cały katalog w bazie.</p>
                <ul>{counters.map(({label, description}) => <li key={label}>{label}: {description}</li>)}</ul>
                <p>Zgodność rozmiaru i daty modyfikacji nie jest ponowną weryfikacją zawartości pliku.</p>
            </details>
        </section>
    )
}
