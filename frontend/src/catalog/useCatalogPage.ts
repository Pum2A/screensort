import {useState} from 'react'
import {useQuery, useQueryClient} from '@tanstack/react-query'
import {fetchCatalog} from './api.js'

export const CATALOG_PAGE_SIZE = 24
const LOAD_ERROR = 'Nie udało się pobrać katalogu. Sprawdź folder i działanie backendu.'

export const catalogKeys = {
    all: ['catalog'] as const,
    folder: (folder: string) => ['catalog', folder] as const,
    page: (folder: string, pageNumber: number) => ['catalog', folder, pageNumber] as const,
}

/** The catalog folder on screen and its current page. TanStack Query owns the loaded data. */
export function useCatalogPage(initialFolder: string) {
    const queryClient = useQueryClient()
    const [folder, setFolder] = useState(initialFolder)
    const [pageNumber, setPageNumber] = useState(0)
    // Bumped on every show(): the gallery remounts, so its search and selection start fresh.
    const [generation, setGeneration] = useState(0)
    const enabled = folder.trim() !== ''
    const query = useQuery({
        queryKey: catalogKeys.page(folder, pageNumber),
        queryFn: ({signal}) => fetchCatalog(folder, pageNumber, CATALOG_PAGE_SIZE, signal),
        enabled,
    })

    /** Shows a folder from its first page, reloading it when it is already shown (e.g. after a scan). */
    function show(next: string) {
        setFolder(next)
        setPageNumber(0)
        setGeneration(current => current + 1)
        void queryClient.invalidateQueries({queryKey: catalogKeys.folder(next)})
    }

    function changePage(direction: -1 | 1) {
        setPageNumber(current => Math.max(0, current + direction))
    }

    const page = enabled ? query.data ?? null : null
    return {
        folder,
        pageNumber,
        generation,
        page,
        loading: enabled && query.isFetching,
        error: enabled && query.isError ? LOAD_ERROR : null,
        show,
        changePage,
        retry: () => void query.refetch(),
    }
}

export type CatalogPageState = ReturnType<typeof useCatalogPage>
