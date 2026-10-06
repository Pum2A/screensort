export type ScanSummaryData = {
    newImages: number
    rescannedImages: number
    unchangedImages: number
    rejectedFiles: number
}

type ScanSummaryProps = {
    summary: ScanSummaryData
}

export function ScanSummary({summary}: ScanSummaryProps) {
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
            <h2 id="scan-summary-title">Podsumowanie ostatniego skanu</h2>
            <p className="scan-summary-note">Te liczniki opisują ten skan, nie cały katalog w bazie.</p>
            <dl className="scan-summary-grid">
                {counters.map(({label, count, description}) => (
                    <div className="scan-summary-card" key={label}>
                        <dt>{label}</dt>
                        <dd>
                            <strong>{count}</strong>
                            <p>{description}</p>
                        </dd>
                    </div>
                ))}
            </dl>
            <p className="scan-summary-note">
                Zgodność rozmiaru i daty modyfikacji nie jest ponowną weryfikacją zawartości pliku.
            </p>
        </section>
    )
}
