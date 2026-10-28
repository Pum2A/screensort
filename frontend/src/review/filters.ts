import type {BatchItem} from '../analysis/batchApi.js'
import {isCategory} from './state.js'

export function effectiveCategory(item: BatchItem): string {
    const category = item.review?.matchesCatalog && item.review.decision === 'APPROVED' ? item.review.category : item.category
    return isCategory(category) ? category : 'REVIEW'
}
