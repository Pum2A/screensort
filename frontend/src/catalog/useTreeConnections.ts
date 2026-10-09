import {useLayoutEffect, useRef, useState} from 'react'
import {treeConnections} from './galleryLayout'
import type {TreeBox} from './galleryLayout'

type Connections = {width: number, height: number, paths: string[]}

export function useTreeConnections(fileId: number) {
    const tree = useRef<HTMLElement>(null)
    const [connections, setConnections] = useState<Connections | null>(null)
    useLayoutEffect(() => {
        const element = tree.current
        const preview = element?.querySelector('[data-tree-preview]')
        const branches = element?.querySelectorAll('[data-tree-branch]')
        if (!element || !preview || !branches) return
        let frame = 0
        const measure = () => {
            const bounds = element.getBoundingClientRect()
            if (!bounds.width || !bounds.height) return
            const relativeBox = (node: Element): TreeBox => {
                const box = node.getBoundingClientRect()
                return {left: box.left - bounds.left, top: box.top - bounds.top, width: box.width, height: box.height}
            }
            const next = {
                width: bounds.width, height: bounds.height,
                paths: treeConnections(relativeBox(preview), Array.from(branches, relativeBox)),
            }
            setConnections((previous) => previous && previous.width === next.width && previous.height === next.height
                && previous.paths.length === next.paths.length
                && previous.paths.every((path, index) => path === next.paths[index]) ? previous : next)
        }
        measure()
        const observer = new ResizeObserver(() => {
            cancelAnimationFrame(frame)
            frame = requestAnimationFrame(measure)
        })
        // Rozwinięcie gałęzi zmienia geometrię, ale nie zmienia danych zdjęcia.
        for (const node of [element, preview, ...branches]) observer.observe(node)
        return () => {
            observer.disconnect()
            cancelAnimationFrame(frame)
        }
    }, [fileId])
    return {tree, connections}
}
