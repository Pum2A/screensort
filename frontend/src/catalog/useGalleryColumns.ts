import {useLayoutEffect, useRef, useState} from 'react'

export function useGalleryColumns() {
    const grid = useRef<HTMLDivElement>(null)
    const [columns, setColumns] = useState(1)
    useLayoutEffect(() => {
        const element = grid.current
        if (!element) return
        const measure = () => {
            // CSS wylicza rzeczywistą liczbę kolumn, także po zmianie szerokości okna.
            const tracks = getComputedStyle(element).gridTemplateColumns.trim().split(/\s+/)
            setColumns(Math.max(1, tracks.length))
        }
        measure()
        const observer = new ResizeObserver(measure)
        observer.observe(element)
        return () => observer.disconnect()
    }, [])
    return {grid, columns}
}
