import {FolderOpen, ScanLine, Image, Grid2X2, ShieldCheck, X, ChevronLeft, ChevronRight,
    RefreshCw, Check, ChevronDown, Eye, Type, History, ZoomIn} from 'lucide-react'

// Explicit imports keep the icon set tree-shakable. No custom SVG path dictionary.
const icons = {folder: FolderOpen, scan: ScanLine, image: Image, grid: Grid2X2, shield: ShieldCheck,
    close: X, left: ChevronLeft, right: ChevronRight, refresh: RefreshCw, check: Check,
    down: ChevronDown, eye: Eye, text: Type, history: History, zoom: ZoomIn} as const

export function Icon({name, className = ''}: {name: keyof typeof icons, className?: string}) {
    const Component = icons[name]
    return <Component className={`icon ${className}`} size={20} strokeWidth={1.8} aria-hidden="true"/>
}
