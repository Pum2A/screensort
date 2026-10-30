import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'
import {z} from 'zod'
import {requestJson} from '../http/http.js'
import type {ErrorMessages} from '../http/http.js'
import type {ScanSummaryData} from '../gallery/reading/types.js'

const settingSchema = z.object({enabled: z.boolean()})
const messages: ErrorMessages = {byCode: {}, fallback: 'Nie udało się zapisać ustawienia. Spróbuj ponownie.'}
const settingKey = ['settings', 'auto-analysis'] as const

/** "Analizuj nowe zdjęcia automatycznie": off until the owner turns it on. */
export function useAutoAnalysis() {
    const queryClient = useQueryClient()
    const setting = useQuery({
        queryKey: settingKey,
        queryFn: ({signal}) => requestJson('/api/settings/auto-analysis', settingSchema, messages, {signal}),
    })
    const change = useMutation({
        mutationFn: (enabled: boolean) => requestJson('/api/settings/auto-analysis', settingSchema, messages,
            {method: 'PUT', body: {enabled}}),
        onSuccess: saved => queryClient.setQueryData(settingKey, saved),
    })
    return {enabled: change.isPending ? change.variables : setting.data?.enabled ?? false,
        loading: setting.isPending || change.isPending, error: (change.error ?? setting.error)?.message ?? null,
        change: change.mutate}
}

/** A folder read that found new or changed images is worth an automatic analysis. */
export function foundNewImages(summary: ScanSummaryData): boolean {
    return summary.newImages + summary.rescannedImages > 0
}
