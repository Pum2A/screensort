import {useId, useState} from 'react'
import {Icon} from '../../ui/Icon'
import {demoNameError} from './reviewName'
import styles from './ReviewActions.module.css'

type Props = {originalName: string, name: string, approved: boolean, onChange: (name: string) => void, onApprove: () => void}

export function ReviewActions({originalName, name, approved, onChange, onApprove}: Props) {
    const id = useId()
    const [error, setError] = useState<string | null>(null)
    return <form className={styles.review} onSubmit={(event) => {
        event.preventDefault()
        const problem = demoNameError(name, originalName)
        setError(problem)
        if (!problem) onApprove()
    }}>
        <label htmlFor={id}>Propozycja nazwy</label>
        <input id={id} value={name} maxLength={255} autoComplete="off" spellCheck={false}
            aria-invalid={Boolean(error)} aria-describedby={`${id}-hint${error ? ` ${id}-error` : ''}`}
            onChange={(event) => { setError(null); onChange(event.target.value) }}/>
        {error && <p id={`${id}-error`} className={styles.error} role="alert">{error}</p>}
        <button type="submit" className="primary-button"><Icon name="check"/>
            {approved ? 'Zatwierdzono w demo' : 'Zatwierdź propozycję (demo)'}</button>
        <p id={`${id}-hint`} className={styles.hint} role="status">
            {approved ? 'Zapamiętano tylko na tym ekranie. Żaden plik ani rekord bazy nie został zmieniony.'
                : 'Demo bez zapisu. Zamknięcie demo lub przeładowanie strony usuwa decyzje.'}</p>
    </form>
}
