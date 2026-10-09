import {useId, useState} from 'react'
import {AnimatedBranch} from '../../ui/AnimatedBranch'
import {Icon} from '../../ui/Icon'
import {analysisExamples} from './examples'
import {ReviewActions} from './ReviewActions'
import styles from './AnalysisDemo.module.css'

type Review = {name: string, approved: boolean}

export function AnalysisDemo({onClose}: {onClose: () => void}) {
    const id = useId()
    const [selected, setSelected] = useState(analysisExamples[0].id)
    const [reviews, setReviews] = useState<Record<string, Review>>({})
    const example = analysisExamples.find(item => item.id === selected) ?? analysisExamples[0]
    const review = reviews[example.id] ?? {name: example.proposedName, approved: false}

    return <aside className={styles.panel} aria-label="Makieta analizy — przykładowe wyniki">
        <header className={styles.heading}><div><h3>Co jest na obrazie?</h3><span>DEMO / BEZ OCR</span></div>
            <button type="button" className="icon-button" aria-label="Zamknij makietę analizy" onClick={onClose}><Icon name="close"/></button>
        </header>
        <div className={styles.body}>
            <p className={styles.notice}>Przykłady niezwiązane z Twoimi plikami. Nie uruchamiamy analizy ani nie wysyłamy obrazów.</p>
            <label className={styles.exampleLabel} htmlFor={id}>Przykład</label>
            <select id={id} className={styles.select} value={selected} onChange={(event) => {
                const next = analysisExamples.find(item => item.id === event.target.value)
                if (next) setSelected(next.id)
            }}>{analysisExamples.map(item => <option key={item.id} value={item.id}>{item.label}</option>)}</select>
            <div className={styles.selected}>
                <div className={`${styles.art} ${styles[example.id]}`} role="img" aria-label={`Ilustracja przykładu: ${example.label}`}>
                    <span className={styles.shapeA}/><span className={styles.shapeB}/><span className={styles.head}/><span className={styles.bodyShape}/>
                </div>
                <div className={styles.description}><p>{example.originalName}</p><span className={review.approved ? styles.approved : styles.pending}>
                    {review.approved ? 'Propozycja zatwierdzona w demo' : 'Czeka na Twoją decyzję'}</span></div>
            </div>
            <div className={styles.tree}>
                <AnimatedBranch label="Analiza wizualna" icon={<Icon name="eye"/>} defaultOpen>
                    <p className={styles.result}>{example.description}</p>
                    <div className={styles.tags}>{example.tags.map(tag => <span key={tag}>{tag}</span>)}</div>
                    <p className={styles.caption}>Proponowana kategoria</p><p className={styles.result}>{example.category}</p>
                </AnimatedBranch>
                <AnimatedBranch label="Tekst na obrazie" icon={<Icon name="text"/>} defaultOpen>
                    <pre className={styles.ocr}>{example.ocrText}</pre>
                </AnimatedBranch>
                <AnimatedBranch label="Historia" icon={<Icon name="history"/>}>
                    <p className={styles.result}>{review.approved ? 'Zatwierdzono przykład w pamięci Reacta.' : 'Przykład jeszcze niezatwierdzony.'}</p>
                    <p className={styles.caption}>Nie jest to historia z PostgreSQL.</p>
                </AnimatedBranch>
            </div>
            <ReviewActions key={example.id} originalName={example.originalName} name={review.name} approved={review.approved}
                onChange={(name) => setReviews(previous => ({...previous, [example.id]: {name, approved: false}}))}
                onApprove={() => setReviews(previous => ({...previous, [example.id]: {...review, approved: true}}))}/>
        </div>
    </aside>
}
