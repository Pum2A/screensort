import {QueryClient} from '@tanstack/react-query'
import {ApiError} from './http.js'

export function createQueryClient(): QueryClient {
    return new QueryClient({
        defaultOptions: {
            queries: {
                // A desktop window regains focus all the time; refetching on every focus would hammer the local backend.
                refetchOnWindowFocus: false,
                // The server already answered with a reason (4xx/5xx): retrying would only repeat it. Network hiccups get one retry.
                retry: (failures, error) => !(error instanceof ApiError) && failures < 1,
            },
        },
    })
}
