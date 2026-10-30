import type {Batch} from './batchApi.js'

/**
 * Whether a "Uporządkuj z AI" click should analyse the folder: always without a result or when
 * paused, and after a finished analysis only if the folder has gained images since.
 */
export function wantsAnalysis(job: Batch | null, fileCount: number): boolean {
    if (!job) return true
    if (job.status === 'PAUSED') return true
    return job.status !== 'RUNNING' && fileCount > job.total
}

export type OrganizeState = {
    ready: boolean
    /** Scanning, another action, an open edit or an export: wait until it ends. */
    busyElsewhere: boolean
    fileCount: number
    /** False in the browser, where the engine is started outside the app. */
    desktop: boolean
    engineStarting: boolean
    engineError: string | null
    engineRequested: boolean
}

export type OrganizeStep = 'analyze' | 'start-engine' | 'wait' | 'give-up'

/** The next step of a pending "Uporządkuj z AI": start the engine once, then the analysis. */
export function organizeStep(state: OrganizeState): OrganizeStep {
    if (state.ready) return state.busyElsewhere || state.fileCount === 0 ? 'wait' : 'analyze'
    if (!state.desktop) return 'wait'
    if (state.engineRequested) return !state.engineStarting && state.engineError ? 'give-up' : 'wait'
    return state.engineStarting ? 'wait' : 'start-engine'
}
