import {useQuery} from '@tanstack/react-query'
import {fetchLibrary, libraryKeys} from './api.js'

/** One query for the library, the duplicates screen and the sidebar counts, so they never disagree. */
export function useLibrary(folderPath: string) {
    return useQuery({
        queryKey: libraryKeys.folder(folderPath),
        queryFn: ({signal}) => fetchLibrary(folderPath, signal),
        enabled: folderPath.trim() !== '',
    })
}
