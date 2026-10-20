import type {BatchItem} from '../api.js'
import {isCategory} from './state.js'

export function effectiveCategory(item: BatchItem): string {
    const category = item.review?.matchesCatalog && item.review.decision === 'APPROVED' ? item.review.category : item.category
    return isCategory(category) ? category : 'REVIEW'
}

export function filterReviewPage(items: BatchItem[], filter: string): BatchItem[] {
    return items.filter(item => {
        if (filter === 'ALL') return true
        if (filter === 'APPROVED' || filter === 'SKIPPED') return item.review?.matchesCatalog && item.review.decision === filter
        if (filter === 'FAILED') return item.status === 'FAILED'
        if (filter === 'UNREVIEWED') return item.status === 'COMPLETED' && !item.review?.matchesCatalog
        return filter.startsWith('category:') && effectiveCategory(item) === filter.slice(9)
    })
}
