export type DesktopStatus = {phase: string; backendReady: boolean; analysisReady: boolean; analysisStarting: boolean; error: string | null}

// Native API currently exposes phase text, not step percentages. Do not invent progress.
export function startupStep(status: DesktopStatus | null): number {
    if (status?.backendReady) return 2
    if (status && /backend|migrac/i.test(status.phase)) return 1
    return 0
}

// One unanswered status poll is a hiccup, not a broken app: only this many in a row count as an error.
export const MAX_STATUS_FAILURES = 3

export type StartupState = {status: DesktopStatus | null; failures: number; everReady: boolean}
export type StartupEvent = {kind: 'status'; status: DesktopStatus} | {kind: 'failed'}

export const initialStartup: StartupState = {status: null, failures: 0, everReady: false}

export function nextStartup(state: StartupState, event: StartupEvent): StartupState {
    if (event.kind === 'status') {
        return {status: event.status, failures: 0, everReady: state.everReady || event.status.backendReady}
    }
    return {...state, failures: state.failures + 1}
}

/** What the gate shows: the startup screen until the first ready backend, then always the app. */
export function startupView(state: StartupState): {screen: 'startup' | 'app'; status: DesktopStatus | null; connectionLost: boolean} {
    const lost = state.failures >= MAX_STATUS_FAILURES
    if (!state.everReady) {
        const status = lost
            ? {phase: 'Błąd uruchamiania', backendReady: false, analysisReady: false, analysisStarting: false,
                error: 'Nie udało się odczytać stanu aplikacji. Zamknij ją i spróbuj ponownie.'}
            : state.status
        return {screen: 'startup', status, connectionLost: lost}
    }
    return {screen: 'app', status: state.status, connectionLost: lost || state.status?.backendReady === false}
}

export function elapsedLabel(seconds: number): string {
    const whole = Math.max(0, Math.floor(seconds))
    return whole < 60 ? `${whole} s` : `${Math.floor(whole / 60)} min ${whole % 60} s`
}
