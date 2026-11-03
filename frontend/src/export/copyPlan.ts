import type {LibraryEntry} from '../gallery/model.js'
import {categories} from '../gallery/model.js'
import type {ImageCategory} from '../review/state.js'

export type CopyFile = {entry: LibraryEntry, name: string}
export type CopyGroup = {category: ImageCategory, files: CopyFile[], bytes: number}

/**
 * What the organized copy will hold: every approved image under its category folder, in the
 * category order the app uses everywhere. Skipped, undecided and outdated images stay out.
 */
export function copyGroups(entries: LibraryEntry[]): CopyGroup[] {
    const groups = new Map<ImageCategory, CopyGroup>()
    for (const entry of entries) {
        if (entry.state !== 'approved' || !entry.category || !entry.item?.approvedName) continue
        const group = groups.get(entry.category) ?? {category: entry.category, files: [], bytes: 0}
        group.files.push({entry, name: entry.item.approvedName})
        group.bytes += entry.file.sizeBytes
        groups.set(entry.category, group)
    }
    return categories.filter(category => groups.has(category)).map(category => groups.get(category) as CopyGroup)
}

/** Where the copy lands: the backend writes into a new "ScreenSort" folder inside the chosen place. */
export function copyRoot(destination: string): string {
    const separator = destination.includes('\\') ? '\\' : '/'
    return destination.replace(/[\\/]+$/, '') + separator + 'ScreenSort'
}
