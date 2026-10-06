import {useState} from 'react'
import {Icon} from '../ui/Icon'

// Rodzic nadaje key=src: inny obraz dostaje świeży stan ładowania i błędu.
export function CatalogImage({src, name, preview = false}: {src: string, name: string, preview?: boolean}) {
    const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
    const [attempt, setAttempt] = useState(0)
    const url = attempt === 0 ? src : `${src}&retry=${attempt}`
    return <div className={`catalog-image ${preview ? 'catalog-image-large' : ''}`}>
        {status !== 'error' && <img src={url} alt={name} loading={preview ? 'eager' : 'lazy'}
            decoding="async" className={status === 'ready' ? 'is-ready' : ''}
            onLoad={() => setStatus('ready')} onError={() => setStatus('error')}/>}
        {status === 'loading' && <span className="image-placeholder" aria-hidden="true"><Icon name="image"/></span>}
        {status === 'error' && <span className="image-unavailable"><Icon name="image"/><span>Podgląd niedostępny</span>
            {preview && <><small>Plik mógł zostać zmieniony lub usunięty. Oryginał pozostaje bez zmian.</small>
                <button type="button" className="text-button" onClick={() => {
                    setStatus('loading')
                    setAttempt((current) => current + 1)
                }}><Icon name="refresh"/>Spróbuj ponownie</button></>}
        </span>}
    </div>
}
