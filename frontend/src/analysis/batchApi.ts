import {requestJson, requestJsonOrNull} from '../shared/http.js'
import type {ErrorMessages} from '../shared/http.js'
import {batchSchema, organizationPlanPageSchema} from './batchTypes.js'
import type {Batch, OrganizationPlanPage} from './batchTypes.js'

export type {Batch, BatchItem, OrganizationDecision, OrganizationPlanItem, OrganizationPlanPage} from './batchTypes.js'

const messages: ErrorMessages = {
    byStatus: {
        400: 'Sprawdź folder i wybrane opcje.',
        409: 'Zadanie jest już w toku lub folder wymaga skanu. Poczekaj na bieżący obraz.',
        503: 'Uruchom silnik analizy i spróbuj ponownie.',
    },
    fallback: 'Nie udało się odczytać zadania. Spróbuj ponownie.',
}

const batchPath = (id: string) => `/api/organization/batches/${encodeURIComponent(id)}`

export const organizationKeys = {
    latest: (folderPath: string) => ['organization', 'latest', folderPath] as const,
    batch: (id: string) => ['organization', 'batch', id] as const,
    plan: (id: string, page: number) => ['organization', 'batch', id, 'plan', page] as const,
}

export function latestBatch(folderPath: string, signal?: AbortSignal): Promise<Batch | null> {
    return requestJsonOrNull(`/api/organization/batches/latest?${new URLSearchParams({folderPath})}`, batchSchema, messages, {signal})
}

export function startBatch(folderPath: string, ocr: boolean, visual: boolean, signal?: AbortSignal): Promise<Batch> {
    return requestJson('/api/organization/batches', batchSchema, messages, {method: 'POST', signal, body: {folderPath, ocr, visual}})
}

export function readBatch(id: string, signal?: AbortSignal): Promise<Batch> {
    return requestJson(batchPath(id), batchSchema, messages, {signal})
}

export function organizationPlan(id: string, page: number, signal?: AbortSignal): Promise<OrganizationPlanPage> {
    return requestJson(`${batchPath(id)}/plan?page=${page}`, organizationPlanPageSchema, messages, {signal})
}

export function controlBatch(id: string, action: 'PAUSE' | 'RESUME' | 'CANCEL', signal?: AbortSignal): Promise<Batch> {
    return requestJson(`${batchPath(id)}/control`, batchSchema, messages, {method: 'POST', signal, body: {action}})
}
