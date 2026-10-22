import {QueryClientProvider} from '@tanstack/react-query'
import {useState} from 'react'
import type {ReactNode} from 'react'
import {createQueryClient} from './queryClient.js'

export function QueryProvider({children}: {children: ReactNode}) {
    // One client per app mount; useState keeps it stable across re-renders.
    const [client] = useState(createQueryClient)
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}
