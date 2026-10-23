import {useEffect, useRef, useState} from 'react'
import {Icon} from '../ui/Icon.js'
import './CatalogImage.css'
import {desktopImage, isDesktop} from '../desktop/transport.js'
import {imageCache} from './imageCache.js'
import {imageFailure, imageFailureText, isPermanent} from './imageFailure.js'
import type {ImageFailure} from './imageFailure.js'
import {thumbnailSlots, withRetry} from './imageLoading.js'

// Rodzic nadaje key=src: inny obraz dostaje świeży stan ładowania i błędu.
export function CatalogImage({src, name, preview = false}: {src: string, name: string, preview?: boolean}) {
    const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
    const [failure, setFailure] = useState<ImageFailure>('unavailable')
    const [attempt, setAttempt] = useState(0)
    const url = attempt === 0 ? src : `${src}&retry=${attempt}`
    const [nativeSrc, setNativeSrc] = useState<string | null>(null)
    const container = useRef<HTMLDivElement>(null)
    useEffect(() => {
        if (!isDesktop()) return
        const controller = new AbortController()
        let objectUrl: string | null = null
        let requested = false
        function show(blob: Blob) {
            objectUrl = URL.createObjectURL(blob)
            setNativeSrc(objectUrl)
        }
        async function load() {
            if (requested) return
            requested = true
            // Keyed by src, not url: a manual retry stores the image where the next visit looks.
            // The catalog row decides what is shown, so a file deleted since the scan keeps its
            // thumbnail until the next scan, the same as its name and details.
            const cached = imageCache.get(src)
            if (cached) { show(cached); return }
            const fetchImage = () => desktopImage(url)
            try {
                // The opened preview skips the queue: it is the one image the user is looking at.
                const blob = await withRetry(preview ? fetchImage
                    : () => thumbnailSlots.run(fetchImage, controller.signal), controller.signal,
                    {shouldRetry: cause => !isPermanent(imageFailure(cause))})
                // Kept even if the user already scrolled away: the next visit gets it for free.
                imageCache.set(src, blob)
                if (!controller.signal.aborted) show(blob)
            } catch (cause) {
                if (controller.signal.aborted) return
                setFailure(imageFailure(cause))
                setStatus('error')
            }
        }
        const observer = new IntersectionObserver(entries => {
            if (entries.some(entry => entry.isIntersecting)) { observer.disconnect(); void load() }
        }, {rootMargin: '180px'})
        // An image already in memory costs nothing, so it does not wait until it scrolls into view.
        if (preview || imageCache.get(src)) void load()
        else if (container.current) observer.observe(container.current)
        // Leaving the page cancels thumbnails that are still waiting for a slot.
        return () => { controller.abort(); observer.disconnect(); if (objectUrl) URL.revokeObjectURL(objectUrl) }
    }, [src, url, preview])
    const actualSrc = isDesktop() ? nativeSrc : url
    return <div ref={container} className={`catalog-image ${preview ? 'catalog-image-large' : ''}`}>
        {status !== 'error' && actualSrc && <img src={actualSrc} alt={name} loading={preview ? 'eager' : 'lazy'}
            decoding="async" className={status === 'ready' ? 'is-ready' : ''}
            onLoad={() => setStatus('ready')} onError={() => setStatus('error')}/>}
        {status === 'loading' && <span className="image-placeholder" aria-hidden="true"><Icon name="refresh" className="spin"/></span>}
        {status === 'error' && <span className="image-unavailable"><Icon name="image"/>
            <span>{imageFailureText[failure].label}</span>
            {preview && <><small>{imageFailureText[failure].detail} Oryginał pozostaje bez zmian.</small>
                {!isPermanent(failure) && <button type="button" className="text-button" onClick={() => {
                    setStatus('loading')
                    setFailure('unavailable')
                    setNativeSrc(null)
                    setAttempt((current) => current + 1)
                }}><Icon name="refresh"/>Spróbuj ponownie</button>}</>}
        </span>}
    </div>
}
