import {CheckCheck, Code2, FileText, Folder, FolderTree, Gamepad2, Image, Images, MessageCircleQuestion} from 'lucide-react'
import type {BatchItem} from '../api.js'
import {effectiveCategory} from './filters.js'
import {categoryLabels} from './state.js'

const icons = {PROGRAMMING: Code2, DOCUMENTS: FileText, GAMES: Gamepad2, PHOTOGRAPHS: Image, OTHER: Folder, REVIEW: MessageCircleQuestion}

export function ReviewCategoryTree({items, filter, onFilter, disabled}: {items: BatchItem[]; filter: string; onFilter: (filter: string) => void; disabled: boolean}) {
    const groups = {...categoryLabels, REVIEW: 'Do przypisania'}
    const counts = new Map<string, number>()
    for (const item of items) counts.set(effectiveCategory(item), (counts.get(effectiveCategory(item)) ?? 0) + 1)
    return <nav className="review-category-tree" aria-label="Kategorie propozycji na tej stronie">
        <span className="eyebrow">NA TEJ STRONIE</span>
        <button className="review-category-root" disabled={disabled} aria-pressed={filter === 'ALL'} onClick={() => onFilter('ALL')}><FolderTree size={16}/>Wszystkie<span>{items.length}</span></button>
        <ul>{Object.entries(groups).map(([category, label]) => {
            const count = counts.get(category) ?? 0
            if (!count) return null
            const Symbol = icons[category as keyof typeof icons] ?? Images
            return <li key={category} data-category={category}><button disabled={disabled} aria-pressed={filter === 'category:' + category} onClick={() => onFilter('category:' + category)}>
                <Symbol size={15}/><span>{label}</span><b>{count}</b></button></li>
        })}</ul>
        <p><CheckCheck size={14}/>Kategorie są propozycjami.<br/>Ty podejmujesz decyzję.</p>
    </nav>
}
