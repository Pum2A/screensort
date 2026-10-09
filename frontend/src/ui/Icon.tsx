const paths = {
    folder: 'M3 7a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z',
    scan: 'M8 3H5a2 2 0 0 0-2 2v3m13-5h3a2 2 0 0 1 2 2v3M3 16v3a2 2 0 0 0 2 2h3m8 0h3a2 2 0 0 0 2-2v-3M4 12h16',
    image: 'M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm-2 14 6-6 4 4 3-3 5 5M15 7h.01',
    grid: 'M3 3h7v7H3Zm11 0h7v7h-7ZM3 14h7v7H3Zm11 0h7v7h-7Z',
    shield: 'M12 3 3 7v6c0 5 9 9 9 9s9-4 9-9V7Zm-4 9 3 3 5-6',
    close: 'm6 6 12 12M6 18 18 6',
    left: 'm14 6-6 6 6 6',
    right: 'm10 6 6 6-6 6',
    refresh: 'M20 7v5h-5M4 17v-5h5M6 7a7 7 0 0 1 12-1l2 3M4 15l2 3a7 7 0 0 0 12-1',
    check: 'm5 12 4 4L19 6',
    down: 'm6 9 6 6 6-6',
    eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7Zm13 0a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
    text: 'M4 5h16M12 5v14M8 19h8',
    history: 'M3 11a9 9 0 1 1 2.5 7M3 4v7h7M12 7v5l3 2',
    zoom: 'M11 5v12M5 11h12m4 10-5-5M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z',
} as const

export function Icon({name, className = ''}: {name: keyof typeof paths, className?: string}) {
    return <svg className={`icon ${className}`} viewBox="0 0 24 24" fill="none"
                stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"
                aria-hidden="true"><path d={paths[name]}/></svg>
}
