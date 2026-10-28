import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'
import {z} from 'zod'
import {libraryKeys} from '../gallery/libraryApi.js'
import {requestJson} from '../shared/http.js'
import type {ErrorMessages} from '../shared/http.js'

const languageSchema = z.object({language: z.enum(['PL', 'EN'])})
type Language = z.infer<typeof languageSchema>['language']

const messages: ErrorMessages = {byCode: {}, fallback: 'Nie udało się zmienić języka nazw. Spróbuj ponownie.'}
const languageKey = ['settings', 'naming-language'] as const
const options: {language: Language, label: string, title: string}[] = [
    {language: 'PL', label: 'PL', title: 'Nazwy po polsku'},
    {language: 'EN', label: 'EN', title: 'Nazwy po angielsku'},
]

/**
 * The language of every proposed name. The backend rewrites waiting proposals from the saved
 * analysis, so the cards change at once without running the model again.
 */
export function NamingLanguageSwitch({disabled}: {disabled: boolean}) {
    const queryClient = useQueryClient()
    const current = useQuery({
        queryKey: languageKey,
        queryFn: ({signal}) => requestJson('/api/settings/naming-language', languageSchema, messages, {signal}),
    })
    const change = useMutation({
        mutationFn: (language: Language) => requestJson('/api/settings/naming-language', languageSchema, messages,
            {method: 'PUT', body: {language}}),
        onSuccess: saved => {
            queryClient.setQueryData(languageKey, saved)
            void queryClient.invalidateQueries({queryKey: libraryKeys.all})
        },
    })
    const language = change.isPending ? change.variables : current.data?.language

    return <div className="naming-language">
        <span id="naming-language-label">Język nazw</span>
        <div className="lib-chips" role="group" aria-labelledby="naming-language-label">
            {options.map(option => <button key={option.language} title={option.title}
                aria-pressed={language === option.language}
                disabled={disabled || current.isPending || change.isPending}
                onClick={() => { if (language !== option.language) change.mutate(option.language) }}>
                {option.label}</button>)}
        </div>
        {(change.isError || current.isError) && <small role="alert">{(change.error ?? current.error)?.message}</small>}
    </div>
}
