import {useEffect, useRef, useState} from 'react'
import {Icon} from '../ui/Icon.js'
import './CatalogImage.css'
import {desktopImage, isDesktop} from '../desktop/transport.js'

// Rodzic nadaje key=src: inny obraz dostaje świeży stan ładowania i błędu.
export function CatalogImage({src, name, preview = false}: {src: string, name: string, preview?: boolean}) {
    const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
    const [attempt, setAttempt] = useState(0)
    const url = attempt === 0 ? src : `${src}&retry=${attempt}`
    const [nativeSrc, setNativeSrc] = useState<string | null>(null)
    const container = useRef<HTMLDivElement>(null)
    useEffect(() => {
        if (!isDesktop()) return
        let stopped = false
        let objectUrl: string | null = null
        let requested = false
        async function load() {
            if (requested) return
            requested = true
            try {
                const value = await desktopImage(url)
                if (stopped) { URL.revokeObjectURL(value); return }
                objectUrl = value
                setNativeSrc(value)
            } catch { if (!stopped) setStatus('error') }
        }
        const observer = new IntersectionObserver(entries => {
            if (entries.some(entry => entry.isIntersecting)) { observer.disconnect(); void load() }
        }, {rootMargin: '180px'})
        if (preview) void load()
        else if (container.current) observer.observe(container.current)
        return () => { stopped = true; observer.disconnect(); if (objectUrl) URL.revokeObjectURL(objectUrl) }
    }, [url, preview])
    const actualSrc = isDesktop() ? nativeSrc : url
    return <div ref={container} className={`catalog-image ${preview ? 'catalog-image-large' : ''}`}>
        {status !== 'error' && actualSrc && <img src={actualSrc} alt={name} loading={preview ? 'eager' : 'lazy'}
            decoding="async" className={status === 'ready' ? 'is-ready' : ''}
            onLoad={() => setStatus('ready')} onError={() => setStatus('error')}/>}
        {status === 'loading' && <span className="image-placeholder" aria-hidden="true"><Icon name="refresh" className="spin"/></span>}
        {status === 'error' && <span className="image-unavailable"><Icon name="image"/><span>Podgląd niedostępny</span>
            {preview && <><small>Plik mógł zostać zmieniony lub usunięty. Oryginał pozostaje bez zmian.</small>
                <button type="button" className="text-button" onClick={() => {
                    setStatus('loading')
                    setNativeSrc(null)
                    setAttempt((current) => current + 1)
                }}><Icon name="refresh"/>Spróbuj ponownie</button></>}
        </span>}
    </div>
}
